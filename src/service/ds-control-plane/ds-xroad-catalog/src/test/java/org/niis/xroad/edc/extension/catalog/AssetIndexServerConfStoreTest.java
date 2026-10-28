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
import org.eclipse.edc.controlplane.DataAddress;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.BuiltinServiceCodes;
import org.niis.xroad.common.core.ManagementServiceCodes;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.extension.catalog.ParticipantContextTestFixtures.participantContext;

@ExtendWith(MockitoExtension.class)
class AssetIndexServerConfStoreTest {

    private static final String SYSTEM_PARTICIPANT_CONTEXT_ID = ParticipantIdentifierScheme.SYSTEM_SEGMENT;
    private static final StoreEnumerationCache<Asset> DISABLED_CACHE =
            new StoreEnumerationCache<>(false, 60, 1000, "test");

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    private ServiceContextResolver serviceContextResolver;
    private String participantContextId = MEMBER_1_CTX;

    private AssetIndexServerConfStore assetIndex;

    // Test data: 2 members, member1 has 2 services, member2 has 1 service
    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
    private static final ClientId.Conf MEMBER_2 = ClientId.Conf.create("DEV", "COM", "2222", "SubsystemB");
    private static final String MEMBER_1_CTX = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "1111"));
    private static final String MEMBER_2_CTX = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "COM", "2222"));
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
        lenient().when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of()));
        lenient().when(participantContextService.getParticipantContext(any())).thenReturn(ServiceResult.notFound("no such context"));
        serviceContextResolver = new ServiceContextResolver(
                globalConfProvider, serverConfProvider, participantContextService);
        assetIndex = new AssetIndexServerConfStore(
                serverConfProvider, noBuiltins(), DISABLED_CACHE, serviceContextResolver);
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
    void queryAssetsReturnsServicesAcrossMultipleMembers() {
        setupMembersAndServices();

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(Asset::getId)
                .contains(SERVICE_1.asEncodedId(), SERVICE_2.asEncodedId(), SERVICE_3.asEncodedId());
    }

    @Test
    void queryAssetsFilteredByIdFindsAssetBeyondDefaultLimit() {
        var services = IntStream.range(0, 60)
                .mapToObj(i -> ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "service" + i))
                .toList();
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(services);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of(participantContext(MEMBER_1_CTX))));
        var target = services.getLast().asEncodedId();
        var defaultLimit = QuerySpec.none().getLimit();

        assertThat(assetIndex.queryAssets(QuerySpec.none()).map(Asset::getId)).hasSize(defaultLimit).doesNotContain(target);

        var filtered = QuerySpec.Builder.newInstance().filter(Criterion.criterion("id", "=", target)).build();
        var result = assetIndex.queryAssets(filtered).toList();

        assertThat(result).extracting(Asset::getId).containsExactly(target);
    }

    @Test
    void queryAssetsPublishesServiceUnderMemberContextOnlyOnceProvisioned() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var memberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "1111"));
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of(participantContext(memberCtx))));

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(SERVICE_1.asEncodedId());
        assertThat(result.getFirst().getParticipantContextId()).isEqualTo(memberCtx);
    }

    @Test
    void findByIdResolvesServiceUnderMemberContextOnlyOnceProvisioned() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        var memberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "1111"));
        when(participantContextService.getParticipantContext(memberCtx))
                .thenReturn(ServiceResult.success(participantContext(memberCtx)));

        var result = assetIndex.findById(participantContextId, SERVICE_1.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(memberCtx);
    }

    @Test
    void queryAssetsPublishesDisabledServiceUnderMemberContext() {
        setupMembersAndServices();
        lenient().when(serverConfProvider.getDisabledNotice(SERVICE_2)).thenReturn("Maintenance");

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var disabledAssets = result.stream()
                .filter(a -> a.getId().equals(SERVICE_2.asEncodedId()))
                .toList();
        assertThat(disabledAssets).hasSize(1);
        assertThat(disabledAssets.getFirst().getParticipantContextId()).isEqualTo(MEMBER_1_CTX);
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryAssetsAssetPropertyShapeMatchesDcatCore() {
        setupMembersAndServices();

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();
        var asset = result.stream()
                .filter(a -> a.getId().equals(SERVICE_1.asEncodedId())
                        && MEMBER_1_CTX.equals(a.getParticipantContextId()))
                .findFirst()
                .orElseThrow();

        assertThat(asset.getId()).isEqualTo(SERVICE_1.asEncodedId());
        assertThat(asset.getParticipantContextId()).isEqualTo(MEMBER_1_CTX);
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

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(asset ->
                assertThat(asset.getParticipantContextId()).isIn(MEMBER_1_CTX, MEMBER_2_CTX));
    }

    @Test
    void findByIdReturnsNullForKnownServiceWithNoProvisionedContext() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);

        var result = assetIndex.findById(participantContextId, SERVICE_1.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForUnknownId() {
        var unknownService = ServiceId.Conf.create("DEV", "GOV", "9999", "Unknown", "noSuchService");
        when(serverConfProvider.serviceExists(unknownService)).thenReturn(false);

        var result = assetIndex.findById(participantContextId, unknownService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForMalformedId() {
        var result = assetIndex.findById(participantContextId, "garbage");

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForLocallyRegisteredSubsystemWithoutServiceDescription() {
        var clientDisableService = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientDisable");
        when(serverConfProvider.serviceExists(clientDisableService)).thenReturn(false);

        var result = assetIndex.findById(participantContextId, clientDisableService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForUnregisteredSubsystemWithoutServiceDescription() {
        var foreignSubsystem = ClientId.Conf.create("DEV", "COM", "9999", "Foreign");
        var foreignService = ServiceId.Conf.create(foreignSubsystem, "noSuchService");
        when(serverConfProvider.serviceExists(foreignService)).thenReturn(false);

        var result = assetIndex.findById(participantContextId, foreignService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void queryAssetsDoesNotEnumerateSyntheticEntriesForSubsystemWithoutManagementContext() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void queryAssetsEmitsSyntheticManagementCatalogWhenSubsystemRegisteredWithoutServiceDescriptions() {
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of());
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var systemCtx = result.stream().filter(a -> SYSTEM_PARTICIPANT_CONTEXT_ID.equals(a.getParticipantContextId())).toList();
        assertThat(systemCtx).hasSize(ManagementServiceCodes.DSP_NEGOTIATED.size());
        assertThat(result).allSatisfy(asset -> assertThat(asset.getId()).startsWith(MGMT_CLIENT.asEncodedId()));
        assertThat(systemCtx).noneSatisfy(asset ->
                assertThat(asset.getId()).endsWith("authCertReg"));
    }

    @Test
    void queryAssetsPublishesRealManagementServiceUnderSystemContext() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var systemEntry = result.stream()
                .filter(a -> a.getId().equals(MGMT_SERVICE.asEncodedId())
                        && SYSTEM_PARTICIPANT_CONTEXT_ID.equals(a.getParticipantContextId()))
                .toList();
        assertThat(systemEntry).hasSize(1);
    }

    @Test
    void queryAssetsExcludesDisabledRealManagementServiceFromSystemContext() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn("Maintenance");
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).noneSatisfy(asset -> {
            assertThat(asset.getId()).isEqualTo(MGMT_SERVICE.asEncodedId());
            assertThat(asset.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
        });
    }

    @Test
    void queryAssetsIncludesDisabledRealManagementServiceWithConfiguredAccessRightsUnderSystemContext() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn("Maintenance");
        when(serverConfProvider.getServiceAccessRights(MGMT_SERVICE)).thenReturn(nonEmptyAcl());
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var systemEntry = result.stream()
                .filter(a -> a.getId().equals(MGMT_SERVICE.asEncodedId())
                        && SYSTEM_PARTICIPANT_CONTEXT_ID.equals(a.getParticipantContextId()))
                .toList();
        assertThat(systemEntry).hasSize(1);
    }

    @Test
    void queryAssetsExcludesRealAuthCertRegFromSystemContext() {
        var authCertRegService = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "authCertReg");
        when(serverConfProvider.getMembers()).thenReturn(List.of(MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE, authCertRegService));
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var systemCtx = result.stream()
                .filter(a -> SYSTEM_PARTICIPANT_CONTEXT_ID.equals(a.getParticipantContextId()))
                .toList();
        assertThat(systemCtx).extracting(Asset::getId).containsExactly(MGMT_SERVICE.asEncodedId());
    }

    @Test
    void findByIdRealManagementServiceResolvesSystemContextWhenSystemRequested() {
        when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, MGMT_SERVICE.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdDisabledRealManagementServiceNotFoundUnderSystemContext() {
        when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn("Maintenance");
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, MGMT_SERVICE.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdDisabledRealManagementServiceWithConfiguredAccessRightsFoundUnderSystemContext() {
        when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(true);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn("Maintenance");
        when(serverConfProvider.getServiceAccessRights(MGMT_SERVICE)).thenReturn(nonEmptyAcl());
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, MGMT_SERVICE.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolveForAssetReturnsHttpDataTypeAddress() {
        when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn(SERVICE_1_ADDRESS);

        var result = assetIndex.resolveForAsset(participantContextId, SERVICE_1.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getClass()).isEqualTo(DataAddress.class);
        assertThat(result.getType()).isEqualTo("HttpData");
        assertThat(result.getProperties()).containsOnly(
                entry(EDC_NAMESPACE + "type", "HttpData"),
                entry(EDC_NAMESPACE + "baseUrl", SERVICE_1_ADDRESS),
                entry("proxyPath", "true"),
                entry("proxyMethod", "true"),
                entry("proxyBody", "true"),
                entry("proxyQueryParams", "true"));
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
        var result = assetIndex.deleteById(participantContextId, "test-id");

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
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(nonEmptyAcl());
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_3)).thenReturn(nonEmptyAcl());
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of(
                participantContext(MEMBER_1_CTX), participantContext(MEMBER_2_CTX))));
    }

    private static List<AccessRight> nonEmptyAcl() {
        var ar = new AccessRight();
        ar.setSubjectId(ClientId.Conf.create("DEV", "GOV", "9999", "Consumer"));
        ar.setEndpoint(new Endpoint("svc", "GET", "/", false));
        ar.setRightsGiven(new Date());
        return List.of(ar);
    }

    @Test
    void queryAssetsManagementOwnServiceNotPublishedWhenMemberContextNotProvisioned() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).noneMatch(a -> a.getId().equals(SERVICE_1.asEncodedId()));
        assertThat(result).noneMatch(a -> a.getId().equals(MGMT_SERVICE.asEncodedId()));
    }

    @Test
    void queryAssetsManagementOwnServicePublishedUnderMemberContextLikeAnyOtherServiceOnceProvisioned() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        var mgmtMemberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "COM", "3333"));
        when(participantContextService.search(any()))
                .thenReturn(ServiceResult.success(List.of(participantContext(mgmtMemberCtx))));

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        var mgmt = result.stream().filter(a -> a.getId().equals(MGMT_SERVICE.asEncodedId())).findFirst().orElseThrow();
        assertThat(mgmt.getParticipantContextId()).isEqualTo(mgmtMemberCtx);
    }

    @Test
    void findByIdManagementOwnServiceNotFoundWhenMemberContextNotProvisioned() {
        when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(true);

        var result = assetIndex.findById(participantContextId, MGMT_SERVICE.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullWhenRequestedContextIsNotAmongResolvedContexts() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        var memberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "1111"));
        when(participantContextService.getParticipantContext(memberCtx))
                .thenReturn(ServiceResult.success(participantContext(memberCtx)));
        participantContextId = "some-other-context";

        var result = assetIndex.findById(participantContextId, SERVICE_1.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void queryAssetsIncludesBuiltinsWhenNoServerconfServices() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = store.queryAssets(QuerySpec.max()).toList();

        assertThat(result).hasSize(7);
        assertThat(result).extracting(Asset::getId)
                .allMatch(id -> id.startsWith("DEV:GOV:1111:"));
    }

    @Test
    void queryAssetsBuiltinsTaggedWithSystemContextOnly() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = store.queryAssets(QuerySpec.max()).toList();

        assertThat(result).allSatisfy(asset ->
                assertThat(asset.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID));
        assertThat(result).hasSize(7);
    }

    @Test
    void findByIdReturnsBuiltinAsset() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCodes.GET_SECURITY_SERVER_METRICS;

        var result = store.findById(participantContextId, builtinAssetId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(builtinAssetId);
        assertThat(result.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdReturnsNullForUnknownBuiltinServiceCode() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);

        var result = store.findById(participantContextId, "DEV:GOV:1111:nonExistentService");

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void resolveForAssetReturnsDataAddressForBuiltin() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCodes.GET_SECURITY_SERVER_METRICS;

        var result = store.resolveForAsset(participantContextId, builtinAssetId);

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
                serverConfProvider, noBuiltins(), DISABLED_CACHE, serviceContextResolver);
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCodes.GET_SECURITY_SERVER_METRICS;

        var result = store.resolveForAsset(participantContextId, builtinAssetId);

        assertThat(result).isNull();
    }

    @Test
    void queryAssetsOwnerOnlyServiceNotPublishedWhenMemberContextNotProvisioned() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);

        var result = assetIndex.queryAssets(QuerySpec.max()).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void countAssetsIncludesBuiltins() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var count = store.countAssets(List.of());

        assertThat(count).isEqualTo(7);
    }

    @Test
    void countAssetsAppliesTheDefinitionSelectorAndTheTargetIdTogether() {
        setupMembersAndServices();
        var selectorForService1 = new Criterion(EDC_NAMESPACE + "id", "=", SERVICE_1.asEncodedId());

        var sameAsset = assetIndex.countAssets(List.of(selectorForService1,
                new Criterion(Asset.PROPERTY_ID, "=", SERVICE_1.asEncodedId())));
        var otherAsset = assetIndex.countAssets(List.of(selectorForService1,
                new Criterion(Asset.PROPERTY_ID, "=", SERVICE_2.asEncodedId())));

        assertThat(sameAsset).isEqualTo(1);
        assertThat(otherAsset).isZero();
    }

    @Test
    void findByIdBuiltinResolvesSystemContextWhenSystemRequested() {
        var store = new AssetIndexServerConfStore(
                serverConfProvider, allBuiltins(), DISABLED_CACHE, serviceContextResolver);
        var builtinAssetId = "DEV:GOV:1111:" + BuiltinServiceCodes.GET_SECURITY_SERVER_METRICS;
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = store.findById(participantContextId, builtinAssetId);

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdMgmtServiceResolvesSystemContextWhenSystemRequestedAndEligible() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, MGMT_SERVICE.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdMgmtServiceResolvesSystemContextWhenNoRealServiceConfigured() {
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(false);
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of());
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, MGMT_SERVICE.asEncodedId());

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
    }

    @Test
    void findByIdUnconfiguredEligibleCodeNotFoundUnderSystemWhenSiblingCodeIsReal() {
        // Partial migration: clientReg is real, so the whole subsystem no longer qualifies for the
        // no-real-services synthetic fallback, but maintenanceModeEnable was never configured —
        // buildAssetList never enumerates it either, so findById must agree.
        var unconfiguredService = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "maintenanceModeEnable");
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.serviceExists(unconfiguredService)).thenReturn(false);
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, unconfiguredService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdAuthCertRegNotFoundUnderSystemContext() {
        // authCertReg fails the cheap SYSTEM_SERVICE_CODES check before any globalconf/serverconf
        // lookup runs, so no management-subsystem resolution needs to be stubbed here.
        var authCertRegService = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "authCertReg");
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, authCertRegService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdRealServiceNotFoundUnderSystemContext() {
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, SERVICE_1.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdArbitraryLocallyRegisteredSubsystemAssetNotFoundUnderSystemContext() {
        var customService = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "customThing");
        participantContextId = SYSTEM_PARTICIPANT_CONTEXT_ID;

        var result = assetIndex.findById(participantContextId, customService.asEncodedId());

        assertThat(result).isNull();
    }

    @Test
    void findByIdAndResolveForAssetResolveNothingForNullParticipantContext() {
        lenient().when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        lenient().when(serverConfProvider.getServiceAddress(SERVICE_1)).thenReturn(SERVICE_1_ADDRESS);
        lenient().when(participantContextService.getParticipantContext(MEMBER_1_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_1_CTX)));

        assertThat(assetIndex.findById(null, SERVICE_1.asEncodedId())).isNull();
        assertThat(assetIndex.resolveForAsset(null, SERVICE_1.asEncodedId())).isNull();
    }

    @Test
    void findByIdResolvesTheSameAssetIdToEachContextsOwnAsset() {
        var mgmtMemberCtx = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "COM", "3333"));
        lenient().when(serverConfProvider.serviceExists(MGMT_SERVICE)).thenReturn(true);
        lenient().when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);
        lenient().when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        lenient().when(globalConfProvider.isSecurityServerClient(MGMT_CLIENT, SS_ID)).thenReturn(true);
        lenient().when(participantContextService.getParticipantContext(mgmtMemberCtx))
                .thenReturn(ServiceResult.success(participantContext(mgmtMemberCtx)));
        var cachingAssetIndex = new AssetIndexServerConfStore(
                serverConfProvider, noBuiltins(), new StoreEnumerationCache<>(true, 60, 1000, "test"), serviceContextResolver);
        var assetId = MGMT_SERVICE.asEncodedId();

        var memberAsset = cachingAssetIndex.findById(mgmtMemberCtx, assetId);
        var systemAsset = cachingAssetIndex.findById(SYSTEM_PARTICIPANT_CONTEXT_ID, assetId);
        var otherMemberAsset = cachingAssetIndex.findById(MEMBER_2_CTX, assetId);

        assertThat(memberAsset).isNotNull();
        assertThat(memberAsset.getParticipantContextId()).isEqualTo(mgmtMemberCtx);
        assertThat(systemAsset).isNotNull();
        assertThat(systemAsset.getParticipantContextId()).isEqualTo(SYSTEM_PARTICIPANT_CONTEXT_ID);
        assertThat(otherMemberAsset).isNull();
    }

    @Test
    void deleteByIdLeavesTheAssetResolvableInItsContext() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(participantContextService.getParticipantContext(MEMBER_1_CTX))
                .thenReturn(ServiceResult.success(participantContext(MEMBER_1_CTX)));

        var deleteResult = assetIndex.deleteById(MEMBER_1_CTX, SERVICE_1.asEncodedId());

        assertThat(deleteResult.failed()).isTrue();
        assertThat(assetIndex.findById(MEMBER_1_CTX, SERVICE_1.asEncodedId())).isNotNull();
    }
}
