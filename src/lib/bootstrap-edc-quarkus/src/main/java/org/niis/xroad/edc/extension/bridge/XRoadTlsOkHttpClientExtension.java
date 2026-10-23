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
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;
import org.niis.xroad.edc.trust.ConnectorTlsCaTrustManagerLoader;
import org.niis.xroad.edc.trust.ConnectorTlsCompositeTrustManager;
import org.niis.xroad.edc.trust.DelegatingTrustManager;
import org.niis.xroad.edc.trust.RejectAllTrustManager;
import org.niis.xroad.edc.trust.VaultEndpointTrust;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import java.security.GeneralSecurityException;
import java.time.Duration;

/**
 * Replaces the default EDC {@link OkHttpClient} with one whose outbound TLS trust is entirely X-Road's own:
 * exactly the certificate authorities globalconf's {@code approvedConnectorTlsCA} list designates, fail-closed. This is
 * the one place in the X-Road EDC distribution that builds the singleton OkHttp client every DataSpace outbound
 * connection — DID resolution, STS/OAuth2, DSP dispatch, credential and status-list fetches, and EDC's own
 * OpenBao vault client — shares, so it is also the only place trust needs replacing. The JVM default trust store
 * is never merged in, and the member {@code approvedCA} list is never consulted.
 *
 * <p>It is also the only place that sets the client's concurrent-request limits: the {@link Dispatcher}'s
 * {@code maxRequestsPerHost} comes from {@link DspConfigKeys#OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST}, and
 * {@code maxRequests} is raised to match it when the configured per-host limit exceeds the dispatcher default.
 *
 * <p>The {@code @Inject RetryPolicy} field forces EDC's dependency resolver to run this extension after
 * {@code RuntimeDefaultCoreServicesExtension}, whose {@code @Provider OkHttpClient} would otherwise be the last
 * registered instance.
 */
@Extension(XRoadTlsOkHttpClientExtension.NAME)
public class XRoadTlsOkHttpClientExtension implements ServiceExtension {

    static final String NAME = "X-Road TLS OkHttpClient";

    private static final String TRUST_MATERIAL_NAME = "ds-tls-ca-trust";

    private static final String VAULT_URL_SETTING = "edc.vault.hashicorp.url";
    private static final String VAULT_CA_CERT_ENV = "QUARKUS_VAULT_TLS_CA_CERT";

    /** Mirrors {@code xroad.edc.web.https.keystore.reload-interval-seconds} (XRoadJettyExtension) in naming. */
    private static final String RELOAD_INTERVAL_SETTING = "xroad.edc.web.https.trust.reload-interval-seconds";
    private static final long DEFAULT_RELOAD_INTERVAL_SECONDS = 60;
    private static final int RELOAD_MAX_ATTEMPTS_PER_CYCLE = 3;
    private static final Duration RELOAD_RETRY_DELAY = Duration.ofSeconds(2);

    /** EDC's own setting keys for its default client; X-Road defaults both to 10 s where EDC defaults to 30 s. */
    private static final String CONNECT_TIMEOUT_SETTING = "edc.http.client.timeout.connect";
    private static final String READ_TIMEOUT_SETTING = "edc.http.client.timeout.read";
    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final long DEFAULT_READ_TIMEOUT_SECONDS = 10;

    @Inject
    @SuppressWarnings("unused")
    private RetryPolicy<?> retryPolicy;

    private final Duration pendingPollInterval;

    private PeriodicMaterialReloader<?> reloader;

    public XRoadTlsOkHttpClientExtension() {
        this(PeriodicMaterialReloader.DEFAULT_PENDING_INTERVAL);
    }

