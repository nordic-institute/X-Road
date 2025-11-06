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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.JettyUtils;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.antidos.AntiDosConfiguration;
import org.niis.xroad.proxy.core.antidos.AntiDosConnector;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.SSLContextUtil;

import java.util.Optional;

/**
 * Server proxy that handles requests of client proxies.
 */
@Slf4j
@Startup
@Singleton
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions")
public class ServerProxy {

    private static final int ACCEPTOR_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors());

    // SSL session timeout in seconds
    private static final int SSL_SESSION_TIMEOUT = 600;

    private static final String CLIENT_PROXY_CONNECTOR_NAME = "ClientProxyConnector";

    private final Server server = new Server();

    private final ProxyProperties proxyProperties;
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final ServerProxyHandler serverProxyHandler;
    private final IdleConnectionMonitorThread connMonitor;
    private final AntiDosConfiguration antiDosConfiguration;

    private SslContextFactory.Server sslContextFactory;

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        var file = proxyProperties.server().jettyConfigurationFile();

        log.debug("Configuring server from {}", file);

        new XmlConfiguration(JettyUtils.toResource(file)).configure(server);

        final var writer = new Slf4jRequestLogWriter();
        writer.setLoggerName(getClass().getPackage().getName() + ".RequestLog");
        final var reqLog = new CustomRequestLog(writer, CustomRequestLog.EXTENDED_NCSA_FORMAT);
        server.setRequestLog(reqLog);
    }

    private void createConnectors() throws Exception {
        log.trace("createConnectors()");

        int port = proxyProperties.server().listenPort();

        ServerConnector connector = proxyProperties.sslEnabled()
                ? createClientProxySslConnector() : createClientProxyConnector();

        connector.setName(CLIENT_PROXY_CONNECTOR_NAME);
        connector.setPort(port);
        connector.setHost(proxyProperties.server().listenAddress());

        connector.setIdleTimeout(proxyProperties.server().connectorInitialIdleTime());

        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    httpCf.getHttpConfiguration().setSendServerVersion(false);
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> customizer.setSniHostCheck(false));
                });

        server.addConnector(connector);

        log.info("ClientProxy {} created ({}:{})", connector.getClass().getSimpleName(), proxyProperties.server().listenAddress(), port);
    }

    private void registerHandlers() {
        log.trace("registerHandlers()");

        var handler = new Handler.Sequence();
        handler.addHandler(serverProxyHandler);

        server.setHandler(handler);
    }

    @PostConstruct
    public void init() throws Exception {
        log.info("Initializing server proxy");

        configureServer();

        createConnectors();
        registerHandlers();

        server.start();
        connMonitor.start();
    }

    @PreDestroy
    public void destroy() throws Exception {
        log.info("Shutting down server proxy..");

        connMonitor.shutdown();

        server.stop();
        log.info("Shutting down server proxy.. Success!");
    }

    /**
     * Close idle connections.
     */
    public void closeIdleConnections() {
        connMonitor.closeNow();
    }

    private ServerConnector createClientProxyConnector() {
        return antiDosConfiguration.enabled()
                ? new AntiDosConnector(antiDosConfiguration, globalConfProvider, server, ACCEPTOR_COUNT)
                : new ServerConnector(server, ACCEPTOR_COUNT, -1);
    }

    private ServerConnector createClientProxySslConnector() throws Exception {
        sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setNeedClientAuth(true);
        sslContextFactory.setIncludeProtocols(CryptoUtils.SSL_PROTOCOL);
        sslContextFactory.setIncludeCipherSuites(proxyProperties.xroadTlsCiphers());
        sslContextFactory.setSessionCachingEnabled(true);
        sslContextFactory.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        sslContextFactory.setSslContext(SSLContextUtil.createXroadSSLContext(globalConfProvider, keyConfProvider));

        return antiDosConfiguration.enabled()
                ? new AntiDosConnector(antiDosConfiguration, globalConfProvider, server, ACCEPTOR_COUNT, sslContextFactory)
                : new ServerConnector(server, ACCEPTOR_COUNT, -1, sslContextFactory);
    }

    public void reloadAuthKey() {
        log.trace("reloadAuthKey()");
        if (sslContextFactory != null) {
            try {
                sslContextFactory.setSslContext(SSLContextUtil.createXroadSSLContext(globalConfProvider, keyConfProvider));
                sslContextFactory.reload(cf -> log.debug("Server SSL context reloaded"));
            } catch (Exception e) {
                log.error("Failed to reload auth key", e);
            }
        }
    }
}
