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

import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetIndexServerConfStoreTest {

    private static final String PARTICIPANT_CONTEXT_ID = "xroad-provider";
    private static final String MGMT_PARTICIPANT_CONTEXT_ID = "xroad-provider-mgmt";

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private AssetIndexServerConfStore assetIndex;

    // Test data: 2 members, member1 has 2 services (1 disabled), member2 has 1 service
    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
    private static final ClientId.Conf MEMBER_2 = ClientId.Conf.create("DEV", "COM", "2222", "SubsystemB");
    // MANAGEMENT subsystem client — matches globalConfProvider.getManagementRequestService()
    private static final ClientId.Conf MGMT_CLIENT = ClientId.Conf.create("DEV", "COM", "3333", "MANAGEMENT");

    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "getRecords", "v1");
    private static final ServiceId.Conf SERVICE_2 = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "submitForm");
    private static final ServiceId.Conf SERVICE_3 = ServiceId.Conf.create("DEV", "COM", "2222", "SubsystemB", "lookupData", "v2");
    private static final ServiceId.Conf MGMT_SERVICE = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg");

    private static final String SERVICE_1_ADDRESS = "https://ss1.example.com/r1/DEV/GOV/1111/SubsystemA/getRecords/v1";
    private static final String SERVICE_3_ADDRESS = "https://ss2.example.com/r1/DEV/COM/2222/SubsystemB/lookupData/v2";
    private static final String MGMT_SERVICE_ADDRESS = "https://ss0.example.com/r1/DEV/COM/3333/MANAGEMENT/clientReg";

    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create("DEV", "GOV", "1111", "ss0");

    @BeforeEach
    void setUp() {
        assetIndex = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                noBuiltins());
    }

    private BuiltinServiceCatalog allBuiltins() {
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        return new BuiltinServiceCatalog(serverConfProvider, true, true, true,
                BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
    }

    private BuiltinServiceCatalog noBuiltins() {
        return new BuiltinServiceCatalog(serverConfProvider, false, false, false,
                BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
    }

    @Test
    void queryAssetsReturnsEnabledServicesAcrossMultipleMembers() {
        setupMembersAndServices();

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).hasSize(5);
        assertThat(result).extracting(Asset::getId)
                .contains(SERVICE_1.asEncodedId(), SERVICE_2.asEncodedId(), SERVICE_3.asEncodedId());
    }

    @Test
    void queryAssetsExcludesDisabledService() {
        setupMembersAndServices();

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var disabledAssets = result.stream()
                .filter(a -> a.getId().equals(SERVICE_2.asEncodedId()))
                .toList();
        assertThat(disabledAssets).hasSize(1);
        assertThat(disabledAssets.getFirst().getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID);
        assertThat(result).noneSatisfy(asset -> {
            assertThat(asset.getId()).isEqualTo(SERVICE_2.asEncodedId());
            assertThat(asset.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryAssetsAssetPropertyShapeMatchesDcatCore() {
        setupMembersAndServices();

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();
        var asset = result.stream()
                .filter(a -> a.getId().equals(SERVICE_1.asEncodedId())
                        && PARTICIPANT_CONTEXT_ID.equals(a.getParticipantContextId()))
                .findFirst()
                .orElseThrow();

        assertThat(asset.getId()).isEqualTo(SERVICE_1.asEncodedId());
        assertThat(asset.getParticipantContextId()).isEqualTo("xroad-provider");
        assertThat(asset.getProperty(EDC_NAMESPACE + "name")).isEqualTo("getRecords");
        assertThat(asset.getProperty("http://purl.org/dc/terms/title")).isEqualTo("getRecords:v1");
        assertThat((String) asset.getProperty("http://purl.org/dc/terms/description"))
                .startsWith("X-Road service getRecords:v1 provided by 1111/SubsystemA");
        assertThat((List<String>) asset.getProperty("http://www.w3.org/ns/dcat#keyword"))
                .contains("GOV", "SubsystemA");
    }

    @Test
    void queryAssetsEveryAssetHasParticipantContextId() {
        setupMembersAndServices();

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).allSatisfy(asset ->
                assertThat(asset.getParticipantContextId())
                        .isIn(PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID));
    }

    @Test
    void findByIdRoundtripsKnownServiceId() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);

        var result = assetIndex.findById(SERVICE_1.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(SERVICE_1.asEncodedId());
        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdReturnsNullForUnknownId() {
        var unknownService = ServiceId.Conf.create("DEV", "GOV", "9999", "Unknown", "noSuchService");
        when(serverConfProvider.serviceExists(unknownService)).thenReturn(false);

        var result = assetIndex.findById(unknownService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForMalformedId() {
        var result = assetIndex.findById("garbage");

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolveForAssetReturnsHttpDataAddress() {
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn(SERVICE_1_ADDRESS);

        var result = assetIndex.resolveForAsset(SERVICE_1.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("HttpData");
        assertThat(result.getStringProperty("baseUrl")).isEqualTo(SERVICE_1_ADDRESS);
        assertThat(result.getStringProperty("proxyPath")).isEqualTo("true");
        assertThat(result.getStringProperty("proxyMethod")).isEqualTo("true");
        assertThat(result.getStringProperty("proxyBody")).isEqualTo("true");
        assertThat(result.getStringProperty("proxyQueryParams")).isEqualTo("true");
    }

    @Test
    void resolveForAssetReturnsNullForDisabledService() {
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn("Maintenance");

        var result = assetIndex.resolveForAsset(SERVICE_1.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void createReturnsAlreadyExists() {
        var asset = Asset.Builder.newInstance().id("test").build();

        var result = assetIndex.create(asset);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Read-only");
    }

    @Test
    void deleteByIdReturnsNotFound() {
        var result = assetIndex.deleteById("test-id");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Read-only");
    }

    @Test
    void updateAssetReturnsNotFound() {
        var asset = Asset.Builder.newInstance().id("test").build();

        var result = assetIndex.updateAsset(asset);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Read-only");
    }

    private void setupMembersAndServices() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MEMBER_2));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1, SERVICE_2));
        when(serverConfProvider.getAllServices(MEMBER_2)).thenReturn(List.of(SERVICE_3));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(SERVICE_2)).thenReturn("Maintenance");
        when(serverConfProvider.getDisabledNotice(SERVICE_3)).thenReturn(null);
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(nonEmptyAcl());
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_3)).thenReturn(nonEmptyAcl());
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
    }

    private static List<AccessRight> nonEmptyAcl() {
        var ar = new AccessRight();
        ar.setSubjectId(ClientId.Conf.create("DEV", "GOV", "9999", "Consumer"));
        ar.setEndpoint(new Endpoint("svc", "GET", "/", false));
        ar.setRightsGiven(new Date());
        return List.of(ar);
    }

    @Test
    void queryAssetsMgmtServiceTaggedWithMgmtCtx() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var regularHost = result.stream()
                .filter(a -> a.getId().equals(SERVICE_1.asEncodedId())
                        && PARTICIPANT_CONTEXT_ID.equals(a.getParticipantContextId()))
                .findFirst().orElseThrow();
        var mgmt = result.stream()
                .filter(a -> a.getId().equals(MGMT_SERVICE.asEncodedId()))
                .findFirst().orElseThrow();
        assertThat(regularHost.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID);
        assertThat(mgmt.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void queryAssetsWithMgmtCtxFilterReturnsMgmtServicesOnly() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var mgmtSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", MGMT_PARTICIPANT_CONTEXT_ID))
                .build();
        var mgmtResult = assetIndex.queryAssets(mgmtSpec).toList();
        assertThat(mgmtResult).extracting(Asset::getId)
                .containsExactlyInAnyOrder(SERVICE_1.asEncodedId(), MGMT_SERVICE.asEncodedId(), MGMT_SERVICE.asEncodedId());

        var hostSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CONTEXT_ID))
                .build();
        var hostResult = assetIndex.queryAssets(hostSpec).toList();
        assertThat(hostResult).hasSize(1);
        assertThat(hostResult.getFirst().getId()).isEqualTo(SERVICE_1.asEncodedId());
    }

    @Test
    void findByIdMgmtServiceTaggedWithMgmtCtx() {
        when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = assetIndex.findById(MGMT_SERVICE.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void queryAssetsIncludesBuiltinsWhenNoServerconfServices() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = store.queryAssets(QuerySpec.max()).toList();

        assertThat(result).hasSize(7);
        assertThat(result).extracting(Asset::getId)
                .allMatch(id -> id.startsWith("DEV:GOV:1111:"));
    }

    @Test
    void queryAssetsBuiltinsTaggedWithMgmtContext() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = store.queryAssets(QuerySpec.max()).toList();

        assertThat(result).allSatisfy(asset ->
                assertThat(asset.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID));
    }

    @Test
    void queryAssetsBuiltinsNeverTaggedWithHostContext() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = store.queryAssets(QuerySpec.max()).toList();

        assertThat(result).noneSatisfy(asset ->
                assertThat(asset.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID));
    }

    @Test
    void queryAssetsHostCtxFilterExcludesBuiltins() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var hostSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CONTEXT_ID))
                .build();

        var result = store.queryAssets(hostSpec).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdReturnsBuiltinAsset() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCatalog.PROXY_MONITOR_SERVICE_CODE;

        var result = store.findById(builtinAssetId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(builtinAssetId);
        assertThat(result.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdReturnsNullForUnknownBuiltinServiceCode() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());

        var result = store.findById("DEV:GOV:1111:nonExistentService");

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolveForAssetReturnsDataAddressForBuiltin() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCatalog.PROXY_MONITOR_SERVICE_CODE;

        var result = store.resolveForAsset(builtinAssetId);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("HttpData");
        assertThat(result.getStringProperty("baseUrl")).isEqualTo(BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
        assertThat(result.getStringProperty("proxyPath")).isEqualTo("true");
        assertThat(result.getStringProperty("proxyMethod")).isEqualTo("true");
        assertThat(result.getStringProperty("proxyBody")).isEqualTo("true");
        assertThat(result.getStringProperty("proxyQueryParams")).isEqualTo("true");
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolveForAssetReturnsNullForDisabledBuiltin() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                noBuiltins());
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCatalog.PROXY_MONITOR_SERVICE_CODE;

        var result = store.resolveForAsset(builtinAssetId);

        assertThat(result).isNull();
    }

    @Test
    void queryAssetsOwnerOnlyServiceEmittedUnderBothHostAndMgmtCtx() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Asset::getId)
                .containsExactly(SERVICE_1.asEncodedId(), SERVICE_1.asEncodedId());
        assertThat(result).extracting(Asset::getParticipantContextId)
                .containsExactlyInAnyOrder(PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdOwnerOnlyAssetReturnedUnderMgmtCtx() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn("Maintenance");

        var result = assetIndex.findById(SERVICE_1.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void countAssetsIncludesBuiltins() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, PARTICIPANT_CONTEXT_ID, MGMT_PARTICIPANT_CONTEXT_ID,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var count = store.countAssets(List.of());

        assertThat(count).isEqualTo(7);
    }
}
