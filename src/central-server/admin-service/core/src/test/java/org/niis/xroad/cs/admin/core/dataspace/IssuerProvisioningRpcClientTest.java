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
import org.niis.xroad.cs.admin.api.domain.CanSignCause;
import org.niis.xroad.cs.admin.api.domain.CanSignResult;
import org.niis.xroad.edc.issuer.provisioning.proto.CanSignReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CanSignResp;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.issuer.provisioning.proto.IssuerProvisioningServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialReq;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialResp;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("java:S2925")
@ExtendWith(MockitoExtension.class)
class IssuerProvisioningRpcClientTest {

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private IssuerProvisioningRpcChannelProperties channelProperties;

    private Server server;
    private ManagedChannel channel;
    private IssuerProvisioningRpcClient client;

    private RevokeCredentialResp configuredRevokeResp;
    private final AtomicReference<RevokeCredentialReq> capturedRevokeReq = new AtomicReference<>();

    private CanSignResp configuredCanSignResp;
    private final AtomicReference<CanSignReq> capturedCanSignReq = new AtomicReference<>();
    private long canSignDelayMillis;
    private boolean canSignUnavailable;

    private final AtomicReference<CreateParticipantContextReq> capturedCreateParticipantContextReq = new AtomicReference<>();
    private ErrorCode configuredCreateParticipantContextError;
    private String configuredCreateParticipantContextRawErrorCode;

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new IssuerProvisioningServiceGrpc.IssuerProvisioningServiceImplBase() {
            @Override
            public void revokeCredential(RevokeCredentialReq request, StreamObserver<RevokeCredentialResp> responseObserver) {
                capturedRevokeReq.set(request);
                responseObserver.onNext(configuredRevokeResp);
                responseObserver.onCompleted();
            }

            @Override
            public void canSign(CanSignReq request, StreamObserver<CanSignResp> responseObserver) {
                capturedCanSignReq.set(request);
                if (canSignUnavailable) {
                    responseObserver.onError(io.grpc.Status.UNAVAILABLE.asRuntimeException());
                    return;
                }
                if (canSignDelayMillis > 0) {
                    try {
                        Thread.sleep(canSignDelayMillis);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                responseObserver.onNext(configuredCanSignResp);
                responseObserver.onCompleted();
            }

            @Override
            public void createParticipantContext(CreateParticipantContextReq request,
                                                 StreamObserver<CreateParticipantContextResp> responseObserver) {
                capturedCreateParticipantContextReq.set(request);
                if (configuredCreateParticipantContextRawErrorCode != null) {
                    responseObserver.onError(StatusProto.toStatusRuntimeException(
                            com.google.rpc.Status.newBuilder()
                                    .setCode(io.grpc.Status.Code.INTERNAL.value())
                                    .addDetails(Any.pack(XrdRuntimeExceptionProto.newBuilder()
                                            .setErrorCode(configuredCreateParticipantContextRawErrorCode)
                                            .build()))
                                    .build()));
                    return;
                }
                if (configuredCreateParticipantContextError != null) {
                    responseObserver.onError(StatusProto.toStatusRuntimeException(
                            com.google.rpc.Status.newBuilder()
                                    .setCode(io.grpc.Status.Code.INTERNAL.value())
                                    .addDetails(Any.pack(XrdRuntimeExceptionProto.newBuilder()
                                            .setErrorCode(configuredCreateParticipantContextError.code())
                                            .build()))
                                    .build()));
                    return;
                }
                responseObserver.onNext(CreateParticipantContextResp.getDefaultInstance());
                responseObserver.onCompleted();
            }
        };

        server = ServerBuilder.forPort(0).addService(mockService).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);

        client = new IssuerProvisioningRpcClient(rpcChannelFactory, channelProperties);
        client.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        client.close();
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void revokeCredentialForwardsAllFieldsAndReturnsRevokedCount() {
        configuredRevokeResp = RevokeCredentialResp.newBuilder().setRevokedCount(2).build();

        var revokedCount = client.revokeCredential("ctx-id", "did:web:ss1.example.com", 1_700_000_000_000L);

        assertThat(revokedCount).isEqualTo(2);
        var req = capturedRevokeReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getHolderDid()).isEqualTo("did:web:ss1.example.com");
        assertThat(req.getIssuedBefore()).isEqualTo(1_700_000_000_000L);
    }

    @Test
    void revokeCredentialReturnsZeroWhenNoCredentialsRevoked() {
        configuredRevokeResp = RevokeCredentialResp.newBuilder().setRevokedCount(0).build();

        var revokedCount = client.revokeCredential("ctx-id", "did:web:ss1.example.com", 1_700_000_000_000L);

        assertThat(revokedCount).isZero();
    }

