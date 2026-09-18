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

import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pins the guarantee that a consumer without an access-right entry cannot tell a disabled service
 * from one it simply may not call: no per-subject contract definition or policy is ever published
 * for such a consumer, whether the service is disabled or enabled, and nothing published for a
 * disabled service — asset, contract definition, or policy — carries the operator's disabled
 * notice. A consumer that does hold an access-right entry keeps negotiating normally.
 *
 * <p>The property is an emergent consequence of two independent facts: {@link ServiceContextResolver}
 * resolves the same publication contexts for disabled and enabled services, and the three
 * ServerConf-backed stores derive per-subject contract definitions and policies from access-right
 * entries alone. Neither fact mentions the other; this test exists so a change to
 * either — such as moving a disabled check earlier — cannot silently reintroduce a leak.
 */
@ExtendWith(MockitoExtension.class)
class UnauthorizedConsumerCannotObserveDisabledServiceStateTest {

    private static final String PARTICIPANT_CTX = "xroad-provider";
    private static final String MGMT_PARTICIPANT_CTX = "xroad-provider-mgmt";
    private static final String SYSTEM_PARTICIPANT_CTX = ParticipantIdentifierScheme.SYSTEM_SEGMENT;
    private static final CatalogContextIds CONTEXT_IDS = new CatalogContextIds(
            PARTICIPANT_CTX, MGMT_PARTICIPANT_CTX, SYSTEM_PARTICIPANT_CTX);

    private static final String DISABLED_NOTICE = "Service temporarily suspended for scheduled maintenance, retry after 18:00 UTC";

