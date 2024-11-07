/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.JettyUtils;
import ee.ria.xroad.proxy.conf.KeyConfProvider;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.niis.xroad.proxy.ProxyProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client proxy that handles requests of service clients.
 */
@Slf4j
public class ClientProxy implements InitializingBean, DisposableBean {
    private static final int ACCEPTOR_COUNT = Runtime.getRuntime().availableProcessors();

    // SSL session timeout
    private static final int SSL_SESSION_TIMEOUT = 600;

    private static final String CLIENTPROXY_HANDLERS = SystemProperties.PREFIX + "proxy.clientHandlers";

    private static final String CLIENT_HTTP_CONNECTOR_NAME = "ClientConnector";
    private static final String CLIENT_HTTPS_CONNECTOR_NAME = "ClientSSLConnector";

    private final ProxyProperties.ClientProxyProperties clientProxyProperties;
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final CertChainFactory certChainFactory;

    private final AuthTrustVerifier authTrustVerifier;

    private final Server server = new Server();

    private final HttpClient client;
    private final ClientRestMessageHandler clientRestMessageHandler;
    private final ClientSoapMessageHandler clientSoapMessageHandler;


    /**
     * Constructs and configures a new client proxy.
     *
     * @throws Exception in case of any errors
     */
    public ClientProxy(ProxyProperties.ClientProxyProperties clientProxyProperties, HttpClient httpClient,
                       ClientRestMessageHandler clientRestMessageHandler,
                       ClientSoapMessageHandler clientSoapMessageHandler,
                       GlobalConfProvider globalConfProvider,
                       KeyConfProvider keyConfProvider,
                       ServerConfProvider serverConfProvider,
                       CertChainFactory certChainFactory,
                       AuthTrustVerifier authTrustVerifier) throws Exception {
        this.clientProxyProperties = clientProxyProperties;
        this.client = httpClient;
        this.clientRestMessageHandler = clientRestMessageHandler;
        this.clientSoapMessageHandler = clientSoapMessageHandler;

        this.globalConfProvider = globalConfProvider;
        this.keyConfProvider = keyConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.certChainFactory = certChainFactory;
        this.authTrustVerifier = authTrustVerifier;

        configureServer();
        createConnectors();
        createHandlers();
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        var file = clientProxyProperties.jettyConfigurationFile();

        log.debug("Configuring server from {}", file);
        new XmlConfiguration(JettyUtils.toResource(file)).configure(server);

        final var writer = new Slf4jRequestLogWriter();
        writer.setLoggerName(getClass().getPackage().getName() + ".RequestLog");
        final var reqLog = new CustomRequestLog(writer, CustomRequestLog.EXTENDED_NCSA_FORMAT
                + " \"%{X-Forwarded-For}i\"");
        server.setRequestLog(reqLog);
    }

    private void createConnectors() throws Exception {
        log.trace("createConnectors()");

        createClientHttpConnector(clientProxyProperties.connectorHost(), clientProxyProperties.clientHttpPort());
        createClientHttpsConnector(clientProxyProperties.connectorHost(), clientProxyProperties.clientHttpsPort());
    }

    private void createClientHttpConnector(String hostname, int port) {
        log.trace("createClientHttpConnector({}, {})", hostname, port);

        ServerConnector connector = new ServerConnector(server, ACCEPTOR_COUNT, -1);

        connector.setName(CLIENT_HTTP_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);
        connector.setIdleTimeout(clientProxyProperties.clientConnectorInitialIdleTime());

        applyConnectionFactoryConfig(connector);
        server.addConnector(connector);

        log.info("Client HTTP connector created ({}:{})", hostname, port);
    }

    private void createClientHttpsConnector(String hostname, int port) throws Exception {
        log.trace("createClientHttpsConnector({}, {})", hostname, port);

        SslContextFactory.Server cf = new SslContextFactory.Server();
        // Note: Don't use restricted chiper suites
        // (SystemProperties.getXroadTLSCipherSuites()) between client IS and
        // client proxy.
        cf.setWantClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setIncludeProtocols(clientProxyProperties.clientTlsProtocols());
        cf.setIncludeCipherSuites(clientProxyProperties.clientTlsCiphers());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[]{new ClientSslKeyManager(serverConfProvider)}, new TrustManager[]{new ClientSslTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        ServerConnector connector = new ServerConnector(server, ACCEPTOR_COUNT, -1, cf);

        connector.setName(CLIENT_HTTPS_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);
        connector.setIdleTimeout(clientProxyProperties.clientConnectorInitialIdleTime());

        applyConnectionFactoryConfig(connector);
        server.addConnector(connector);

        log.info("Client HTTPS connector created ({}:{})", hostname, port);
    }

    private void applyConnectionFactoryConfig(ServerConnector connector) {
        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    httpCf.getHttpConfiguration().setSendServerVersion(false);
                    httpCf.getHttpConfiguration().setResponseHeaderSize(clientProxyProperties.jettyMaxHeaderSize());
                    httpCf.getHttpConfiguration().setRequestHeaderSize(clientProxyProperties.jettyMaxHeaderSize());
                    httpCf.getHttpConfiguration().setUriCompliance(UriCompliance.DEFAULT
                            .with("x-road", UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR));
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> customizer.setSniHostCheck(false));
                });
    }

    private void createHandlers() throws Exception {
        log.trace("createHandlers()");

        var handlers = new Handler.Sequence();

        getClientHandlers().forEach(handlers::addHandler);

        server.setHandler(handlers);
    }

    private List<Handler> getClientHandlers() {
        List<Handler> handlers = new ArrayList<>();
        String handlerClassNames = System.getProperty(CLIENTPROXY_HANDLERS);

        handlers.add(clientRestMessageHandler);

        if (!StringUtils.isBlank(handlerClassNames)) {
            var handlerLoader = new HandlerLoader(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory);
            for (String handlerClassName : handlerClassNames.split(",")) {
                try {
                    log.trace("Loading client handler {}", handlerClassName);

                    handlers.add(handlerLoader.loadHandler(handlerClassName, client));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load client handler: " + handlerClassName, e);
                }
            }
        }

        log.trace("Loading default client handler");
        handlers.add(clientSoapMessageHandler); // default handler

        return handlers;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.trace("start()");

        server.start();
    }

    @Override
    public void destroy() throws Exception {
        log.trace("stop()");

        server.stop();
    }

    private static final class ClientSslTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Trusts all cause ClientMessageProcessor#verifyClientAuthentication checks if the client certificate
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // Never called cause TrustManager is used by ServerConnector
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }
}