    XRoadTlsOkHttpClientExtension(Duration pendingPollInterval) {
        this.pendingPollInterval = pendingPollInterval;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public OkHttpClient okHttpClient(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var globalConfProvider = context.getService(GlobalConfProvider.class);
        var loader = new ConnectorTlsCaTrustManagerLoader(globalConfProvider);
        var initial = loadInitial(loader, monitor);

        var listTrustManager = new DelegatingTrustManager(
                initial.loaded() != null ? initial.loaded().material() : RejectAllTrustManager.INSTANCE);
        var vaultTrust = VaultEndpointTrust.from(
                context.getSetting(VAULT_URL_SETTING, null),
                System.getenv(VAULT_CA_CERT_ENV),
                monitor);
        var trustManager = new ConnectorTlsCompositeTrustManager(vaultTrust.orElse(null), listTrustManager);

        var reloadInterval = Duration.ofSeconds(context.getSetting(RELOAD_INTERVAL_SETTING, DEFAULT_RELOAD_INTERVAL_SECONDS));
        reloader = initial.loaded() != null
                ? PeriodicMaterialReloader.schedule(TRUST_MATERIAL_NAME, initial.loaded(), reloadInterval,
                        RELOAD_MAX_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, loader::load, listTrustManager::setDelegate, monitor)
                : PeriodicMaterialReloader.scheduleUntilFirstLoad(TRUST_MATERIAL_NAME, pendingPollInterval, reloadInterval,
                        RELOAD_MAX_ATTEMPTS_PER_CYCLE, RELOAD_RETRY_DELAY, loader::load, listTrustManager::setDelegate, monitor,
                        initial.failure());

        return buildClient(trustManager,
                Duration.ofSeconds(context.getSetting(CONNECT_TIMEOUT_SETTING, DEFAULT_CONNECT_TIMEOUT_SECONDS)),
                Duration.ofSeconds(context.getSetting(READ_TIMEOUT_SETTING, DEFAULT_READ_TIMEOUT_SECONDS)),
                resolveMaxRequestsPerHost(context, monitor));
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
     * a valid running state). When the boot load fails, the reloader polls at the pending interval until the
     * first load succeeds and then continues at the normal interval. Globalconf that has not been initialized yet
     * is an expected state on a fresh Security Server, so it is logged as a warning without a stack trace.
     */
    private static InitialLoad loadInitial(ConnectorTlsCaTrustManagerLoader loader, Monitor monitor) {
        try {
            return new InitialLoad(loader.load(), null);
        } catch (GlobalConfInitException e) {
            monitor.warning("Globalconf is not initialized yet; rejecting all DataSpace TLS connections until the "
                    + "DataSpace TLS CA list can be loaded: " + e.getMessage());
            return new InitialLoad(null, String.valueOf(e.getMessage()));
        } catch (RuntimeException e) {
            monitor.severe("Failed to load the DataSpace TLS CA list from globalconf at startup; rejecting all "
                    + "DataSpace TLS connections until a poll succeeds", e);
            return new InitialLoad(null, String.valueOf(e.getMessage()));
        }
    }

    private static OkHttpClient buildClient(X509ExtendedTrustManager trustManager, Duration connectTimeout, Duration readTimeout,
                                            int maxRequestsPerHost) {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustManager}, null);
            var dispatcher = new Dispatcher();
            dispatcher.setMaxRequestsPerHost(maxRequestsPerHost);
            dispatcher.setMaxRequests(Math.max(dispatcher.getMaxRequests(), maxRequestsPerHost));
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .dispatcher(dispatcher)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new EdcException("Failed to initialize the DataSpace outbound TLS trust context", e);
        }
    }

    private static int resolveMaxRequestsPerHost(ServiceExtensionContext context, Monitor monitor) {
        var key = DspConfigKeys.OUTBOUND_HTTP_MAX_REQUESTS_PER_HOST;
        int defaultValue = key.convertedDefaultValue();
        int configuredValue = context.getSetting(key.key(), defaultValue);
        if (configuredValue < 1) {
            monitor.warning("%s: %s must be at least 1, got %d; using %d"
                    .formatted(NAME, key.key(), configuredValue, defaultValue));
            return defaultValue;
        }
        return configuredValue;
    }

    private record InitialLoad(PeriodicMaterialReloader.Loaded<X509ExtendedTrustManager> loaded, String failure) {
    }
}
