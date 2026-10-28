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
package org.niis.xroad.edc.extension.assetaccess.grpc;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.telemetry.SpanAttributes;
import org.niis.xroad.common.core.telemetry.XrdSpanAttrs;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessCandidate;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessWinner;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;
import org.niis.xroad.edc.extension.assetaccess.token.AgreementTokenExpiry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_TIMEOUT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATAADDRESS_INVALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;

/**
 * gRPC service implementation that delegates to {@link AssetAccessOrchestrator}.
 * Bridges the async {@code CompletableFuture<AssetAccessResult>} to gRPC's
 * sync {@code StreamObserver} pattern using {@link RpcResponseHandler#handleRequest}.
 */
@RequiredArgsConstructor
class AssetAccessGrpcService extends AssetAccessServiceGrpc.AssetAccessServiceImplBase {

    private static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String ENDPOINT_KEY = EDC_NS + "endpoint";
    private static final String AUTHORIZATION_KEY = EDC_NS + "authorization";

    private final AssetAccessOrchestrator assetAccessOrchestrator;
    private final ParticipantContextService participantContextService;
    private final RpcResponseHandler responseHandler;
    private final Duration acquisitionTimeout;

    @Override
    @WithSpan("dsp-asset-acquire")
    public void acquire(AcquireAssetAccessReq request, StreamObserver<AcquireAssetAccessResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> acquireInternal(request));
    }

    private AcquireAssetAccessResp acquireInternal(AcquireAssetAccessReq request) {
        recordSpanAttributes(request);
        requireCandidates(request);
        var participantContext = resolveParticipantContext(request.getParticipantContextId());
        var assetAccessRequest = toAssetAccessRequest(request);
        var result = awaitResult(assetAccessOrchestrator.acquireAssetAccess(participantContext, assetAccessRequest));
        return buildResponse(result);
    }

    private static void requireCandidates(AcquireAssetAccessReq request) {
        if (request.getCandidatesCount() == 0) {
            throw XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                    .origin(ErrorOrigin.DATASPACE)
                    .details("Empty candidate list")
                    .build();
        }
    }

    private void recordSpanAttributes(AcquireAssetAccessReq request) {
        SpanAttributes.onCurrent()
                .set(XrdSpanAttrs.AssetAccess.PARTICIPANT_CONTEXT_ID, request.getParticipantContextId())
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, request.getAssetId())
                .set(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ID, joinCandidates(request, AssetAccessCandidate::getCounterPartyId))
                .set(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ADDRESS,
                        joinCandidates(request, AssetAccessCandidate::getCounterPartyAddress))
                .set(XrdSpanAttrs.AssetAccess.PROTOCOL, request.getProtocol().isEmpty() ? null : request.getProtocol())
                .set(XrdSpanAttrs.AssetAccess.CLIENT_ID, request.hasClientId() ? request.getClientId() : null)
                .apply();
    }

    private static String joinCandidates(AcquireAssetAccessReq request, Function<AssetAccessCandidate, String> field) {
        return request.getCandidatesList().stream().map(field).collect(Collectors.joining(","));
    }

    private ParticipantContext resolveParticipantContext(String participantContextId) {
        var result = participantContextService.getParticipantContext(participantContextId);
        if (result.failed()) {
            throw XrdRuntimeException.systemException(DSP_PARTICIPANT_CONTEXT_FAILED)
                    .origin(ErrorOrigin.DATASPACE)
                    .metadataItems(participantContextId)
                    .details(result.getFailureDetail())
                    .build();
        }
        return result.getContent();
    }

    private static AssetAccessRequest toAssetAccessRequest(AcquireAssetAccessReq request) {
        return new AssetAccessRequest(
                request.getAssetId(),
                request.getCandidatesList().stream()
                        .map(candidate -> new AssetAccessRequest.Candidate(
                                candidate.getCounterPartyId(), candidate.getCounterPartyAddress()))
                        .toList(),
                request.getProtocol().isEmpty() ? null : request.getProtocol(),
                request.hasClientId() ? request.getClientId() : null,
                request.hasForcedTargetServerId() ? request.getForcedTargetServerId() : null);
    }

    private static AcquireAssetAccessResp buildResponse(AssetAccessResult result) {
        var dataAddress = result.dataAddress();
        var properties = dataAddress.getProperties();
        var endpoint = (String) properties.get(ENDPOINT_KEY);
        if (endpoint == null || endpoint.isBlank()) {
            throw XrdRuntimeException.systemException(DSP_DATAADDRESS_INVALID)
                    .origin(ErrorOrigin.DATASPACE)
                    .build();
        }
        var authorization = (String) properties.get(AUTHORIZATION_KEY);

        var respBuilder = AcquireAssetAccessResp.newBuilder()
                .setEndpoint(endpoint)
                .setWinner(AssetAccessWinner.newBuilder()
                        .setCounterPartyId(result.winner().counterPartyId())
                        .setHost(result.winner().host()));
        if (authorization != null) {
            respBuilder.setAuthorization(authorization);
        }
        AgreementTokenExpiry.expiryOf(dataAddress)
                .ifPresent(expiresAt -> respBuilder.setExpiresAtEpochSeconds(expiresAt.getEpochSecond()));
        return respBuilder.build();
    }

    private <T> T awaitResult(CompletableFuture<T> future) {
        try {
            return future.get(acquisitionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                    .origin(ErrorOrigin.DATASPACE)
                    .cause(e)
                    .details("Asset access acquisition interrupted")
                    .build();
        } catch (ExecutionException e) {
            return unwrapExecutionException(e);
        } catch (CompletionException e) {
            return unwrapCompletionException(e);
        } catch (TimeoutException e) {
            throw XrdRuntimeException.systemException(DSP_ACQUISITION_TIMEOUT)
                    .origin(ErrorOrigin.DATASPACE)
                    .cause(e)
                    .metadataItems(acquisitionTimeout.toMillis())
                    .build();
        }
    }

    private static <T> T unwrapExecutionException(ExecutionException e) {
        if (e.getCause() instanceof XrdRuntimeException xre) {
            throw xre;
        }
        throw XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .cause(e.getCause())
                .build();
    }

    private static <T> T unwrapCompletionException(CompletionException e) {
        if (e.getCause() instanceof XrdRuntimeException xre) {
            throw xre;
        }
        throw XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .cause(e.getCause())
                .build();
    }
}
