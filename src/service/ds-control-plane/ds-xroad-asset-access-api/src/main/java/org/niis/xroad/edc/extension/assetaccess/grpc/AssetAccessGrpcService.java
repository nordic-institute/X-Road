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
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.niis.xroad.common.core.telemetry.SpanAttributes;
import org.niis.xroad.common.core.telemetry.XrdSpanAttrs;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gRPC service implementation that delegates to {@link AssetAccessOrchestrator}.
 * Bridges the async {@code CompletableFuture<ServiceResult<DataAddress>>} to gRPC's
 * sync {@code StreamObserver} pattern using {@link RpcResponseHandler#handleRequest}.
 */
@Slf4j
@RequiredArgsConstructor
class AssetAccessGrpcService extends AssetAccessServiceGrpc.AssetAccessServiceImplBase {

    private static final long TIMEOUT_SECONDS = 60;
    private static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String ENDPOINT_KEY = EDC_NS + "endpoint";
    private static final String AUTHORIZATION_KEY = EDC_NS + "authorization";

    private final AssetAccessOrchestrator assetAccessOrchestrator;
    private final ParticipantContextService participantContextService;
    private final RpcResponseHandler responseHandler;

    @Override
    @WithSpan("dsp-asset-acquire")
    public void acquire(AcquireAssetAccessReq request, StreamObserver<AcquireAssetAccessResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> {
            try {
                return acquireInternal(request);
            } catch (EdcException e) {
                throw DspFailureClassifier.classify(e);
            }
        });
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
            throw new EdcException("Failed to resolve participant context: " + result.getFailureDetail());
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
            throw new EdcException("Asset access acquisition failed: " + serviceResult.getFailureDetail());
        }
        return serviceResult.getContent();
    }

    private static AcquireAssetAccessResp buildResponse(DataAddress dataAddress) {
        var properties = dataAddress.getProperties();
        var endpoint = (String) properties.get(ENDPOINT_KEY);
        if (endpoint == null || endpoint.isBlank()) {
            throw new EdcException("DataAddress missing required 'endpoint' property");
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
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdcException("Asset access acquisition interrupted", e);
        } catch (ExecutionException e) {
            throw new EdcException("Asset access acquisition failed", e.getCause());
        } catch (TimeoutException e) {
            throw new EdcException("Asset access acquisition timed out after " + TIMEOUT_SECONDS + "s", e);
        }
    }
}
