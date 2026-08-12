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

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.reload.PeriodicMaterialReloader;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import static org.niis.xroad.edc.web.jetty.XRoadJettyExtension.NAME;

/**
 * X-Road's replacement for EDC's {@code jetty-core} module (see {@code JettyExtension} in that
 * artifact): serves the DS TLS keystore from OpenBao ({@link VaultClient#getDsHttpsTlsCredentials()})
 * instead of a file path, with no keystore-password setting and no file fallback. An empty or
 * unreachable vault slot fails {@link #initialize(ServiceExtensionContext)} outright - there is
 * nothing to serve HTTPS with, so there is nothing this extension can safely start.
 *
 * <p>Once started, the keystore is kept fresh by a {@link PeriodicMaterialReloader} (see
 * lib:vault-core) - fingerprinted on the leaf certificate, retried with backoff within a cycle, old
 * material kept on exhaustion, the schedule itself never cancelled. This is the reusable reload seam:
 * the fail-closed outbound trust extension (a later addition to this distribution) is its second
 * consumer, reloading the {@code approvedDsTlsCa} trust set through the same component.
 *
 * <p>{@code WebServer} and {@link PortMappingRegistry} stay the exact upstream {@code web-spi}
 * interfaces, so every other EDC extension that only depends on those (the vast majority - Jersey,
 * DSP, DID, STS, ...) keeps working unmodified against this replacement.
 */
@Extension(NAME)
@Provides({WebServer.class, XRoadJettyService.class})
public class XRoadJettyExtension implements ServiceExtension {

    static final String NAME = "X-Road Jetty Service";

    private static final String DEFAULT_CONTEXT_NAME = "default";
    private static final String KEYSTORE_ALIAS = "ds-https";
    private static final String KEYSTORE_TYPE = "PKCS12";

    @Configuration
    private DefaultApiConfiguration apiConfiguration;

    @Setting(key = "xroad.edc.web.https.reload-interval-seconds",
            description = "Interval, in seconds, at which the DS TLS keystore is reloaded from the secret store.",
            defaultValue = "60")
    private long reloadIntervalSeconds;

    @Inject
    private VaultClient vaultClient;

    private final PortMappingRegistry portMappingRegistry = new PortMappingRegistryImpl();

    private XRoadJettyService jettyService;
    private PeriodicMaterialReloader<InternalSSLKey> reloader;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        portMappingRegistry.register(new PortMapping(DEFAULT_CONTEXT_NAME, apiConfiguration.port(), apiConfiguration.path()));

        jettyService = new XRoadJettyService(monitor, portMappingRegistry);

        reloader = PeriodicMaterialReloader.<InternalSSLKey>builder("ds-https-tls", vaultClient::getDsHttpsTlsCredentials)
                .fingerprint(XRoadJettyExtension::fingerprintOf)
                .onChange(key -> jettyService.applyKeyStore(toKeyStore(key)))
                .reloadInterval(Duration.ofSeconds(reloadIntervalSeconds))
                .build();

        try {
            reloader.loadInitial();
        } catch (Exception e) {
            throw new EdcException("No DS TLS certificate found in the secret store (tls/ds-https). Enroll one "
                    + "via ACME or upload one manually before starting this component - there is no file-based "
                    + "fallback.", e);
        }

        context.registerService(WebServer.class, jettyService);
        context.registerService(XRoadJettyService.class, jettyService);
    }

    @Override
    public void start() {
        jettyService.start();
        reloader.start();
    }

    @Override
    public void shutdown() {
        if (reloader != null) {
            reloader.shutdown();
        }
        if (jettyService != null) {
            jettyService.shutdown();
        }
    }

    @Provider
    public PortMappingRegistry portMappings() {
        return portMappingRegistry;
    }

    private static String fingerprintOf(InternalSSLKey key) {
        try {
            var leaf = key.getCertChain()[0];
            var digest = MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded());
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new EdcException("Failed to compute the DS TLS certificate fingerprint", e);
        }
    }

    private static KeyStore toKeyStore(InternalSSLKey key) throws IOException, GeneralSecurityException {
        var keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEYSTORE_ALIAS, key.getKey(), null, key.getCertChain());
        return keyStore;
    }

    @Settings
    record DefaultApiConfiguration(
            @Setting(key = "web.http.port", description = "Port for default api context", defaultValue = "8181") int port,
            @Setting(key = "web.http.path", description = "Path for default api context", defaultValue = "/api") String path) {
    }
}
