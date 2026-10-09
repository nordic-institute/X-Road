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
package org.niis.xroad.proxy.dataplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.signaling.domain.DataFlowPrepareMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartMessage;
import org.eclipse.edc.signaling.domain.DspDataAddress;
import org.eclipse.edc.spi.constants.CoreConstants;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.agreementtoken.AgreementTokenRequestContext;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerificationResult;
import org.niis.xroad.common.agreementtoken.AgreementTokenVerifier;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.controlplane.AgreementGrant;
import org.niis.xroad.proxy.controlplane.AgreementGrantRpcClient;
import org.niis.xroad.proxy.core.configuration.AgreementTokenKeyMaterial;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.Endpoint;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneManagerTest {

    private static final SecurityServerId.Conf OWN_ID = SecurityServerId.Conf.create("DEV", "COM", "1234", "SS0");
    private static final String OWN_ADDRESS = "provider.example.org";
    private static final String SERVERPROXY_ENDPOINT = "https://provider.example.org:5500";
    private static final String FALLBACK_ENDPOINT = "https://fallback.example.org:5500";

    private static final ClientId CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create("DEV", "COM", "333", "PROVIDER", "getData", "v1");
    private static final TestAgreementTokenProtocolProperties TOKEN_PROPERTIES =
            new TestAgreementTokenProtocolProperties("x-road-provider-data-plane", "x-road-server-proxy", Duration.ofSeconds(60));

    @Mock
    private DataPlaneServerProperties properties;
    @Mock
    private OwnSecurityServerResolver ownSecurityServerResolver;
    @Mock
    private ServerConfProvider serverConfProvider;
    @Mock
    private ProxyProperties proxyProperties;
    @Mock
    private AgreementGrantRpcClient grantRpcClient;

    private AgreementTokenKeyMaterial keyMaterial;
    private TestAgreementTokenKeyProvider keyProvider;
    private DataFlowStateStore flowStateStore;
    private AgreementTokenIssuer agreementTokenIssuer;
    private XRoadDataPlaneManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(serverConfProvider.getIdentifier()).thenReturn(OWN_ID);
        lenient().when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(OWN_ID, OWN_ADDRESS));
        lenient().when(proxyProperties.sslEnabled()).thenReturn(true);
        lenient().when(proxyProperties.serverProxyPort()).thenReturn(5500);

        keyProvider = new TestAgreementTokenKeyProvider();
        keyProvider.addKey("1");
        keyMaterial = mock(AgreementTokenKeyMaterial.class);
        lenient().when(keyMaterial.provider()).thenReturn(Optional.of(keyProvider));

        agreementTokenIssuer = new AgreementTokenIssuer(grantRpcClient, serverConfProvider, keyMaterial, TOKEN_PROPERTIES);
        flowStateStore = new InMemoryDataFlowStateStore();
        manager = new XRoadDataPlaneManager(properties, ownSecurityServerResolver, proxyProperties, flowStateStore,
                agreementTokenIssuer);
    }

    @Test
    void startAdvertisesServerproxyEndpointNotSignalingPath() {
        var message = buildStartMessage("flow-1");

        var result = manager.start(message);

        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
        assertThat(result.getDataAddress().getEndpointType()).isEqualTo("https");
    }

    @Test
    void startAdvertisedEndpointDoesNotContainDataflowsPath() {
        var message = buildStartMessage("flow-2");

        var result = manager.start(message);

        assertThat(result.getDataAddress().getEndpoint()).doesNotContain("/api/v1/dataflows");
    }

    @Test
    void startReturnsStartedState() {
        var message = buildStartMessage("flow-3");

        var result = manager.start(message);

        assertThat(result.getState()).isEqualTo(DataFlowStates.STARTED.toString());
    }

    @Test
    void prepareAdvertisesServerproxyEndpoint() {
        var message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("flow-4")
                .transferType("Xrd-PULL")
                .build();

        var result = manager.prepare(message);

        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
        assertThat(result.getDataAddress().getEndpointType()).isEqualTo("https");
        assertThat(result.getState()).isEqualTo(DataFlowStates.PROVISIONED.toString());
    }

    @ParameterizedTest
    @MethodSource("unavailableOwnAddresses")
    void startAdvertisesFallbackEndpointWhenOwnAddressIsNotAvailable(OwnAddress ownAddress) {
        when(ownSecurityServerResolver.address()).thenReturn(ownAddress);
        when(properties.serverproxyEndpoint()).thenReturn(FALLBACK_ENDPOINT);
        var result = manager.start(buildStartMessage("flow-fallback-start"));
        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(FALLBACK_ENDPOINT);
        assertThat(result.getState()).isEqualTo(DataFlowStates.STARTED.toString());
    }

    @ParameterizedTest
    @MethodSource("unavailableOwnAddresses")
    void prepareAdvertisesFallbackEndpointWhenOwnAddressIsNotAvailable(OwnAddress ownAddress) {
        when(ownSecurityServerResolver.address()).thenReturn(ownAddress);
        when(properties.serverproxyEndpoint()).thenReturn(FALLBACK_ENDPOINT);
        var result = manager.prepare(buildPrepareMessage("flow-fallback-prepare"));
        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(FALLBACK_ENDPOINT);
        assertThat(result.getState()).isEqualTo(DataFlowStates.PROVISIONED.toString());
    }

    @Test
    void startAdvertisesFallbackEndpointWhenOwnAddressLookupFailsOnDatabase() {
        when(ownSecurityServerResolver.address())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.DATABASE_ERROR, "database down"));
        when(properties.serverproxyEndpoint()).thenReturn(FALLBACK_ENDPOINT);
        var result = manager.start(buildStartMessage("flow-fallback-db"));
        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(FALLBACK_ENDPOINT);
        assertThat(result.getState()).isEqualTo(DataFlowStates.STARTED.toString());
    }

    @Test
    void startRejectsNonXrdPullTransferType() {
        var message = DataFlowStartMessage.Builder.newInstance()
                .processId("flow-5")
                .transferType("Http-PUSH")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .build();

        assertThatThrownBy(() -> manager.start(message))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Http-PUSH");
    }

    @Test
    void startedTransitionsFlowToStartedAndReturnsStateOnlyStatus() {
        manager.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("flow-9")
                .transferType("Xrd-PULL")
                .build());

        var result = manager.started("flow-9");

        assertThat(result.getState()).isEqualTo(DataFlowStates.STARTED.toString());
        assertThat(result.getDataAddress()).isNull();
        assertThat(manager.state("flow-9")).isEqualTo(DataFlowStates.STARTED);
    }

    @Test
    void completedTransitionsFlowToCompleted() {
        manager.start(buildStartMessage("flow-10"));

        manager.completed("flow-10");

        assertThat(manager.state("flow-10")).isEqualTo(DataFlowStates.COMPLETED);
    }

    @Test
    void terminateTransitionsFlowToTerminated() {
        manager.start(buildStartMessage("flow-6"));

        manager.terminate("flow-6");

        assertThat(manager.state("flow-6")).isEqualTo(DataFlowStates.TERMINATED);
    }

    @Test
    void suspendTransitionsFlowToSuspended() {
        manager.start(buildStartMessage("flow-7"));

        manager.suspend("flow-7", "maintenance");

        assertThat(manager.state("flow-7")).isEqualTo(DataFlowStates.SUSPENDED);
    }

    @Test
    void stateReturnsFailedForUnknownFlow() {
        assertThat(manager.state("unknown")).isEqualTo(DataFlowStates.FAILED);
    }

    @Test
    void startAndPrepareCarryAuthorizationTokenWhenGrantAndLiveAclMatch() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"));
        when(grantRpcClient.resolveAgreementGrant("agreement-1"))
                .thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE))
                .thenReturn(List.of(new Endpoint("getData", "GET", "/foo/*", false)));

        var startResult = manager.start(buildStartMessage("flow-token-start"));
        var prepareResult = manager.prepare(buildPrepareMessage("flow-token-prepare"));

        assertTokenPresentAndValid(startResult.getDataAddress(), scope);
        assertTokenPresentAndValid(prepareResult.getDataAddress(), scope);
    }

    @Test
    void noTokenWhenNoGrant() {
        when(grantRpcClient.resolveAgreementGrant("agreement-1")).thenReturn(Optional.empty());

        var result = manager.start(buildStartMessage("flow-no-grant"));

        assertThat(authorizationProperty(result.getDataAddress())).isEmpty();
        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
    }

    @Test
    void noTokenWhenGrantLookupThrows() {
        when(grantRpcClient.resolveAgreementGrant("agreement-1")).thenThrow(new RuntimeException("boom"));

        var result = manager.start(buildStartMessage("flow-grant-throws"));

        assertThat(authorizationProperty(result.getDataAddress())).isEmpty();
        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
    }

    @Test
    void tokenCarriesOnlyTheScopeEntryStillGrantedByTheLiveAclWhenTheOtherIsMissing() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"), new AgreementTokenScope("POST", "/bar"));
        when(grantRpcClient.resolveAgreementGrant("agreement-1"))
                .thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE))
                .thenReturn(List.of(new Endpoint("getData", "GET", "/foo/*", false)));

        var result = manager.start(buildStartMessage("flow-partial-acl"));

        assertTokenPresentAndValid(result.getDataAddress(), List.of(new AgreementTokenScope("GET", "/foo/*")));
    }

    @Test
    void noTokenWhenNoGrantedScopeEntrySurvivesTheLiveAcl() {
        var scope = List.of(new AgreementTokenScope("GET", "/foo/*"), new AgreementTokenScope("POST", "/bar"));
        when(grantRpcClient.resolveAgreementGrant("agreement-1"))
                .thenReturn(Optional.of(new AgreementGrant(CONSUMER, SERVICE, scope)));
        when(serverConfProvider.getAclEndpoints(CONSUMER, SERVICE)).thenReturn(List.of());

        var result = manager.start(buildStartMessage("flow-empty-acl"));

        assertThat(authorizationProperty(result.getDataAddress())).isEmpty();
    }

    @Test
    void noTokenWhenKeyMaterialUnavailable() {
        when(keyMaterial.provider()).thenReturn(Optional.empty());

        var result = manager.start(buildStartMessage("flow-no-key-material"));

        assertThat(authorizationProperty(result.getDataAddress())).isEmpty();
        assertThat(result.getDataAddress().getEndpoint()).isEqualTo(SERVERPROXY_ENDPOINT);
        verifyNoInteractions(grantRpcClient);
    }

    @Test
    void flowStartedOnOneNodeIsVisibleOnAnotherNodeSharingTheStore() {
        var otherNodeManager = new XRoadDataPlaneManager(properties, ownSecurityServerResolver,
                proxyProperties, flowStateStore, agreementTokenIssuer);

        manager.start(buildStartMessage("flow-shared"));

        assertThat(otherNodeManager.state("flow-shared")).isEqualTo(DataFlowStates.STARTED);
    }

    @Test
    void lifecycleTransitionOnOneNodeUpdatesTheSharedRecordSeenByAnother() {
        var nodeA = manager;
        var nodeB = new XRoadDataPlaneManager(properties, ownSecurityServerResolver,
                proxyProperties, flowStateStore, agreementTokenIssuer);

        nodeA.start(buildStartMessage("flow-cluster"));
        assertThat(nodeB.state("flow-cluster")).isEqualTo(DataFlowStates.STARTED);

        nodeB.suspend("flow-cluster", "maintenance");
        assertThat(nodeA.state("flow-cluster")).isEqualTo(DataFlowStates.SUSPENDED);

        nodeA.terminate("flow-cluster");
        assertThat(nodeB.state("flow-cluster")).isEqualTo(DataFlowStates.TERMINATED);
    }

    private static Stream<OwnAddress> unavailableOwnAddresses() {
        return Stream.of(
                new OwnAddress.NotRegistered(OWN_ID),
                new OwnAddress.GlobalConfUnavailable(OWN_ID, new IllegalStateException("global conf unreadable")),
                new OwnAddress.OwnerNotInitialised());
    }

    private DataFlowStartMessage buildStartMessage(String processId) {
        return DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType("Xrd-PULL")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .build();
    }

    private DataFlowPrepareMessage buildPrepareMessage(String processId) {
        return DataFlowPrepareMessage.Builder.newInstance()
                .processId(processId)
                .transferType("Xrd-PULL")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .build();
    }

    private void assertTokenPresentAndValid(DspDataAddress dataAddress, List<AgreementTokenScope> expectedScope) {
        var token = authorizationProperty(dataAddress);
        assertThat(token).isPresent();
        assertThat(authTypeProperty(dataAddress)).contains("bearer");

        var verifier = new AgreementTokenVerifier(keyProvider, TOKEN_PROPERTIES);
        var result = verifier.verify(token.get(), AgreementTokenRequestContext.forSoap(CONSUMER, SERVICE));
        assertThat(result).isInstanceOf(AgreementTokenVerificationResult.Valid.class);
        var claims = ((AgreementTokenVerificationResult.Valid) result).claims();
        assertThat(claims.agreementId()).isEqualTo("agreement-1");
        assertThat(claims.client()).isEqualTo(CONSUMER);
        assertThat(claims.service()).isEqualTo(SERVICE);
        assertThat(claims.scope()).containsExactlyInAnyOrderElementsOf(expectedScope);
    }

    private static Optional<String> authorizationProperty(DspDataAddress dataAddress) {
        return findProperty(dataAddress, CoreConstants.EDC_NAMESPACE + "authorization");
    }

    private static Optional<String> authTypeProperty(DspDataAddress dataAddress) {
        return findProperty(dataAddress, CoreConstants.EDC_NAMESPACE + "authType");
    }

    private static Optional<String> findProperty(DspDataAddress dataAddress, String name) {
        return dataAddress.getEndpointProperties().stream()
                .filter(property -> property.getName().equals(name))
                .map(DspDataAddress.EndpointProperty::getValue)
                .findFirst();
    }

    /**
     * A shared-map fake for {@link SharedDataFlowStateStore}, handed to every manager in a test so
     * it models one record visible to every node.
     */
    private static final class InMemoryDataFlowStateStore implements DataFlowStateStore {

        private final ConcurrentHashMap<String, DataFlowStates> states = new ConcurrentHashMap<>();

        @Override
        public StoreResult<Void> save(String flowId, DataFlowStates state) {
            states.put(flowId, state);
            return StoreResult.success();
        }

        @Override
        public Optional<DataFlowStates> find(String flowId) {
            return Optional.ofNullable(states.get(flowId));
        }
    }
}
