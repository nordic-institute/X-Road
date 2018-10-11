/**
 * The MIT License
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
import ee.ria.xroad.common.logging.RequestLogImplFixLogback1052;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.XroadProxy;
import ee.ria.xroad.proxy.serverproxy.IdleConnectionMonitorThread;

import ch.qos.logback.access.jetty.RequestLogImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static ee.ria.xroad.proxy.clientproxy.HandlerLoader.loadHandler;


/**
 * Client proxy that handles requests of service clients.
 */
@Slf4j
public class ClientProxy extends XroadProxy {

    private static final int ACCEPTOR_COUNT = Runtime.getRuntime().availableProcessors();

    private static final int CONNECTOR_SO_LINGER_MILLIS = SystemProperties.getClientProxyConnectorSoLinger() * 1000;

    private static final String CLIENTPROXY_HANDLERS = SystemProperties.PREFIX + "proxy.clientHandlers";

    private static final String CLIENT_HTTP_CONNECTOR_NAME = "ClientConnector";
    private static final String CLIENT_HTTPS_CONNECTOR_NAME = "ClientSSLConnector";

    private HttpClientConnectionManager connectionManager;

    /**
     * Constructs and configures a new client proxy.
     * @throws Exception in case of any errors
     */
    public ClientProxy() throws Exception {
        connectionManager = getClientConnectionManager();
    }

    @Override
    protected IdleConnectionMonitorThread createConnectionMonitor() {
        IdleConnectionMonitorThread monitor = null;
        if (SystemProperties.isClientUseIdleConnectionMonitor()) {
            monitor = new IdleConnectionMonitorThread(connectionManager);
            monitor.setIntervalMilliseconds(SystemProperties.getClientProxyIdleConnectionMonitorInterval());
            monitor.setConnectionIdleTimeMilliseconds(
                    SystemProperties.getClientProxyIdleConnectionMonitorIdleTime());
        }
        return monitor;
    }

    @Override
    protected Path getJettyServerConfFilePath() {
        return Paths.get(SystemProperties.getJettyClientProxyConfFile());
    }

    @Override
    protected CloseableHttpClient createClient() throws Exception {
        log.trace("createClient()");

        int timeout = SystemProperties.getClientProxyTimeout();
        int socketTimeout = SystemProperties.getClientProxyHttpClientTimeout();
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setSocketTimeout(socketTimeout);

        HttpClientBuilder cb = HttpClients.custom();

        cb.setConnectionManager(connectionManager);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        return cb.build();
    }

    @Override
    protected Collection<ServerConnector> createConnectors() throws Exception {
        log.trace("createConnectors()");

        return Arrays.asList(
                createClientHttpConnector(SystemProperties.getConnectorHost(),
                        SystemProperties.getClientProxyHttpPort()),
                createClientHttpsConnector(SystemProperties.getConnectorHost(),
                        SystemProperties.getClientProxyHttpsPort()));
    }

    @Override
    protected AbstractHandler createHandlers() {
        log.trace("createHandlers()");

        RequestLogImpl reqLog = new RequestLogImplFixLogback1052();
        reqLog.setResource("/logback-access-clientproxy.xml");
        reqLog.setQuiet(true);

        RequestLogHandler logHandler = new RequestLogHandler();
        logHandler.setRequestLog(reqLog);

        HandlerCollection handlers = new HandlerCollection();

        handlers.addHandler(logHandler);

        getClientHandlers().forEach(handlers::addHandler);

        return handlers;
    }

    private HttpClientConnectionManager getClientConnectionManager() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory());
        }

        SocketConfig.Builder sockBuilder =  SocketConfig.custom().setTcpNoDelay(true);
        sockBuilder.setSoLinger(SystemProperties.getClientProxyHttpClientSoLinger());
        sockBuilder.setSoTimeout(SystemProperties.getClientProxyHttpClientTimeout());
        SocketConfig socketConfig = sockBuilder.build();

        PoolingHttpClientConnectionManager poolingManager = new PoolingHttpClientConnectionManager(sfr.build());
        poolingManager.setMaxTotal(SystemProperties.getClientProxyPoolTotalMaxConnections());
        poolingManager.setDefaultMaxPerRoute(SystemProperties.getClientProxyPoolDefaultMaxConnectionsPerRoute());
        poolingManager.setDefaultSocketConfig(socketConfig);
        poolingManager.setValidateAfterInactivity(
                SystemProperties.getClientProxyValidatePoolConnectionsAfterInactivityMs());

        return poolingManager;
    }

    private SSLConnectionSocketFactory createSSLSocketFactory() throws Exception {
        return new FastestConnectionSelectingSSLSocketFactory(createSSLContext(), getAcceptedCipherSuites());
    }

    private ServerConnector createClientHttpConnector(String hostname, int port) {
        log.trace("createClientHttpConnector({}, {})", hostname, port);

        ServerConnector connector = new ServerConnector(getServer(), ACCEPTOR_COUNT, -1);

        connector.setName(CLIENT_HTTP_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(CONNECTOR_SO_LINGER_MILLIS);
        connector.setIdleTimeout(SystemProperties.getClientProxyConnectorMaxIdleTime());

        disableSendServerVersion(connector);

        log.info("Client HTTP connector created ({}:{})", hostname, port);
        return connector;
    }

    private ServerConnector createClientHttpsConnector(String hostname, int port) throws Exception {
        log.trace("createClientHttpsConnector({}, {})", hostname, port);

        SslContextFactory cf = new SslContextFactory(false);
        // Note: Don't use restricted chiper suites
        // (SystemProperties.getXroadTLSCipherSuites()) between client IS and
        // client proxy.
        cf.setWantClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setIncludeProtocols(SystemProperties.getProxyClientTLSProtocols());
        cf.setIncludeCipherSuites(SystemProperties.getProxyClientTLSCipherSuites());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new ClientSslKeyManager()}, new TrustManager[] {new ClientSslTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        ServerConnector connector = new ServerConnector(getServer(), ACCEPTOR_COUNT, -1, cf);

        connector.setName(CLIENT_HTTPS_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(CONNECTOR_SO_LINGER_MILLIS);
        connector.setIdleTimeout(SystemProperties.getClientProxyConnectorMaxIdleTime());

        disableSendServerVersion(connector);

        log.info("Client HTTPS connector created ({}:{})", hostname, port);
        return connector;
    }

    private void disableSendServerVersion(ServerConnector connector) {
        connector.getConnectionFactories().stream()
                .filter(cf -> cf instanceof HttpConnectionFactory)
                .forEach(httpCf -> ((HttpConnectionFactory) httpCf)
                        .getHttpConfiguration().setSendServerVersion(false));
    }

    private List<Handler> getClientHandlers() {
        List<Handler> handlers = new ArrayList<>();
        String handlerClassNames = System.getProperty(CLIENTPROXY_HANDLERS);

        if (!StringUtils.isBlank(handlerClassNames)) {
            for (String handlerClassName : handlerClassNames.split(",")) {
                try {
                    log.trace("Loading client handler {}", handlerClassName);

                    handlers.add(loadHandler(handlerClassName, getClient()));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load client handler: " + handlerClassName, e);
                }
            }
        }

        log.trace("Loading default client handler");

        handlers.add(new ClientMessageHandler(getClient())); // default handler

        return handlers;
    }

    private static class ClientSslTrustManager implements X509TrustManager {

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
