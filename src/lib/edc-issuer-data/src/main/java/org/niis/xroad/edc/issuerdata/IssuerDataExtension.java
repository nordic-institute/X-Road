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

package org.niis.xroad.edc.issuerdata;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.failsafe.RetryPolicy;
import lombok.NoArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CACHE_DID_DOWNLOAD_SIZE;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_DOWNLOAD_SIZE;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CACHE_STATUSLIST_SIZE;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_DOWNLOAD_EXPIRE_AFTER;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_DOWNLOAD_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_DOWNLOAD_WAIT;
import static org.niis.xroad.edc.issuerdata.IssuerDataExtension.NAME;

/**
 * Replaces the stock bitstring statuslist revocation service with one that serves the last good statuslist
 * while the issuer does not answer, also after a restart. It serves the DID documents of the credential issuers of the
 * own instance the same way, through an {@link IssuerDidResolver} that the {@link IssuerDataDidResolverRegistry}
 * calls without caching, and registers the stock {@code web} DID resolver for every other {@code did:web} DID. The last
 * good copies are kept in the default data source.
 * The stock cache time, accepted content type, DID over HTTPS and DNS over HTTPS settings keep their meaning.
 * The downloads of both kinds end within the download timeout and are not retried by the HTTP client; the stock
 * resolver keeps the shared HTTP client.
 */