    @Test
    void canSignReturnsAbsentWhenParticipantContextIsMissing() {
        configuredCanSignResp = CanSignResp.newBuilder()
                .setOutcome(org.niis.xroad.edc.issuer.provisioning.proto.CanSignOutcome.ABSENT)
                .build();

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.absent());
        assertThat(capturedCanSignReq.get().getParticipantContextId()).isEqualTo("ctx-id");
    }

    @Test
    void canSignReturnsCanSignWithDidWhenIssuerCanSign() {
        configuredCanSignResp = CanSignResp.newBuilder()
                .setOutcome(org.niis.xroad.edc.issuer.provisioning.proto.CanSignOutcome.CAN_SIGN)
                .setDid("did:web:issuer.example.com")
                .build();

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.canSign("did:web:issuer.example.com"));
    }

    @Test
    void canSignReturnsCannotSignWithNoActiveKeyCause() {
        configuredCanSignResp = CanSignResp.newBuilder()
                .setOutcome(org.niis.xroad.edc.issuer.provisioning.proto.CanSignOutcome.CANNOT_SIGN)
                .setCause(org.niis.xroad.edc.issuer.provisioning.proto.CanSignCause.NO_ACTIVE_KEY)
                .build();

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.cannotSign(CanSignCause.NO_ACTIVE_KEY));
    }

    @Test
    void canSignReturnsCannotSignWithDidNotPublishedCause() {
        configuredCanSignResp = CanSignResp.newBuilder()
                .setOutcome(org.niis.xroad.edc.issuer.provisioning.proto.CanSignOutcome.CANNOT_SIGN)
                .setCause(org.niis.xroad.edc.issuer.provisioning.proto.CanSignCause.DID_NOT_PUBLISHED)
                .build();

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.cannotSign(CanSignCause.DID_NOT_PUBLISHED));
    }

    @Test
    void canSignReturnsCannotSignWithKeySecretNotReadableCause() {
        configuredCanSignResp = CanSignResp.newBuilder()
                .setOutcome(org.niis.xroad.edc.issuer.provisioning.proto.CanSignOutcome.CANNOT_SIGN)
                .setCause(org.niis.xroad.edc.issuer.provisioning.proto.CanSignCause.KEY_SECRET_NOT_READABLE)
                .build();

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.cannotSign(CanSignCause.KEY_SECRET_NOT_READABLE));
    }

    @Test
    void canSignReturnsCannotSignWithTimeoutCauseOnDeadlineOverrun() {
        configuredCanSignResp = CanSignResp.newBuilder()
                .setOutcome(org.niis.xroad.edc.issuer.provisioning.proto.CanSignOutcome.CAN_SIGN)
                .setDid("did:web:issuer.example.com")
                .build();
        canSignDelayMillis = 4_000;

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.cannotSign(CanSignCause.TIMEOUT));
    }

    @Test
    void canSignReturnsCannotSignWithIssuerServiceUnreachableCauseOnAnyOtherGrpcFailure() {
        canSignUnavailable = true;

        var result = client.canSign("ctx-id");

        assertThat(result).isEqualTo(CanSignResult.cannotSign(CanSignCause.ISSUER_SERVICE_UNREACHABLE));
    }

    /**
     * A closed port refuses the connection attempt outright; a wait-for-ready stub would instead queue the call
     * until {@link IssuerProvisioningRpcClient#CAN_SIGN_DEADLINE_MILLIS} and come back as {@link CanSignCause#TIMEOUT}.
     */
    @Test
    void canSignReturnsCannotSignWithIssuerServiceUnreachableCauseFastOnAClosedPort() throws Exception {
        int closedPort;
        try (var probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }

        var closedPortChannelFactory = mock(RpcChannelFactory.class);
        var closedPortChannelProperties = mock(IssuerProvisioningRpcChannelProperties.class);
        var closedPortChannel = ManagedChannelBuilder.forAddress("localhost", closedPort).usePlaintext().build();
        when(closedPortChannelFactory.createChannel(closedPortChannelProperties)).thenReturn(closedPortChannel);

        var closedPortClient = new IssuerProvisioningRpcClient(closedPortChannelFactory, closedPortChannelProperties);
        closedPortClient.afterPropertiesSet();
        try {
            var start = System.nanoTime();
            var result = closedPortClient.canSign("ctx-id");
            var elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(result).isEqualTo(CanSignResult.cannotSign(CanSignCause.ISSUER_SERVICE_UNREACHABLE));
            assertThat(elapsed).isLessThan(Duration.ofMillis(IssuerProvisioningRpcClient.CAN_SIGN_DEADLINE_MILLIS));
        } finally {
            closedPortClient.close();
            closedPortChannel.shutdownNow();
        }
    }

    @Test
    void createParticipantContextSwallowsAConflictFromTheIssuer() {
        configuredCreateParticipantContextError = ErrorCode.PARTICIPANT_CONTEXT_CONFLICT;

        client.createParticipantContext("ctx-id", "did:web:issuer.example.com", "https://issuer.example.com",
                "key-id", "key-alias");

        var req = capturedCreateParticipantContextReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getDid()).isEqualTo("did:web:issuer.example.com");
    }

    @Test
    void createParticipantContextSwallowsAConflictSentWithItsOriginPrefix() {
        configuredCreateParticipantContextRawErrorCode = "dataspace." + ErrorCode.PARTICIPANT_CONTEXT_CONFLICT.code();

        client.createParticipantContext("ctx-id", "did:web:issuer.example.com", "https://issuer.example.com",
                "key-id", "key-alias");

        var req = capturedCreateParticipantContextReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getDid()).isEqualTo("did:web:issuer.example.com");
    }

    @Test
    void createParticipantContextRethrowsAnyOtherErrorFromTheIssuer() {
        configuredCreateParticipantContextError = ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;

        assertThatThrownBy(() -> client.createParticipantContext("ctx-id", "did:web:issuer.example.com",
                "https://issuer.example.com", "key-id", "key-alias"))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED)).isTrue());
    }
}
