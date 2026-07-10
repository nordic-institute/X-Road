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
import org.niis.xroad.edc.controlplane.provisioning.proto.ControlPlaneProvisioningServiceGrpc;
import org.niis.xroad.edc.controlplane.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.controlplane.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.controlplane.provisioning.proto.PutParticipantContextConfigReq;
import org.niis.xroad.edc.controlplane.provisioning.proto.PutParticipantContextConfigResp;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlPlaneProvisioningRpcClientTest {

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private ControlPlaneProvisioningRpcChannelProperties channelProperties;

    private Server server;
    private ManagedChannel channel;
    private ControlPlaneProvisioningRpcClient client;

    private final AtomicReference<CreateParticipantContextReq> capturedCreateReq = new AtomicReference<>();
    private final AtomicReference<PutParticipantContextConfigReq> capturedPutConfigReq = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new ControlPlaneProvisioningServiceGrpc.ControlPlaneProvisioningServiceImplBase() {
            @Override
            public void createParticipantContext(CreateParticipantContextReq request,
                                                 StreamObserver<CreateParticipantContextResp> responseObserver) {
                capturedCreateReq.set(request);
                responseObserver.onNext(CreateParticipantContextResp.newBuilder().build());
                responseObserver.onCompleted();
            }

            @Override
            public void putParticipantContextConfig(PutParticipantContextConfigReq request,
                                                    StreamObserver<PutParticipantContextConfigResp> responseObserver) {
                capturedPutConfigReq.set(request);
                responseObserver.onNext(PutParticipantContextConfigResp.newBuilder().build());
                responseObserver.onCompleted();
            }
        };

        server = ServerBuilder.forPort(0).addService(mockService).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);

        client = new ControlPlaneProvisioningRpcClient(rpcChannelFactory, channelProperties);
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
    void createParticipantContextForwardsParticipantIdAndDid() {
        client.createParticipantContext("ctx-id", "did:web:example");

        var req = capturedCreateReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getDid()).isEqualTo("did:web:example");
    }

    @Test
    void putParticipantContextConfigForwardsAllFields() {
        client.putParticipantContextConfig("ctx-id", "did:web:example", "https://sts.example/token");

        var req = capturedPutConfigReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getDid()).isEqualTo("did:web:example");
        assertThat(req.getStsTokenUrl()).isEqualTo("https://sts.example/token");
    }
}
