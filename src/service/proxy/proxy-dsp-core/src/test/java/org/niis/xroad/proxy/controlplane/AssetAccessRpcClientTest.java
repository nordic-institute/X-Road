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
package org.niis.xroad.proxy.controlplane;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessRpcClientTest {

    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private AssetAccessRpcChannelProperties channelProperties;
    @Mock
    private AssetAccessClientProperties clientProperties;

    private Server server;
    private ManagedChannel channel;
    private AssetAccessRpcClient client;

    private final AtomicReference<AcquireAssetAccessReq> capturedRequest = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private AcquireAssetAccessResp configuredResponse;
    private StatusRuntimeException configuredError;

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new AssetAccessServiceGrpc.AssetAccessServiceImplBase() {
            @Override
            public void acquire(AcquireAssetAccessReq request, StreamObserver<AcquireAssetAccessResp> responseObserver) {
                capturedRequest.set(request);
                requestCount.incrementAndGet();
                if (configuredError != null) {
                    responseObserver.onError(configuredError);
                } else {
                    responseObserver.onNext(configuredResponse);
                    responseObserver.onCompleted();
                }
            }
        };

        server = ServerBuilder.forPort(0)
                .addService(mockService)
                .build()
                .start();

        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();

        when(channelProperties.host()).thenReturn("localhost");
        when(channelProperties.port()).thenReturn(server.getPort());
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);
        when(clientProperties.participantContextId()).thenReturn("test-participant-ctx");
        when(clientProperties.protocol()).thenReturn("dataspace-protocol-http:2025-1");

        client = new AssetAccessRpcClient(rpcChannelFactory, channelProperties, clientProperties);
        client.init();
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
    void acquireSuccess_returnsTypedEndpointAndAuthorization() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-abc-123")
                .build();

        var result = client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");

        assertThat(result.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result.authorization()).isEqualTo("token-abc-123");

        var request = capturedRequest.get();
        assertThat(request.getParticipantContextId()).isEqualTo("test-participant-ctx");
        assertThat(request.getAssetId()).isEqualTo("asset-1");
        assertThat(request.getCounterPartyId()).isEqualTo("provider-1");
        assertThat(request.getCounterPartyAddress()).isEqualTo("http://provider/dsp");
        assertThat(request.getProtocol()).isEqualTo("dataspace-protocol-http:2025-1");
    }

    @Test
    void acquireSuccess_withNullAuthorization_returnsNullAuthorization() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var result = client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");

        assertThat(result.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result.authorization()).isNull();
    }

    @Test
    void acquireFailure_throwsStatusRuntimeException() {
        configuredError = new StatusRuntimeException(Status.INTERNAL);

        assertThatThrownBy(() -> client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp"))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    @Test
    void acquireSuccess_cacheHitReturnsWithoutSecondGrpcCall() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-abc-123")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        var result1 = client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");
        var result2 = client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");

        assertThat(result1.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.authorization()).isEqualTo("token-abc-123");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccess_differentKeyCausesCacheMiss() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");
        client.acquireAssetAccess("asset-2", "provider-1", "http://provider/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccess_differentParticipantContextCausesCacheMiss() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        // Sequential stubbing: first call resolves to ctx-a, second call to ctx-b.
        when(clientProperties.participantContextId()).thenReturn("ctx-a", "ctx-b");

        client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");
        client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccess_differentCounterPartyAddressCausesCacheMiss() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("asset-1", "provider-1", "http://providerA/dsp");
        client.acquireAssetAccess("asset-1", "provider-1", "http://providerB/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccess_withoutExpiresAtUsesDefaultTtl() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var result1 = client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");
        var result2 = client.acquireAssetAccess("asset-1", "provider-1", "http://provider/dsp");

        assertThat(result1.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccess_callOptionsCarryDeadline() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var deadlineRef = new AtomicReference<Deadline>();
        var capturingInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                deadlineRef.set(callOptions.getDeadline());
                return next.newCall(method, callOptions);
            }
        };

        // Mirror RpcChannelFactory#timeoutInterceptor so the deadline is actually applied at channel level.
        var timeoutInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return next.newCall(method,
                        callOptions.withDeadlineAfter(channelProperties.deadlineAfter(), TimeUnit.MILLISECONDS));
            }
        };

        // Tear down the channel set up by @BeforeEach, then rebuild one that wears the two interceptors.
        client.close();
        channel.shutdownNow();

        when(channelProperties.deadlineAfter()).thenReturn(60_000);
        var interceptedChannel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .intercept(capturingInterceptor)
                .intercept(timeoutInterceptor)
                .build();
        channel = interceptedChannel;
        Mockito.reset(rpcChannelFactory);
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(interceptedChannel);

        client = new AssetAccessRpcClient(rpcChannelFactory, channelProperties, clientProperties);
        client.init();

        client.acquireAssetAccess("asset-1", "provider-1", "http://p/dsp");

        assertThat(deadlineRef.get()).isNotNull();
    }
}
