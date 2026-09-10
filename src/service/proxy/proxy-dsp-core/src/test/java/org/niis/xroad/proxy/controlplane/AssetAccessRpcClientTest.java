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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    @Mock
    private AssetAccessClientProperties.Cache cacheProperties;

    private Server server;
    private ManagedChannel channel;
    private AssetAccessRpcClient client;

    private final AtomicReference<AcquireAssetAccessReq> capturedRequest = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private AcquireAssetAccessResp configuredResponse;
    private StatusRuntimeException configuredError;
    private CountDownLatch holdLatch;

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new AssetAccessServiceGrpc.AssetAccessServiceImplBase() {
            @Override
            public void acquire(AcquireAssetAccessReq request, StreamObserver<AcquireAssetAccessResp> responseObserver) {
                capturedRequest.set(request);
                requestCount.incrementAndGet();
                if (holdLatch != null) {
                    try {
                        holdLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        responseObserver.onError(Status.CANCELLED.asRuntimeException());
                        return;
                    }
                }
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
        when(clientProperties.protocol()).thenReturn("http-dsp-profile-2025-1");
        when(clientProperties.cache()).thenReturn(cacheProperties);
        when(cacheProperties.enabled()).thenReturn(true);
        when(cacheProperties.defaultTtl()).thenReturn(Duration.ofMinutes(5));
        when(cacheProperties.maximumSize()).thenReturn(10_000L);

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
    void acquireSuccessReturnsTypedEndpointAndAuthorization() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-abc-123")
                .build();

        var result = client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");

        assertThat(result.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result.authorization()).isEqualTo("token-abc-123");

        var request = capturedRequest.get();
        assertThat(request.getParticipantContextId()).isEqualTo("test-participant-ctx");
        assertThat(request.getAssetId()).isEqualTo("asset-1");
        assertThat(request.getCounterPartyId()).isEqualTo("provider-1");
        assertThat(request.getCounterPartyAddress()).isEqualTo("http://provider/dsp");
        assertThat(request.getProtocol()).isEqualTo("http-dsp-profile-2025-1");
    }

    @Test
    void acquireSuccessWithNullAuthorizationReturnsNullAuthorization() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var result = client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");

        assertThat(result.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result.authorization()).isNull();
    }

    @Test
    void acquireFailureThrowsStatusRuntimeException() {
        configuredError = new StatusRuntimeException(Status.INTERNAL);

        assertThatThrownBy(() -> client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp"))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    @Test
    void acquireSuccessCacheHitReturnsWithoutSecondGrpcCall() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-abc-123")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        var result1 = client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");
        var result2 = client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");

        assertThat(result1.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.authorization()).isEqualTo("token-abc-123");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccessDifferentKeyCausesCacheMiss() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");
        client.acquireAssetAccess("test-participant-ctx", "asset-2", "provider-1", "http://provider/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessDifferentParticipantContextCausesCacheMiss() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("ctx-a", "asset-1", "provider-1", "http://provider/dsp");
        client.acquireAssetAccess("ctx-b", "asset-1", "provider-1", "http://provider/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessDifferentCounterPartyAddressCausesCacheMiss() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://providerA/dsp");
        client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://providerB/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessWithoutExpiresAtUsesDefaultTtl() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var result1 = client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");
        var result2 = client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");

        assertThat(result1.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccessConcurrentCallsForSameKeyCollapseToSingleGrpcRoundTrip() throws Exception {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-x")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();
        holdLatch = new CountDownLatch(1);

        int threadCount = 8;
        var executor = Executors.newFixedThreadPool(threadCount);
        var startGate = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");
                }));
            }
            startGate.countDown();
            // Allow worker threads to pile up at Caffeine's per-key loader lock — only one
            // is permitted to call the gRPC loader, the rest must wait for its result.
            Thread.sleep(200);
            holdLatch.countDown();

            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccessCacheDisabledBypassesCache() {
        configuredResponse = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        // Rebuild client with cache disabled. Tear down original channel and stub a fresh one so init() succeeds.
        client.close();
        channel.shutdownNow();
        var freshChannel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        channel = freshChannel;
        Mockito.reset(rpcChannelFactory);
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(freshChannel);
        when(cacheProperties.enabled()).thenReturn(false);

        client = new AssetAccessRpcClient(rpcChannelFactory, channelProperties, clientProperties);
        client.init();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");
        client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://provider/dsp");

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessCallOptionsCarryDeadline() {
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

        client.acquireAssetAccess("test-participant-ctx", "asset-1", "provider-1", "http://p/dsp");

        assertThat(deadlineRef.get()).isNotNull();
    }
}
