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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.spi.EdcException;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessAcquisitionService;

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gRPC service implementation that delegates to {@link AssetAccessAcquisitionService}.
 * Bridges the async {@code CompletableFuture<ServiceResult<DataAddress>>} to gRPC's
 * sync {@code StreamObserver} pattern using {@link RpcResponseHandler#handleRequest}.
 */
@Slf4j
@RequiredArgsConstructor
class AssetAccessGrpcService extends AssetAccessServiceGrpc.AssetAccessServiceImplBase {

    private static final long TIMEOUT_SECONDS = 60;
    private static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetAccessAcquisitionService assetAccessAcquisitionService;
    private final ParticipantContextService participantContextService;
    private final RpcResponseHandler responseHandler;

    @Override
    public void acquire(AcquireAssetAccessReq request,
                        StreamObserver<AcquireAssetAccessResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> {
            var participantResult = participantContextService
                    .getParticipantContext(request.getParticipantContextId());
            if (participantResult.failed()) {
                throw new EdcException("Failed to resolve participant context: "
                        + participantResult.getFailureDetail());
            }
            var participantContext = participantResult.getContent();

            var assetAccessRequest = new AssetAccessRequest(
                    request.getAssetId(),
                    request.getCounterPartyId(),
                    request.getCounterPartyAddress(),
                    request.getProtocol().isEmpty() ? null : request.getProtocol());

            var serviceResult = awaitResult(assetAccessAcquisitionService
                    .acquireAssetAccess(participantContext, assetAccessRequest));

            if (serviceResult.failed()) {
                throw new EdcException("Asset access acquisition failed: "
                        + serviceResult.getFailureDetail());
            }

            var dataAddress = serviceResult.getContent();
            var properties = dataAddress.getProperties();
            var endpoint = (String) properties.get(EDC_NS + "endpoint");
            if (endpoint == null || endpoint.isBlank()) {
                throw new EdcException("DataAddress missing required 'endpoint' property");
            }
            var authorization = (String) properties.get(EDC_NS + "authorization");

            var expiresAt = extractJwtExpiry(authorization);

            var respBuilder = AcquireAssetAccessResp.newBuilder()
                    .setEndpoint(endpoint);
            if (authorization != null) {
                respBuilder.setAuthorization(authorization);
            }
            if (expiresAt != null) {
                respBuilder.setExpiresAtEpochSeconds(expiresAt);
            }
            return respBuilder.build();
        });
    }

    private Long extractJwtExpiry(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        try {
            var parts = authorization.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            var payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            var node = OBJECT_MAPPER.readTree(payload);
            var expNode = node.get("exp");
            return (expNode != null && expNode.isNumber()) ? expNode.longValue() : null;
        } catch (Exception e) {
            log.debug("Could not extract JWT expiry from authorization token", e);
            return null;
        }
    }

    private <T> T awaitResult(java.util.concurrent.CompletableFuture<T> future) {
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
