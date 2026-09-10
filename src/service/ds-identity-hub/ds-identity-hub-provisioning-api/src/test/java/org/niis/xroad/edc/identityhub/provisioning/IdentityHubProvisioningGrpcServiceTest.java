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
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.identityhub.spi.credential.request.model.HolderCredentialRequest;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.AfterEach;
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
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextDidReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextDidResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialResp;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityHubProvisioningGrpcServiceTest {

    private static final String REACHABLE_ISSUER_DID = "did:web:issuer.example.com";
    private static final String UNREACHABLE_ISSUER_DID = "did:web:unreachable.example.com";
    private static final String OTHER_UNREACHABLE_ISSUER_DID = "did:web:also-unreachable.example.com";

    @Mock
    private IdentityHubParticipantContextService participantContextService;
    @Mock
    private CredentialRequestManager credentialRequestManager;
    @Mock
    private DidResolverRegistry didResolverRegistry;
    @Mock
    private StreamObserver<CreateParticipantContextResp> createObserver;

    private IdentityHubProvisioningGrpcService service;

    @BeforeEach
    void setUp() {
        service = new IdentityHubProvisioningGrpcService(
                participantContextService, credentialRequestManager, didResolverRegistry, new RpcResponseHandler());
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    private void givenReachableIssuer(String did) {
        var document = DidDocument.Builder.newInstance()
                .id(did)
                .service(List.of(new Service("issuer-service", CredentialRequestManager.ISSUER_SERVICE_ENDPOINT_TYPE,
                        "https://issuer.example.com")))
                .build();
        when(didResolverRegistry.resolve(did)).thenReturn(Result.success(document));
    }

    private void givenUnreachableIssuer(String did) {
        when(didResolverRegistry.resolve(did)).thenReturn(Result.failure("DID document not reachable"));
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
    void requestCredentialUsesSoleCandidateWithoutProbing() {
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.success("issuer-pid-1"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(REACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        service.requestCredential(request, observer);

        verify(didResolverRegistry, never()).resolve(any());
        verify(credentialRequestManager).initiateRequest(eq("ctx-1"), eq(REACHABLE_ISSUER_DID), eq("holder-pid-1"), any());
        verify(observer).onNext(any());
        verify(observer, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialToleratesConflict() {
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.conflict("already requested"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(REACHABLE_ISSUER_DID)
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
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.unexpected("storage error"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(REACHABLE_ISSUER_DID)
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
    void requestCredentialFailsOverToTheNextIssuerDidWhenTheFirstIsUnreachable() {
        givenUnreachableIssuer(UNREACHABLE_ISSUER_DID);
        givenReachableIssuer(REACHABLE_ISSUER_DID);
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.success("issuer-pid-1"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(REACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        service.requestCredential(request, observer);

        verify(credentialRequestManager).initiateRequest(eq("ctx-1"), eq(REACHABLE_ISSUER_DID), eq("holder-pid-1"), any());
        verify(observer).onNext(any());
        verify(observer, never()).onError(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialReturnsThePromptlyRespondingCandidateWhileTheOtherIsStillProbing() {
        var releaseLoser = new CountDownLatch(1);
        when(didResolverRegistry.resolve(UNREACHABLE_ISSUER_DID)).thenAnswer(invocation -> {
            releaseLoser.await();
            return Result.failure("resolved only after the winner already returned");
        });
        givenReachableIssuer(REACHABLE_ISSUER_DID);
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.success("issuer-pid-1"));

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(REACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        var start = System.nanoTime();
        service.requestCredential(request, observer);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
        verify(credentialRequestManager).initiateRequest(eq("ctx-1"), eq(REACHABLE_ISSUER_DID), eq("holder-pid-1"), any());
        verify(observer).onNext(any());
        verify(observer, never()).onError(any());

        releaseLoser.countDown();
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialTimesOutAfterTheOverallBoundWhenAllCandidatesHang() {
        var releaseFirst = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);
        when(didResolverRegistry.resolve(UNREACHABLE_ISSUER_DID)).thenAnswer(invocation -> {
            releaseFirst.await();
            return Result.failure("resolved only after the deadline");
        });
        when(didResolverRegistry.resolve(OTHER_UNREACHABLE_ISSUER_DID)).thenAnswer(invocation -> {
            releaseSecond.await();
            return Result.failure("resolved only after the deadline");
        });

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(OTHER_UNREACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        var start = System.nanoTime();
        service.requestCredential(request, observer);
        var elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(9));
        verify(observer).onError(any(StatusRuntimeException.class));
        verify(credentialRequestManager, never()).initiateRequest(any(), any(), any(), any());

        // the hung probes are still running in the background (never cancelled); release them now so they
        // conclude and record their outcome, same as a slow real resolver eventually timing out on its own.
        releaseFirst.countDown();
        releaseSecond.countDown();

        givenReachableIssuer(REACHABLE_ISSUER_DID);
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.success("issuer-pid-2"));

        var followUpRequest = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(OTHER_UNREACHABLE_ISSUER_DID)
                .addIssuerDids(REACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-2")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();
        StreamObserver<RequestCredentialResp> followUpObserver = mock(StreamObserver.class);

        // the release above only unblocks the hung probes; recording their outcome still races the assertions
        // below, so retry the follow-up call (with a clean invocation ledger each time) until both candidates
        // have settled into backoff and stopped being re-probed.
        await().untilAsserted(() -> {
            clearInvocations(didResolverRegistry, followUpObserver);
            service.requestCredential(followUpRequest, followUpObserver);
            verify(didResolverRegistry, never()).resolve(UNREACHABLE_ISSUER_DID);
            verify(didResolverRegistry, never()).resolve(OTHER_UNREACHABLE_ISSUER_DID);
            verify(didResolverRegistry).resolve(REACHABLE_ISSUER_DID);
            verify(followUpObserver).onNext(any());
            verify(followUpObserver, never()).onError(any());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialMarksALoserUnreachableEvenWhenItsFailureLandsAfterTheWinnerAlreadyReturned() {
        var releaseLoser = new CountDownLatch(1);
        when(didResolverRegistry.resolve(UNREACHABLE_ISSUER_DID)).thenAnswer(invocation -> {
            releaseLoser.await();
            return Result.failure("resolved only after the winner already returned");
        });
        givenReachableIssuer(REACHABLE_ISSUER_DID);
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.success("issuer-pid-1"));

        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(REACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        StreamObserver<RequestCredentialResp> firstObserver = mock(StreamObserver.class);
        service.requestCredential(request, firstObserver);
        verify(firstObserver).onNext(any());
        verify(firstObserver, never()).onError(any());

        // the loser only reports its failure now, well after the winner already made call 1 return.
        releaseLoser.countDown();

        var followUpRequest = request.toBuilder().setHolderPid("holder-pid-2").build();
        StreamObserver<RequestCredentialResp> secondObserver = mock(StreamObserver.class);

        // recording the late failure races this call, so retry (with a clean invocation ledger each time)
        // until the loser has settled into backoff and a follow-up call stops re-probing it.
        await().untilAsserted(() -> {
            clearInvocations(didResolverRegistry, secondObserver);
            service.requestCredential(followUpRequest, secondObserver);
            verify(didResolverRegistry, never()).resolve(UNREACHABLE_ISSUER_DID);
            verify(didResolverRegistry).resolve(REACHABLE_ISSUER_DID);
            verify(secondObserver).onNext(any());
            verify(secondObserver, never()).onError(any());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialSkipsUnreachableCandidateDuringItsBackoffWindow() {
        givenUnreachableIssuer(UNREACHABLE_ISSUER_DID);
        givenReachableIssuer(REACHABLE_ISSUER_DID);
        when(credentialRequestManager.initiateRequest(anyString(), eq(REACHABLE_ISSUER_DID), anyString(), any()))
                .thenReturn(ServiceResult.success("issuer-pid-1"));

        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(REACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        StreamObserver<RequestCredentialResp> firstObserver = mock(StreamObserver.class);
        service.requestCredential(request, firstObserver);
        verify(firstObserver).onNext(any());

        // call 1 only waits for the winner; the loser's own outcome can still be landing after it returns.
        await().untilAsserted(() -> verify(didResolverRegistry, times(1)).resolve(UNREACHABLE_ISSUER_DID));

        StreamObserver<RequestCredentialResp> secondObserver = mock(StreamObserver.class);
        await().untilAsserted(() -> {
            clearInvocations(didResolverRegistry, secondObserver);
            service.requestCredential(request, secondObserver);
            verify(didResolverRegistry, never()).resolve(UNREACHABLE_ISSUER_DID);
            verify(didResolverRegistry).resolve(REACHABLE_ISSUER_DID);
            verify(secondObserver).onNext(any());
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialFailsWithoutCreatingAnyRequestWhenNoCandidateIsReachable() {
        givenUnreachableIssuer(UNREACHABLE_ISSUER_DID);
        givenUnreachableIssuer(OTHER_UNREACHABLE_ISSUER_DID);

        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(OTHER_UNREACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        service.requestCredential(request, observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        verify(credentialRequestManager, never()).initiateRequest(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialRetriesAllCandidatesWhenEveryOneIsWithinItsBackoffWindow() {
        givenUnreachableIssuer(UNREACHABLE_ISSUER_DID);
        givenUnreachableIssuer(OTHER_UNREACHABLE_ISSUER_DID);

        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .addIssuerDids(UNREACHABLE_ISSUER_DID)
                .addIssuerDids(OTHER_UNREACHABLE_ISSUER_DID)
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        StreamObserver<RequestCredentialResp> firstObserver = mock(StreamObserver.class);
        service.requestCredential(request, firstObserver);
        StreamObserver<RequestCredentialResp> secondObserver = mock(StreamObserver.class);
        service.requestCredential(request, secondObserver);

        verify(didResolverRegistry, times(2)).resolve(UNREACHABLE_ISSUER_DID);
        verify(didResolverRegistry, times(2)).resolve(OTHER_UNREACHABLE_ISSUER_DID);
        verify(firstObserver).onError(any(StatusRuntimeException.class));
        verify(secondObserver).onError(any(StatusRuntimeException.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestCredentialFailsWithoutCreatingAnyRequestWhenTheCandidateSetIsEmpty() {
        StreamObserver<RequestCredentialResp> observer = mock(StreamObserver.class);
        var request = RequestCredentialReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setHolderPid("holder-pid-1")
                .setCredentialDefinitionId("def-1")
                .setCredentialType("MembershipCredential")
                .setFormat("JWT_VC")
                .build();

        service.requestCredential(request, observer);

        verify(observer).onError(any(StatusRuntimeException.class));
        verify(credentialRequestManager, never()).initiateRequest(any(), any(), any(), any());
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
}
