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
package org.niis.xroad.securityserver.restapi.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.IdentityHubProvisioningServiceGrpc;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialResp;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityHubProvisioningRpcClientTest {

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private IdentityHubProvisioningRpcChannelProperties channelProperties;

    private Server server;
    private ManagedChannel channel;
    private IdentityHubProvisioningRpcClient client;

    private GetCredentialRequestStateResp configuredStateResp;
    private GetParticipantContextExistsResp configuredExistsResp;
    private final AtomicReference<CreateParticipantContextReq> capturedCreateReq = new AtomicReference<>();
    private final AtomicReference<RequestCredentialReq> capturedRequestCredReq = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new IdentityHubProvisioningServiceGrpc.IdentityHubProvisioningServiceImplBase() {
            @Override
            public void createParticipantContext(CreateParticipantContextReq request,
                                                 StreamObserver<CreateParticipantContextResp> responseObserver) {
                capturedCreateReq.set(request);
                responseObserver.onNext(CreateParticipantContextResp.newBuilder().build());
                responseObserver.onCompleted();
            }

            @Override
            public void requestCredential(RequestCredentialReq request,
                                          StreamObserver<RequestCredentialResp> responseObserver) {
                capturedRequestCredReq.set(request);
                responseObserver.onNext(RequestCredentialResp.newBuilder().setRequestId("req-42").build());
                responseObserver.onCompleted();
            }

            @Override
            public void getCredentialRequestState(GetCredentialRequestStateReq request,
                                                  StreamObserver<GetCredentialRequestStateResp> responseObserver) {
                responseObserver.onNext(configuredStateResp);
                responseObserver.onCompleted();
            }

            @Override
            public void getParticipantContextExists(GetParticipantContextExistsReq request,
                                                    StreamObserver<GetParticipantContextExistsResp> responseObserver) {
                responseObserver.onNext(configuredExistsResp);
                responseObserver.onCompleted();
            }
        };

        server = ServerBuilder.forPort(0).addService(mockService).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);

        client = new IdentityHubProvisioningRpcClient(rpcChannelFactory, channelProperties);
        client.init();
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
    void getCredentialRequestStateReturnsStatusWhenFound() {
        configuredStateResp = GetCredentialRequestStateResp.newBuilder()
                .setFound(true)
                .setStatus("ISSUED")
                .build();

        var result = client.getCredentialRequestState("ctx-id", "holder-pid");

        assertThat(result).isEqualTo("ISSUED");
    }

    @Test
    void getCredentialRequestStateReturnsNullWhenNotFound() {
        configuredStateResp = GetCredentialRequestStateResp.newBuilder()
                .setFound(false)
                .build();

        var result = client.getCredentialRequestState("ctx-id", "holder-pid");

        assertThat(result).isNull();
    }

    @Test
    void participantContextExistsReturnsTrueWhenExists() {
        configuredExistsResp = GetParticipantContextExistsResp.newBuilder().setExists(true).build();

        assertThat(client.participantContextExists("ctx-id")).isTrue();
    }

    @Test
    void participantContextExistsReturnsFalseWhenAbsent() {
        configuredExistsResp = GetParticipantContextExistsResp.newBuilder().setExists(false).build();

        assertThat(client.participantContextExists("ctx-id")).isFalse();
    }

    @Test
    void createIdentityHubParticipantContextForwardsAllFields() {
        client.createIdentityHubParticipantContext("ctx-id", "did:web:example", "member-id",
                "https://cred.example/v1", "did:web:example#key-1", "ctx-id-key");

        var req = capturedCreateReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getDid()).isEqualTo("did:web:example");
        assertThat(req.getMemberId()).isEqualTo("member-id");
        assertThat(req.getCredentialServiceUrl()).isEqualTo("https://cred.example/v1");
        assertThat(req.getKeyId()).isEqualTo("did:web:example#key-1");
        assertThat(req.getPrivateKeyAlias()).isEqualTo("ctx-id-key");
    }

    @Test
    void requestMembershipCredentialReturnsRequestId() {
        var requestId = client.requestMembershipCredential("ctx-id", "did:web:issuer", "holder-pid",
                "cred-def-id", "XRoadMembershipCredential", "VC1_0_JWT");

        assertThat(requestId).isEqualTo("req-42");
        var req = capturedRequestCredReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getIssuerDid()).isEqualTo("did:web:issuer");
        assertThat(req.getHolderPid()).isEqualTo("holder-pid");
        assertThat(req.getCredentialDefinitionId()).isEqualTo("cred-def-id");
        assertThat(req.getCredentialType()).isEqualTo("XRoadMembershipCredential");
        assertThat(req.getFormat()).isEqualTo("VC1_0_JWT");
    }
}
