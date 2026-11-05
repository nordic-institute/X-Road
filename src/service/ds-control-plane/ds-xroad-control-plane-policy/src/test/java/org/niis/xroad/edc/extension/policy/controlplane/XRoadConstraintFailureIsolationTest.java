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

package org.niis.xroad.edc.extension.policy.controlplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;

import org.eclipse.edc.connector.controlplane.catalog.ContractDefinitionResolverImpl;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.controlplane.services.spi.contractagreement.ContractAgreementService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.connector.policy.monitor.PolicyMonitorConfiguration;
import org.eclipse.edc.connector.policy.monitor.manager.PolicyMonitor;
import org.eclipse.edc.connector.policy.monitor.manager.PolicyMonitorManagerImpl;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntry;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorStore;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.engine.PolicyEngineImpl;
import org.eclipse.edc.policy.engine.RuleBindingRegistryImpl;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.policy.controlplane.util.LocalGroupConstraintValue;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A failing X-Road constraint function must answer "no match" in every policy scope it is registered for,
 * so that one bad access-rights entry hides only itself from the catalog and never stops the policy monitor.
 * Runs against a real {@link PolicyEngineImpl} with the bindings {@link XRoadControlPlanePolicyExtension}
 * installs, and the real EDC catalog resolver and policy monitor watchdog.
 */
@ExtendWith(MockitoExtension.class)
class XRoadConstraintFailureIsolationTest {

    private static final String MEMBERSHIP_CREDENTIAL_TYPE = "XRoadMembershipCredential";
    private static final ClientId GROUP_OWNER = ClientId.Conf.create("DEV", "COM", "111", "Owner");
    private static final String BAD_GROUP = "bad-group";
    private static final String GOOD_GROUP = "good-group";
    private static final String PARTICIPANT_CONTEXT_ID = "provider-ctx";

    @Mock
    JsonLd jsonLd;
    @Mock
    TypeManager typeManager;
    @Mock
    GlobalConfProvider globalConfProvider;
    @Mock
    ServerConfProvider serverConfProvider;
    @Mock
    ServiceExtensionContext serviceExtensionContext;
    @Mock
    Monitor monitor;

    PolicyEngine policyEngine;

    @BeforeEach
    void setUp() throws Exception {
        var ruleBindingRegistry = new RuleBindingRegistryImpl();
        policyEngine = new PolicyEngineImpl(ruleBindingRegistry);

        var extension = new XRoadControlPlanePolicyExtension();
        setField(extension, "jsonLd", jsonLd);
        setField(extension, "ruleBindingRegistry", ruleBindingRegistry);
        setField(extension, "policyEngine", policyEngine);
        setField(extension, "contractNegotiationStore", null);
        setField(extension, "typeManager", typeManager);
        setField(extension, "globalConfProvider", globalConfProvider);
        setField(extension, "serverConfProvider", serverConfProvider);

        when(serviceExtensionContext.getMonitor()).thenReturn(monitor);
        extension.initialize(serviceExtensionContext);
    }

    @ParameterizedTest(name = "{0} in {1} scope")
    @MethodSource("functionsInScopes")
    void failingFunctionReturnsNoMatchAndLogsInEveryScope(String constraintKey, String scopeName,
                                                         Function<ParticipantAgent, PolicyContext> contextFactory) {
        var rightValue = failingRightValue(constraintKey);

        var result = policyEngine.evaluate(policy(constraintKey, rightValue), contextFactory.apply(memberAgent()));

        assertThat(result.failed()).isTrue();
        verify(monitor).warning(anyString(), any(Throwable.class));
    }

    @Test
    void catalogListsTheOtherServicesWhenOneAccessRightsEntryFails() {
        when(serverConfProvider.isLocalGroupMember(any(ClientId.class), any(LocalGroupId.class), any(ClientId.class)))
                .thenAnswer(invocation -> {
                    LocalGroupId group = invocation.getArgument(1);
                    if (BAD_GROUP.equals(group.getGroupCode())) {
                        throw new IllegalStateException("lookup failed");
                    }
                    return true;
                });
        var policyStore = mock(PolicyDefinitionStore.class);
        when(policyStore.findById(PARTICIPANT_CONTEXT_ID, BAD_GROUP))
                .thenReturn(policyDefinition(BAD_GROUP, localGroupPolicy(BAD_GROUP)));
        when(policyStore.findById(PARTICIPANT_CONTEXT_ID, GOOD_GROUP))
                .thenReturn(policyDefinition(GOOD_GROUP, localGroupPolicy(GOOD_GROUP)));
        var definitionStore = mock(ContractDefinitionStore.class);
        when(definitionStore.findAll(any())).thenReturn(Stream.of(
                contractDefinition("def-bad", BAD_GROUP), contractDefinition("def-good", GOOD_GROUP)));
        var participantContext = mock(ParticipantContext.class);
        when(participantContext.getId()).thenReturn(PARTICIPANT_CONTEXT_ID);
        var resolver = new ContractDefinitionResolverImpl(definitionStore, policyEngine, policyStore);

        var resolved = resolver.resolveFor(participantContext, memberAgent());

        assertThat(resolved.contractDefinitions()).extracting(ContractDefinition::getId).containsExactly("def-good");
    }

