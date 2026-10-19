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
import ee.ria.xroad.common.identifier.LocalGroupId;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.PolicyEngineImpl;
import org.eclipse.edc.policy.engine.RuleBindingRegistryImpl;
import org.eclipse.edc.policy.engine.ScopeFilter;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.engine.validation.RuleValidator;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the {@code POLICY_MONITOR_SCOPE} bindings {@link XRoadControlPlanePolicyExtension} installs,
 * against a real {@link PolicyEngineImpl}/{@link RuleBindingRegistryImpl}.
 *
 * <p>{@link PolicyMonitorContext} carries only the stored {@link ContractAgreement}, not a live
 * request's participant agent, so the member identity has to come from the agreement's claims —
 * the same {@code XRoadMembershipCredential} the consumer presented at negotiation time, captured
 * verbatim onto the agreement. The termination step mirrors (without reusing, since it lives in an
 * EDC-internal package) the "if failed, terminate" branch of {@code PolicyMonitor#monitor}.
 */
@ExtendWith(MockitoExtension.class)
class XRoadControlPlanePolicyMonitorBindingTest {

    private static final String MEMBERSHIP_CREDENTIAL_TYPE = "XRoadMembershipCredential";

    @Mock
    JsonLd jsonLd;
    @Mock
    ContractNegotiationStore contractNegotiationStore;
    @Mock
    TypeManager typeManager;
    @Mock
    GlobalConfProvider globalConfProvider;
    @Mock
    ServerConfProvider serverConfProvider;
    @Mock
    ServiceExtensionContext serviceExtensionContext;
    @Mock
    TransferProcessService transferProcessService;

    PolicyEngine policyEngine;

    @BeforeEach
    void setUp() throws Exception {
        RuleBindingRegistry ruleBindingRegistry = new RuleBindingRegistryImpl();
        policyEngine = new PolicyEngineImpl(new ScopeFilter(ruleBindingRegistry), new RuleValidator(ruleBindingRegistry));

        var extension = new XRoadControlPlanePolicyExtension();
        setField(extension, "jsonLd", jsonLd);
        setField(extension, "ruleBindingRegistry", ruleBindingRegistry);
        setField(extension, "policyEngine", policyEngine);
        setField(extension, "contractNegotiationStore", contractNegotiationStore);
        setField(extension, "typeManager", typeManager);
        setField(extension, "globalConfProvider", globalConfProvider);
        setField(extension, "serverConfProvider", serverConfProvider);

        when(serviceExtensionContext.getMonitor()).thenReturn(new ConsoleMonitor());
        extension.initialize(serviceExtensionContext);
    }

    @Test
    void transferIsTerminatedWhenLocalGroupConstraintNoLongerHolds() {
        when(serverConfProvider.isSubjectInLocalGroup(any(ClientId.class), any(LocalGroupId.class))).thenReturn(false);

        var result = policyEngine.evaluate(localGroupPolicy(), monitorContextWithMember("DEV", "COM", "222"));
        terminateIfFailed(result, "tp-1");

        assertThat(result.failed()).isTrue();
        verify(transferProcessService).terminate(any(TerminateTransferCommand.class));
    }

    @Test
    void transferIsNotTerminatedWhenLocalGroupConstraintStillHolds() {
        when(serverConfProvider.isSubjectInLocalGroup(any(ClientId.class), any(LocalGroupId.class))).thenReturn(true);

        var result = policyEngine.evaluate(localGroupPolicy(), monitorContextWithMember("DEV", "COM", "222"));
        terminateIfFailed(result, "tp-2");

        assertThat(result.succeeded()).isTrue();
        verify(transferProcessService, never()).terminate(any());
    }

    private void terminateIfFailed(Result<Void> evaluation, String transferProcessId) {
        if (evaluation.failed()) {
            transferProcessService.terminate(new TerminateTransferCommand(transferProcessId, evaluation.getFailureDetail()));
        }
    }

    private static Policy localGroupPolicy() {
        var constraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XRoadPolicyNamespace.XROAD_LOCAL_GROUP))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression("DEV:security-server-owners"))
                .build();
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance().constraint(constraint).build())
                .build();
    }

    private static PolicyMonitorContext monitorContextWithMember(String xroadInstance, String memberClass, String memberCode) {
        var subject = CredentialSubject.Builder.newInstance()
                .claim("xroadInstance", xroadInstance)
                .claim("memberClass", memberClass)
                .claim("memberCode", memberCode)
                .build();
        var vc = VerifiableCredential.Builder.newInstance()
                .type(MEMBERSHIP_CREDENTIAL_TYPE)
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(Instant.now())
                .credentialSubject(subject)
                .build();
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .claims(Map.of("vc", List.of(vc)))
                .build();
        return new PolicyMonitorContext(Instant.now(), agreement);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
