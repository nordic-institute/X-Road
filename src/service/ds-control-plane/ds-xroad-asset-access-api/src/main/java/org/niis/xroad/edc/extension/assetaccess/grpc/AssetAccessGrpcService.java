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
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.telemetry.SpanAttributes;
import org.niis.xroad.common.core.telemetry.XrdSpanAttrs;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;
import org.niis.xroad.edc.extension.assetaccess.util.DspExceptionWrapper;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_TIMEOUT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATAADDRESS_INVALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;

/**
 * gRPC service implementation that delegates to {@link AssetAccessOrchestrator}.
 * Bridges the async {@code CompletableFuture<ServiceResult<DataAddress>>} to gRPC's
 * sync {@code StreamObserver} pattern using {@link RpcResponseHandler#handleRequest}.
 */
@Slf4j
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
        responseHandler.handleRequest(responseObserver, DspExceptionWrapper.wrap(() -> acquireInternal(request)));
    }

    private AcquireAssetAccessResp acquireInternal(AcquireAssetAccessReq request) {
        recordSpanAttributes(request);
        var participantContext = resolveParticipantContext(request.getParticipantContextId());
        var assetAccessRequest = toAssetAccessRequest(request);
        var dataAddress = awaitAcquireResult(participantContext, assetAccessRequest);
        return buildResponse(dataAddress);
    }

    private void recordSpanAttributes(AcquireAssetAccessReq request) {
        SpanAttributes.onCurrent()
                .set(XrdSpanAttrs.AssetAccess.PARTICIPANT_CONTEXT_ID, request.getParticipantContextId())
                .set(XrdSpanAttrs.AssetAccess.ASSET_ID, request.getAssetId())
                .set(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ID, request.getCounterPartyId())
                .set(XrdSpanAttrs.AssetAccess.COUNTERPARTY_ADDRESS, request.getCounterPartyAddress())
                .set(XrdSpanAttrs.AssetAccess.PROTOCOL, request.getProtocol().isEmpty() ? null : request.getProtocol())
                .apply();
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
                request.getCounterPartyId(),
                request.getCounterPartyAddress(),
                request.getProtocol().isEmpty() ? null : request.getProtocol());
    }

    private DataAddress awaitAcquireResult(ParticipantContext participantContext, AssetAccessRequest assetAccessRequest) {
        var serviceResult = awaitResult(assetAccessOrchestrator.acquireAssetAccess(participantContext, assetAccessRequest));
        if (serviceResult.failed()) {
            throw XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                    .origin(ErrorOrigin.DATASPACE)
                    .details(serviceResult.getFailureDetail())
                    .build();
        }
        return serviceResult.getContent();
    }

    private static AcquireAssetAccessResp buildResponse(DataAddress dataAddress) {
        var properties = dataAddress.getProperties();
        var endpoint = (String) properties.get(ENDPOINT_KEY);
        if (endpoint == null || endpoint.isBlank()) {
            throw XrdRuntimeException.systemException(DSP_DATAADDRESS_INVALID)
                    .origin(ErrorOrigin.DATASPACE)
                    .build();
        }
        var authorization = (String) properties.get(AUTHORIZATION_KEY);
        var expiresAt = extractJwtExpiry(authorization);

        var respBuilder = AcquireAssetAccessResp.newBuilder().setEndpoint(endpoint);
        if (authorization != null) {
            respBuilder.setAuthorization(authorization);
        }
        if (expiresAt != null) {
            respBuilder.setExpiresAtEpochSeconds(expiresAt);
        }
        return respBuilder.build();
    }

    private static Long extractJwtExpiry(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        try {
            var parts = authorization.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            var payload = Base64.getUrlDecoder().decode(parts[1]);
            try (var reader = Json.createReader(new ByteArrayInputStream(payload))) {
                return (reader.readObject().get("exp") instanceof JsonNumber number)
                        ? number.longValueExact() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract JWT expiry from authorization token", e);
            return null;
        }
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
