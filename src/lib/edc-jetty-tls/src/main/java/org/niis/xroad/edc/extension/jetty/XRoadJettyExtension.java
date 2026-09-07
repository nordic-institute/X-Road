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
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.edc.reload.InitialMaterialWait;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;

import java.time.Duration;

import static org.niis.xroad.edc.extension.jetty.XRoadJettyExtension.NAME;

/**
 * X-Road's replacement for EDC's {@code JettyExtension}. Sources the HTTPS keystore for every connector on
 * this JVM from OpenBao ({@code tls/ds-https}) instead of a file, and keeps checking it on a schedule so a
 * rotated certificate replaces the served one without a restart — capabilities stock's file-at-boot,
 * no-reload design does not expose. Boot blocks until the slot holds a certificate rather than failing, so
 * a freshly installed service converges on its own once the certificate is provisioned. Excluded from EDC's
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
    private static final Duration STARTUP_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final int RELOAD_MAX_ATTEMPTS_PER_CYCLE = 3;
    private static final Duration RELOAD_RETRY_DELAY = Duration.ofSeconds(2);

    @Setting(key = "web.http.port", description = "Port for " + DEFAULT_CONTEXT_NAME + " api context", defaultValue = DEFAULT_PORT + "")
    private int defaultPort;

    @Setting(key = "web.http.path", description = "Path for " + DEFAULT_CONTEXT_NAME + " api context", defaultValue = DEFAULT_PATH)
    private String defaultPath;

    @Setting(key = "xroad.edc.web.https.keystore.reload-interval-seconds",
            description = "How often the DataSpace TLS keystore is re-read from OpenBao to detect rotation.",
            defaultValue = "60")
    private long reloadIntervalSeconds;

    @Inject
    private VaultClient vaultClient;

    private final PortMappingRegistry portMappingRegistry = new XRoadPortMappingRegistry();

    private XRoadJettyService jettyService;
    private PeriodicMaterialReloader<?> reloader;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        portMappingRegistry.register(new PortMapping(DEFAULT_CONTEXT_NAME, defaultPort, defaultPath));

        var loader = new DsHttpsKeyStoreLoader(vaultClient);
        var initial = InitialMaterialWait.await(KEYSTORE_MATERIAL_NAME, loader, STARTUP_POLL_INTERVAL, monitor);

        jettyService = new XRoadJettyService(initial.material(), monitor, portMappingRegistry);
        context.registerService(WebServer.class, jettyService);
        context.registerService(XRoadJettyService.class, jettyService);

        reloader = PeriodicMaterialReloader.schedule(KEYSTORE_MATERIAL_NAME, initial, Duration.ofSeconds(reloadIntervalSeconds),
                RELOAD_MAX_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, loader::load, jettyService::reload, monitor);
    }

    @Override
    public void start() {
        jettyService.start();
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
