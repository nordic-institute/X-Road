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
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;
import org.niis.xroad.edc.trust.DelegatingTrustManager;
import org.niis.xroad.edc.trust.DsTlsCaTrustManagerLoader;
import org.niis.xroad.edc.trust.DsTlsCompositeTrustManager;
import org.niis.xroad.edc.trust.RejectAllTrustManager;
import org.niis.xroad.edc.trust.VaultEndpointTrust;
import org.niis.xroad.globalconf.GlobalConfProvider;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import java.security.GeneralSecurityException;
import java.time.Duration;

/**
 * Replaces the default EDC {@link OkHttpClient} with one whose outbound TLS trust is entirely X-Road's own:
 * exactly the certificate authorities globalconf's {@code approvedDsTlsCa} list designates, fail-closed. This is
 * the one place in the X-Road EDC distribution that builds the singleton OkHttp client every DataSpace outbound
 * connection — DID resolution, STS/OAuth2, DSP dispatch, credential and status-list fetches, and EDC's own
 * OpenBao vault client — shares, so it is also the only place trust needs replacing. The JVM default trust store
 * is never merged in, and the member {@code approvedCA} list is never consulted.
 *
 * <p>The {@code @Inject RetryPolicy} field forces EDC's dependency resolver to run this extension after
 * {@code RuntimeDefaultCoreServicesExtension}, whose {@code @Provider OkHttpClient} would otherwise be the last
 * registered instance.
 */
@Extension(XRoadTlsOkHttpClientExtension.NAME)
public class XRoadTlsOkHttpClientExtension implements ServiceExtension {

    static final String NAME = "X-Road TLS OkHttpClient";

    private static final String VAULT_URL_SETTING = "edc.vault.hashicorp.url";
    private static final String VAULT_CA_CERT_ENV = "QUARKUS_VAULT_TLS_CA_CERT";

    /** Mirrors {@code xroad.edc.web.https.keystore.reload-interval-seconds} (XRoadJettyExtension) in naming. */
    private static final String RELOAD_INTERVAL_SETTING = "xroad.edc.web.https.trust.reload-interval-seconds";
    private static final long DEFAULT_RELOAD_INTERVAL_SECONDS = 60;
    private static final int RELOAD_MAX_ATTEMPTS_PER_CYCLE = 3;
    private static final Duration RELOAD_RETRY_DELAY = Duration.ofSeconds(2);

    @Inject
    @SuppressWarnings("unused")
    private RetryPolicy<?> retryPolicy;

    private PeriodicMaterialReloader<?> reloader;

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public OkHttpClient okHttpClient(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var globalConfProvider = context.getService(GlobalConfProvider.class);
        var loader = new DsTlsCaTrustManagerLoader(globalConfProvider);
        var initial = loadOrRejectAll(loader, monitor);

        var listTrustManager = new DelegatingTrustManager(initial.material());
        var vaultTrust = VaultEndpointTrust.from(
                context.getSetting(VAULT_URL_SETTING, null),
                System.getenv(VAULT_CA_CERT_ENV),
                monitor);
        var trustManager = new DsTlsCompositeTrustManager(vaultTrust.orElse(null), listTrustManager);

        var reloadInterval = Duration.ofSeconds(context.getSetting(RELOAD_INTERVAL_SETTING, DEFAULT_RELOAD_INTERVAL_SECONDS));
        reloader = PeriodicMaterialReloader.schedule("ds-tls-ca-trust", initial, reloadInterval,
                RELOAD_MAX_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, loader::load, listTrustManager::setDelegate, monitor);

        return buildClient(trustManager);
    }

    @Override
    public void shutdown() {
        if (reloader != null) {
            reloader.close();
        }
    }

    /**
     * Globalconf answering successfully with an empty list and globalconf being unreadable are deliberately
     * different outcomes everywhere else in this class's collaborators — but at boot, before any reload cycle
     * has had a chance to run, both must leave the DataSpace TLS surface in the same safe place: rejecting every
     * connection rather than aborting startup (unlike the serving keystore, an empty or unreachable trust list is
     * a valid running state). The periodic reloader takes over from here and keeps retrying on its own schedule.
     */
    private static PeriodicMaterialReloader.Loaded<X509ExtendedTrustManager> loadOrRejectAll(
            DsTlsCaTrustManagerLoader loader, Monitor monitor) {
        try {
            return loader.load();
        } catch (RuntimeException e) {
            monitor.severe("Failed to load the DataSpace TLS CA list from globalconf at startup; rejecting all "
                    + "DataSpace TLS connections until a scheduled refresh succeeds", e);
            return new PeriodicMaterialReloader.Loaded<>(RejectAllTrustManager.INSTANCE, DsTlsCaTrustManagerLoader.REJECT_ALL_FINGERPRINT);
        }
    }

    private static OkHttpClient buildClient(X509ExtendedTrustManager trustManager) {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustManager}, null);
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new EdcException("Failed to initialize the DataSpace outbound TLS trust context", e);
        }
    }
}
