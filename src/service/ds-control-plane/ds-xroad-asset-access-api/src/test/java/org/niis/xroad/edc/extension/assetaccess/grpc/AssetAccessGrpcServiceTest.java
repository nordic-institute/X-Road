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

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_TIMEOUT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_FETCH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATAADDRESS_INVALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_NEGOTIATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

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
                assetAccessOrchestrator, participantContextService, responseHandler, java.time.Duration.ofSeconds(60));
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
                .setProtocol("http-dsp-profile-2025-1")
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

    static Stream<Arguments> dspErrorCodes() {
        return Stream.of(
                Arguments.of(DSP_CATALOG_FETCH_FAILED, IO_ERROR),
                Arguments.of(DSP_CATALOG_PARSE_FAILED, IO_ERROR),
                Arguments.of(DSP_ACQUISITION_TIMEOUT, IO_ERROR),
                Arguments.of(DSP_ACQUISITION_FAILED, IO_ERROR),
                Arguments.of(DSP_DATASET_NOT_FOUND, UNKNOWN_MEMBER),
                Arguments.of(DSP_OFFERS_NOT_FOUND, UNKNOWN_MEMBER),
                Arguments.of(DSP_PULL_DISTRIBUTION_MISSING, SERVICE_FAILED),
                Arguments.of(DSP_DATAADDRESS_INVALID, SERVICE_FAILED),
                Arguments.of(DSP_NEGOTIATION_FAILED, SERVICE_FAILED),
                Arguments.of(DSP_TRANSFER_FAILED, SERVICE_FAILED),
                Arguments.of(DSP_PARTICIPANT_CONTEXT_FAILED, INTERNAL_ERROR)
        );
    }

    @ParameterizedTest
    @MethodSource("dspErrorCodes")
    void dspExceptionMappedToCommonException(ErrorCode dspCode, ErrorCode expectedErrorCode) {
        stubParticipantContext();
        var dspException = XrdRuntimeException.systemException(dspCode)
                .origin(ErrorOrigin.DATASPACE)
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(dspException));

        var request = baseRequest().build();

        try {
            stub.acquire(request);
            throw new AssertionError("Expected StatusRuntimeException");
        } catch (StatusRuntimeException ex) {
            assertThat(extractErrorCode(ex)).isEqualTo(expectedErrorCode.code());
            assertThat(extractMetadata(ex)).containsExactly("originalCode=" + dspCode.code());
        }
    }

    @Test
    void dspExceptionPreservesMetadataItemsVerbatim() {
        stubParticipantContext();
        var dspException = XrdRuntimeException.systemException(ErrorCode.DSP_DATASET_NOT_FOUND)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems("my-asset-id", "extra-context")
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(dspException));

        var request = baseRequest().setAssetId("my-asset-id").build();

        try {
            stub.acquire(request);
            throw new AssertionError("Expected StatusRuntimeException");
        } catch (StatusRuntimeException ex) {
            assertThat(extractMetadata(ex))
                    .containsExactly("originalCode=" + DSP_DATASET_NOT_FOUND.code(), "my-asset-id", "extra-context");
        }
    }

    @Test
    void dspExceptionPreservesIdentifierAndDetails() {
        stubParticipantContext();
        var dspException = XrdRuntimeException.systemException(ErrorCode.DSP_CATALOG_FETCH_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .identifier("fixed-uuid-1234")
                .details("provider unreachable")
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(dspException));

        try {
            stub.acquire(baseRequest().build());
            throw new AssertionError("Expected StatusRuntimeException");
        } catch (StatusRuntimeException ex) {
            var proto = unpackProto(ex);
            assertThat(proto.getIdentifier()).isEqualTo("fixed-uuid-1234");
            assertThat(proto.getDetails()).isEqualTo("provider unreachable");
        }
    }

    @Test
    void nonDspXrdExceptionPassesThroughUnchanged() {
        stubParticipantContext();
        var signerException = XrdRuntimeException.systemException(ErrorCode.SERVICE_FAILED)
                .origin(ErrorOrigin.SIGNER)
                .details("signer unavailable")
                .build();
        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(signerException));

        try {
            stub.acquire(baseRequest().build());
            throw new AssertionError("Expected StatusRuntimeException");
        } catch (StatusRuntimeException ex) {
            assertThat(extractErrorCode(ex)).isEqualTo(ErrorOrigin.SIGNER.toPrefix() + ErrorCode.SERVICE_FAILED.code());
        }
    }

    @Test
    void plainRuntimeExceptionFromFutureBecomesDspAcquisitionFailedMappedToIoError() {
        stubParticipantContext();
        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("unexpected")));

        try {
            stub.acquire(baseRequest().build());
            throw new AssertionError("Expected StatusRuntimeException");
        } catch (StatusRuntimeException ex) {
            assertThat(extractErrorCode(ex)).isEqualTo(IO_ERROR.code());
            assertThat(extractMetadata(ex)).containsExactly("originalCode=" + ErrorCode.DSP_ACQUISITION_FAILED.code());
        }
    }

    @Test
    void orchestratorThrowsDirectlyBecomesInternalError() {
        stubParticipantContext();
        when(assetAccessOrchestrator.acquireAssetAccess(any(), any()))
                .thenThrow(new RuntimeException("unexpected direct throw"));

        try {
            stub.acquire(baseRequest().build());
            throw new AssertionError("Expected StatusRuntimeException");
        } catch (StatusRuntimeException ex) {
            assertThat(extractErrorCode(ex)).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
        }
    }

    private AcquireAssetAccessReq.Builder baseRequest() {
        return AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId("participant-1")
                .setAssetId("asset-1")
                .setCounterPartyId("provider-1")
                .setCounterPartyAddress("http://provider/dsp");
    }

    private String extractErrorCode(StatusRuntimeException ex) {
        return unpackProto(ex).getErrorCode();
    }

    private java.util.List<String> extractMetadata(StatusRuntimeException ex) {
        return unpackProto(ex).getErrorMetadataList();
    }

    private XrdRuntimeExceptionProto unpackProto(StatusRuntimeException ex) {
        var status = StatusProto.fromThrowable(ex);
        assertThat(status).isNotNull();
        for (var any : status.getDetailsList()) {
            if (any.is(XrdRuntimeExceptionProto.class)) {
                try {
                    return any.unpack(XrdRuntimeExceptionProto.class);
                } catch (InvalidProtocolBufferException e) {
                    throw new AssertionError("Failed to unpack XrdRuntimeExceptionProto", e);
                }
            }
        }
        throw new AssertionError("No XrdRuntimeExceptionProto found in gRPC status details");
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
