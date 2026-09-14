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
package org.niis.xroad.edc.issuer.provisioning;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.issuerservice.spi.credentials.CredentialStatusService;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionService;
import org.eclipse.edc.issuerservice.spi.issuance.credentialdefinition.CredentialDefinitionService;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialReq;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialResp;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerProvisioningGrpcServiceTest {

    @Mock
    private IdentityHubParticipantContextService participantContextService;
    @Mock
    private AttestationDefinitionService attestationDefinitionService;
    @Mock
    private CredentialDefinitionService credentialDefinitionService;
    @Mock
    private CredentialStatusService credentialStatusService;
    @Mock
    private StreamObserver<CreateParticipantContextResp> participantContextResponseObserver;
    @Mock
    private StreamObserver<RevokeCredentialResp> revokeResponseObserver;
    @Captor
    private ArgumentCaptor<QuerySpec> querySpecCaptor;

    private IssuerProvisioningGrpcService service;

    @BeforeEach
    void setUp() {
        service = new IssuerProvisioningGrpcService(participantContextService, attestationDefinitionService,
                credentialDefinitionService, credentialStatusService, new RpcResponseHandler());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createParticipantContextRejectsBlankParticipantContextId(String blank) {
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(blank)
                .setDid("did:web:example.com")
                .build();

        service.createParticipantContext(request, participantContextResponseObserver);

        verify(participantContextResponseObserver).onError(any(StatusRuntimeException.class));
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createParticipantContextRejectsBlankDid(String blank) {
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid(blank)
                .build();

        service.createParticipantContext(request, participantContextResponseObserver);

        verify(participantContextResponseObserver).onError(any(StatusRuntimeException.class));
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @Test
    void revokeCredentialHappyPath() {
        var resource = hostCredentialResource("cred-id-1", "issuer", "did:web:ss1.example.com", 1_000L);
        stubStore(resource);
        when(credentialStatusService.revokeCredential("cred-id-1"))
                .thenReturn(ServiceResult.success(null));

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(credentialStatusService).revokeCredential(eq("cred-id-1"));
        verify(revokeResponseObserver).onNext(RevokeCredentialResp.newBuilder().setRevokedCount(1).build());
        verify(revokeResponseObserver).onCompleted();
    }

    @Test
    void revokeCredentialBuildsPushedDownQuerySpec() {
        var resource = hostCredentialResource("cred-id-1", "issuer", "did:web:ss1.example.com", 1_000L);
        when(credentialStatusService.queryCredentials(querySpecCaptor.capture()))
                .thenReturn(ServiceResult.success(List.of(resource)));
        when(credentialStatusService.revokeCredential("cred-id-1"))
                .thenReturn(ServiceResult.success(null));

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        var querySpec = querySpecCaptor.getValue();
        assertThat(querySpec.getLimit()).isEqualTo(Integer.MAX_VALUE);
        assertThat(querySpec.getFilterExpression()).containsExactlyInAnyOrder(
                Criterion.criterion("participantContextId", "=", "issuer"),
                Criterion.criterion("holderId", "=", "did:web:ss1.example.com"),
                Criterion.criterion("timestamp", "<", 2_000L));
    }

    @Test
    void revokeCredentialRevokesAllMatches() {
        var first = hostCredentialResource("cred-id-1", "issuer", "did:web:ss1.example.com", 1_000L);
        var second = hostCredentialResource("cred-id-2", "issuer", "did:web:ss1.example.com", 1_500L);
        stubStore(first, second);
        when(credentialStatusService.revokeCredential(any())).thenReturn(ServiceResult.success(null));

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(credentialStatusService).revokeCredential(eq("cred-id-1"));
        verify(credentialStatusService).revokeCredential(eq("cred-id-2"));
        verify(revokeResponseObserver).onNext(RevokeCredentialResp.newBuilder().setRevokedCount(2).build());
    }

    @Test
    void revokeCredentialIsolatesByHolderAcrossServers() {
        var server1Credential = hostCredentialResource("cred-ss1", "issuer", "did:web:ss1.example.com", 1_000L);
        var server2Credential = hostCredentialResource("cred-ss2", "issuer", "did:web:ss2.example.com", 1_000L);
        stubStore(server1Credential, server2Credential);
        when(credentialStatusService.revokeCredential("cred-ss1")).thenReturn(ServiceResult.success(null));

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(credentialStatusService).revokeCredential(eq("cred-ss1"));
        verify(credentialStatusService, never()).revokeCredential(eq("cred-ss2"));
        verify(revokeResponseObserver).onNext(RevokeCredentialResp.newBuilder().setRevokedCount(1).build());
    }

    @Test
    void revokeCredentialAppliesIssuedBeforeCutoff() {
        var beforeCutoff = hostCredentialResource("cred-old", "issuer", "did:web:ss1.example.com", 1_000L);
        var atCutoff = hostCredentialResource("cred-new", "issuer", "did:web:ss1.example.com", 2_000L);
        stubStore(beforeCutoff, atCutoff);
        when(credentialStatusService.revokeCredential("cred-old")).thenReturn(ServiceResult.success(null));

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(credentialStatusService).revokeCredential(eq("cred-old"));
        verify(credentialStatusService, never()).revokeCredential(eq("cred-new"));
        verify(revokeResponseObserver).onNext(RevokeCredentialResp.newBuilder().setRevokedCount(1).build());
    }

    @Test
    void revokeCredentialSkipsMgmtHolder() {
        var mgmtResource = hostCredentialResource("mgmt-id", "issuer", "did:web:ss1.example.com:mgmt", 1_000L);
        var hostResource = hostCredentialResource("host-id", "issuer", "did:web:ss1.example.com", 1_000L);
        when(credentialStatusService.queryCredentials(any(QuerySpec.class)))
                .thenReturn(ServiceResult.success(List.of(mgmtResource, hostResource)));
        when(credentialStatusService.revokeCredential("host-id"))
                .thenReturn(ServiceResult.success(null));

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(credentialStatusService).revokeCredential(eq("host-id"));
        verify(credentialStatusService, never()).revokeCredential(eq("mgmt-id"));
    }

    @Test
    void revokeCredentialZeroMatchesIsIdempotentSuccess() {
        stubStore();

        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(credentialStatusService, never()).revokeCredential(any());
        verify(revokeResponseObserver).onNext(RevokeCredentialResp.newBuilder().setRevokedCount(0).build());
        verify(revokeResponseObserver).onCompleted();
        verify(revokeResponseObserver, never()).onError(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void revokeCredentialRejectsBlankParticipantContextId(String blank) {
        service.revokeCredential(revokeReq(blank, "did:web:ss1.example.com", 2_000L), revokeResponseObserver);

        verify(revokeResponseObserver).onError(any(StatusRuntimeException.class));
        verify(credentialStatusService, never()).queryCredentials(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void revokeCredentialRejectsBlankHolderDid(String blank) {
        service.revokeCredential(revokeReq("issuer", blank, 2_000L), revokeResponseObserver);

        verify(revokeResponseObserver).onError(any(StatusRuntimeException.class));
        verify(credentialStatusService, never()).queryCredentials(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void revokeCredentialRejectsNonPositiveIssuedBefore(long nonPositive) {
        service.revokeCredential(revokeReq("issuer", "did:web:ss1.example.com", nonPositive), revokeResponseObserver);

        verify(revokeResponseObserver).onError(any(StatusRuntimeException.class));
        verify(credentialStatusService, never()).queryCredentials(any());
    }

    private void stubStore(VerifiableCredentialResource... seed) {
        when(credentialStatusService.queryCredentials(any(QuerySpec.class))).thenAnswer(invocation -> {
            QuerySpec spec = invocation.getArgument(0);
            var matches = Arrays.stream(seed).filter(resource -> matchesAll(resource, spec)).toList();
            return ServiceResult.success(matches);
        });
    }

    private boolean matchesAll(VerifiableCredentialResource resource, QuerySpec spec) {
        return spec.getFilterExpression().stream().allMatch(criterion -> matches(resource, criterion));
    }

    private boolean matches(VerifiableCredentialResource resource, Criterion criterion) {
        var field = (String) criterion.getOperandLeft();
        var value = criterion.getOperandRight();
        return switch (field) {
            case "participantContextId" -> resource.getParticipantContextId().equals(value);
            case "holderId" -> resource.getHolderId().equals(value);
            case "timestamp" -> resource.getTimestamp() < (Long) value;
            default -> throw new IllegalStateException("Unexpected filter field: " + field);
        };
    }

    private RevokeCredentialReq revokeReq(String ctx, String holderDid, long issuedBefore) {
        return RevokeCredentialReq.newBuilder()
                .setParticipantContextId(ctx)
                .setHolderDid(holderDid)
                .setIssuedBefore(issuedBefore)
                .build();
    }

    private VerifiableCredentialResource hostCredentialResource(String id, String participantContextId,
                                                                String subjectDid, long timestamp) {
        var subject = CredentialSubject.Builder.newInstance()
                .id(subjectDid)
                .claim("subjectDid", subjectDid)
                .build();
        var vc = VerifiableCredential.Builder.newInstance()
                .type("MembershipCredential")
                .issuer(new Issuer("did:web:issuer.example.com"))
                .issuanceDate(Instant.now())
                .credentialSubject(subject)
                .build();
        var container = new VerifiableCredentialContainer("raw", CredentialFormat.VC1_0_LD, vc);
        return VerifiableCredentialResource.Builder.newInstance()
                .id(id)
                .participantContextId(participantContextId)
                .issuerId("did:web:issuer.example.com")
                .holderId(subjectDid)
                .timestamp(timestamp)
                .credential(container)
                .build();
    }
}
