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
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
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
class ContractDefinitionServerConfStoreTest {

    private static final String PARTICIPANT_CTX = "xroad-provider";
    private static final String MGMT_PARTICIPANT_CTX = "xroad-provider-mgmt";

    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1234", "SubSys");
    private static final ClientId.Conf MEMBER_2 = ClientId.Conf.create("DEV", "GOV", "5678", "SubSys");
    private static final ClientId.Conf MGMT_CLIENT = ClientId.Conf.create("DEV", "COM", "3333", "MANAGEMENT");

    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1234", "SubSys", "svc1", "v1");
    private static final ServiceId.Conf SERVICE_2 = ServiceId.Conf.create("DEV", "GOV", "5678", "SubSys", "svc2");
    private static final ServiceId.Conf MGMT_SERVICE = ServiceId.Conf.create("DEV", "COM", "3333", "MANAGEMENT", "clientReg");

    private static final ClientId.Conf SUBJECT_CLIENT = ClientId.Conf.create("DEV", "GOV", "9999", "Consumer");
    private static final GlobalGroupId SUBJECT_GROUP = GlobalGroupId.Conf.create("DEV", "sec-owners");

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private ContractDefinitionServerConfStore store;

    @BeforeEach
    void setUp() {
        store = new ContractDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new ContractDefinitionMapper(), PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX);
    }

    @Test
    void findAllReturnsOneDefinitionPerAssetSubjectPair() {
        var ep1 = new Endpoint("svc1", "GET", "/api/data", false);
        var ep2 = new Endpoint("svc1", "POST", "/api/data", false);
        var ar1 = createAccessRight(SUBJECT_CLIENT, ep1);
        var ar2 = createAccessRight(SUBJECT_CLIENT, ep2);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(ar1, ar2));

        var result = store.findAll(QuerySpec.max()).toList();

        // Two access rights for the same subject -> grouped into one definition
        assertThat(result).hasSize(1);
    }

    @Test
    void findAllReturnsMultipleDefinitionsForMultipleSubjects() {
        var ep = new Endpoint("svc1", "GET", "/api/data", false);
        var arClient = createAccessRight(SUBJECT_CLIENT, ep);
        var arGroup = createAccessRight(SUBJECT_GROUP, ep);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(arClient, arGroup));

        var result = store.findAll(QuerySpec.max()).toList();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ContractDefinition::getId)
                .doesNotHaveDuplicates();
    }

    @Test
    void findAllSkipsDisabledServices() {
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn("Maintenance");

        var result = store.findAll(QuerySpec.max()).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdReturnsCorrectDefinition() {
        var ep = new Endpoint("svc1", "GET", "/api/data", false);
        var ar = createAccessRight(SUBJECT_CLIENT, ep);

        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(ar));

        var contractId = AssetMapper.encodeAssetId(SERVICE_1)
                + XRoadId.ENCODED_ID_SEPARATOR + SUBJECT_CLIENT.asEncodedId()
                + ContractDefinitionMapper.getContractDefinitionSuffix();

        var result = store.findById(contractId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(contractId);
        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
    }

    @Test
    void findByIdWithFivePartServiceId() {
        var ep = new Endpoint("svc2", "*", "**", true);
        var ar = createAccessRight(SUBJECT_CLIENT, ep);

        // The store tries 6-part decode first; for a 5-part service ID the 6-part attempt decodes
        // a different ServiceId (first 6 tokens include part of the subject), which won't exist.
        // We need to allow that serviceExists call to return false so the store falls through to 5-part.
        var sixPartAttempt = ServiceId.Conf.create("DEV", "GOV", "5678", "SubSys", "svc2", "DEV");
        when(serverConfProvider.serviceExists(sixPartAttempt)).thenReturn(false);
        when(serverConfProvider.serviceExists(SERVICE_2)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(SERVICE_2)).thenReturn(List.of(ar));

        var contractId = AssetMapper.encodeAssetId(SERVICE_2)
                + XRoadId.ENCODED_ID_SEPARATOR + SUBJECT_CLIENT.asEncodedId()
                + ContractDefinitionMapper.getContractDefinitionSuffix();

        var result = store.findById(contractId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(contractId);
    }

    @Test
    void findByIdWithUnknownServiceReturnsNull() {
        when(serverConfProvider.serviceExists(SERVICE_1)).thenReturn(false);

        var contractId = "DEV:GOV:1234:SubSys:svc1:v1:DEV:GOV:9999:Consumer-contract-definition";
        var result = store.findById(contractId);

        assertThat(result).isNull();
    }

    @Test
    void findByIdWithMalformedIdReturnsNull() {
        // "not:enough:parts-contract-definition" -> after suffix strip: "not:enough:parts" -> only 3 parts < 6
        var result = store.findById("not:enough:parts-contract-definition");

        assertThat(result).isNull();
    }

    @Test
    void findByIdWithNoSuffixReturnsNull() {
        // Valid policy ID format but missing -contract-definition suffix (D-11)
        var result = store.findById("DEV:GOV:1234:SubSys:svc1:v1:DEV:GOV:9999:Consumer");

        assertThat(result).isNull();
    }

    @Test
    void findByIdWithNullReturnsNull() {
        var result = store.findById(null);

        assertThat(result).isNull();
    }

    @Test
    void findByIdWithBlankReturnsNull() {
        var result = store.findById("  ");

        assertThat(result).isNull();
    }

    @Test
    void findAllWithParticipantContextIdCriterionFilters() {
        var ep = new Endpoint("svc1", "GET", "/api/data", false);
        var ar = createAccessRight(SUBJECT_CLIENT, ep);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(ar));

        var matchingSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CTX))
                .build();
        var matchResult = store.findAll(matchingSpec).toList();
        assertThat(matchResult).hasSize(1);

        var wrongSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", "wrong-ctx"))
                .build();
        var wrongResult = store.findAll(wrongSpec).toList();
        assertThat(wrongResult).isEmpty();
    }

    @Test
    void saveReturnsAlreadyExists() {
        var definition = ContractDefinition.Builder.newInstance()
                .id("test")
                .accessPolicyId("p")
                .contractPolicyId("p")
                .build();

        var result = store.save(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void updateReturnsNotFound() {
        var definition = ContractDefinition.Builder.newInstance()
                .id("test")
                .accessPolicyId("p")
                .contractPolicyId("p")
                .build();

        var result = store.update(definition);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void deleteByIdReturnsNotFound() {
        var result = store.deleteById("any-id");

        assertThat(result.failed()).isTrue();
    }

    @Test
    void findAllMgmtServiceTaggedWithMgmtCtx() {
        var epSvc1 = new Endpoint("svc1", "GET", "/api/data", false);
        var arSvc1 = createAccessRight(SUBJECT_CLIENT, epSvc1);
        var epMgmt = new Endpoint("clientReg", "*", "**", true);
        var arMgmt = createAccessRight(SUBJECT_CLIENT, epMgmt);

        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MGMT_CLIENT));
        when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        when(serverConfProvider.getAllServices(MGMT_CLIENT)).thenReturn(List.of(MGMT_SERVICE));
        when(serverConfProvider.getDisabledNotice(SERVICE_1)).thenReturn(null);
        when(serverConfProvider.getDisabledNotice(MGMT_SERVICE)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(SERVICE_1)).thenReturn(List.of(arSvc1));
        when(serverConfProvider.getServiceAccessRights(MGMT_SERVICE)).thenReturn(List.of(arMgmt));
        when(globalConfProvider.getManagementRequestService()).thenReturn(MGMT_CLIENT);

        var result = store.findAll(QuerySpec.max()).toList();

        assertThat(result).hasSize(2);
        var mgmtDef = result.stream()
                .filter(d -> d.getId().startsWith(MGMT_SERVICE.asEncodedId()))
                .findFirst().orElseThrow();
        var hostDef = result.stream()
                .filter(d -> d.getId().startsWith(SERVICE_1.asEncodedId()))
                .findFirst().orElseThrow();
        assertThat(mgmtDef.getParticipantContextId()).isEqualTo(MGMT_PARTICIPANT_CTX);
        assertThat(hostDef.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
    }

    @Test
    void findAllWithMgmtCtxFilterReturnsOnlyMgmtDefinitions() {
        var epSvc1 = new Endpoint("svc1", "GET", "/api/data", false);
        var arSvc1 = createAccessRight(SUBJECT_CLIENT, epSvc1);
        var epMgmt = new Endpoint("clientReg", "*", "**", true);
        var arMgmt = createAccessRight(SUBJECT_CLIENT, epMgmt);

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
        assertThat(mgmtResult).hasSize(1);
        assertThat(mgmtResult.getFirst().getId()).startsWith(MGMT_SERVICE.asEncodedId());

        var hostSpec = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", PARTICIPANT_CTX))
                .build();
        var hostResult = store.findAll(hostSpec).toList();
        assertThat(hostResult).hasSize(1);
        assertThat(hostResult.getFirst().getId()).startsWith(SERVICE_1.asEncodedId());
    }

    private static AccessRight createAccessRight(XRoadId subjectId, Endpoint endpoint) {
        var ar = new AccessRight();
        ar.setSubjectId(subjectId);
        ar.setEndpoint(endpoint);
        ar.setRightsGiven(new Date());
        return ar;
    }
}
