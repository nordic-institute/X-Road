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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.google.common.base.Ticker;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
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

@ExtendWith(MockitoExtension.class)
class CachingStoreTest {

    private static final String PARTICIPANT_CONTEXT_ID = "xroad-provider";
    private static final String MGMT_PARTICIPANT_CONTEXT_ID = "xroad-provider-mgmt";
    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "getRecords", "v1");
    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create("DEV", "GOV", "1111", "ss0");

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    private StoreEnumerationCache<Asset> noCache;
    private StoreEnumerationCache<Asset> withCache;
    private final DspParticipantContextHolder dspParticipantContextHolder = new DspParticipantContextHolder();

    @BeforeEach
    void setUp() {
        noCache = new StoreEnumerationCache<>(false, 60, 1000, "test");
        withCache = new StoreEnumerationCache<>(true, 3600, 1000, "test");
        lenient().when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of()));
    }

    private AssetIndexServerConfStore buildStore(StoreEnumerationCache<Asset> cache) {
        return new AssetIndexServerConfStore(serverConfProvider, globalConfProvider,
                PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                new BuiltinServiceCatalog(serverConfProvider, false, false, false,
                        BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL), cache,
                new ServiceContextResolver(participantContextService, globalConfProvider,
                        PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID),
                dspParticipantContextHolder);
    }

    private void setupSingleMemberService() {
        lenient().when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        lenient().when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        lenient().when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(nonEmptyAcl());
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
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
        lenient().when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var store = buildStore(withCache);

        store.findById(SERVICE_1.asEncodedId());
        store.findById(SERVICE_1.asEncodedId());

        verify(serverConfProvider, times(1)).serviceExists(SERVICE_1);
    }

    /**
     * Regression test: a by-id lookup's result depends on which participant context the caller
     * addressed, so the cache key must fold in that context — otherwise the first context to ask
     * for a given id would poison the cache for every other legitimate context until TTL expiry,
     * silently reintroducing the negotiation-ownership-validation 404 fixed by making findById
     * context-aware in the first place.
     */
    @Test
    void findByIdCacheKeyIncludesRequestedContextSoDifferentContextsDoNotShareAStaleRow() {
        var memberOnly = ClientId.Conf.create("DEV", "GOV", "1111");
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(memberOnly);
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(
                List.of(ParticipantContext.Builder.newInstance().participantContextId(memberCtxId).identity(memberCtxId).build())));
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        lenient().when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var store = buildStore(withCache);

        dspParticipantContextHolder.set(PARTICIPANT_CONTEXT_ID);
        var hostResult = store.findById(SERVICE_1.asEncodedId());

        dspParticipantContextHolder.set(memberCtxId);
        var memberResult = store.findById(SERVICE_1.asEncodedId());

        // Re-request the host context after the member lookup was cached — must still be host-tagged
        dspParticipantContextHolder.set(PARTICIPANT_CONTEXT_ID);
        var hostResultAgain = store.findById(SERVICE_1.asEncodedId());

        assertThat(hostResult.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID);
        assertThat(memberResult.getParticipantContextId()).isEqualTo(memberCtxId);
        assertThat(hostResultAgain.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdNotFoundNotCached() {
        var unknownService = ServiceId.Conf.create("DEV", "GOV", "9999", "Unknown", "noSvc");
        when(serverConfProvider.serviceExists(unknownService)).thenReturn(false);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        var unknownClient = unknownService.getClientId();
        when(globalConfProvider.isSecurityServerClient(unknownClient, SS_ID)).thenReturn(false);
        var store = buildStore(withCache);

        var r1 = store.findById(unknownService.asEncodedId());
        var r2 = store.findById(unknownService.asEncodedId());

        assertThat(r1).isNull();
        assertThat(r2).isNull();
        verify(serverConfProvider, times(2)).serviceExists(unknownService);
    }

    @Test
    void findByIdSynthesisPreservedAfterCaching() {
        var localService = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "localOp");
        when(serverConfProvider.serviceExists(localService)).thenReturn(false);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MEMBER_1, SS_ID)).thenReturn(true);
        var store = buildStore(withCache);

        var r1 = store.findById(localService.asEncodedId());
        var r2 = store.findById(localService.asEncodedId());

        assertThat(r1).isNotNull();
        assertThat(r1.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID);
        assertThat(r2).isNotNull();
        assertThat(r2.getId()).isEqualTo(r1.getId());
        verify(serverConfProvider, times(1)).serviceExists(localService);
    }

    @Test
    void participantContextIdScopingUnchangedWithCache() {
        setupSingleMemberService();
        var store = buildStore(withCache);

        var spec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CONTEXT_ID))
                .build();
        var result = store.queryAssets(spec).toList();

        assertThat(result).allSatisfy(a ->
                assertThat(a.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID));
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
        lenient().when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
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
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn("https://example.com/svc");
        var store = buildStore(withCache);

        store.resolveForAsset(SERVICE_1.asEncodedId());
        store.resolveForAsset(SERVICE_1.asEncodedId());

        verify(serverConfProvider, times(1)).getServiceAddress(SERVICE_1);
    }

    @Test
    void resolveForAssetNullNotCached() {
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn("Maintenance");
        var store = buildStore(withCache);

        var r1 = store.resolveForAsset(SERVICE_1.asEncodedId());
        var r2 = store.resolveForAsset(SERVICE_1.asEncodedId());

        assertThat(r1).isNull();
        assertThat(r2).isNull();
        verify(serverConfProvider, times(2)).getDisabledNotice(SERVICE_1);
    }

    @Test
    void resolveForAssetCacheDisabledReloadsEachCall() {
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
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

        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
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
