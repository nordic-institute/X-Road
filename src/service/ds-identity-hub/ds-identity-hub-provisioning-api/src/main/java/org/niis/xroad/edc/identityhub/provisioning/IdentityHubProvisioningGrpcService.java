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

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.identityhub.spi.credential.request.model.RequestedCredential;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextDidReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextDidResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.IdentityHubProvisioningServiceGrpc;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialResp;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.failure;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.requireSuccessOrConflict;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.validateManifestFields;

/**
 * gRPC service that provisions IdentityHub participant contexts and holder credential requests by
 * delegating to the EDC IdentityHub services directly (no REST management API).
 */
@RequiredArgsConstructor
class IdentityHubProvisioningGrpcService extends IdentityHubProvisioningServiceGrpc.IdentityHubProvisioningServiceImplBase
        implements AutoCloseable {

    private static final String XROAD_MEMBER_ID_PROPERTY = "xroadMemberId";
    private static final String CREDENTIAL_SERVICE_TYPE = "CredentialService";
    private static final String CREDENTIAL_SERVICE_ID_SUFFIX = "-credential-service";
    private static final String KEY_ALGORITHM_PARAM = "algorithm";
    private static final String KEY_ALGORITHM = "EdDSA";
    private static final Duration RESOLVE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration UNREACHABLE_BACKOFF = Duration.ofSeconds(30);

    private final IdentityHubParticipantContextService participantContextService;
    private final CredentialRequestManager credentialRequestManager;
    private final DidResolverRegistry didResolverRegistry;
    private final RpcResponseHandler responseHandler;

    private final ExecutorService resolveExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Instant> unreachableUntil = new ConcurrentHashMap<>();

    @Override
    public void close() {
        resolveExecutor.shutdownNow();
    }

    @Override
    public void createParticipantContext(CreateParticipantContextReq request,
                                         StreamObserver<CreateParticipantContextResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> createParticipantContextInternal(request));
    }

    @Override
    public void requestCredential(RequestCredentialReq request, StreamObserver<RequestCredentialResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> requestCredentialInternal(request));
    }

    @Override
    public void getCredentialRequestState(GetCredentialRequestStateReq request,
                                          StreamObserver<GetCredentialRequestStateResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> getCredentialRequestStateInternal(request));
    }

    @Override
    public void getParticipantContextDid(GetParticipantContextDidReq request,
                                         StreamObserver<GetParticipantContextDidResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> getParticipantContextDidInternal(request));
    }

    private CreateParticipantContextResp createParticipantContextInternal(CreateParticipantContextReq request) {
        validateManifestFields(request.getParticipantContextId(), request.getDid());
        var manifest = ParticipantManifest.Builder.newInstance()
                .participantContextId(request.getParticipantContextId())
                .did(request.getDid())
                .active(true)
                .property(XROAD_MEMBER_ID_PROPERTY, request.getMemberId())
                .serviceEndpoint(new Service(
                        request.getParticipantContextId() + CREDENTIAL_SERVICE_ID_SUFFIX,
                        CREDENTIAL_SERVICE_TYPE,
                        request.getCredentialServiceUrl()))
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId(request.getKeyId())
                        .privateKeyAlias(request.getPrivateKeyAlias())
                        .keyGeneratorParams(Map.of(KEY_ALGORITHM_PARAM, KEY_ALGORITHM))
                        .build())
                .build();

        var result = participantContextService.createParticipantContext(manifest);
        requireSuccessOrConflict(result, DSP_PARTICIPANT_CONTEXT_FAILED, request.getParticipantContextId());
        return CreateParticipantContextResp.getDefaultInstance();
    }

    private RequestCredentialResp requestCredentialInternal(RequestCredentialReq request) {
        var requested = List.of(new RequestedCredential(
                request.getCredentialDefinitionId(), request.getCredentialType(), request.getFormat()));

        var issuerDid = selectReachableIssuer(request.getIssuerDidsList())
                .orElseThrow(() -> failure(DSP_PROVISIONING_FAILED, request.getHolderPid(),
                        "none of the %d candidate issuer DID(s) resolved to a reachable IssuerService endpoint"
                                .formatted(request.getIssuerDidsCount())));

        var result = credentialRequestManager.initiateRequest(
                request.getParticipantContextId(), issuerDid, request.getHolderPid(), requested);

        if (result.failed() && result.reason() != ServiceFailure.Reason.CONFLICT) {
            throw failure(DSP_PROVISIONING_FAILED, request.getHolderPid(), result.getFailureDetail());
        }

        var builder = RequestCredentialResp.newBuilder();
        if (result.succeeded() && result.getContent() != null) {
            builder.setRequestId(result.getContent());
        }
        return builder.build();
    }

    /**
     * Picks the issuer DID to target for a credential request, done before any
     * {@link org.eclipse.edc.identityhub.spi.credential.request.model.HolderCredentialRequest} is created so an
     * unreachable issuer never burns a holder request slot.
     * <p>
     * A single candidate is returned as-is: with no alternative to fail over to, probing it can only add
     * latency, and a lone issuer being temporarily down is the credential-request state machine's retry
     * concern, not this one's. With two or more candidates, all are probed concurrently for a DID document
     * carrying an {@link CredentialRequestManager#ISSUER_SERVICE_ENDPOINT_TYPE} service entry — the same
     * targeting decision {@link CredentialRequestManager} itself makes once a request is in flight — and the
     * first one to resolve successfully wins.
     * <p>
     * Probes are never cancelled. Each records its own outcome — reachable, or remembered as unreachable for
     * {@link #UNREACHABLE_BACKOFF} — whenever it finishes, whether that is before or after this call has
     * already returned a winner or given up at the timeout. This is what makes racing a fast candidate against
     * a slow one safe: a losing candidate that only fails after the winner has already been returned still
     * gets recorded and still enters backoff, instead of being silently dropped and re-probed on every
     * subsequent call.
     * <p>
     * The call itself is bounded by {@link #RESOLVE_TIMEOUT} regardless of candidate count, and returns early,
     * before that bound, once every candidate has answered. Should the bound elapse first, an empty result is
     * returned and the still-running probes are left to finish on their own; each is bounded by the resolver's
     * own HTTP timeout, so nothing is left running indefinitely, and each still records its outcome once it
     * concludes. A candidate that resolves but lacks the service entry, fails to resolve, or throws is
     * remembered as unreachable and skipped by later calls, unless every candidate is currently within its
     * backoff window, in which case all candidates are tried anyway rather than failing on stale memory.
     */
    private Optional<String> selectReachableIssuer(List<String> candidateDids) {
        if (candidateDids.size() == 1) {
            return Optional.of(candidateDids.getFirst());
        }
        return probeConcurrently(eligibleCandidates(candidateDids));
    }

    private List<String> eligibleCandidates(List<String> candidateDids) {
        var now = Instant.now();
        var eligible = candidateDids.stream()
                .filter(did -> now.isAfter(unreachableUntil.getOrDefault(did, Instant.MIN)))
                .toList();
        return eligible.isEmpty() ? candidateDids : eligible;
    }

    private Optional<String> probeConcurrently(List<String> candidateDids) {
        var winner = new CompletableFuture<String>();
        var probes = candidateDids.stream()
                .map(did -> CompletableFuture.supplyAsync(() -> probe(did), resolveExecutor)
                        .whenComplete((reachable, throwable) -> recordOutcome(did, reachable, throwable, winner)))
                .toList();
        CompletableFuture.allOf(probes.toArray(CompletableFuture[]::new))
                .whenComplete((ignoredResult, ignoredThrowable) -> winner.complete(null));

        try {
            return Optional.ofNullable(winner.get(RESOLVE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            return Optional.empty();
        }
    }

    private void recordOutcome(String did, Boolean reachable, Throwable throwable, CompletableFuture<String> winner) {
        if (throwable == null && Boolean.TRUE.equals(reachable)) {
            unreachableUntil.remove(did);
            winner.complete(did);
        } else {
            markUnreachable(did);
        }
    }

    private void markUnreachable(String did) {
        unreachableUntil.put(did, Instant.now().plus(UNREACHABLE_BACKOFF));
    }

    private boolean probe(String did) {
        try {
            return resolvesToIssuerService(did);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private boolean resolvesToIssuerService(String did) {
        var resolved = didResolverRegistry.resolve(did);
        return resolved.succeeded() && resolved.getContent().getService().stream()
                .anyMatch(service -> service.getType().equalsIgnoreCase(
                        CredentialRequestManager.ISSUER_SERVICE_ENDPOINT_TYPE));
    }

    private GetCredentialRequestStateResp getCredentialRequestStateInternal(GetCredentialRequestStateReq request) {
        var holderRequest = credentialRequestManager.findById(request.getHolderPid());
        if (holderRequest == null) {
            return GetCredentialRequestStateResp.newBuilder().setFound(false).build();
        }
        return GetCredentialRequestStateResp.newBuilder()
                .setFound(true)
                .setStatus(holderRequest.stateAsString())
                .build();
    }

    private GetParticipantContextDidResp getParticipantContextDidInternal(GetParticipantContextDidReq request) {
        var result = participantContextService.getParticipantContext(request.getParticipantContextId());
        if (result.succeeded()) {
            return GetParticipantContextDidResp.newBuilder()
                    .setDid(result.getContent().getDid())
                    .build();
        }
        if (result.reason() == ServiceFailure.Reason.NOT_FOUND) {
            return GetParticipantContextDidResp.getDefaultInstance();
        }
        throw failure(DSP_PARTICIPANT_CONTEXT_FAILED, request.getParticipantContextId(), result.getFailureDetail());
    }
}