@Extension(NAME)
@NoArgsConstructor
public class IssuerDataExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuer Data";
    static final String CACHE_VALIDITY_SETTING = "edc.iam.credential.revocation.cache.validity";
    static final String CONTENT_TYPES_SETTING = "edc.iam.credential.revocation.mimetype";
    static final long DEFAULT_CACHE_VALIDITY_MILLIS = 15 * 60 * 1000L;
    static final String DID_CACHE_EXPIRY_SETTING = "edc.did.resolver.cache.expiry";
    static final long DEFAULT_DID_CACHE_EXPIRY_MILLIS = 5 * 60 * 1000L;
    static final String DID_CACHE_SIZE_SETTING = "edc.did.resolver.cache.size";
    static final int DEFAULT_DID_CACHE_SIZE = 50;
    static final String DEFAULT_CONTENT_TYPES = "*/*";
    static final String WEB_DID_HTTPS_SETTING = "edc.iam.did.web.use.https";
    static final String WEB_DID_DOH_SETTING = "edc.webdid.doh.url";
    static final String REFRESH_THREAD_PREFIX = "xroad-issuer-data-refresh-";
    static final String SCHEDULER_THREAD_NAME = "xroad-issuer-data-scheduler";

    @Inject
    private RevocationServiceRegistry revocationServiceRegistry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private TokenValidationService tokenValidationService;

    @Inject
    private DidPublicKeyResolver didPublicKeyResolver;

    @Inject
    private DidResolverRegistry didResolverRegistry;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private XRoadConfig xRoadConfig;

    @Inject
    private Clock clock;

    private ExecutorService refreshExecutor;
    private ScheduledExecutorService scheduler;
    private IssuerDataStore issuerDataStore;

    private IssuerStatusListAvailabilityRule availabilityRule;

    IssuerDataExtension(RevocationServiceRegistry revocationServiceRegistry, TypeManager typeManager,
                        EdcHttpClient httpClient, OkHttpClient okHttpClient, TokenValidationService tokenValidationService,
                        DidPublicKeyResolver didPublicKeyResolver, DidResolverRegistry didResolverRegistry,
                        GlobalConfProvider globalConfProvider, ExecutorInstrumentation executorInstrumentation,
                        XRoadConfig xRoadConfig, IssuerDataStore issuerDataStore, Clock clock) {
        this.revocationServiceRegistry = revocationServiceRegistry;
        this.typeManager = typeManager;
        this.httpClient = httpClient;
        this.okHttpClient = okHttpClient;
        this.tokenValidationService = tokenValidationService;
        this.didPublicKeyResolver = didPublicKeyResolver;
        this.didResolverRegistry = didResolverRegistry;
        this.globalConfProvider = globalConfProvider;
        this.executorInstrumentation = executorInstrumentation;
        this.xRoadConfig = xRoadConfig;
        this.issuerDataStore = issuerDataStore;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        var cacheTime = Duration.ofMillis(Math.max(0, context.getSetting(CACHE_VALIDITY_SETTING, DEFAULT_CACHE_VALIDITY_MILLIS)));
        var contentTypes = List.of(context.getSetting(CONTENT_TYPES_SETTING, DEFAULT_CONTENT_TYPES).split(","));
        var mapper = typeManager.getMapper();

        var fetchClient = fetchClient(monitor);
        var fetcher = new BitstringStatusListFetcher(mapper, contentTypes, fetchClient, tokenValidationService,
                didPublicKeyResolver);
        var store = issuerDataStore != null ? issuerDataStore
                : new SqlIssuerDataStore(dataSourceRegistry, DataSourceRegistry.DEFAULT_DATASOURCE, transactionContext,
                        queryExecutor);
        refreshExecutor = executorInstrumentation.instrument(
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(REFRESH_THREAD_PREFIX, 0).factory()), NAME);
        scheduler = IssuerDataExecutors.newScheduler(SCHEDULER_THREAD_NAME, executorInstrumentation);
        var downloadRetry = DownloadRetry.from(xRoadConfig);
        var source = new LastGoodSource<>(fetcher, clock, Ticker.systemTicker(), refreshExecutor, scheduler, cacheTime,
                settings(xRoadConfig.value(ISSUER_DATA_CACHE_STATUSLIST_SIZE),
                        xRoadConfig.value(ISSUER_DATA_CACHE_STATUSLIST_DOWNLOAD_SIZE), downloadRetry),
                maximumAge(globalConfProvider), store, monitor);
        revocationServiceRegistry.addService(BitstringStatusListStatus.TYPE, new XRoadBitstringStatusListRevocationService(
                mapper, contentTypes, fetchClient, tokenValidationService, didPublicKeyResolver, source));
        availabilityRule = new IssuerStatusListAvailabilityRule(globalConfProvider, source);

        var useHttps = context.getSetting(WEB_DID_HTTPS_SETTING, true);
        var didFetcher = new DidDocumentFetcher(withDohResolver(fetchClient, context), new DidWebUrl(useHttps), mapper);
        var didCacheTime = Duration.ofMillis(Math.max(0, context.getSetting(DID_CACHE_EXPIRY_SETTING,
                DEFAULT_DID_CACHE_EXPIRY_MILLIS)));
        var didCacheSize = Math.max(0, context.getSetting(DID_CACHE_SIZE_SETTING, DEFAULT_DID_CACHE_SIZE));
        var didSource = new LastGoodSource<>(didFetcher, clock, Ticker.systemTicker(), refreshExecutor, scheduler, didCacheTime,
                settings(didCacheSize, xRoadConfig.value(ISSUER_DATA_CACHE_DID_DOWNLOAD_SIZE), downloadRetry),
                maximumAge(globalConfProvider), store, monitor);
        didResolverRegistry.register(new IssuerWebDidResolver(didSource, globalConfProvider, monitor));
        didResolverRegistry.register(new WebDidResolver(withDohResolver(httpClient, context), useHttps, mapper, monitor));
    }

    @Provider
    public IssuerStatusListAvailabilityRule issuerStatusListAvailabilityRule() {
        return availabilityRule;
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (refreshExecutor != null) {
            refreshExecutor.shutdownNow();
        }
    }

    private IssuerDataSettings settings(long maximumEntries, long maximumDownloads, DownloadRetry downloadRetry) {
        return new IssuerDataSettings(maximumEntries, maximumDownloads, xRoadConfig.value(ISSUER_DATA_DOWNLOAD_WAIT),
                downloadRetry, xRoadConfig.value(ISSUER_DATA_DOWNLOAD_EXPIRE_AFTER));
    }

    /**
     * Builds the client for issuer data downloads: {@link BitstringStatusListFetcher} (statuslists) and
     * {@link DidDocumentFetcher} (issuer DID documents). EDC retries are off: {@code callTimeout} applies to each
     * attempt, so EDC retries would multiply the download timeout. The per-key backoff in {@link LastGoodSource} is
     * the only retry layer.
     * <p>
     * The stock {@link WebDidResolver} keeps the shared client with the EDC retry settings. It resolves participant
     * DIDs, so the issuer data download timeout must not limit it, and participant DID resolution stays as in stock
     * EDC.
     */
    private EdcHttpClient fetchClient(Monitor monitor) {
        var limited = okHttpClient.newBuilder()
                .callTimeout(xRoadConfig.value(ISSUER_DATA_DOWNLOAD_TIMEOUT))
                .build();
        return new EdcHttpClientImpl(limited, RetryPolicy.<Response>builder().withMaxRetries(0).build(), monitor);
    }

    private static EdcHttpClient withDohResolver(EdcHttpClient client, ServiceExtensionContext context) {
        var dohUrl = context.getSetting(WEB_DID_DOH_SETTING, null);
        return dohUrl == null || dohUrl.isEmpty() ? client : client.withDns(dohUrl);
    }

    static Supplier<Result<Duration>> maximumAge(GlobalConfProvider globalConf) {
        return () -> {
            try {
                var seconds = globalConf.getIssuerDataFreshnessSeconds(globalConf.getInstanceIdentifier());
                return Result.success(Duration.ofSeconds(Math.max(0, seconds)));
            } catch (RuntimeException e) {
                return Result.failure("Global configuration is not readable: " + e.getMessage());
            }
        };
    }
}
