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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.health.HealthCheckService;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;

import java.security.KeyStore;
import java.time.Duration;

import static org.niis.xroad.edc.extension.jetty.XRoadJettyExtension.NAME;

/**
 * X-Road's replacement for EDC's {@code JettyExtension}. Sources the HTTPS keystore for every connector on
 * this JVM from OpenBao ({@code tls/ds-https}) instead of a file, and keeps checking it on a schedule so a
 * rotated certificate replaces the served one without a restart — capabilities stock's file-at-boot,
 * no-reload design does not expose. Boot never waits for the connector TLS certificate: one load attempt is
 * made, and when the slot is still empty the HTTPS endpoints stay closed while the rest of the runtime starts;
 * the reloader keeps polling and opens them as soon as the certificate is stored. Excluded from EDC's
 * own boot in favour of this one via
 * {@code xroad.edc.boot.excluded-service-extensions}; the {@code jetty-core} artifact carrying it is also
 * excluded from the runtime classpath entirely (see the {@code xroad.edc-owned-jetty-conventions} Gradle
 * convention), so both layers agree on which module serves HTTPS.
 */
@Extension(NAME)
@Provides({WebServer.class, XRoadJettyService.class})
public class XRoadJettyExtension implements ServiceExtension {

    static final String NAME = "X-Road Jetty Service";

    private static final String DEFAULT_CONTEXT_NAME = "default";
    private static final String DEFAULT_PATH = "/api";
    private static final int DEFAULT_PORT = 8181;
    private static final String KEYSTORE_MATERIAL_NAME = "ds-https-keystore";
    private static final Duration PENDING_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final int RELOAD_MAX_ATTEMPTS_PER_CYCLE = 3;
    private static final Duration RELOAD_RETRY_DELAY = Duration.ofSeconds(2);

    @Setting(key = "web.http.port", description = "Port for " + DEFAULT_CONTEXT_NAME + " api context", defaultValue = DEFAULT_PORT + "")
    private int defaultPort;

    @Setting(key = "web.http.path", description = "Path for " + DEFAULT_CONTEXT_NAME + " api context", defaultValue = DEFAULT_PATH)
    private String defaultPath;

    @Setting(key = "xroad.edc.web.https.keystore.reload-interval-seconds",
            description = "How often the connector TLS keystore is re-read from OpenBao to detect rotation.",
            defaultValue = "60")
    private long reloadIntervalSeconds;

    @Inject
    private VaultClient vaultClient;

    @Inject
    private HealthCheckService healthCheckService;

    private final PortMappingRegistry portMappingRegistry = new XRoadPortMappingRegistry();
    private Duration pendingPollInterval = PENDING_POLL_INTERVAL;

    private Monitor monitor;
    private XRoadJettyService jettyService;
    private DsHttpsKeyStoreLoader loader;
    private PeriodicMaterialReloader.Loaded<KeyStore> initial;
    private PeriodicMaterialReloader<?> reloader;
    private String lastLoadedFingerprint;
    private String lastUnusableFingerprint;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        portMappingRegistry.register(new PortMapping(DEFAULT_CONTEXT_NAME, defaultPort, defaultPath));

        jettyService = new XRoadJettyService(monitor, portMappingRegistry);
        context.registerService(WebServer.class, jettyService);
        context.registerService(XRoadJettyService.class, jettyService);
        healthCheckService.addReadinessProvider(new ConnectorTlsReadinessProvider(jettyService));

        loader = new DsHttpsKeyStoreLoader(vaultClient);
        initial = loadInitial();
    }

    @Override
    public void start() {
        jettyService.start();
        var reloadInterval = Duration.ofSeconds(reloadIntervalSeconds);
        reloader = initial != null && applyInitial()
                ? PeriodicMaterialReloader.schedule(KEYSTORE_MATERIAL_NAME, initial, reloadInterval,
                        RELOAD_MAX_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, this::load,
                        this::applyConnectorTlsMaterial, monitor)
                : PeriodicMaterialReloader.scheduleUntilFirstLoad(KEYSTORE_MATERIAL_NAME, pendingPollInterval, reloadInterval,
                        RELOAD_MAX_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, this::load,
                        this::applyConnectorTlsMaterial, monitor);
    }
    private PeriodicMaterialReloader.Loaded<KeyStore> loadInitial() {
        try {
            return load();
        } catch (RuntimeException e) {
            monitor.warning("Connector TLS certificate is not available yet, the HTTPS endpoints stay closed until it is "
                    + "stored in OpenBao: " + e.getMessage());
            return null;
        }
    }
    private boolean applyInitial() {
        try {
            applyConnectorTlsMaterial(initial.material());
            return true;
        } catch (RuntimeException _) {
            return false;
        }
    }
    private PeriodicMaterialReloader.Loaded<KeyStore> load() {
        var loaded = loader.load();
        lastLoadedFingerprint = loaded.fingerprint();
        return loaded;
    }
    private void applyConnectorTlsMaterial(KeyStore keyStore) {
        try {
            jettyService.applyConnectorTlsMaterial(keyStore);
        } catch (RuntimeException e) {
            if (!lastLoadedFingerprint.equals(lastUnusableFingerprint)) {
                lastUnusableFingerprint = lastLoadedFingerprint;
                monitor.severe("connector TLS certificate present but unusable (fingerprint %s): %s"
                        .formatted(lastLoadedFingerprint, e.getMessage()), e);
            }
            throw e;
        }
    }
    @Override
    public void shutdown() {
        if (reloader != null) {
            reloader.close();
        }
        if (jettyService != null) {
            jettyService.shutdown();
        }
    }

    @Provider
    public PortMappingRegistry portMappings() {
        return portMappingRegistry;
    }
}