    @Test
    void policyMonitorProcessesTheNextEntryAfterAFailingOne() {
        when(serverConfProvider.isLocalGroupMember(any(ClientId.class), any(LocalGroupId.class), any(ClientId.class)))
                .thenAnswer(invocation -> {
                    LocalGroupId group = invocation.getArgument(1);
                    if (BAD_GROUP.equals(group.getGroupCode())) {
                        throw new IllegalStateException("lookup failed");
                    }
                    return true;
                });
        var transferProcessService = mock(TransferProcessService.class);
        var contractAgreementService = mock(ContractAgreementService.class);
        var store = mock(PolicyMonitorStore.class);
        var badEntry = PolicyMonitorEntry.Builder.newInstance().id("tp-bad").contractId("agreement-bad").build();
        var goodEntry = PolicyMonitorEntry.Builder.newInstance().id("tp-good").contractId("agreement-good").build();
        var transferProcess = mock(TransferProcess.class);
        when(transferProcess.getState()).thenReturn(TransferProcessStates.STARTED.code());
        when(transferProcessService.findById(anyString())).thenReturn(transferProcess);
        when(contractAgreementService.findById("agreement-bad")).thenReturn(agreement("agreement-bad", BAD_GROUP));
        when(contractAgreementService.findById("agreement-good")).thenReturn(agreement("agreement-good", GOOD_GROUP));
        when(transferProcessService.terminate(any(TerminateTransferCommand.class))).thenReturn(ServiceResult.success());
        when(store.save(any())).thenReturn(StoreResult.success());
        when(store.nextNotLeased(anyInt(), any(), any())).thenReturn(List.of(badEntry, goodEntry));
        var edcMonitor = mock(Monitor.class);
        when(edcMonitor.withPrefix(anyString())).thenReturn(edcMonitor);
        var policyMonitor = new PolicyMonitor(store, mock(), transferProcessService, contractAgreementService,
                policyEngine, edcMonitor, Clock.systemDefaultZone(), new NoopTransactionContext());
        var manager = new PolicyMonitorManagerImpl(policyMonitor, new PolicyMonitorConfiguration(20, Duration.ofHours(1)),
                ExecutorInstrumentation.noop(), edcMonitor, store, Clock.systemDefaultZone());

        manager.start();
        try {
            await().untilAsserted(() -> verify(store).save(goodEntry));
        } finally {
            manager.stop();
        }

        verify(transferProcessService).terminate(argThat(command -> "tp-bad".equals(command.getEntityId())));
        verify(transferProcessService, never()).terminate(argThat(command -> "tp-good".equals(command.getEntityId())));
    }

    static Stream<Arguments> functionsInScopes() {
        var scopes = Map.<String, Function<ParticipantAgent, PolicyContext>>of(
                "catalog", CatalogPolicyContext::new,
                "negotiation", ContractNegotiationPolicyContext::new,
                "transfer", agent -> new TransferProcessPolicyContext(agent, null, Instant.now()),
                "policy monitor", agent -> new PolicyMonitorContext(Instant.now(), agreement("agreement-1", GOOD_GROUP)));
        return Stream.of(
                        XRoadPolicyNamespace.XROAD_CLIENT_ID,
                        XRoadPolicyNamespace.XROAD_LOCAL_GROUP,
                        XRoadPolicyNamespace.XROAD_GLOBAL_GROUP)
                .flatMap(key -> scopes.entrySet().stream()
                        .map(scope -> Arguments.of(key, scope.getKey(), scope.getValue())));
    }

    private String failingRightValue(String constraintKey) {
        return switch (constraintKey) {
            case XRoadPolicyNamespace.XROAD_CLIENT_ID -> "not-an-encoded-client-id";
            case XRoadPolicyNamespace.XROAD_LOCAL_GROUP -> {
                when(serverConfProvider.isLocalGroupMember(any(ClientId.class), any(LocalGroupId.class), any(ClientId.class)))
                        .thenThrow(new IllegalStateException("lookup failed"));
                yield new LocalGroupConstraintValue(GROUP_OWNER, BAD_GROUP).encode();
            }
            case XRoadPolicyNamespace.XROAD_GLOBAL_GROUP -> {
                when(globalConfProvider.isMemberInGlobalGroup(any(ClientId.class), any(GlobalGroupId.class)))
                        .thenThrow(new IllegalStateException("lookup failed"));
                yield "DEV:bad-global-group";
            }
            default -> throw new IllegalArgumentException(constraintKey);
        };
    }

    private static ParticipantAgent memberAgent() {
        return new ParticipantAgent("test-id", Map.of(), Map.of(
                "xrd:xroadInstance", "DEV",
                "xrd:memberClass", "COM",
                "xrd:memberCode", "222"));
    }

    private static Policy localGroupPolicy(String groupCode) {
        return policy(XRoadPolicyNamespace.XROAD_LOCAL_GROUP, new LocalGroupConstraintValue(GROUP_OWNER, groupCode).encode());
    }

    private static Policy policy(String constraintKey, String rightValue) {
        var constraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(constraintKey))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(rightValue))
                .build();
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance().constraint(constraint).build())
                .build();
    }

    private static PolicyDefinition policyDefinition(String id, Policy policy) {
        return PolicyDefinition.Builder.newInstance().id(id).policy(policy).build();
    }

    private static ContractDefinition contractDefinition(String id, String accessPolicyId) {
        return ContractDefinition.Builder.newInstance()
                .id(id)
                .accessPolicyId(accessPolicyId)
                .contractPolicyId(accessPolicyId)
                .build();
    }

    private static ContractAgreement agreement(String id, String groupCode) {
        var subject = CredentialSubject.Builder.newInstance()
                .claim("xroadInstance", "DEV")
                .claim("memberClass", "COM")
                .claim("memberCode", "222")
                .build();
        var vc = VerifiableCredential.Builder.newInstance()
                .type(MEMBERSHIP_CREDENTIAL_TYPE)
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(Instant.now())
                .credentialSubject(subject)
                .build();
        return ContractAgreement.Builder.newInstance()
                .id(id)
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(localGroupPolicy(groupCode))
                .claims(Map.of("vc", List.of(vc)))
                .build();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
