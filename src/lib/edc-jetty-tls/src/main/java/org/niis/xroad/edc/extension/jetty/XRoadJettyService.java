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
import org.apache.commons.lang3.StringUtils;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.Source;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.UriCompliance;
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
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
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
 * {@link ConnectorTlsKeyStoreLoadException}; a port that cannot be bound fails with an {@link EdcException}.
 * While connector TLS is not served, the most recent failure is kept as a sanitised one-line cause (see
 * {@link #connectorTlsStatus()}).
 *
 * <p>New material for a running factory is proven first: a throwaway factory with the same configuration must
 * load it, and its private key must match its certificate. Material that fails either check is rejected with a
 * {@link ConnectorTlsKeyStoreLoadException} naming the rejected material and the certificate still served, and
 * the previously served certificate keeps serving.
 *
 * <p>Every connector is HTTPS: the connector TLS certificate is mandatory for this runtime (see
 * {@link XRoadJettyExtension}), so stock's plaintext fallback for a missing keystore has no reason to exist
 * here — carrying it forward would silently reopen the unguarded HTTP path the owned module exists to close.
 *
 * <p>Each connector's bind host follows one rule: the {@link #DEFAULT_CONTEXT_NAME} context always binds
 * {@link #DEFAULT_CONTEXT_BIND_HOST}, unconditionally; every other context binds whatever host the
 * constructor's resolver returns for its name, or every interface when the resolver returns {@code null} or
 * a blank value. Upstream's {@link PortMapping} carries no host, so this rule — not the mapping — is the
 * one place that decides it.
 */
public final class XRoadJettyService implements WebServer {

    private static final String LOG_ANNOUNCE = "org.eclipse.jetty.util.log.announce";
    private static final String ROOT_PATH = "/";
    private static final int MAX_CAUSE_LENGTH = 300;

    /**
     * The context name {@link XRoadJettyExtension} registers its own API port under. Its bind host is always
     * {@link #DEFAULT_CONTEXT_BIND_HOST}; no setting overrides it.
     */
    static final String DEFAULT_CONTEXT_NAME = "default";
    private static final String DEFAULT_CONTEXT_BIND_HOST = "127.0.0.1";
    private static final UnaryOperator<String> NO_HOST_OVERRIDE = contextName -> null;

    private final Monitor monitor;
    private final PortMappingRegistry portMappingRegistry;
    private final Map<String, ServletContextHandler> handlers = new HashMap<>();
    private final List<Consumer<ServerConnector>> connectorConfigurationCallbacks = new ArrayList<>();
    private final Clock clock;
    private final UnaryOperator<String> hostResolver;

    private SslContextFactory.Server sslContextFactory;
    private volatile X509Certificate servedCertificate;
    private Server server;
    private volatile boolean connectorsOpen;
    private volatile boolean stopped;
    private volatile String notServedCause;

    XRoadJettyService(Monitor monitor, PortMappingRegistry portMappingRegistry) {
        this(monitor, portMappingRegistry, NO_HOST_OVERRIDE, Clock.systemUTC());
    }

    /**
     * @param hostResolver resolves the configured bind host of a named context, by context name; {@code null} when
     *                      none is configured. Never consulted for {@link #DEFAULT_CONTEXT_NAME}.
     */
    public XRoadJettyService(Monitor monitor, PortMappingRegistry portMappingRegistry, UnaryOperator<String> hostResolver) {
        this(monitor, portMappingRegistry, hostResolver, Clock.systemUTC());
    }

    XRoadJettyService(Monitor monitor, PortMappingRegistry portMappingRegistry, Clock clock) {
        this(monitor, portMappingRegistry, NO_HOST_OVERRIDE, clock);
    }

    XRoadJettyService(Monitor monitor, PortMappingRegistry portMappingRegistry, UnaryOperator<String> hostResolver, Clock clock) {
        this.monitor = monitor;
        this.clock = clock;
        this.portMappingRegistry = portMappingRegistry;
        this.hostResolver = hostResolver;
        System.setProperty(LOG_ANNOUNCE, "false");
        this.handlers.put(ROOT_PATH, new ServletContextHandler(ROOT_PATH, 0));
    }

    /**
     * Accepts connector TLS material. The first call builds the shared SSL context factory; every later call
     * applies the keystore to the running SSL context, so every connector on this JVM — including
     * framework-internal ones such as the STS — serves the new certificate on its next handshake, without a
     * restart. The HTTPS connectors open once a factory exists, and a call that fails while opening them leaves
     * them to be opened by the next call; the server must be started first. A call after {@link #shutdown()} is
     * ignored. Material that a running factory cannot serve is rejected and leaves the served certificate
     * untouched.
     */
    public synchronized void applyConnectorTlsMaterial(KeyStore keyStore) {
        if (stopped) {
            monitor.debug("Connector TLS material ignored, the X-Road Jetty service is stopped");
            return;
        }
        try {
            applyOrBuild(keyStore);
        } catch (RuntimeException e) {
            notServedCause = sanitize(describeFailure(e));
            throw e;
        }
        notServedCause = null;
    }

    private void applyOrBuild(KeyStore keyStore) {
        if (sslContextFactory == null) {
            var leaf = ConnectorTlsCertificates.leafOfMatchingKey(keyStore);
            sslContextFactory = buildAndStart(keyStore);
            servedCertificate = leaf;
        } else {
            reload(keyStore);
        }
        if (!connectorsOpen) {
            openConnectors(sslContextFactory);
        }
    }

    /**
     * Records why the connector TLS material could not be loaded, for {@link #connectorTlsStatus()}. The cause is
     * cleared by the next successful {@link #applyConnectorTlsMaterial}.
     */
    void recordLoadFailure(RuntimeException failure) {
        notServedCause = sanitize(describeFailure(failure));
    }

    private static String describeFailure(RuntimeException failure) {
        return failure instanceof ConnectorTlsKeyStoreLoadException loadFailure
                ? loadFailure.summary()
                : String.valueOf(failure.getMessage());
    }

    private static String sanitize(String text) {
        var collapsed = text.replaceAll("\\s+", " ").strip();
        return collapsed.length() <= MAX_CAUSE_LENGTH ? collapsed : collapsed.substring(0, MAX_CAUSE_LENGTH) + "...";
    }

    private void reload(KeyStore keyStore) {
        X509Certificate leaf;
        try {
            loadInThrowawayFactory(keyStore);
            leaf = ConnectorTlsCertificates.leafOfMatchingKey(keyStore);
        } catch (ConnectorTlsKeyStoreLoadException e) {
            throw rejected(keyStore, e);
        }
        try {
            sslContextFactory.reload(factory -> factory.setKeyStore(keyStore));
        } catch (Exception e) {
            throw new ConnectorTlsKeyStoreLoadException("Failed to apply the reloaded connector TLS keystore", e);
        }
        servedCertificate = leaf;
    }

    private void loadInThrowawayFactory(KeyStore keyStore) {
        var probe = newSslContextFactory(keyStore);
        try {
            probe.start();
        } catch (Exception e) {
            throw new ConnectorTlsKeyStoreLoadException("The connector TLS keystore cannot be loaded by Jetty", e);
        } finally {
            try {
                probe.stop();
            } catch (Exception _) {
                // the probe is discarded; a failed stop leaves nothing to clean up
            }
        }
    }

    private ConnectorTlsKeyStoreLoadException rejected(KeyStore keyStore, ConnectorTlsKeyStoreLoadException failure) {
        return new ConnectorTlsKeyStoreLoadException(
                "Connector TLS material (SHA-256 %s) rejected: %s. Still serving %s".formatted(
                        ConnectorTlsCertificates.fingerprintOrUnknown(keyStore), describeCause(failure),
                        ConnectorTlsCertificates.describe(servedCertificate)),
                failure.summary(), failure);
    }

    private static String describeCause(Throwable failure) {
        var root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root == failure ? failure.getMessage() : failure.getMessage() + ": " + root.getMessage();
    }

    private static SslContextFactory.Server newSslContextFactory(KeyStore keyStore) {
        var contextFactory = new SslContextFactory.Server();
        contextFactory.setKeyStore(keyStore);
        contextFactory.setKeyStorePassword(new String(InternalSSLKey.getKEY_PASSWORD()));
        contextFactory.setKeyManagerPassword(new String(InternalSSLKey.getKEY_PASSWORD()));
        return contextFactory;
    }

    private SslContextFactory.Server buildAndStart(KeyStore keyStore) {
        var contextFactory = newSslContextFactory(keyStore);
        try {
            contextFactory.start();
        } catch (Exception e) {
            throw new ConnectorTlsKeyStoreLoadException(
                    "Failed to initialize the connector TLS SSL context from the certificate stored in OpenBao", e);
        }
        return contextFactory;
    }

    /**
     * The connector TLS serving state. Lock-free so a health probe never waits on connectors still being opened;
     * the cause is read before the open flag because the connectors open before the cause is cleared.
     */
    ConnectorTlsStatus connectorTlsStatus() {
        var cause = Optional.ofNullable(notServedCause);
        if (!connectorsOpen || stopped) {
            return new ConnectorTlsStatus(false, cause, Optional.empty());
        }
        var notAfter = servedCertificate.getNotAfter().toInstant();
        var expiredAt = clock.instant().isBefore(notAfter) ? Optional.<Instant>empty() : Optional.of(notAfter);
        return new ConnectorTlsStatus(true, Optional.empty(), expiredAt);
    }

    /**
     * Whether connector TLS is served, why not when it is not, and when the served certificate expired once it has.
     */
    record ConnectorTlsStatus(boolean serving, Optional<String> notServedCause, Optional<Instant> expiredAt) {
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
            throw new EdcException("HTTPS port %d of the '%s' connector is not bindable: %s"
                    .formatted(mapping.port(), mapping.name(), e.getMessage()), e);
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
        var connector = new ServerConnector(server, sslConnectionFactory(factory), httpsConnectionFactory(mapping));
        connector.setName(mapping.name());
        connector.setPort(mapping.port());
        var bindHost = bindHost(mapping);
        if (bindHost != null) {
            connector.setHost(bindHost);
        }
        connectorConfigurationCallbacks.forEach(callback -> callback.accept(connector));
        monitor.debug("HTTPS context '" + mapping.name() + "' listening on "
                + (bindHost != null ? bindHost : "every interface") + ", port " + mapping.port());
        return connector;
    }

    /**
     * The {@link #DEFAULT_CONTEXT_NAME} context always binds {@link #DEFAULT_CONTEXT_BIND_HOST}, no setting
     * overrides it. Any other context binds the host {@link #hostResolver} resolves for its name, or
     * {@code null} to bind every interface when none is configured or the resolved host is blank.
     */
    private String bindHost(PortMapping mapping) {
        if (DEFAULT_CONTEXT_NAME.equals(mapping.name())) {
            return DEFAULT_CONTEXT_BIND_HOST;
        }
        var resolvedHost = hostResolver.apply(mapping.name());
        return StringUtils.isBlank(resolvedHost) ? null : resolvedHost;
    }

    private static ConnectionFactory sslConnectionFactory(SslContextFactory.Server factory) {
        return new SslConnectionFactory(factory, HttpVersion.HTTP_1_1.asString());
    }

    private static ConnectionFactory httpsConnectionFactory(PortMapping mapping) {
        var httpsConfiguration = new HttpConfiguration();
        httpsConfiguration.setSecureScheme("https");
        httpsConfiguration.setSecurePort(mapping.port());
        if (carriesEncodedParticipantSegment(mapping)) {
            httpsConfiguration.setUriCompliance(
                    UriCompliance.DEFAULT.with("dsp-participant-segment", UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING));
        }
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer());
        return new HttpConnectionFactory(httpsConfiguration);
    }

    /**
     * The DSP port carries the participant context id as a path segment in which a literal percent sign travels
     * as {@code %25}; Jetty classes any encoded percent sign as ambiguous path encoding and rejects it by default.
     */
    private static boolean carriesEncodedParticipantSegment(PortMapping mapping) {
        return ApiContext.PROTOCOL.equals(mapping.name());
    }

    private ServletContextHandler createHandler(PortMapping mapping) {
        var handler = new ServletContextHandler(ROOT_PATH, 0);
        handler.setVirtualHosts(List.of("@" + mapping.name()));
        if (carriesEncodedParticipantSegment(mapping)) {
            handler.getServletHandler().setDecodeAmbiguousURIs(true);
        }
        return handler;
    }

    private ServletContextHandler getOrCreate(String contextPath) {
        return handlers.computeIfAbsent(contextPath, path -> new ServletContextHandler(ROOT_PATH, 0));
    }
}