    private static final ClientId.Conf MEMBER = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
    private static final ServiceId.Conf DISABLED_SERVICE =
            ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "svcDisabled", "v1");
    private static final ServiceId.Conf ENABLED_SERVICE =
            ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "svcEnabled", "v1");
    private static final ClientId.Conf AUTHORIZED_SUBJECT = ClientId.Conf.create("DEV", "GOV", "9999", "Authorized");
    private static final ClientId.Conf UNAUTHORIZED_SUBJECT = ClientId.Conf.create("DEV", "GOV", "8888", "Unauthorized");

    // When the 6-part (versioned) decode of a compound findById id finds no matching subject, the
    // store falls through to a 5-part decode attempt that misreads the version segment as part of
    // the subject; that attempt's bogus versionless ServiceId must resolve to false, not throw.
    private static final ServiceId.Conf DISABLED_SERVICE_FALLBACK_DECODE =
            ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "svcDisabled");
    private static final ServiceId.Conf ENABLED_SERVICE_FALLBACK_DECODE =
            ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "svcEnabled");

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    private ServiceContextResolver serviceContextResolver;
    private final ThreadLocalRequestedParticipantContext requestedParticipantContext = new ThreadLocalRequestedParticipantContext();

    private ContractDefinitionServerConfStore contractStore;
    private PolicyDefinitionServerConfStore policyStore;
    private AssetIndexServerConfStore assetIndex;

    @BeforeEach
    void setUp() {
        lenient().when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of()));
        lenient().when(participantContextService.getParticipantContext(any())).thenReturn(ServiceResult.notFound("no such context"));
        serviceContextResolver = new ServiceContextResolver(
                CONTEXT_IDS, globalConfProvider, serverConfProvider, participantContextService);
        requestedParticipantContext.clear();

        var noBuiltins = new BuiltinServiceCatalog(serverConfProvider, false, false, false,
                BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);
        contractStore = new ContractDefinitionServerConfStore(
                serverConfProvider, CONTEXT_IDS, noBuiltins,
                new StoreEnumerationCache<>(false, 60, 1000, "test"), serviceContextResolver, requestedParticipantContext);
        policyStore = new PolicyDefinitionServerConfStore(
                serverConfProvider, new PolicyMapper(), CONTEXT_IDS, noBuiltins,
                new StoreEnumerationCache<>(false, 60, 1000, "test"), serviceContextResolver, requestedParticipantContext);
        assetIndex = new AssetIndexServerConfStore(
                serverConfProvider, CONTEXT_IDS, noBuiltins,
                new StoreEnumerationCache<>(false, 60, 1000, "test"), serviceContextResolver, requestedParticipantContext);
    }

    @Test
    void contractNegotiationRefusedForUnauthorizedConsumerIndistinguishablyFromEnabledService() {
        lenient().when(serverConfProvider.getDisabledNotice(DISABLED_SERVICE)).thenReturn(DISABLED_NOTICE);
        when(serverConfProvider.serviceExists(DISABLED_SERVICE)).thenReturn(true);
        when(serverConfProvider.serviceExists(ENABLED_SERVICE)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(DISABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcDisabled", "GET", "/api/data", false))));
        when(serverConfProvider.getServiceAccessRights(ENABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcEnabled", "GET", "/api/data", false))));
        lenient().when(serverConfProvider.serviceExists(DISABLED_SERVICE_FALLBACK_DECODE)).thenReturn(false);
        lenient().when(serverConfProvider.serviceExists(ENABLED_SERVICE_FALLBACK_DECODE)).thenReturn(false);

        var disabledRefusal = contractStore.findById(contractDefinitionId(DISABLED_SERVICE, UNAUTHORIZED_SUBJECT));
        var enabledRefusal = contractStore.findById(contractDefinitionId(ENABLED_SERVICE, UNAUTHORIZED_SUBJECT));

        assertThat(disabledRefusal).isNull();
        assertThat(enabledRefusal).isNull();
    }

    @Test
    void policyNegotiationRefusedForUnauthorizedConsumerIndistinguishablyFromEnabledService() {
        lenient().when(serverConfProvider.getDisabledNotice(DISABLED_SERVICE)).thenReturn(DISABLED_NOTICE);
        when(serverConfProvider.serviceExists(DISABLED_SERVICE)).thenReturn(true);
        when(serverConfProvider.serviceExists(ENABLED_SERVICE)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(DISABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcDisabled", "GET", "/api/data", false))));
        when(serverConfProvider.getServiceAccessRights(ENABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcEnabled", "GET", "/api/data", false))));
        lenient().when(serverConfProvider.serviceExists(DISABLED_SERVICE_FALLBACK_DECODE)).thenReturn(false);
        lenient().when(serverConfProvider.serviceExists(ENABLED_SERVICE_FALLBACK_DECODE)).thenReturn(false);

        var disabledRefusal = policyStore.findById(policyDefinitionId(DISABLED_SERVICE, UNAUTHORIZED_SUBJECT));
        var enabledRefusal = policyStore.findById(policyDefinitionId(ENABLED_SERVICE, UNAUTHORIZED_SUBJECT));

        assertThat(disabledRefusal).isNull();
        assertThat(enabledRefusal).isNull();
    }

    @Test
    void authorizedConsumerStillNegotiatesContractDefinitionForDisabledService() {
        lenient().when(serverConfProvider.getDisabledNotice(DISABLED_SERVICE)).thenReturn(DISABLED_NOTICE);
        when(serverConfProvider.serviceExists(DISABLED_SERVICE)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(DISABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcDisabled", "GET", "/api/data", false))));

        var result = contractStore.findById(contractDefinitionId(DISABLED_SERVICE, AUTHORIZED_SUBJECT));

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
    }

    @Test
    void authorizedConsumerStillNegotiatesPolicyForDisabledService() {
        lenient().when(serverConfProvider.getDisabledNotice(DISABLED_SERVICE)).thenReturn(DISABLED_NOTICE);
        when(serverConfProvider.serviceExists(DISABLED_SERVICE)).thenReturn(true);
        when(serverConfProvider.getServiceAccessRights(DISABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcDisabled", "GET", "/api/data", false))));

        var result = policyStore.findById(policyDefinitionId(DISABLED_SERVICE, AUTHORIZED_SUBJECT));

        assertThat(result).isNotNull();
        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
    }

    @Test
    void findAllProducesIdenticalEmptyPerSubjectSetForDisabledAndEnabledServiceWithNoAccessRights() {
        lenient().when(serverConfProvider.getDisabledNotice(DISABLED_SERVICE)).thenReturn(DISABLED_NOTICE);
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER));
        when(serverConfProvider.getAllServices(MEMBER)).thenReturn(List.of(DISABLED_SERVICE, ENABLED_SERVICE));
        when(serverConfProvider.getServiceAccessRights(DISABLED_SERVICE)).thenReturn(List.of());
        when(serverConfProvider.getServiceAccessRights(ENABLED_SERVICE)).thenReturn(List.of());

        var contractDefinitions = contractStore.findAll(QuerySpec.max()).toList();
        var policies = policyStore.findAll(QuerySpec.none()).toList();

        assertThat(perSubjectContractDefinitionsFor(contractDefinitions, DISABLED_SERVICE)).isEmpty();
        assertThat(perSubjectContractDefinitionsFor(contractDefinitions, ENABLED_SERVICE)).isEmpty();
        assertThat(perSubjectPoliciesFor(policies, DISABLED_SERVICE)).isEmpty();
        assertThat(perSubjectPoliciesFor(policies, ENABLED_SERVICE)).isEmpty();
    }

    @Test
    void noPublishedArtifactForDisabledServiceCarriesTheDisabledNoticeText() {
        lenient().when(serverConfProvider.getDisabledNotice(DISABLED_SERVICE)).thenReturn(DISABLED_NOTICE);
        when(serverConfProvider.getMembers()).thenReturn(List.of(MEMBER));
        when(serverConfProvider.getAllServices(MEMBER)).thenReturn(List.of(DISABLED_SERVICE));
        when(serverConfProvider.getServiceAccessRights(DISABLED_SERVICE)).thenReturn(List.of(
                createAccessRight(AUTHORIZED_SUBJECT, new Endpoint("svcDisabled", "GET", "/api/data", false))));

        var assets = assetIndex.queryAssets(QuerySpec.max()).toList();
        var contractDefinitions = contractStore.findAll(QuerySpec.max()).toList();
        var policies = policyStore.findAll(QuerySpec.none()).toList();

        assertThat(assets).isNotEmpty();
        assertThat(contractDefinitions).isNotEmpty();
        assertThat(policies).isNotEmpty();

        assertThat(assets).allSatisfy(asset ->
                assertThat(asset.getProperties().values().toString()).doesNotContain(DISABLED_NOTICE));
        assertThat(contractDefinitions).allSatisfy(def ->
                assertThat(def.getId() + def.getAccessPolicyId() + def.getContractPolicyId() + def.getAssetsSelector())
                        .doesNotContain(DISABLED_NOTICE));
        assertThat(policies).allSatisfy(pol ->
                assertThat(pol.getId() + pol.getPolicy().getPermissions()).doesNotContain(DISABLED_NOTICE));
    }

    private static List<ContractDefinition> perSubjectContractDefinitionsFor(List<ContractDefinition> definitions, ServiceId serviceId) {
        var assetId = AssetMapper.encodeAssetId(serviceId);
        return definitions.stream()
                .filter(d -> d.getAccessPolicyId().startsWith(assetId)
                        && !d.getAccessPolicyId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
    }

    private static List<PolicyDefinition> perSubjectPoliciesFor(List<PolicyDefinition> policies, ServiceId serviceId) {
        var assetId = AssetMapper.encodeAssetId(serviceId);
        return policies.stream()
                .filter(p -> p.getId().startsWith(assetId) && !p.getId().endsWith(ContractDefinitionMapper.OWNER_ONLY_SUFFIX))
                .toList();
    }

    private static String contractDefinitionId(ServiceId serviceId, ClientId subject) {
        return AssetMapper.encodeAssetId(serviceId) + XRoadId.ENCODED_ID_SEPARATOR + subject.asEncodedId()
                + ContractDefinitionMapper.getContractDefinitionSuffix();
    }

    private static String policyDefinitionId(ServiceId serviceId, ClientId subject) {
        return AssetMapper.encodeAssetId(serviceId) + XRoadId.ENCODED_ID_SEPARATOR + subject.asEncodedId();
    }

    private static AccessRight createAccessRight(XRoadId subjectId, Endpoint endpoint) {
        var ar = new AccessRight();
        ar.setSubjectId(subjectId);
        ar.setEndpoint(endpoint);
        ar.setRightsGiven(new Date());
        return ar;
    }
}
