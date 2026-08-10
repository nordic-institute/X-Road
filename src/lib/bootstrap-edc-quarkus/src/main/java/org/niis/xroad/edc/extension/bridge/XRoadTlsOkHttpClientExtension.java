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
package org.niis.xroad.edc.extension.bridge;

import dev.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.edc.extension.bridge.trust.DsTlsTrustSource;
import org.niis.xroad.edc.extension.bridge.trust.VaultEndpoint;
import org.niis.xroad.globalconf.GlobalConfProvider;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces EDC's default {@link OkHttpClient} - the one shared instance injected into every EDC
 * HTTP client (DID resolution, STS/OAuth2, DSP dispatch, credential and status-list fetches, and
 * EDC's own OpenBao vault client) - with fail-closed dataspace TLS trust: a trust manager built
 * in-process from the globalconf {@code approvedDsTlsCa} chains alone. The JVM default trust store
 * is replaced, not extended, and the member {@code approvedCA} list is never consulted. Trust
 * refreshes on DS TLS CA list changes through its own {@link DsTlsTrustSource}, independent of the
 * DS TLS keystore's reload cycle.
 *
 * <p>One tightly-scoped exception: EDC's OpenBao (Hashicorp Vault) client shares this same HTTP
 * client, so the OpenBao CA certificate(s) supplied via {@code QUARKUS_VAULT_TLS_CA_CERT} (the same
 * env var the Quarkus vault client uses) are additionally trusted - but strictly scoped to the
 * vault endpoint parsed from {@code edc.vault.hashicorp.url}, the same setting EDC's own
 * {@code HashicorpVaultExtension} reads. See {@code DsTlsTrustManager} for exactly which paths fall
 * back to list-only trust.
 *
 * <p>The {@code @Inject RetryPolicy} field forces EDC's dependency resolver to run this extension
 * after {@code RuntimeDefaultCoreServicesExtension}, whose {@code @Provider OkHttpClient} would
 * otherwise be the last registered instance.
 */
@Extension(XRoadTlsOkHttpClientExtension.NAME)
public class XRoadTlsOkHttpClientExtension implements ServiceExtension {

    static final String NAME = "X-Road TLS OkHttpClient";

    private static final String VAULT_TLS_CA_CERT_ENV = "QUARKUS_VAULT_TLS_CA_CERT";
    private static final String VAULT_URL_SETTING = "edc.vault.hashicorp.url";

    @Setting(key = "xroad.edc.dataspace.trust.reload-interval-seconds",
            description = "Interval, in seconds, at which the dataspace TLS trust set is reloaded from globalconf.",
            defaultValue = "60")
    private long reloadIntervalSeconds;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Inject
    @SuppressWarnings("unused")
    private RetryPolicy<?> retryPolicy;

    private DsTlsTrustSource trustSource;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void shutdown() {
        if (trustSource != null) {
            trustSource.shutdown();
        }
    }

    @Provider
    public OkHttpClient okHttpClient(ServiceExtensionContext context) {
        return buildOkHttpClient(context, System.getenv(VAULT_TLS_CA_CERT_ENV));
    }

    OkHttpClient buildOkHttpClient(ServiceExtensionContext context, String vaultCaCertPath) {
        var monitor = context.getMonitor();
        var vaultEndpoint = VaultEndpoint.parse(context.getSetting(VAULT_URL_SETTING, null)).orElse(null);
        var vaultCaCertificates = loadVaultCaCertificates(vaultCaCertPath, monitor);

        trustSource = new DsTlsTrustSource(globalConfProvider, monitor, vaultEndpoint, vaultCaCertificates,
                Duration.ofSeconds(reloadIntervalSeconds));
        trustSource.start();
        var trustManager = trustSource.trustManager();

        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustManager}, null);
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new EdcException("Failed to initialize the dataspace TLS trust context", e);
        }
    }

    private static List<X509Certificate> loadVaultCaCertificates(String certPath, Monitor monitor) {
        if (certPath == null || certPath.isBlank()) {
            return List.of();
        }
        try (var in = new FileInputStream(certPath)) {
            var certificateFactory = CertificateFactory.getInstance("X.509");
            var certificates = new ArrayList<X509Certificate>();
            for (var certificate : certificateFactory.generateCertificates(in)) {
                certificates.add((X509Certificate) certificate);
            }
            return List.copyOf(certificates);
        } catch (IOException | GeneralSecurityException e) {
            monitor.warning("Failed to load the OpenBao TLS CA certificate from %s - the vault-scoped trust "
                    + "exception is disabled, OpenBao access now depends on its CA also being in the DS TLS CA "
                    + "list: %s".formatted(certPath, e.getMessage()));
            return List.of();
        }
    }
}
