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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.policy.model.Policy;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyDefinitionServerConfStoreTest {

    private static final String PARTICIPANT_CTX = "xroad-provider";
    private static final String MGMT_PARTICIPANT_CTX = "xroad-provider-mgmt";

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private PolicyDefinitionServerConfStore store;

    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1234", "SubSys");
    private static final ClientId.Conf MEMBER_2 = ClientId.Conf.create("DEV", "GOV", "5678", "SubSys");
    private static final ClientId.Conf MGMT_CLIENT = ClientId.Conf.create("DEV", "COM", "3333", "MANAGEMENT");

    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1234", "SubSys", "svc1", "v1");
    private static final ServiceId.Conf SERVICE_2 = ServiceId.Conf.create("DEV", "GOV", "5678", "SubSys", "svc2");
    private static final ServiceId.Conf MGMT_SERVICE = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg");

    private static final ClientId.Conf SUBJECT_CLIENT = ClientId.Conf.create("DEV", "GOV", "9999", "Consumer");
    private static final GlobalGroupId SUBJECT_GROUP = GlobalGroupId.Conf.create("DEV", "sec-owners");

    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create("DEV", "GOV", "1234", "ss0");

    @BeforeEach
    void setUp() {
        store = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
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
    void findByIdReturnsValidPolicyDefinition() {
        var ep = new Endpoint("svc1", "GET", "/api/data", false);
        var accessRight = createAccessRight(SUBJECT_CLIENT, ep);

        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(accessRight));

        var policyId = AssetMapper.encodeAssetId(SERVICE_1)
                + XRoadId.ENCODED_ID_SEPARATOR + SUBJECT_CLIENT.asEncodedId();

        var result = store.findById(policyId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(policyId);
        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
        assertThat(result.getPolicy().getPermissions()).hasSize(1);
    }

    @Test
    void findByIdReturnsNullForUnknownService() {
        var unknownService = ServiceId.Conf.create("DEV", "GOV", "0000", "None", "noSvc", "v1");
        when(serverConfProvider.serviceExists(unknownService)).thenReturn(false);

        var policyId = AssetMapper.encodeAssetId(unknownService)
                + XRoadId.ENCODED_ID_SEPARATOR + SUBJECT_CLIENT.asEncodedId();

        var result = store.findById(policyId);

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForMalformedId() {
        var result = store.findById("garbage");

        assertThat(result).isNull();
    }

    @Test
    void findByIdReturnsNullForNullId() {
        var result = store.findById(null);

        assertThat(result).isNull();
    }

    @Test
    void findAllReturnsAllPoliciesAcrossMembersAndServices() {
        var ep1 = new Endpoint("svc1", "GET", "/api/data", false);
        var ep2 = new Endpoint("svc2", "*", "**", true);

        var ar1Svc1 = createAccessRight(SUBJECT_CLIENT, ep1);
        var ar2Svc1 = createAccessRight(SUBJECT_GROUP, ep1);
        var ar1Svc2 = createAccessRight(SUBJECT_CLIENT, ep2);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MEMBER_2));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MEMBER_2)).thenReturn(List.of(SERVICE_2));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(SERVICE_2)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(ar1Svc1, ar2Svc1));
        when(serverConfProvider.getServiceAccessRights(SERVICE_2)).thenReturn(List.of(ar1Svc2));

        var result = store.findAll(QuerySpec.none()).toList();

        assertThat(result).hasSize(5);
        var perSubject = result.stream()
                .filter(p -> !p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
        assertThat(perSubject).hasSize(3);
        assertThat(perSubject).extracting(PolicyDefinition::getParticipantContextId)
                .containsOnly(PARTICIPANT_CTX);
        var ownerOnly = result.stream()
                .filter(p -> p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
        assertThat(ownerOnly).hasSize(2);
        assertThat(ownerOnly).extracting(PolicyDefinition::getParticipantContextId)
                .containsOnly(MGMT_PARTICIPANT_CTX);
    }

    @Test
    void findAllWithParticipantContextIdCriterionFiltersCorrectly() {
        var ep = new Endpoint("svc1", "GET", "/api/data", false);
        var ar = createAccessRight(SUBJECT_CLIENT, ep);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(ar));

        // Matching participantContextId
        var matchingSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CTX))
                .build();
        var matchResult = store.findAll(matchingSpec).toList();
        assertThat(matchResult).hasSize(1);

        // Non-matching participantContextId
        var wrongSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", "wrong-context"))
                .build();
        var wrongResult = store.findAll(wrongSpec).toList();
        assertThat(wrongResult).isEmpty();
    }

    @Test
    void findAllSkipsDisabledServices() {
        var ep = new Endpoint("svc1", "GET", "/api/data", false);
        var ar = createAccessRight(SUBJECT_CLIENT, ep);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MEMBER_2));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MEMBER_2)).thenReturn(List.of(SERVICE_2));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn("Maintenance");
        when(serverConfProvider.getDisabledNotice(SERVICE_2)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_2)).thenReturn(List.of(ar));

        var result = store.findAll(QuerySpec.none()).toList();

        assertThat(result).hasSize(3);
        var perSubject = result.stream()
                .filter(p -> !p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
        assertThat(perSubject).hasSize(1);
        var ownerOnly = result.stream()
                .filter(p -> p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
        assertThat(ownerOnly).hasSize(2);
        assertThat(ownerOnly).extracting(PolicyDefinition::getParticipantContextId)
                .containsOnly(MGMT_PARTICIPANT_CTX);
    }

    @Test
    void createReturnsAlreadyExists() {
        var policy = PolicyDefinition.Builder.newInstance()
                .id("test")
                .policy(Policy.Builder.newInstance().build())
                .build();

        var result = store.create(policy);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Read-only");
    }

    @Test
    void updateReturnsNotFound() {
        var policy = PolicyDefinition.Builder.newInstance()
                .id("test")
                .policy(Policy.Builder.newInstance().build())
                .build();

        var result = store.update(policy);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Read-only");
    }

    @Test
    void deleteReturnsNotFound() {
        var result = store.delete("any-policy-id");

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Read-only");
    }

    @Test
    void findAllMgmtServiceTaggedWithMgmtCtx() {
        var ep = new Endpoint("clientReg", "*", "**", true);
        var arMgmt = createAccessRight(SUBJECT_CLIENT, ep);
        var epSvc1 = new Endpoint("svc1", "GET", "/api/data", false);
        var arSvc1 = createAccessRight(SUBJECT_CLIENT, epSvc1);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(arSvc1));
        when(serverConfProvider.getServiceAccessRights(MGMT_SERVICE)).thenReturn(List.of(arMgmt));
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = store.findAll(QuerySpec.none()).toList();

        assertThat(result).hasSize(4);
        var hostPerSubject = result.stream()
                .filter(p -> p.getId().startsWith(SERVICE_1.asEncodedId())
                        && !p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .findFirst().orElseThrow();
        var mgmtPerSubject = result.stream()
                .filter(p -> p.getId().startsWith(MGMT_SERVICE.asEncodedId())
                        && !p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .findFirst().orElseThrow();
        assertThat(hostPerSubject.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
        assertThat(mgmtPerSubject.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX);
        var ownerOnly = result.stream()
                .filter(p -> p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
        assertThat(ownerOnly).hasSize(2);
        assertThat(ownerOnly).extracting(PolicyDefinition::getParticipantContextId)
                .containsOnly(MGMT_PARTICIPANT_CTX);
    }

    @Test
    void findAllWithMgmtCtxFilterReturnsMgmtPoliciesOnly() {
        var ep = new Endpoint("clientReg", "*", "**", true);
        var arMgmt = createAccessRight(SUBJECT_CLIENT, ep);
        var epSvc1 = new Endpoint("svc1", "GET", "/api/data", false);
        var arSvc1 = createAccessRight(SUBJECT_CLIENT, epSvc1);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(arSvc1));
        when(serverConfProvider.getServiceAccessRights(MGMT_SERVICE)).thenReturn(List.of(arMgmt));
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var mgmtSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", MGMT_PARTICIPANT_CTX))
                .build();
        var mgmtResult = store.findAll(mgmtSpec).toList();
        assertThat(mgmtResult).hasSize(3);

        var hostSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CTX))
                .build();
        var hostResult = store.findAll(hostSpec).toList();
        assertThat(hostResult).hasSize(1);
        assertThat(hostResult.getFirst().getId()).startsWith(SERVICE_1.asEncodedId());
    }

    @Test
    void findAllIncludesBuiltinPolicies() {
        var builtinStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = builtinStore.findAll(QuerySpec.none()).toList();

        assertThat(result).hasSize(7);
    }

    @Test
    void findAllBuiltinPoliciesTaggedWithMgmtContext() {
        var builtinStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = builtinStore.findAll(QuerySpec.none()).toList();

        assertThat(result).allSatisfy(pol ->
                assertThat(pol.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX));
    }

    @Test
    void findAllBuiltinsHostCtxFilterExcludesBuiltins() {
        var builtinStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var hostSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CTX))
                .build();

        var result = builtinStore.findAll(hostSpec).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdReturnsBuiltinPolicy() {
        var builtinStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
                allBuiltins());
        var builtinAssetId = "DEV:GOV:1234:" + BuiltinServiceCatalog.PROXY_MONITOR_SERVICE_CODE;

        var result = builtinStore.findById(builtinAssetId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(builtinAssetId);
        assertThat(result.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX);
    }

    @Test
    void findByIdReturnsNullForUnknownBuiltinId() {
        var builtinStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
                allBuiltins());

        var result = builtinStore.findById("DEV:GOV:1234:nonExistentService");

        assertThat(result).isNull();
    }

    @Test
    void findAllEmptyAclEmitsOwnerOnlyPolicyDefinition() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of());

        var result = store.findAll(QuerySpec.none()).toList();

        assertThat(result).hasSize(1);
        var policy = result.getFirst();
        assertThat(policy.getId())
                .isEqualTo(AssetMapper.encodeAssetId(SERVICE_1) + ContractDefinitionMapper.OWNER_ONLY_SUFFIX);
        assertThat(policy.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX);
        assertThat(policy.getPolicy().getPermissions()).hasSize(1);
    }

    @Test
    void findByIdEmptyAclResolvesOwnerOnlyPolicyDefinition() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);

        var policyId = AssetMapper.encodeAssetId(SERVICE_1) + ContractDefinitionMapper.OWNER_ONLY_SUFFIX;

        var result = store.findById(policyId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(policyId);
        assertThat(result.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX);
        assertThat(result.getPolicy().getPermissions()).hasSize(1);
    }

    @Test
    void findByIdOwnerOnlyReturnedEvenWhenServiceHasAcl() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);

        var policyId = AssetMapper.encodeAssetId(SERVICE_1) + ContractDefinitionMapper.OWNER_ONLY_SUFFIX;

        var result = store.findById(policyId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(policyId);
        assertThat(result.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX);
    }

    @Test
    void findAllBuiltinPoliciesHavePermissivePolicy() {
        var builtinStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX,
                allBuiltins());
        when(serverConfProvider.getMembers()).thenReturn(List.of());

        var result = builtinStore.findAll(QuerySpec.none()).toList();

        result.forEach(pol -> assertThat(pol.getPolicy()).isNotNull());
    }

    private static AccessRight createAccessRight(ee.ria.xroad.common.identifier.XRoadId subjectId, Endpoint endpoint) {
        var ar = new AccessRight();
        ar.setSubjectId(subjectId);
        ar.setEndpoint(endpoint);
        ar.setRightsGiven(new Date());
        return ar;
    }
}
