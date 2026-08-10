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
package org.niis.xroad.edc.web.jetty;

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
 * X-Road-owned replacement for EDC's {@code jetty-core} {@code JettyService}. Every connector is
 * always HTTPS, served from a single, eagerly-started {@link SslContextFactory.Server} shared across
 * every connector of this JVM (external protocol and framework-internal alike) - so one
 * {@link #applyKeyStore(KeyStore)} call rotates all of them without a restart. Stock EDC read its
 * keystore from a file at boot with no reload seam; this variant is fed by a {@link
 * org.niis.xroad.common.vault.reload.PeriodicMaterialReloader} sourcing the keystore from OpenBao (see
 * {@link XRoadJettyExtension}) - there is no file-based keystore path or password here at all.
 *
 * <p>{@link #registerServlet(String, Servlet)} preserves stock EDC's contract exactly, verified
 * against the actual {@code org.eclipse.edc.web.jetty.JettyService} bytecode: {@code contextName} is
 * matched by equality against {@link PortMapping#name()}, and the servlet is mounted at that
 * mapping's {@link PortMapping#path()} - the two are independent strings, an alias is not a path.
 */
public class XRoadJettyService implements WebServer {

    private final Monitor monitor;
    private final PortMappingRegistry portMappingRegistry;
    private final Map<String, ServletContextHandler> handlers = new HashMap<>();
    private final List<Consumer<ServerConnector>> connectorConfigurationCallbacks = new ArrayList<>();
    private final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    private Server server;

    public XRoadJettyService(Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this.monitor = monitor;
        this.portMappingRegistry = portMappingRegistry;
        this.handlers.put("/", new ServletContextHandler("/", 0));
    }

    /**
     * Applies {@code keyStore} to the shared TLS context: eagerly starts it on the first call (before
     * any connector exists), or hot-reloads it in place on every later call via Jetty's own {@code
     * SslContextFactory.reload}, the capability stock EDC's wrapper never exposed. Every connector
     * created by {@link #start()} shares this exact instance, so a single call here rotates all of
     * them without restarting the server.
     */
    public void applyKeyStore(KeyStore keyStore) {
        try {
            if (!sslContextFactory.isStarted()) {
                sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setKeyStorePassword(null);
                sslContextFactory.setKeyManagerPassword(null);
                sslContextFactory.start();
                monitor.info("X-Road Jetty TLS context started");
            } else {
                sslContextFactory.reload(scf -> {
                    scf.setKeyStore(keyStore);
                    scf.setKeyStorePassword(null);
                    scf.setKeyManagerPassword(null);
                });
                monitor.info("X-Road Jetty TLS context reloaded");
            }
        } catch (Exception e) {
            throw new EdcException("Failed to apply the DS TLS keystore to the X-Road Jetty TLS context", e);
        }
    }

    public void start() {
        if (!sslContextFactory.isStarted()) {
            // No file fallback exists here - applyKeyStore() must have run (and it is the only thing
            // that starts the shared TLS context) before any connector is allowed to come up.
            throw new EdcException("Cannot start the X-Road Jetty service: no DS TLS keystore has been applied", null);
        }
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
            throw new EdcException("Error starting X-Road Jetty service", e);
        }
    }

    public void shutdown() {
        try {
            if (server != null) {
                server.stop();
                server.join();
            }
            if (sslContextFactory.isStarted()) {
                sslContextFactory.stop();
            }
        } catch (Exception e) {
            throw new EdcException("Error shutting down X-Road Jetty service", e);
        }
    }

    @Override
    public void registerServlet(String contextName, Servlet servlet) {
        var servletHolder = new ServletHolder(Source.EMBEDDED);
        servletHolder.setName("XROAD-" + contextName);
        servletHolder.setServlet(servlet);
        servletHolder.setInitOrder(1);
        var actualPath = portMappingRegistry.getAll().stream()
                .filter(pm -> Objects.equals(contextName, pm.name()))
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
        var connector = new ServerConnector(server, getSslConnectionFactory(), httpsConnectionFactory(mapping.port()));
        monitor.debug("HTTPS context '" + mapping.name() + "' listening on port " + mapping.port());
        connector.setName(mapping.name());
        connector.setPort(mapping.port());
        connectorConfigurationCallbacks.forEach(c -> c.accept(connector));
        return connector;
    }

    private ServletContextHandler createHandler(PortMapping mapping) {
        var handler = new ServletContextHandler("/", 0);
        handler.setVirtualHosts(List.of("@" + mapping.name()));
        return handler;
    }

    private HttpConnectionFactory httpsConnectionFactory(int port) {
        var httpsConfiguration = new HttpConfiguration();
        httpsConfiguration.setSecureScheme("https");
        httpsConfiguration.setSecurePort(port);
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer());
        return new HttpConnectionFactory(httpsConfiguration);
    }

    private SslConnectionFactory getSslConnectionFactory() {
        return new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
    }

    private ServletContextHandler getOrCreate(String contextPath) {
        return handlers.computeIfAbsent(contextPath, k -> new ServletContextHandler("/", 0));
    }
}
