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
import ee.ria.xroad.common.identifier.XRoadId;

import org.eclipse.edc.connector.controlplane.catalog.ContractDefinitionResolverImpl;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.result.Result;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.niis.xroad.edc.extension.catalog.ParticipantContextTestFixtures.participantContext;

/**
 * Runs EDC's own contract definition resolver against the ServerConf-backed definition and policy stores,
 * so the queries the resolver sends are answered by the real stores.
 */
@ExtendWith(MockitoExtension.class)
class ContractDefinitionResolverAgainstServerConfStoresTest {

    private static final ClientId.Conf MEMBER_1 = ClientId.Conf.create("DEV", "GOV", "1234", "SubSys");
    private static final ClientId.Conf MEMBER_2 = ClientId.Conf.create("DEV", "GOV", "5678", "SubSys");
    private static final String MEMBER_1_CTX = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "1234"));
    private static final String MEMBER_2_CTX = ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create("DEV", "GOV", "5678"));

    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1234", "SubSys", "svc1", "v1");
    private static final ServiceId.Conf SERVICE_2 = ServiceId.Conf.create("DEV", "GOV", "5678", "SubSys", "svc2");

    private static final ClientId.Conf SUBJECT_A = ClientId.Conf.create("DEV", "GOV", "9999", "ConsumerA");
    private static final ClientId.Conf SUBJECT_B = ClientId.Conf.create("DEV", "GOV", "9998", "ConsumerB");
    private static final ClientId.Conf SUBJECT_C = ClientId.Conf.create("DEV", "GOV", "9997", "ConsumerC");

    private static final StoreEnumerationCache<ContractDefinition> DISABLED_DEFINITION_CACHE =
            new StoreEnumerationCache<>(false, 60, 1000, "test");
    private static final StoreEnumerationCache<PolicyDefinition> DISABLED_POLICY_CACHE =
            new StoreEnumerationCache<>(false, 60, 1000, "test");

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private PolicyEngine policyEngine;

    private ContractDefinitionResolverImpl resolver;

    @BeforeEach
    void setUp() {
        var builtins = new BuiltinServiceCatalog(serverConfProvider, false, false, false,
                BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
        var serviceContextResolver = new ServiceContextResolver(globalConfProvider, serverConfProvider, participantContextService);
        resolver = new ContractDefinitionResolverImpl(
                new ContractDefinitionServerConfStore(serverConfProvider, builtins, DISABLED_DEFINITION_CACHE, serviceContextResolver),
                policyEngine,
                new PolicyDefinitionServerConfStore(
                        serverConfProvider, new PolicyMapper(), builtins, DISABLED_POLICY_CACHE, serviceContextResolver));

        lenient().when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER_1, MEMBER_2));
        lenient().when(serverConfProvider.getAllServices(MEMBER_1)).thenReturn(List.of(SERVICE_1));
        lenient().when(serverConfProvider.getAllServices(MEMBER_2)).thenReturn(List.of(SERVICE_2));
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_1))
                .thenReturn(List.of(accessRight(SUBJECT_A), accessRight(SUBJECT_B)));
        lenient().when(serverConfProvider.getServiceAccessRights(SERVICE_2)).thenReturn(List.of(accessRight(SUBJECT_C)));
        lenient().when(participantContextService.search(any())).thenReturn(ServiceResult.success(
                List.of(participantContext(MEMBER_1_CTX), participantContext(MEMBER_2_CTX))));
        lenient().when(policyEngine.evaluate(any(Policy.class), any(PolicyContext.class))).thenReturn(Result.success());
    }

    @Test
    void resolvesTheMemberContextsDefinitionsAndTheirPolicies() {
        var resolved = resolver.resolveFor(participantContext(MEMBER_1_CTX), consumerAgent());

        var expectedPolicyIds = List.of(policyId(SERVICE_1, SUBJECT_A), policyId(SERVICE_1, SUBJECT_B));
        assertThat(resolved.contractDefinitions()).extracting(ContractDefinition::getAccessPolicyId)
                .containsExactlyInAnyOrderElementsOf(expectedPolicyIds);
        assertThat(resolved.policies()).containsOnlyKeys(expectedPolicyIds);
        assertThat(resolved.policies().values()).allSatisfy(policy -> assertThat(policy.getPermissions()).hasSize(1));
    }

    @Test
    void resolvesAnotherMemberContextsOwnDefinitionsOnly() {
        var resolved = resolver.resolveFor(participantContext(MEMBER_2_CTX), consumerAgent());

        var expectedPolicyId = policyId(SERVICE_2, SUBJECT_C);
        assertThat(resolved.contractDefinitions()).extracting(ContractDefinition::getAccessPolicyId)
                .containsExactly(expectedPolicyId);
        assertThat(resolved.policies()).containsOnlyKeys(expectedPolicyId);
    }

    private static ParticipantAgent consumerAgent() {
        return new ParticipantAgent("consumer", Map.of(), Map.of());
    }

    private static String policyId(ServiceId service, XRoadId subject) {
        return AssetMapper.encodeAssetId(service) + XRoadId.ENCODED_ID_SEPARATOR + subject.asEncodedId();
    }

    private static AccessRight accessRight(XRoadId subjectId) {
        var accessRight = new AccessRight();
        accessRight.setSubjectId(subjectId);
        accessRight.setEndpoint(new Endpoint("svc", "GET", "/api/data", false));
        accessRight.setRightsGiven(new Date());
        return accessRight;
    }
}
