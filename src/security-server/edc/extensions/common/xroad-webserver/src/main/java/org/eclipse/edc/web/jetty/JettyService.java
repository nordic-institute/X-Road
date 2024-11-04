/*
 * The MIT License
 *
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
package org.eclipse.edc.web.jetty;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;

import jakarta.servlet.Servlet;
import lombok.SneakyThrows;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.edc.extension.bridge.spring.TlsAuthKeyProvider;
import org.niis.xroad.edc.spi.XrdWebServer;
import org.niis.xroad.ssl.EdcSSLConstants;
import org.niis.xroad.ssl.SSLContextBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.eclipse.jetty.ee10.servlet.ServletContextHandler.NO_SESSIONS;

/**
 * This is ant extended vanilla JettyService with customized SSL context and Jetty 12.
 * NOTE: package and name is kept as is due to jersey-core dependency.
 */
public class JettyService implements XrdWebServer {

    private static final int SSL_SESSION_TIMEOUT = 600;
    private static final String LOG_ANNOUNCE = "org.eclipse.jetty.util.log.announce";
    private final JettyConfiguration configuration;
    private final GlobalConfProvider globalConfProvider;
    private final TlsAuthKeyProvider tlsAuthKeyProvider;
    private final CertChainFactory certChainFactory;
    private final Monitor monitor;
    private final Map<String, ServletContextHandler> handlers = new HashMap<>();
    private final List<Consumer<ServerConnector>> connectorConfigurationCallbacks = new ArrayList<>();
    private Server server;

    public JettyService(JettyConfiguration configuration, GlobalConfProvider globalConfProvider,
                        TlsAuthKeyProvider tlsAuthKeyProvider, CertChainFactory certChainFactory,
                        Monitor monitor) {
        this.configuration = configuration;
        this.globalConfProvider = globalConfProvider;
        this.tlsAuthKeyProvider = tlsAuthKeyProvider;
        this.certChainFactory = certChainFactory;
        this.monitor = monitor;
        System.setProperty(LOG_ANNOUNCE, "false");
        handlers.put("/", new ServletContextHandler("/", NO_SESSIONS));
    }

    public void start() {
        try {
            server = new Server();

            if (configuration.isSslDisabled()) {
                monitor.warning("SSL is disabled!");
                monitor.warning("Plain HTTP connectors will be created.");
                monitor.warning("Not recommended for production use!");
            }

            configuration.getPortMappings().forEach(mapping -> {
                if (!mapping.getPath().startsWith("/")) {
                    throw new IllegalArgumentException("A context path must start with /: " + mapping.getPath());
                }

                ServerConnector connector;
                if (Arrays.stream(server.getConnectors()).anyMatch(c -> ((ServerConnector) c).getPort() == mapping.getPort())) {
                    throw new IllegalArgumentException("A binding for port " + mapping.getPort() + " already exists");
                }

                connector = configuration.isSslDisabled()
                        ? httpServerConnector(mapping.getPort()) : httpsServerConnector(mapping);
                monitor.info("HTTPS context '" + mapping.getName() + "' listening on port " + mapping.getPort());

                connector.setName(mapping.getName());
                connector.setPort(mapping.getPort());

                configure(connector);
                server.addConnector(connector);

                var handler = createHandler(mapping);
                handlers.put(mapping.getPath(), handler);
            });
            server.setHandler(new ContextHandlerCollection(handlers.values().toArray(ServletContextHandler[]::new)));
            server.start();
            monitor.debug("Port mappings: " + configuration.getPortMappings().stream().map(PortMapping::toString)
                    .collect(Collectors.joining(", ")));
        } catch (Exception e) {
            throw new EdcException("Error starting Jetty service", e);
        }
    }

    public void shutdown() {
        try {
            if (server != null) {
                server.stop();
                server.join();
            }
        } catch (Exception e) {
            throw new EdcException("Error shutting down Jetty service", e);
        }
    }

    public void registerServlet(String contextName, Servlet servlet) {
        var servletHolder = new ServletHolder(servlet);
        servletHolder.setName("EDC-" + contextName);
        servletHolder.setInitOrder(1);

        var actualPath = configuration.getPortMappings().stream()
                .filter(pm -> Objects.equals(contextName, pm.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No PortMapping for contextName '" + contextName + "' found"))
                .getPath();

        var servletHandler = getOrCreate(actualPath).getServletHandler();
        servletHandler.addServletWithMapping(servletHolder, actualPath);

        var allPathSpec = actualPath.endsWith("/") ? "*" : "/*";
        servletHandler.addServletWithMapping(servletHolder, actualPath + allPathSpec);
    }

    @Override
    public void addPortMapping(String contextName, int port, String path) {
        addPortMapping(contextName, port, path, false);
    }

    @Override
    public void addPortMapping(String contextName, int port, String path, boolean needClientAuth) {
        var portMapping = new PortMapping(contextName, port, path, needClientAuth);
        if (server != null && (server.isStarted() || server.isStarting())) {
            return;
        }
        configuration.getPortMappings().add(portMapping);
    }

    public void addConnectorConfigurationCallback(Consumer<ServerConnector> callback) {
        connectorConfigurationCallbacks.add(callback);
    }

    @NotNull
    private ServletContextHandler createHandler(PortMapping mapping) {
        var handler = new ServletContextHandler("/", NO_SESSIONS);
        handler.setVirtualHosts(List.of("@" + mapping.getName()));
        return handler;
    }

    @NotNull
    @SneakyThrows
    private ServerConnector httpsServerConnector(PortMapping portMapping) {
        boolean sniEnabled = false;
        var cf = new SslContextFactory.Server();
        cf.setNeedClientAuth(portMapping.isNeedClientAuth());
        cf.setIncludeProtocols(EdcSSLConstants.SSL_PROTOCOL);
        cf.setIncludeCipherSuites(EdcSSLConstants.SSL_CYPHER_SUITES);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setSslContext(SSLContextBuilder.create(tlsAuthKeyProvider::getAuthKey, globalConfProvider).sslContext());
        cf.setSniRequired(sniEnabled);

        var httpsConfiguration = getDefaultHttpConfiguration();
        httpsConfiguration.setSecureScheme("https");
        httpsConfiguration.setSecurePort(portMapping.getPort());
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer(sniEnabled));

        var httpConnectionFactory = new HttpConnectionFactory(httpsConfiguration);
        var sslConnectionFactory = new SslConnectionFactory(cf, HttpVersion.HTTP_1_1.asString());
        return new ServerConnector(server, sslConnectionFactory, httpConnectionFactory);
    }


    @NotNull
    private ServerConnector httpServerConnector(int port) {
        var connector = new ServerConnector(server, httpConnectionFactory());
        connector.setPort(port);
        return connector;
    }

    private void configure(ServerConnector connector) {
        connectorConfigurationCallbacks.forEach(c -> c.accept(connector));
    }

    @NotNull
    private HttpConnectionFactory httpConnectionFactory() {
        HttpConfiguration http = getDefaultHttpConfiguration();
        return new HttpConnectionFactory(http);
    }

    private HttpConfiguration getDefaultHttpConfiguration() {
        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setRequestHeaderSize(configuration.getMaxHeaderSize());
        httpConfiguration.setResponseHeaderSize(configuration.getMaxHeaderSize());
        return httpConfiguration;
    }

    private ServletContextHandler getOrCreate(String contextPath) {
        return handlers.computeIfAbsent(contextPath, k -> new ServletContextHandler("/", NO_SESSIONS));
    }
}
