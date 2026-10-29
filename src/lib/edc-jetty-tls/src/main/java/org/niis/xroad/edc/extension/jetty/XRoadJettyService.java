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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.conf.InternalSSLKey;

import jakarta.servlet.Servlet;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.Source;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * X-Road's replacement for EDC's {@code JettyService}: same servlet-registration contract (a
 * {@link #registerServlet} call names a context by the alias its {@link PortMapping} was registered
 * under, resolved to that mapping's path — never the alias itself), but with one {@link SslContextFactory}
 * shared by every connector instead of stock's one-per-connector, so it can be swapped as a unit.
 *
 * <p>The service is constructed without TLS material and its server starts with no connectors, so the rest of
 * the runtime boots before the connector TLS certificate exists. The first {@link #applyConnectorTlsMaterial}
 * call builds and starts the SSL context factory — failing fast if the material cannot back a working SSL
 * context — and opens every HTTPS connector on the running server; later calls hot-swap the keystore. Until
 * the first call, the ports refuse connections. A keystore that cannot back an SSL context fails with
 * {@link ConnectorTlsKeyStoreLoadException}; a port that cannot be bound fails with
 * {@link ConnectorBindException}.
 *
 * <p>Every connector is HTTPS: the connector TLS certificate is mandatory for this runtime (see
 * {@link XRoadJettyExtension}), so stock's plaintext fallback for a missing keystore has no reason to exist
 * here — carrying it forward would silently reopen the unguarded HTTP path the owned module exists to close.
 */
public final class XRoadJettyService implements WebServer {

    private static final String LOG_ANNOUNCE = "org.eclipse.jetty.util.log.announce";
    private static final String ROOT_PATH = "/";

    private final Monitor monitor;
    private final PortMappingRegistry portMappingRegistry;
    private final Map<String, ServletContextHandler> handlers = new HashMap<>();
    private final List<Consumer<ServerConnector>> connectorConfigurationCallbacks = new ArrayList<>();

    private SslContextFactory.Server sslContextFactory;
    private Server server;
    private volatile boolean connectorsOpen;
    private volatile boolean stopped;

    public XRoadJettyService(Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this.monitor = monitor;
        this.portMappingRegistry = portMappingRegistry;
        System.setProperty(LOG_ANNOUNCE, "false");
        this.handlers.put(ROOT_PATH, new ServletContextHandler(ROOT_PATH, 0));
    }

    /**
     * Accepts connector TLS material. The first call builds the shared SSL context factory; every later call
     * applies the keystore to the running SSL context, so every connector on this JVM — including
     * framework-internal ones such as the STS — serves the new certificate on its next handshake, without a
     * restart. The HTTPS connectors open once the server is running and a factory exists, and a call that
     * fails while opening them leaves them to be opened by the next call. A call after {@link #shutdown()} is
     * ignored.
     */
    public synchronized void applyConnectorTlsMaterial(KeyStore keyStore) {
        if (stopped) {
            monitor.debug("Connector TLS material ignored, the X-Road Jetty service is stopped");
            return;
        }
        if (sslContextFactory == null) {
            sslContextFactory = buildAndStart(keyStore);
        } else {
            reload(keyStore);
        }
        if (server != null && !connectorsOpen) {
            openConnectors(sslContextFactory);
        }
    }

    private void reload(KeyStore keyStore) {
        try {
            sslContextFactory.reload(factory -> factory.setKeyStore(keyStore));
        } catch (Exception e) {
            throw new ConnectorTlsKeyStoreLoadException("Failed to apply the reloaded connector TLS keystore", e);
        }
    }

    private static SslContextFactory.Server buildAndStart(KeyStore keyStore) {
        var contextFactory = new SslContextFactory.Server();
        contextFactory.setKeyStore(keyStore);
        contextFactory.setKeyStorePassword(new String(InternalSSLKey.getKEY_PASSWORD()));
        contextFactory.setKeyManagerPassword(new String(InternalSSLKey.getKEY_PASSWORD()));
        try {
            contextFactory.start();
        } catch (Exception e) {
            throw new ConnectorTlsKeyStoreLoadException(
                    "Failed to initialize the connector TLS SSL context from the certificate stored in OpenBao", e);
        }
        return contextFactory;
    }

    /**
     * Whether the HTTPS connectors are open and serving connector TLS material. Reads volatile state, so a
     * health probe never waits on a connector that is still being opened.
     */
    public boolean isServingConnectorTls() {
        return connectorsOpen && !stopped;
    }

    public synchronized void start() {
        try {
            server = new Server();
            portMappingRegistry.getAll().forEach(mapping -> handlers.put(mapping.path(), createHandler(mapping)));
            server.setHandler(new ContextHandlerCollection(handlers.values().toArray(ServletContextHandler[]::new)));
            server.start();
        } catch (Exception e) {
            throw new EdcException("Error starting the X-Road Jetty service", e);
        }
        if (sslContextFactory != null) {
            openConnectors(sslContextFactory);
        }
    }

    public synchronized void shutdown() {
        stopped = true;
        try {
            if (server != null) {
                server.stop();
                server.join();
            }
        } catch (Exception e) {
            throw new EdcException("Error shutting down the X-Road Jetty service", e);
        }
    }

    @Override
    public void registerServlet(String contextName, Servlet servlet) {
        var servletHolder = new ServletHolder(Source.EMBEDDED);
        servletHolder.setName("XRD-" + contextName);
        servletHolder.setServlet(servlet);
        servletHolder.setInitOrder(1);
        var actualPath = portMappingRegistry.getAll().stream()
                .filter(mapping -> Objects.equals(contextName, mapping.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No PortMapping for contextName '" + contextName + "' found"))
                .path();
        var servletHandler = getOrCreate(actualPath).getServletHandler();
        servletHandler.addServletWithMapping(servletHolder, actualPath);
        var allPathSpec = actualPath.endsWith("/") ? "*" : "/*";
        servletHandler.addServletWithMapping(servletHolder, actualPath + allPathSpec);
    }

    public synchronized void addConnectorConfigurationCallback(Consumer<ServerConnector> callback) {
        connectorConfigurationCallbacks.add(callback);
    }

    private void openConnectors(SslContextFactory.Server factory) {
        var opened = new ArrayList<ServerConnector>();
        try {
            for (var mapping : portMappingRegistry.getAll()) {
                var connector = createConnector(mapping, factory);
                server.addConnector(connector);
                opened.add(connector);
                startConnector(connector, mapping);
            }
        } catch (RuntimeException e) {
            opened.forEach(this::closeConnector);
            throw e;
        }
        connectorsOpen = true;
        monitor.debug("Port mappings: " + portMappingRegistry.getAll().stream()
                .map(PortMapping::toString).collect(Collectors.joining(", ")));
    }

    private static void startConnector(ServerConnector connector, PortMapping mapping) {
        try {
            // A connector joining an already running server is not started automatically.
            connector.start();
        } catch (Exception e) {
            throw new ConnectorBindException(
                    "Cannot bind the HTTPS port %d of the '%s' connector".formatted(mapping.port(), mapping.name()), e);
        }
    }

    private void closeConnector(ServerConnector connector) {
        try {
            connector.stop();
        } catch (Exception e) {
            monitor.warning("Failed to stop connector '" + connector.getName() + "' after a failed listener start", e);
        }
        server.removeConnector(connector);
    }

    private ServerConnector createConnector(PortMapping mapping, SslContextFactory.Server factory) {
        var connector = new ServerConnector(server, sslConnectionFactory(factory), httpsConnectionFactory(mapping.port()));
        connector.setName(mapping.name());
        connector.setPort(mapping.port());
        connectorConfigurationCallbacks.forEach(callback -> callback.accept(connector));
        monitor.debug("HTTPS context '" + mapping.name() + "' listening on port " + mapping.port());
        return connector;
    }

    private static ConnectionFactory sslConnectionFactory(SslContextFactory.Server factory) {
        return new SslConnectionFactory(factory, HttpVersion.HTTP_1_1.asString());
    }

    private static ConnectionFactory httpsConnectionFactory(int port) {
        var httpsConfiguration = new HttpConfiguration();
        httpsConfiguration.setSecureScheme("https");
        httpsConfiguration.setSecurePort(port);
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer());
        return new HttpConnectionFactory(httpsConfiguration);
    }

    private ServletContextHandler createHandler(PortMapping mapping) {
        var handler = new ServletContextHandler(ROOT_PATH, 0);
        handler.setVirtualHosts(List.of("@" + mapping.name()));
        return handler;
    }

    private ServletContextHandler getOrCreate(String contextPath) {
        return handlers.computeIfAbsent(contextPath, path -> new ServletContextHandler(ROOT_PATH, 0));
    }
}
