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
package org.niis.xroad.edc.extension.agreementgrant.grpc;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.agreementgrant.proto.AgreementGrantServiceGrpc;
import org.niis.xroad.edc.agreementgrant.proto.NoGrantReason;
import org.niis.xroad.edc.agreementgrant.proto.ResolveAgreementGrantRequest;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace;
import org.niis.xroad.serverconf.model.BaseEndpoint;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementGrantGrpcServiceTest {

    private static final String ODRL_USE_ACTION = "http://www.w3.org/ns/odrl/2/use";

    private static final ClientId.Conf CONSUMER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ClientId.Conf CONSUMER_MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ServiceId.Conf SERVICE_WITH_VERSION =
            ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "getRandom", "v1");
    private static final ServiceId.Conf SERVICE_WITHOUT_VERSION =
            ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "getRandom");

    @Mock
    ContractNegotiationStore contractNegotiationStore;

    private Server server;
    private ManagedChannel channel;
    private AgreementGrantServiceGrpc.AgreementGrantServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        var grpcService = new AgreementGrantGrpcService(contractNegotiationStore, new RpcResponseHandler());
        server = ServerBuilder.forPort(0)
                .addService(grpcService)
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        stub = AgreementGrantServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void grantForSubsystemSubjectWithTwoPathConstraintsPreservesOrder() {
        var policy = policyWithPaths(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER.asEncodedId(),
                List.of("GET /getRandom", "POST /submitRandom"));
        stubAgreement("agreement-1", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-1"));

        assertThat(response.hasGrant()).isTrue();
        var grant = response.getGrant();
        assertThat(ClientIdMapper.fromDto(grant.getClientId())).isEqualTo(CONSUMER);
        assertThat(ServiceIdMapper.fromDto(grant.getServiceId())).isEqualTo(SERVICE_WITH_VERSION);
        assertThat(grant.getScopesList()).hasSize(2);
        assertThat(grant.getScopes(0).getMethod()).isEqualTo("GET");
        assertThat(grant.getScopes(0).getPath()).isEqualTo("/getRandom");
        assertThat(grant.getScopes(1).getMethod()).isEqualTo("POST");
        assertThat(grant.getScopes(1).getPath()).isEqualTo("/submitRandom");
    }

    @Test
    void grantWithNoPathConstraintYieldsBaseEndpointScope() {
        var policy = policyWithSubjectOnly(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER.asEncodedId());
        stubAgreement("agreement-2", policy, SERVICE_WITHOUT_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-2"));

        assertThat(response.hasGrant()).isTrue();
        var grant = response.getGrant();
        assertThat(ServiceIdMapper.fromDto(grant.getServiceId())).isEqualTo(SERVICE_WITHOUT_VERSION);
        assertThat(grant.getScopesList()).hasSize(1);
        assertThat(grant.getScopes(0).getMethod()).isEqualTo(BaseEndpoint.ANY_METHOD);
        assertThat(grant.getScopes(0).getPath()).isEqualTo(BaseEndpoint.ANY_PATH);
    }

    @Test
    void memberSubjectYieldsNoGrantSubjectNotASubsystem() {
        var policy = policyWithSubjectOnly(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER_MEMBER.asEncodedId());
        stubAgreement("agreement-3", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-3"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM);
    }

    @Test
    void localGroupSubjectYieldsNoGrantSubjectNotASubsystem() {
        var localGroup = LocalGroupId.Conf.create("groupCode");
        var policy = policyWithSubjectOnly(XRoadPolicyNamespace.XROAD_LOCAL_GROUP, localGroup.asEncodedId());
        stubAgreement("agreement-4", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-4"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM);
    }

    @Test
    void globalGroupSubjectYieldsNoGrantSubjectNotASubsystem() {
        var globalGroup = GlobalGroupId.Conf.create("DEV", "groupCode");
        var policy = policyWithSubjectOnly(XRoadPolicyNamespace.XROAD_GLOBAL_GROUP, globalGroup.asEncodedId());
        stubAgreement("agreement-5", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-5"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM);
    }

    @Test
    void unknownAgreementYieldsNoGrantAgreementNotFound() {
        when(contractNegotiationStore.findContractAgreement(eq("unknown"))).thenReturn(null);

        var response = stub.resolveAgreementGrant(request("unknown"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.AGREEMENT_NOT_FOUND);
    }

    @Test
    void malformedPathConstraintYieldsNoGrantPolicyNotUnderstood() {
        var policy = policyWithPaths(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER.asEncodedId(),
                List.of("GETgetRandom"));
        stubAgreement("agreement-6", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-6"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.POLICY_NOT_UNDERSTOOD);
    }

    @Test
    void malformedSubjectClientIdYieldsNoGrantPolicyNotUnderstood() {
        var policy = policyWithSubjectOnly(XRoadPolicyNamespace.XROAD_CLIENT_ID, "not-an-encoded-client-id");
        stubAgreement("agreement-8", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-8"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.POLICY_NOT_UNDERSTOOD);
    }

    @Test
    void malformedAssetIdYieldsNoGrantPolicyNotUnderstood() {
        var policy = policyWithSubjectOnly(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER.asEncodedId());
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-7")
                .providerId("provider")
                .consumerId("consumer")
                .assetId("not-a-valid-asset-id")
                .policy(policy)
                .build();
        when(contractNegotiationStore.findContractAgreement(eq("agreement-7"))).thenReturn(agreement);

        var response = stub.resolveAgreementGrant(request("agreement-7"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.POLICY_NOT_UNDERSTOOD);
    }

    @Test
    void subjectConstraintWithNonEqualityOperatorYieldsNoGrantPolicyNotUnderstood() {
        var policy = policy(subjectConstraint(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER.asEncodedId(), Operator.NEQ));
        stubAgreement("agreement-neq-subject", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-neq-subject"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.POLICY_NOT_UNDERSTOOD);
    }

    @Test
    void pathConstraintWithNonEqualityOperatorYieldsNoGrantPolicyNotUnderstood() {
        var subject = subjectConstraint(XRoadPolicyNamespace.XROAD_CLIENT_ID, CONSUMER.asEncodedId());
        var path = pathConstraint("GET /random/**", Operator.IN);
        var policy = policy(AndConstraint.Builder.newInstance().constraints(List.of(subject, path)).build());
        stubAgreement("agreement-in-path", policy, SERVICE_WITH_VERSION);

        var response = stub.resolveAgreementGrant(request("agreement-in-path"));

        assertThat(response.hasNoGrant()).isTrue();
        assertThat(response.getNoGrant().getReason()).isEqualTo(NoGrantReason.POLICY_NOT_UNDERSTOOD);
    }

    @Test
    void storeThrowingBecomesGrpcInternalError() {
        when(contractNegotiationStore.findContractAgreement(eq("boom")))
                .thenThrow(new RuntimeException("store unavailable"));

        assertThatThrownBy(() -> stub.resolveAgreementGrant(request("boom")))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    private void stubAgreement(String agreementId, Policy policy, ServiceId serviceId) {
        var agreement = ContractAgreement.Builder.newInstance()
                .id(agreementId)
                .providerId("provider")
                .consumerId("consumer")
                .assetId(serviceId.asEncodedId())
                .policy(policy)
                .build();
        when(contractNegotiationStore.findContractAgreement(eq(agreementId))).thenReturn(agreement);
    }

    private static ResolveAgreementGrantRequest request(String agreementId) {
        return ResolveAgreementGrantRequest.newBuilder().setAgreementId(agreementId).build();
    }

    /**
     * Mirrors the single-permission, subject-only tree {@code PolicyMapper} writes when a service has no
     * explicit endpoint ACL entries.
     */
    private static Policy policyWithSubjectOnly(String subjectKey, String subjectValue) {
        return policy(subjectConstraint(subjectKey, subjectValue));
    }

    /**
     * Mirrors the {@code AND(subjectConstraint, pathPiece)} tree {@code PolicyMapper} writes for a service with
     * endpoint ACL entries: a lone path constraint when there is one, an {@code OrConstraint} of them otherwise.
     */
    private static Policy policyWithPaths(String subjectKey, String subjectValue, List<String> methodAndPaths) {
        var subject = subjectConstraint(subjectKey, subjectValue);
        List<Constraint> pathConstraints = methodAndPaths.stream()
                .<Constraint>map(AgreementGrantGrpcServiceTest::pathConstraint)
                .toList();
        Constraint pathPiece = pathConstraints.size() == 1
                ? pathConstraints.getFirst()
                : OrConstraint.Builder.newInstance().constraints(pathConstraints).build();
        return policy(AndConstraint.Builder.newInstance().constraints(List.of(subject, pathPiece)).build());
    }

    private static AtomicConstraint subjectConstraint(String key, String value) {
        return subjectConstraint(key, value, Operator.EQ);
    }

    private static AtomicConstraint subjectConstraint(String key, String value, Operator operator) {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(key))
                .operator(operator)
                .rightExpression(new LiteralExpression(value))
                .build();
    }

    private static AtomicConstraint pathConstraint(String methodAndPath) {
        return pathConstraint(methodAndPath, Operator.EQ);
    }

    private static AtomicConstraint pathConstraint(String methodAndPath, Operator operator) {
        return AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XRoadPolicyNamespace.XROAD_DATAPATH))
                .operator(operator)
                .rightExpression(new LiteralExpression(methodAndPath))
                .build();
    }

    private static Policy policy(Constraint rootConstraint) {
        var permission = Permission.Builder.newInstance()
                .action(Action.Builder.newInstance().type(ODRL_USE_ACTION).build())
                .constraint(rootConstraint)
                .build();
        return Policy.Builder.newInstance()
                .type(PolicyType.SET)
                .permission(permission)
                .build();
    }
}
