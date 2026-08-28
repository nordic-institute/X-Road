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
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialResp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    void getParticipantContextExistsReturnsTrueAndDidWhenFound() {
        var context = mock(org.eclipse.edc.identityhub.spi.participantcontext.model.IdentityHubParticipantContext.class);
        when(context.getDid()).thenReturn("did:web:ih.example.test%3A7183");
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.success(context));

        StreamObserver<GetParticipantContextExistsResp> observer = mock(StreamObserver.class);
        var request = GetParticipantContextExistsReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .build();

        service.getParticipantContextExists(request, observer);

        verify(observer).onNext(GetParticipantContextExistsResp.newBuilder()
                .setExists(true)
                .setDid("did:web:ih.example.test%3A7183")
                .build());
        verify(observer).onCompleted();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getParticipantContextExistsReturnsFalseOnlyForNotFound() {
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.notFound("ctx-1 not found"));

        StreamObserver<GetParticipantContextExistsResp> observer = mock(StreamObserver.class);
        var request = GetParticipantContextExistsReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .build();

        service.getParticipantContextExists(request, observer);

        verify(observer).onNext(GetParticipantContextExistsResp.newBuilder().setExists(false).build());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getParticipantContextExistsSurfacesErrorForNonNotFoundFailure() {
        when(participantContextService.getParticipantContext("ctx-1"))
                .thenReturn(ServiceResult.unexpected("db unreachable"));

        StreamObserver<GetParticipantContextExistsResp> observer = mock(StreamObserver.class);
        var request = GetParticipantContextExistsReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .build();

        service.getParticipantContextExists(request, observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        verify(observer, never()).onCompleted();
    }
}
