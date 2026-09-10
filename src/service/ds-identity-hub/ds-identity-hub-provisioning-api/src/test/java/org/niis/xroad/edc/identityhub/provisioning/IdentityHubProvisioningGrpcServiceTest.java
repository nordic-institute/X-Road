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
package org.niis.xroad.edc.identityhub.provisioning;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.eclipse.edc.identityhub.spi.credential.request.model.HolderCredentialRequest;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextDidReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextDidResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialResp;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityHubProvisioningGrpcServiceTest {

    @Mock
    private IdentityHubParticipantContextService participantContextService;
    @Mock
    private CredentialRequestManager credentialRequestManager;
    @Mock
    private StreamObserver<CreateParticipantContextResp> createObserver;

    private IdentityHubProvisioningGrpcService service;

    @BeforeEach
    void setUp() {
        service = new IdentityHubProvisioningGrpcService(participantContextService, credentialRequestManager, new RpcResponseHandler());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createParticipantContextRejectsBlankParticipantContextId(String blank) {
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(blank)
                .setDid("did:web:example.com")
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onError(any(StatusRuntimeException.class));
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createParticipantContextRejectsBlankDid(String blank) {
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid(blank)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onError(any(StatusRuntimeException.class));
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @Test
    void createParticipantContextIgnoresConflictWhenReanchorFlagNotSet() {
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("exists"));
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setMemberId("TEST/GOV/1234")
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(CreateParticipantContextResp.newBuilder().setMemberIdReanchored(true).build());
        verify(createObserver).onCompleted();
        verify(participantContextService, never()).getParticipantContext(anyString());
        verify(participantContextService, never()).updateParticipant(anyString(), any());
    }

    @Test
    void createParticipantContextReanchorsMemberIdOnConflictWhenFlagSetAndMemberIdChanged() {
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("exists"));
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.success(contextWithMemberId("TEST/GOV/1234")));
        when(participantContextService.updateParticipant(anyString(), any())).thenReturn(ServiceResult.success());
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setMemberId("TEST/GOV/5678")
                .setReanchorMemberIdOnConflict(true)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(CreateParticipantContextResp.newBuilder().setMemberIdReanchored(true).build());
        verify(createObserver).onCompleted();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<IdentityHubParticipantContext>> mutation = ArgumentCaptor.forClass(Consumer.class);
        verify(participantContextService).updateParticipant(eq("ctx-1"), mutation.capture());
        var mutated = contextWithMemberId("TEST/GOV/1234");
        mutation.getValue().accept(mutated);
        assertThat(mutated.getProperties()).containsEntry("xroadMemberId", "TEST/GOV/5678");
    }

    @Test
    void createParticipantContextSkipsUpdateWhenMemberIdAlreadyMatches() {
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("exists"));
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.success(contextWithMemberId("TEST/GOV/1234")));
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setMemberId("TEST/GOV/1234")
                .setReanchorMemberIdOnConflict(true)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(CreateParticipantContextResp.newBuilder().setMemberIdReanchored(true).build());
        verify(createObserver).onCompleted();
        verify(participantContextService, never()).updateParticipant(anyString(), any());
    }

    @Test
    void createParticipantContextSwallowsReanchorReadFailureAndReportsNotReanchored() {
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("exists"));
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.unexpected("db unreachable"));
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setMemberId("TEST/GOV/5678")
                .setReanchorMemberIdOnConflict(true)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(CreateParticipantContextResp.newBuilder().setMemberIdReanchored(false).build());
        verify(createObserver).onCompleted();
        verify(createObserver, never()).onError(any());
        verify(participantContextService, never()).updateParticipant(anyString(), any());
    }

    @Test
    void createParticipantContextSwallowsReanchorUpdateFailureAndReportsNotReanchored() {
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("exists"));
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.success(contextWithMemberId("TEST/GOV/1234")));
        when(participantContextService.updateParticipant(anyString(), any())).thenReturn(ServiceResult.unexpected("db down"));
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setMemberId("TEST/GOV/5678")
                .setReanchorMemberIdOnConflict(true)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(CreateParticipantContextResp.newBuilder().setMemberIdReanchored(false).build());
        verify(createObserver).onCompleted();
        verify(createObserver, never()).onError(any());
    }

    @Test
    void createParticipantContextSwallowsThrownReanchorFailureAndReportsNotReanchored() {
        when(participantContextService.createParticipantContext(any())).thenReturn(ServiceResult.conflict("exists"));
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenThrow(new IllegalStateException("store unavailable"));
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setMemberId("TEST/GOV/5678")
                .setReanchorMemberIdOnConflict(true)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(CreateParticipantContextResp.newBuilder().setMemberIdReanchored(false).build());
        verify(createObserver).onCompleted();
        verify(createObserver, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialToleratesConflict() {
        when(credentialRequestManager.initiateRequest(anyString(), anyString(), anyString(), any()))
                .thenReturn(ServiceResult.conflict("already requested"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setIssuerDid("did:web:issuer.example.com")
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        service.requestCredential(request, observer);

        verify(observer).onNext(any());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialPropagatesNonConflictFailure() {
        when(credentialRequestManager.initiateRequest(anyString(), anyString(), anyString(), any()))
                .thenReturn(ServiceResult.unexpected("storage error"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setIssuerDid("did:web:issuer.example.com")
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        service.requestCredential(request, observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        verify(observer, never()).onCompleted();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCredentialRequestStateReturnsNotFoundWhenAbsent() {
        when(credentialRequestManager.findById("holder-pid-1")).thenReturn(null);

        StreamObserver<GetCredentialRequestStateResp> observer = mock(StreamObserver.class);
        var request = GetCredentialRequestStateReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setHolderPid("holder-pid-1")
                .build();

        service.getCredentialRequestState(request, observer);

        verify(observer).onNext(GetCredentialRequestStateResp.newBuilder().setFound(false).build());
        verify(observer).onCompleted();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCredentialRequestStateReturnsStatusWhenFound() {
        var holderRequest = mock(HolderCredentialRequest.class);
        when(holderRequest.stateAsString()).thenReturn("REQUESTED");
        when(credentialRequestManager.findById("holder-pid-1")).thenReturn(holderRequest);

        StreamObserver<GetCredentialRequestStateResp> observer = mock(StreamObserver.class);
        var request = GetCredentialRequestStateReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setHolderPid("holder-pid-1")
                .build();

        service.getCredentialRequestState(request, observer);

        verify(observer).onNext(GetCredentialRequestStateResp.newBuilder().setFound(true).setStatus("REQUESTED").build());
        verify(observer).onCompleted();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getParticipantContextDidReturnsDidWhenFound() {
        var context = mock(org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext.class);
        when(context.getDid()).thenReturn("did:web:ih.example.test%3A7183");
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.success(context));

        StreamObserver<GetParticipantContextDidResp> observer = mock(StreamObserver.class);
        var request = GetParticipantContextDidReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .build();

        service.getParticipantContextDid(request, observer);

        verify(observer).onNext(GetParticipantContextDidResp.newBuilder()
                .setDid("did:web:ih.example.test%3A7183")
                .build());
        verify(observer).onCompleted();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getParticipantContextDidReturnsNoDidOnlyForNotFound() {
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.notFound("ctx-1 not found"));

        StreamObserver<GetParticipantContextDidResp> observer = mock(StreamObserver.class);
        var request = GetParticipantContextDidReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .build();

        service.getParticipantContextDid(request, observer);

        verify(observer).onNext(GetParticipantContextDidResp.getDefaultInstance());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getParticipantContextDidSurfacesErrorForNonNotFoundFailure() {
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.unexpected("db unreachable"));

        StreamObserver<GetParticipantContextDidResp> observer = mock(StreamObserver.class);
        var request = GetParticipantContextDidReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .build();

        service.getParticipantContextDid(request, observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        verify(observer, never()).onCompleted();
    }

    private static IdentityHubParticipantContext contextWithMemberId(String memberId) {
        return IdentityHubParticipantContext.Builder.newInstance()
                .participantContextId("ctx-1")
                .did("did:web:example.com")
                .apiTokenAlias("ctx-1-apikey")
                .property("xroadMemberId", memberId)
                .build();
    }
}
