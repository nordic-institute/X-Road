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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessGrpcServiceTest {

    private static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";

    @Mock
    AssetAccessOrchestrator assetAccessOrchestrator;
    @Mock
    ParticipantContextService participantContextService;

    private Server server;
    private ManagedChannel channel;
    private AssetAccessServiceGrpc.AssetAccessServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        var responseHandler = new RpcResponseHandler();
        var grpcService = new AssetAccessGrpcService(
                assetAccessOrchestrator, participantContextService, responseHandler);
        server = ServerBuilder.forPort(0)
                .addService(grpcService)
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        stub = AssetAccessServiceGrpc.newBlockingStub(channel);
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
    void acquireSuccessReturnsTypedEndpointAndAuthorization() {
        stubParticipantContext();

        var dataAddress = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(EDC_NS + "endpoint", "http://provider/api/data")
                .property(EDC_NS + "authorization", "token-abc-123")
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(
                any(ParticipantContext.class), any(AssetAccessRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ServiceResult.success(dataAddress)));

        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId("participant-1")
                .setAssetId("asset-1")
                .setCounterPartyId("provider-1")
                .setCounterPartyAddress("http://provider/dsp")
                .setProtocol("dataspace-protocol-http:2025-1")
                .build();
        var response = stub.acquire(request);

        assertThat(response.getEndpoint()).isEqualTo("http://provider/api/data");
        assertThat(response.getAuthorization()).isEqualTo("token-abc-123");
    }

    @Test
    void acquireFailureThrowsStatusRuntimeException() {
        stubParticipantContext();

        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("upstream catalog fetch failed")));

        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId("participant-1")
                .setAssetId("asset-1")
                .setCounterPartyId("provider-1")
                .setCounterPartyAddress("http://provider/dsp")
                .build();
        assertThatThrownBy(() -> stub.acquire(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    @Test
    void acquireSuccessWithNullAuthorizationReturnsEmptyAuthorization() {
        stubParticipantContext();

        var dataAddress = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(EDC_NS + "endpoint", "http://provider/api/data")
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(
                any(ParticipantContext.class), any(AssetAccessRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ServiceResult.success(dataAddress)));

        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId("participant-1")
                .setAssetId("asset-1")
                .setCounterPartyId("provider-1")
                .setCounterPartyAddress("http://provider/dsp")
                .build();
        var response = stub.acquire(request);

        assertThat(response.getEndpoint()).isEqualTo("http://provider/api/data");
        assertThat(response.hasAuthorization()).isFalse();
    }

    @Test
    void acquireSuccessExtractsJwtExpiryToProtoResponse() {
        stubParticipantContext();

        var jwtToken = buildJwt("{\"alg\":\"RS256\"}", "{\"exp\":1700000000}", "fake-signature");
        var dataAddress = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(EDC_NS + "endpoint", "http://provider/api/data")
                .property(EDC_NS + "authorization", jwtToken)
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(
                any(ParticipantContext.class), any(AssetAccessRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ServiceResult.success(dataAddress)));

        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId("participant-1")
                .setAssetId("asset-1")
                .setCounterPartyId("provider-1")
                .setCounterPartyAddress("http://provider/dsp")
                .build();
        var response = stub.acquire(request);

        assertThat(response.hasExpiresAtEpochSeconds()).isTrue();
        assertThat(response.getExpiresAtEpochSeconds()).isEqualTo(1700000000L);
        assertThat(response.getAuthorization()).isEqualTo(jwtToken);
    }

    @Test
    void acquireSuccessWithNonJwtAuthorizationOmitsExpiresAt() {
        stubParticipantContext();

        var dataAddress = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(EDC_NS + "endpoint", "http://provider/api/data")
                .property(EDC_NS + "authorization", "opaque-token-abc")
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(
                any(ParticipantContext.class), any(AssetAccessRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ServiceResult.success(dataAddress)));

        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId("participant-1")
                .setAssetId("asset-1")
                .setCounterPartyId("provider-1")
                .setCounterPartyAddress("http://provider/dsp")
                .build();
        var response = stub.acquire(request);

        assertThat(response.hasExpiresAtEpochSeconds()).isFalse();
        assertThat(response.getAuthorization()).isEqualTo("opaque-token-abc");
    }

    private static String buildJwt(String header, String payload, String signature) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(signature.getBytes(StandardCharsets.UTF_8));
    }

    private void stubParticipantContext() {
        var participantContext = ParticipantContext.Builder.newInstance()
                .participantContextId("participant-1")
                .identity("participant-1")
                .build();
        when(participantContextService.getParticipantContext("participant-1"))
                .thenReturn(ServiceResult.success(participantContext));
    }
}
