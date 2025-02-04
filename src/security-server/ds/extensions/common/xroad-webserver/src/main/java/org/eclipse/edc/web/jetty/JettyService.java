/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - added method
 *       ZF Friedrichshafen AG - Set connector name
 *       Materna Information & Communications SE - disable Jetty send server version
 *
 */

package org.eclipse.edc.web.jetty;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;

import jakarta.servlet.Servlet;
import lombok.SneakyThrows;
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
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.edc.extension.bridge.spring.TlsAuthKeyProvider;
import org.niis.xroad.ssl.EdcSSLConstants;
import org.niis.xroad.ssl.SSLContextBuilder;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.stream.Collectors.joining;
import static org.eclipse.jetty.ee10.servlet.ServletContextHandler.NO_SESSIONS;
import static org.niis.xroad.edc.spi.XRoadPublicApiConfiguration.XROAD_PUBLIC_API_CONTEXT;

/**
 * Provides HTTP(S) support using Jetty.
 */
public class JettyService implements WebServer {

    private static final String LOG_ANNOUNCE = "org.eclipse.jetty.util.log.announce";
    private final JettyConfiguration configuration;
    private final Monitor monitor;
    private final KeyStore keyStore;
    private final GlobalConfProvider globalConfProvider;
    private final TlsAuthKeyProvider tlsAuthKeyProvider;
    private final CertChainFactory certChainFactory;
    private final Map<String, ServletContextHandler> handlers = new HashMap<>();
    private final List<Consumer<ServerConnector>> connectorConfigurationCallbacks = new ArrayList<>();
    private final PortMappingRegistry portMappingRegistry;
    private Server server;

    public JettyService(JettyConfiguration configuration, GlobalConfProvider globalConfProvider, TlsAuthKeyProvider tlsAuthKeyProvider,
                        CertChainFactory certChainFactory, Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this.configuration = configuration;
        this.keyStore = null;
        this.globalConfProvider = globalConfProvider;
        this.tlsAuthKeyProvider = tlsAuthKeyProvider;
        this.certChainFactory = certChainFactory;
        this.monitor = monitor;
        this.portMappingRegistry = portMappingRegistry;
        System.setProperty(LOG_ANNOUNCE, "false");
        // for websocket endpoints
        handlers.put("/", new ServletContextHandler("/", NO_SESSIONS));
    }

    public JettyService(JettyConfiguration configuration, Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this(configuration, null, monitor, portMappingRegistry);
    }

    public JettyService(JettyConfiguration configuration, KeyStore keyStore, Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this.configuration = configuration;
        this.keyStore = keyStore;
        this.globalConfProvider = null;
        this.tlsAuthKeyProvider = null;
        this.certChainFactory = null;
        this.monitor = monitor;
        this.portMappingRegistry = portMappingRegistry;
        System.setProperty(LOG_ANNOUNCE, "false");
        // for websocket endpoints
        handlers.put("/", new ServletContextHandler("/", NO_SESSIONS));
    }

    public void start() {
        try {
            server = new Server();
            var portMappingsDescription = portMappingRegistry.getAll().stream()
                    .peek(mapping -> {
                        server.addConnector(createConnector(mapping));
                        handlers.put(mapping.path(), createHandler(mapping));
                    })
                    .map(PortMapping::toString)
                    .collect(joining(", "));

            server.setHandler(new ContextHandlerCollection(handlers.values().toArray(ServletContextHandler[]::new)));
            server.start();
            monitor.debug("Port mappings: " + portMappingsDescription);
        } catch (Exception e) {
            throw new EdcException("Error starting Jetty service", e);
        }
    }

    public void shutdown() {
        try {
            if (server != null) {
                server.stop();
                server.join(); //wait for all threads to wind down
            }
        } catch (Exception e) {
            throw new EdcException("Error shutting down Jetty service", e);
        }
    }

