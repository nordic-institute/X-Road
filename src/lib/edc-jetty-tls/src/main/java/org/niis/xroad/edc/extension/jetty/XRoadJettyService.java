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
 * shared by every connector instead of stock's one-per-connector, so it can be swapped as a unit. The
 * factory is started eagerly, in the constructor, before any connector exists — stock only validated the
 * keystore file was loadable, deferring actual {@code SSLContext}/key-manager construction to server start;
 * starting it here fails the DataSpace TLS certificate fast if OpenBao's material cannot back a working
 * SSL context at all.
 *
 * <p>Every connector is HTTPS: the DataSpace TLS certificate is mandatory for this runtime (see
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
    private final SslContextFactory.Server sslContextFactory;

    private Server server;

    public XRoadJettyService(KeyStore keyStore, Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this.monitor = monitor;
        this.portMappingRegistry = portMappingRegistry;
        System.setProperty(LOG_ANNOUNCE, "false");
        this.handlers.put(ROOT_PATH, new ServletContextHandler(ROOT_PATH, 0));
        this.sslContextFactory = buildAndStart(keyStore);
    }

    private SslContextFactory.Server buildAndStart(KeyStore keyStore) {
        var contextFactory = new SslContextFactory.Server();
        contextFactory.setKeyStore(keyStore);
        contextFactory.setKeyStorePassword(new String(InternalSSLKey.getKEY_PASSWORD()));
        contextFactory.setKeyManagerPassword(new String(InternalSSLKey.getKEY_PASSWORD()));
        try {
            contextFactory.start();
        } catch (Exception e) {
            throw new DsTlsKeyStoreLoadException(
                    "Failed to initialize the DataSpace TLS SSL context from the certificate stored in OpenBao", e);
        }
        return contextFactory;
    }

    /**
     * Applies newly loaded DataSpace TLS material to the already-running SSL context, so every connector on
     * this JVM — including framework-internal ones such as the STS — serves the new certificate on their
     * next handshake, without a restart.
     */
    void reload(KeyStore keyStore) {
        try {
            sslContextFactory.reload(factory -> factory.setKeyStore(keyStore));
        } catch (Exception e) {
            throw new DsTlsKeyStoreLoadException("Failed to apply the reloaded DataSpace TLS keystore", e);
        }
    }

    public void start() {
        try {
            server = new Server();
            var portMappingsDescription = portMappingRegistry.getAll().stream().peek(mapping -> {
                server.addConnector(createConnector(mapping));
                handlers.put(mapping.path(), createHandler(mapping));
            }).map(PortMapping::toString).collect(Collectors.joining(", "));
            server.setHandler(new ContextHandlerCollection(handlers.values().toArray(ServletContextHandler[]::new)));
            server.start();
            monitor.debug("Port mappings: " + portMappingsDescription);
        } catch (Exception e) {
            throw new EdcException("Error starting the X-Road Jetty service", e);
        }
    }

    public void shutdown() {
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

    public void addConnectorConfigurationCallback(Consumer<ServerConnector> callback) {
        connectorConfigurationCallbacks.add(callback);
    }

    private ServerConnector createConnector(PortMapping mapping) {
        var connector = new ServerConnector(server, sslConnectionFactory(), httpsConnectionFactory(mapping.port()));
        connector.setName(mapping.name());
        connector.setPort(mapping.port());
        connectorConfigurationCallbacks.forEach(callback -> callback.accept(connector));
        monitor.debug("HTTPS context '" + mapping.name() + "' listening on port " + mapping.port());
        return connector;
    }

    private ConnectionFactory sslConnectionFactory() {
        return new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
    }

    private ConnectionFactory httpsConnectionFactory(int port) {
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
