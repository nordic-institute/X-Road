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
package org.niis.xroad.cs.admin.core.dataspace;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionOutcome;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionResult;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsReq;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsResp;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DidResolutionRpcClientTest {

    private static final int DEADLINE_MILLIS = 300;
    private static final String DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private DidResolutionRpcChannelProperties channelProperties;

    private Server server;
    private ManagedChannel channel;
    private ManagedChannel deadlineChannel;
    private DidResolutionRpcClient client;

    private final CountDownLatch release = new CountDownLatch(1);

    private Consumer<StreamObserver<ResolveDidsResp>> serverBehavior;

    @BeforeEach
    void setUp() throws Exception {
        var service = new DidResolutionServiceGrpc.DidResolutionServiceImplBase() {
            @Override
            public void resolveDids(ResolveDidsReq request, StreamObserver<ResolveDidsResp> responseObserver) {
                serverBehavior.accept(responseObserver);
            }
        };

        server = ServerBuilder.forPort(0).addService(service).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);

        client = new DidResolutionRpcClient(rpcChannelFactory, channelProperties);
        client.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        client.close();
        channel.shutdownNow();
        if (deadlineChannel != null) {
            deadlineChannel.shutdownNow();
        }
        server.shutdownNow();
    }

    @Test
    void resolvedAndNotResolvedResultsAreMappedInOrder() {
        answerWith(result(DidResolutionOutcome.RESOLVED, 0),
                result(DidResolutionOutcome.NOT_RESOLVED,
                        org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason.NO_DOCUMENT_VALUE));

        var resolutions = client.resolve(List.of(DID, DID));

        assertThat(resolutions).containsExactly(
                DidResolution.resolved(DID),
                DidResolution.notResolved(DID, DidResolutionReason.NO_DOCUMENT));
    }

    @ParameterizedTest
    @EnumSource(value = io.grpc.Status.Code.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "CANCELLED"})
    void aTransportFailureStatusIsANetworkErrorFromTheIssuerService(io.grpc.Status.Code code) {
        serverBehavior = observer -> observer.onError(code.toStatus().asRuntimeException());

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertNetworkErrorFromIssuerService(e);
                    assertStatusCauseAndMetadata(e, code);
                });
    }

    @ParameterizedTest
    @EnumSource(value = io.grpc.Status.Code.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"OK", "UNAVAILABLE", "DEADLINE_EXCEEDED", "CANCELLED"})
    void anyOtherFailureStatusWithoutADetailIsAnInternalErrorFromTheIssuerService(io.grpc.Status.Code code) {
        serverBehavior = observer -> observer.onError(code.toStatus().asRuntimeException());

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertInternalErrorFromIssuerService(e);
                    assertStatusCauseAndMetadata(e, code);
                });
    }

    @Test
    void anInternalStatusWithAnErrorDetailThrowsTheIssuersOwnError() {
        serverBehavior = observer -> observer.onError(StatusProto.toStatusRuntimeException(
                com.google.rpc.Status.newBuilder()
                        .setCode(io.grpc.Status.Code.INTERNAL.value())
                        .addDetails(Any.pack(XrdRuntimeExceptionProto.newBuilder()
                                .setIdentifier("issuer-failure-1")
                                .setErrorCode(ErrorCode.DATABASE_ERROR.code())
                                .setDetails("server side detail")
                                .build()))
                        .build()));

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.DATABASE_ERROR)).isTrue();
                    assertThat(e.originatesFrom(ErrorOrigin.DS_ISSUER_SERVICE)).isTrue();
                    assertThat(e.getIdentifier()).isEqualTo("issuer-failure-1");
                    assertThat(e.getDetails()).isEqualTo("server side detail");
                });
    }

    @Test
    void anIssuerErrorWithACodeAndEmptyDetailsKeepsTheIssuersCodeAndHasBlankDetails() {
        serverBehavior = observer -> observer.onError(StatusProto.toStatusRuntimeException(
                com.google.rpc.Status.newBuilder()
                        .setCode(io.grpc.Status.Code.INTERNAL.value())
                        .addDetails(Any.pack(XrdRuntimeExceptionProto.newBuilder()
                                .setIdentifier("issuer-failure-2")
                                .setErrorCode(ErrorCode.DATABASE_ERROR.code())
                                .build()))
                        .build()));

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.DATABASE_ERROR)).isTrue();
                    assertThat(e.originatesFrom(ErrorOrigin.DS_ISSUER_SERVICE)).isTrue();
                    assertThat(e.getIdentifier()).isEqualTo("issuer-failure-2");
                    assertThat(e.getDetails()).isBlank();
                });
    }

    @Test
    void aStatusWithAnUnparsableErrorDetailThrowsTheParseFailureAsAnInternalError() {
        var unparsable = StatusProto.toStatusRuntimeException(
                com.google.rpc.Status.newBuilder()
                        .setCode(io.grpc.Status.Code.INTERNAL.value())
                        .addDetails(Any.newBuilder()
                                .setTypeUrl(Any.pack(XrdRuntimeExceptionProto.getDefaultInstance()).getTypeUrl())
                                .setValue(ByteString.copyFrom(new byte[] {0x0A, 0x7F}))
                                .build())
                        .build());
        serverBehavior = observer -> observer.onError(unparsable);

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertInternalErrorFromIssuerService(e);
                    assertThat(e.getCause()).isInstanceOf(com.google.protobuf.InvalidProtocolBufferException.class);
                    assertThat(e.getErrorCodeMetadata()).isEmpty();
                    assertThat(e.getDetails()).contains("Failed to parse");
                });
    }

    @Test
    void aDeadlineExceededStatusIsANetworkErrorWithItsOwnIdentifierAndNoNestedTimeoutText() {
        var deadlineStatus = io.grpc.Status.DEADLINE_EXCEEDED.asRuntimeException();
        serverBehavior = observer -> observer.onError(deadlineStatus);

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertNetworkErrorFromIssuerService(e);
                    assertThat(e.getDetails())
                            .contains("DEADLINE_EXCEEDED")
                            .doesNotContain("network_error")
                            .doesNotContain("gRPC client timed out");
                    assertThat(e.getCause()).isInstanceOf(io.grpc.StatusRuntimeException.class);
                    assertThat(e.getErrorCodeMetadata()).containsExactly("DEADLINE_EXCEEDED");
                });
    }

    @Test
    void aNullElementInTheRequestIsAnInternalErrorFromTheIssuerService() {
        assertThatThrownBy(() -> client.resolve(Arrays.asList(DID, null)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue();
                    assertThat(e.originatesFrom(ErrorOrigin.DS_ISSUER_SERVICE)).isTrue();
                });
    }

    @Test
    void aStoppedIssuerServiceFailsAtOnceWithoutWaitingForReadiness() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var closedChannel = ManagedChannelBuilder.forAddress("localhost", closedPort).usePlaintext().build();
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(closedChannel);
        var stoppedClient = new DidResolutionRpcClient(rpcChannelFactory, channelProperties);
        stoppedClient.afterPropertiesSet();
        try {
            catchThrowable(() -> stoppedClient.resolve(List.of(DID)));
            var started = System.nanoTime();

            assertThatThrownBy(() -> stoppedClient.resolve(List.of(DID)))
                    .isInstanceOfSatisfying(XrdRuntimeException.class, this::assertNetworkErrorFromIssuerService);

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        } finally {
            stoppedClient.close();
            closedChannel.shutdownNow();
        }
    }

    @Test
    void aSlowIssuerServiceFailsAtTheChannelDeadline() {
        var credentialsConfigurer = mock(RpcCredentialsConfigurer.class);
        when(credentialsConfigurer.createClientCredentials()).thenReturn(InsecureChannelCredentials.create());
        deadlineChannel = new RpcChannelFactory(credentialsConfigurer)
                .createChannel(new ChannelProperties(server.getPort(), DEADLINE_MILLIS));
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(deadlineChannel);
        client.close();
        client = new DidResolutionRpcClient(rpcChannelFactory, channelProperties);
        client.afterPropertiesSet();
        answerWith();
        catchThrowable(() -> client.resolve(List.of(DID)));
        serverBehavior = this::stallThenAnswer;

        var started = System.nanoTime();

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, this::assertNetworkErrorFromIssuerService);

        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(Duration.ofMillis(DEADLINE_MILLIS + 1000));
    }

    @Test
    void resultsOfEveryOutcomeAndReasonAreMappedInRequestOrder() {
        answerWith(
                result(DidResolutionOutcome.NOT_RESOLVED,
                        org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason.FETCH_FAILURE_VALUE),
                result(DidResolutionOutcome.RESOLVED, 0),
                result(DidResolutionOutcome.NOT_RESOLVED,
                        org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason.MALFORMED_DID_VALUE),
                result(DidResolutionOutcome.NOT_RESOLVED,
                        org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason.ID_MISMATCH_VALUE),
                result(DidResolutionOutcome.NOT_RESOLVED,
                        org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason.NO_DOCUMENT_VALUE));

        var resolutions = client.resolve(List.of(DID, DID, DID, DID, DID));

        assertThat(resolutions).containsExactly(
                DidResolution.notResolved(DID, DidResolutionReason.FETCH_FAILURE),
                DidResolution.resolved(DID),
                DidResolution.notResolved(DID, DidResolutionReason.MALFORMED_DID),
                DidResolution.notResolved(DID, DidResolutionReason.ID_MISMATCH),
                DidResolution.notResolved(DID, DidResolutionReason.NO_DOCUMENT));
    }

    @Test
    void anUnrecognizedOutcomeIsAnInternalError() {
        answerWith(result(99, 0));

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue());
    }

    @Test
    void anUnrecognizedReasonIsAnInternalError() {
        answerWith(result(DidResolutionOutcome.NOT_RESOLVED, 99));

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue());
    }

    private void assertNetworkErrorFromIssuerService(XrdRuntimeException e) {
        assertThat(e.isCausedBy(ErrorCode.NETWORK_ERROR)).isTrue();
        assertThat(e.originatesFrom(ErrorOrigin.DS_ISSUER_SERVICE)).isTrue();
    }

    private void assertInternalErrorFromIssuerService(XrdRuntimeException e) {
        assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue();
        assertThat(e.originatesFrom(ErrorOrigin.DS_ISSUER_SERVICE)).isTrue();
    }

    private void assertStatusCauseAndMetadata(XrdRuntimeException e, io.grpc.Status.Code code) {
        assertThat(e.getCause()).isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(((io.grpc.StatusRuntimeException) e.getCause()).getStatus().getCode()).isEqualTo(code);
        assertThat(e.getErrorCodeMetadata()).containsExactly(code.name());
        assertThat(e.getDetails()).contains(code.name());
    }

    private void stallThenAnswer(StreamObserver<ResolveDidsResp> observer) {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
        observer.onNext(ResolveDidsResp.getDefaultInstance());
        observer.onCompleted();
    }

    private record ChannelProperties(int port, int deadlineAfter) implements RpcChannelProperties {
        @Override
        public String host() {
            return "localhost";
        }
    }

    private void answerWith(DidResolutionResult... results) {
        serverBehavior = observer -> {
            observer.onNext(ResolveDidsResp.newBuilder().addAllResults(List.of(results)).build());
            observer.onCompleted();
        };
    }

    private static DidResolutionResult result(DidResolutionOutcome outcome, int reasonValue) {
        return result(outcome.getNumber(), reasonValue);
    }

    private static DidResolutionResult result(int outcomeValue, int reasonValue) {
        return DidResolutionResult.newBuilder()
                .setDid(DID)
                .setOutcomeValue(outcomeValue)
                .setReasonValue(reasonValue)
                .build();
    }
}
