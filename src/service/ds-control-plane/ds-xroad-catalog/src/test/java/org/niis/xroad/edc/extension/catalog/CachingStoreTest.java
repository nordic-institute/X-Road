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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.google.common.base.Ticker;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.extension.catalog.ParticipantContextTestFixtures.participantContext;

@ExtendWith(MockitoExtension.class)
class CachingStoreTest {

    private static final String SYSTEM_PARTICIPANT_CONTEXT_ID = ParticipantIdentifierScheme.SYSTEM_SEGMENT;
    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "getRecords", "v1");
    private static final String MEMBER_CTX =
            ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "1111"));

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    private StoreEnumerationCache<Asset> noCache;
    private StoreEnumerationCache<Asset> withCache;
    private ServiceContextResolver serviceContextResolver;
    private final ThreadLocalRequestedParticipantContext requestedParticipantContext = new ThreadLocalRequestedParticipantContext();

    @BeforeEach
    void setUp() {
        noCache = new StoreEnumerationCache<>(false, 60, 1000, "test");
        withCache = new StoreEnumerationCache<>(true, 3600, 1000, "test");
        lenient().when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of()));
        lenient().when(participantContextService.getParticipantContext(any()))
                .thenReturn(ServiceResult.notFound("no such context"));
        serviceContextResolver = new ServiceContextResolver(
                globalConfProvider, serverConfProvider, participantContextService);
        requestedParticipantContext.clear();
    }

    private AssetIndexServerConfStore buildStore(StoreEnumerationCache<Asset> cache) {
        return new AssetIndexServerConfStore(serverConfProvider,
                new BuiltinServiceCatalog(serverConfProvider, false, false, false,
                        BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL), cache,
                serviceContextResolver, requestedParticipantContext);
    }

    private void setupSingleMemberService() {
        lenient().when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        lenient().when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(nonEmptyAcl());
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        lenient().when(participantContextService.search(any()))
                .thenReturn(ServiceResult.success(List.of(participantContext(MEMBER_CTX))));
    }

    @Test
    void queryAssetsHitServedFromCache() {
        setupSingleMemberService();
        var store = buildStore(withCache);

        store.queryAssets(QuerySpec.max()).count();
        store.queryAssets(QuerySpec.max()).count();

        verify(serverConfProvider, times(1)).getMembers();
    }

    @Test
    void queryAssetsAfterInvalidateReloads() {
        setupSingleMemberService();
        var store = buildStore(withCache);

        store.queryAssets(QuerySpec.max()).count();
        withCache.invalidate();
        store.queryAssets(QuerySpec.max()).count();

        verify(serverConfProvider, times(2)).getMembers();
    }

    @Test
    void queryAssetsCacheDisabledReloadsEachCall() {
        setupSingleMemberService();
        var store = buildStore(noCache);

        store.queryAssets(QuerySpec.max()).count();
        store.queryAssets(QuerySpec.max()).count();

        verify(serverConfProvider, times(2)).getMembers();
    }

    @Test
    void findByIdHitServedFromCache() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var store = buildStore(withCache);

        store.findById(SERVICE_1.asEncodedId());
        store.findById(SERVICE_1.asEncodedId());

        verify(serverConfProvider, times(1)).serviceExists(SERVICE_1);
    }

    @Test
    void findByIdCacheKeyIncludesRequestedParticipantContext() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var store = buildStore(withCache);

        // A syntactically valid member ctx-id and "no context requested" are both plausible
        // requested contexts, so each must key its own cache entry.
        requestedParticipantContext.set(MEMBER_CTX);
        store.findById(SERVICE_1.asEncodedId());
        requestedParticipantContext.clear();
        store.findById(SERVICE_1.asEncodedId());

        verify(serverConfProvider, times(2)).serviceExists(SERVICE_1);
    }

    @Test
    void findByIdCacheKeyCollapsesGarbageRequestedContextsWithNoContextRequested() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var store = buildStore(withCache);

        // The uncontexted call resolves via select()'s getFirst() fallback and caches the owning
        // member's asset under the "no context requested" key.
        requestedParticipantContext.clear();
        var uncontexted = store.findById(SERVICE_1.asEncodedId());
        assertThat(uncontexted).isNotNull();
        assertThat(uncontexted.getParticipantContextId()).isEqualTo(MEMBER_CTX);

        // Neither string decodes as a member ctx-id nor matches the SYSTEM ctx. Each must be treated
        // as its own, uncacheable lookup rather than sharing the uncontexted call's cache entry — so
        // neither ever returns the member's asset that entry holds.
        requestedParticipantContext.set("not-a-real-ctx");
        var garbage1 = store.findById(SERVICE_1.asEncodedId());
        requestedParticipantContext.set("also-not-a-real-ctx");
        var garbage2 = store.findById(SERVICE_1.asEncodedId());

        assertThat(garbage1).isNull();
        assertThat(garbage2).isNull();
        verify(serverConfProvider, times(3)).serviceExists(SERVICE_1);
    }

    @Test
    void findByIdUnmatchedContextNeverReadsBackTheUncontextedCallsCachedAsset() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var store = buildStore(withCache);

        // An uncontexted internal call legitimately resolves to, and caches, the owning member's
        // asset under the "no context requested" key.
        requestedParticipantContext.clear();
        var cachedForNoContext = store.findById(SERVICE_1.asEncodedId());
        assertThat(cachedForNoContext).isNotNull();
        assertThat(cachedForNoContext.getParticipantContextId()).isEqualTo(MEMBER_CTX);

        // A later request addressed with a distinct, unmatched context string must miss — never read
        // back the member's asset the first call cached — matching select()'s fail-closed contract.
        requestedParticipantContext.set("stale-or-garbage-ctx");
        var result = store.findById(SERVICE_1.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdNotFoundNotCached() {
        var unknownService = ServiceId.Conf.create("DEV", "GOV", "9999", "Unknown", "noSvc");
        when(serverConfProvider.serviceExists(unknownService)).thenReturn(false);
        var store = buildStore(withCache);

        var r1 = store.findById(unknownService.asEncodedId());
        var r2 = store.findById(unknownService.asEncodedId());

        assertThat(r1).isNull();
        assertThat(r2).isNull();
        verify(serverConfProvider, times(2)).serviceExists(unknownService);
    }

    @Test
    void participantContextIdScopingUnchangedWithCache() {
        setupSingleMemberService();
        var store = buildStore(withCache);

        var spec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", MEMBER_CTX))
                .build();
        var result = store.queryAssets(spec).toList();

        assertThat(result).allSatisfy(a ->
                assertThat(a.getParticipantContextId()).isEqualTo(MEMBER_CTX));
        assertThat(result).isNotEmpty();
    }

    @Test
    void enumerationCacheServesStaleCopyUntilTtlExpires() {
        var ticker = new MutableTicker();
        var ttlSeconds = 5L;
        var cache = new StoreEnumerationCache<Asset>(true, ttlSeconds, 1000, "test", ticker);
        var store = buildStore(cache);

        setupSingleMemberService();
        var firstCount = store.queryAssets(QuerySpec.max()).count();
        assertThat(firstCount).isGreaterThan(0);

        // Mutate serverconf to return no members — cache should hide this change
        when(serverConfProvider.getMembers()).thenReturn(List.of());
        ticker.advance(ttlSeconds - 1, TimeUnit.SECONDS);

        var midCount = store.queryAssets(QuerySpec.max()).count();
        assertThat(midCount).isEqualTo(firstCount);

        // Advance past TTL — next access must reload and reflect the mutation
        ticker.advance(2, TimeUnit.SECONDS);
        var afterCount = store.queryAssets(QuerySpec.max()).count();
        assertThat(afterCount).isEqualTo(0);
    }

    @Test
    void findByIdCacheServesStaleCopyUntilTtlExpires() {
        var ticker = new MutableTicker();
        var ttlSeconds = 5L;
        var cache = new StoreEnumerationCache<Asset>(true, ttlSeconds, 1000, "test", ticker);
        var store = buildStore(cache);

        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(participantContextService.getParticipantContext(MEMBER_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_CTX)));
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        var first = store.findById(SERVICE_1.asEncodedId());
        assertThat(first).isNotNull();

        // Second call within TTL still served from cache (loader not called again)
        ticker.advance(ttlSeconds - 1, TimeUnit.SECONDS);
        store.findById(SERVICE_1.asEncodedId());
        verify(serverConfProvider, times(1)).serviceExists(SERVICE_1);

        // Advance past TTL — next access must re-invoke loader
        ticker.advance(2, TimeUnit.SECONDS);
        store.findById(SERVICE_1.asEncodedId());
        assertThat(store.findById(SERVICE_1.asEncodedId())).isNotNull();
        verify(serverConfProvider, times(2)).serviceExists(SERVICE_1);
    }

    @Test
    void resolveForAssetHitServedFromCache() {
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn("https://example.com/svc");
        var store = buildStore(withCache);

        store.resolveForAsset(SERVICE_1.asEncodedId());
        store.resolveForAsset(SERVICE_1.asEncodedId());

        verify(serverConfProvider, times(1)).getServiceAddress(SERVICE_1);
    }

    @Test
    void resolveForAssetNullNotCached() {
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn(null);
        var store = buildStore(withCache);

        var r1 = store.resolveForAsset(SERVICE_1.asEncodedId());
        var r2 = store.resolveForAsset(SERVICE_1.asEncodedId());

        assertThat(r1).isNull();
        assertThat(r2).isNull();
        verify(serverConfProvider, times(2)).getServiceAddress(SERVICE_1);
    }

    @Test
    void resolveForAssetCacheDisabledReloadsEachCall() {
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn("https://example.com/svc");
        var store = buildStore(noCache);

        store.resolveForAsset(SERVICE_1.asEncodedId());
        store.resolveForAsset(SERVICE_1.asEncodedId());

        verify(serverConfProvider, times(2)).getServiceAddress(SERVICE_1);
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolveForAssetCacheServesStaleCopyUntilTtlExpires() {
        var ticker = new MutableTicker();
        var ttlSeconds = 5L;
        var cache = new StoreEnumerationCache<Asset>(true, ttlSeconds, 1000, "test", ticker);
        var store = buildStore(cache);

        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn("https://old.example.com/svc");

        var first = store.resolveForAsset(SERVICE_1.asEncodedId());
        assertThat(first).isNotNull();
        assertThat(first.getStringProperty("baseUrl")).isEqualTo("https://old.example.com/svc");

        // Address changes in serverconf — cache hides the change within TTL
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn("https://new.example.com/svc");
        ticker.advance(ttlSeconds - 1, TimeUnit.SECONDS);

        var mid = store.resolveForAsset(SERVICE_1.asEncodedId());
        assertThat(mid.getStringProperty("baseUrl")).isEqualTo("https://old.example.com/svc");

        // Advance past TTL — next access must reload and reflect the new address
        ticker.advance(2, TimeUnit.SECONDS);
        var after = store.resolveForAsset(SERVICE_1.asEncodedId());
        assertThat(after.getStringProperty("baseUrl")).isEqualTo("https://new.example.com/svc");
    }

    private static List<AccessRight> nonEmptyAcl() {
        var ar = new AccessRight();
        ar.setSubjectId(ClientId.Conf.create("DEV", "GOV", "9999", "Consumer"));
        ar.setEndpoint(new Endpoint("svc", "GET", "/", false));
        ar.setRightsGiven(new Date());
        return List.of(ar);
    }

    static final class MutableTicker extends Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(long amount, TimeUnit unit) {
            nanos.addAndGet(unit.toNanos(amount));
        }
    }
}