    @Override
    public void registerServlet(String contextName, Servlet servlet) {
        var servletHolder = new ServletHolder(Source.EMBEDDED);
        servletHolder.setName("EDC-" + contextName);
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

    private @NotNull ServerConnector createConnector(PortMapping mapping) {
        ServerConnector connector;
        if (keyStore != null) {
            connector = new ServerConnector(server, getSslConnectionFactory(), httpsConnectionFactory(mapping.port(), true));
            monitor.debug("HTTPS context '" + mapping.name() + "' listening on port " + mapping.port());
        } else if (globalConfProvider != null && tlsAuthKeyProvider != null && certChainFactory != null) {
            connector = new ServerConnector(server, getXRoadSslConnectionFactory(mapping), httpsConnectionFactory(mapping.port(), false));
            monitor.debug("HTTPS context '" + mapping.name() + "' listening on port " + mapping.port());
        } else {
            connector = new ServerConnector(server, httpConnectionFactory());
            monitor.debug("HTTP context '" + mapping.name() + "' listening on port " + mapping.port());
        }

        connector.setName(mapping.name());
        connector.setPort(mapping.port());

        connectorConfigurationCallbacks.forEach(c -> c.accept(connector));

        return connector;
    }

    @NotNull
    private ServletContextHandler createHandler(PortMapping mapping) {
        var handler = new ServletContextHandler("/", NO_SESSIONS);
        handler.setVirtualHosts(List.of("@" + mapping.name()));
        return handler;
    }

    @NotNull
    private HttpConnectionFactory httpConnectionFactory() {
        var httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        return new HttpConnectionFactory(httpConfiguration);
    }

    private @NotNull HttpConnectionFactory httpsConnectionFactory(int port, boolean sniEnabled) {
        var httpsConfiguration = new HttpConfiguration();
        httpsConfiguration.setSecureScheme("https");
        httpsConfiguration.setSecurePort(port);
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer(sniEnabled));
        return new HttpConnectionFactory(httpsConfiguration);
    }

    @SneakyThrows
    private @NotNull SslConnectionFactory getSslConnectionFactory() {
        var storePassword = configuration.keystorePassword();
        var managerPassword = configuration.keymanagerPassword();
        // for reference check:
        // https://medium.com/vividcode/enable-https-support-with-self-signed-certificate-for-embedded-jetty-9-d3a86f83e9d9
        var contextFactory = new SslContextFactory.Server();
        contextFactory.setKeyStore(keyStore);
        contextFactory.setKeyStorePassword(storePassword);
        contextFactory.setKeyManagerPassword(managerPassword);
        return new SslConnectionFactory(contextFactory, HttpVersion.HTTP_1_1.asString());
    }

    @SneakyThrows
    private @NotNull SslConnectionFactory getXRoadSslConnectionFactory(PortMapping portMapping) {
        var storePassword = configuration.keystorePassword();
        var managerPassword = configuration.keymanagerPassword();
        // for reference check:
        // https://medium.com/vividcode/enable-https-support-with-self-signed-certificate-for-embedded-jetty-9-d3a86f83e9d9
        var contextFactory = new SslContextFactory.Server();
        if (XROAD_PUBLIC_API_CONTEXT.equals(portMapping.name()) && configuration.xroadPublicApiNeedClientAuth()) {
            contextFactory.setNeedClientAuth(true);
        }
        contextFactory.setIncludeProtocols(EdcSSLConstants.SSL_PROTOCOL);
        contextFactory.setIncludeCipherSuites(EdcSSLConstants.SSL_CYPHER_SUITES);
        contextFactory.setSessionCachingEnabled(true);
        contextFactory.setSslContext(SSLContextBuilder.create(tlsAuthKeyProvider::getAuthKey, globalConfProvider).sslContext());
        contextFactory.setSniRequired(false);
        return new SslConnectionFactory(contextFactory, HttpVersion.HTTP_1_1.asString());
    }

    private ServletContextHandler getOrCreate(String contextPath) {
        return handlers.computeIfAbsent(contextPath, k -> new ServletContextHandler("/", NO_SESSIONS));
    }

}

