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
import org.niis.xroad.edc.issuer.provisioning.proto.IssuerProvisioningServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialReq;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialResp;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new IssuerProvisioningServiceGrpc.IssuerProvisioningServiceImplBase() {
            @Override
            public void revokeCredential(RevokeCredentialReq request, StreamObserver<RevokeCredentialResp> responseObserver) {
                capturedRevokeReq.set(request);
                responseObserver.onNext(configuredRevokeResp);
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
    void revokeCredentialForwardsAllFieldsAndReturnsRevokedTrue() {
        configuredRevokeResp = RevokeCredentialResp.newBuilder().setRevoked(true).build();

        var revoked = client.revokeCredential("ctx-id", "XROAD-INSTANCE", "GOV", "1234");

        assertThat(revoked).isTrue();
        var req = capturedRevokeReq.get();
        assertThat(req.getParticipantContextId()).isEqualTo("ctx-id");
        assertThat(req.getXroadInstance()).isEqualTo("XROAD-INSTANCE");
        assertThat(req.getMemberClass()).isEqualTo("GOV");
        assertThat(req.getMemberCode()).isEqualTo("1234");
    }

    @Test
    void revokeCredentialReturnsRevokedFalseWhenNotRevoked() {
        configuredRevokeResp = RevokeCredentialResp.newBuilder().setRevoked(false).build();

        var revoked = client.revokeCredential("ctx-id", "XROAD-INSTANCE", "GOV", "1234");

        assertThat(revoked).isFalse();
    }
}
