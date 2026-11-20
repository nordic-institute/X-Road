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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CACHE_IDLE_EXPIRY;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CACHE_MAX_ISSUER_DIDS;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_CACHE_MAX_STATUS_LISTS;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_DOWNLOAD_WAIT;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_EXPIRY_MARGIN;
import static org.niis.xroad.edc.issuerdata.IssuerDataExtension.NAME;

/**
 * Replaces the stock bitstring status list revocation service with one that serves the last good status list
 * while the issuer does not answer, also after a restart. It replaces the stock {@code web} DID resolver the same
 * way for the credential issuer DIDs of the own instance. The last good copies are kept in the default data source.
 * The stock cache time, accepted content type, DID over HTTPS and DNS over HTTPS settings keep their meaning.
 */
@Extension(NAME)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
public class IssuerDataExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuer Data";
    static final String CACHE_VALIDITY_SETTING = "edc.iam.credential.revocation.cache.validity";
    static final String CONTENT_TYPES_SETTING = "edc.iam.credential.revocation.mimetype";
    static final long DEFAULT_CACHE_VALIDITY_MILLIS = 15 * 60 * 1000L;
    static final String DID_CACHE_EXPIRY_SETTING = "edc.did.resolver.cache.expiry";
    static final long DEFAULT_DID_CACHE_EXPIRY_MILLIS = 5 * 60 * 1000L;
    static final String DEFAULT_CONTENT_TYPES = "*/*";
    static final String WEB_DID_HTTPS_SETTING = "edc.iam.did.web.use.https";
    static final String WEB_DID_DOH_SETTING = "edc.webdid.doh.url";
    static final String REFRESH_THREAD_PREFIX = "xroad-issuer-data-refresh-";

    @Inject
    private RevocationServiceRegistry revocationServiceRegistry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private EdcHttpClient httpClient;

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

    private ExecutorService refreshExecutor;

    private IssuerStatusListAvailabilityRule availabilityRule;

    private Clock clock = Clock.systemUTC();

    private Ticker ticker = Ticker.systemTicker();

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

        var fetcher = new BitstringStatusListFetcher(mapper, contentTypes, httpClient, tokenValidationService,
                didPublicKeyResolver);
        var store = new SqlIssuerDataStore(dataSourceRegistry, DataSourceRegistry.DEFAULT_DATASOURCE, transactionContext,
                queryExecutor);
        refreshExecutor = executorInstrumentation.instrument(
                Executors.newCachedThreadPool(Thread.ofPlatform().name(REFRESH_THREAD_PREFIX, 0).daemon().factory()), NAME);
        var retryDelay = RetryDelay.from(xRoadConfig, monitor);
        var source = new LastGoodSource<>(fetcher, clock, ticker, refreshExecutor, cacheTime,
                settings(ISSUER_DATA_CACHE_MAX_STATUS_LISTS, retryDelay), maximumAge(globalConfProvider), store, monitor);
        revocationServiceRegistry.addService(BitstringStatusListStatus.TYPE, new XRoadBitstringStatusListRevocationService(
                mapper, contentTypes, httpClient, tokenValidationService, didPublicKeyResolver, source));
        availabilityRule = new IssuerStatusListAvailabilityRule(globalConfProvider, source);

        var didHttpClient = didHttpClient(context);
        var useHttps = context.getSetting(WEB_DID_HTTPS_SETTING, true);
        var stockResolver = new WebDidResolver(didHttpClient, useHttps, mapper, monitor);
        var didFetcher = new DidDocumentFetcher(didHttpClient, new DidWebUrl(useHttps), mapper);
        var didCacheTime = Duration.ofMillis(Math.max(0, context.getSetting(DID_CACHE_EXPIRY_SETTING,
                DEFAULT_DID_CACHE_EXPIRY_MILLIS)));
        var didSource = new LastGoodSource<>(didFetcher, clock, ticker, refreshExecutor, didCacheTime,
                settings(ISSUER_DATA_CACHE_MAX_ISSUER_DIDS, retryDelay), maximumAge(globalConfProvider), store, monitor);
        didResolverRegistry.register(new IssuerDidResolver(stockResolver, didSource, globalConfProvider));
    }

    @Provider
    public IssuerStatusListAvailabilityRule issuerStatusListAvailabilityRule() {
        return availabilityRule;
    }

    @Override
    public void shutdown() {
        if (refreshExecutor != null) {
            refreshExecutor.shutdownNow();
        }
    }

    private LastGoodSettings settings(ConfigKey<Integer> maximumEntriesKey, RetryDelay retryDelay) {
        return new LastGoodSettings(xRoadConfig.value(maximumEntriesKey), xRoadConfig.value(ISSUER_DATA_CACHE_IDLE_EXPIRY),
                xRoadConfig.value(ISSUER_DATA_DOWNLOAD_WAIT), xRoadConfig.value(ISSUER_DATA_EXPIRY_MARGIN), retryDelay);
    }

    private EdcHttpClient didHttpClient(ServiceExtensionContext context) {
        var dohUrl = context.getSetting(WEB_DID_DOH_SETTING, null);
        return dohUrl == null || dohUrl.isEmpty() ? httpClient : httpClient.withDns(dohUrl);
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
