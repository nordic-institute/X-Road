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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionOutcome;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionResult;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsReq;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsResp;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DidResolutionRpcClientTest {

    private static final String DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private DidResolutionRpcChannelProperties channelProperties;

    private Server server;
    private ManagedChannel channel;
    private DidResolutionRpcClient client;

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
        when(channelProperties.deadlineAfter()).thenReturn(5000);

        client = new DidResolutionRpcClient(rpcChannelFactory, channelProperties);
        client.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        client.close();
        channel.shutdownNow();
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

    @Test
    void anInternalStatusWithAnErrorDetailKeepsTheServerErrorCodeAndIdentifier() {
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
                    assertThat(e.getIdentifier()).isEqualTo("issuer-failure-1");
                });
    }

    @Test
    void anInternalStatusWithoutAnErrorDetailIsAnInternalError() {
        serverBehavior = observer -> observer.onError(io.grpc.Status.INTERNAL.asRuntimeException());

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue());
    }

    @Test
    void anUnavailableStatusIsANetworkError() {
        serverBehavior = observer -> observer.onError(io.grpc.Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.NETWORK_ERROR)).isTrue());
    }

    @Test
    void aDeadlineExceededStatusIsANetworkError() {
        serverBehavior = observer -> observer.onError(io.grpc.Status.DEADLINE_EXCEEDED.asRuntimeException());

        assertThatThrownBy(() -> client.resolve(List.of(DID)))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.NETWORK_ERROR)).isTrue());
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
