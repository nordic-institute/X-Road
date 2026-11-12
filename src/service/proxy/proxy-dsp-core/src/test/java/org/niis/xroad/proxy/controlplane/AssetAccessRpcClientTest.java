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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import com.google.protobuf.Any;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessWinner;
import org.niis.xroad.edc.assetaccess.proto.ReportCounterPartyFailureReq;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACCESS_REFUSED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

@ExtendWith(MockitoExtension.class)
class AssetAccessRpcClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MARGIN = Duration.ofSeconds(5);
    @Mock
    private RpcChannelFactory rpcChannelFactory;
    @Mock
    private ControlPlaneRpcChannelProperties channelProperties;
    @Mock
    private AssetAccessClientProperties clientProperties;
    @Mock
    private AssetAccessClientProperties.Cache cacheProperties;

    private static final List<AcquisitionCandidate> PROVIDER =
            List.of(new AcquisitionCandidate("provider-1", "http://provider/dsp"));

    private static final String CONSUMER_CTX = "DEV:COM:222";
    private static final ClientId TESTCLIENT = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final String PETSTORE =
            ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "petstore").asEncodedId();
    private static final AcquisitionCandidate CANDIDATE_SS0 = new AcquisitionCandidate(
            "did:web:ss0.example.org%3A7183:v1:DEV:COM:222",
            "https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    private static final AcquisitionCandidate CANDIDATE_SS1 = new AcquisitionCandidate(
            "did:web:ss1.example.org%3A7183:v1:DEV:COM:222",
            "https://ss1.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    private static final List<AcquisitionCandidate> SS0 = List.of(CANDIDATE_SS0);
    private static final List<AcquisitionCandidate> SS0_AND_SS1 = List.of(CANDIDATE_SS0, CANDIDATE_SS1);

    private Server server;
    private ManagedChannel channel;
    private AssetAccessRpcClient client;

    private final AtomicReference<AcquireAssetAccessReq> capturedRequest = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<Long> capturedDeadlineMillis = new AtomicReference<>();
    private AcquireAssetAccessResp configuredResponse;
    private StatusRuntimeException configuredError;
    private CountDownLatch holdLatch;

    private final AtomicLong refusalNanos = new AtomicLong();

    private final AtomicReference<ReportCounterPartyFailureReq> capturedReport = new AtomicReference<>();
    private final CountDownLatch reportReceived = new CountDownLatch(1);
    private CountDownLatch reportHoldLatch;

    @BeforeEach
    void setUp() throws Exception {
        var mockService = new AssetAccessServiceGrpc.AssetAccessServiceImplBase() {
            @Override
            public void acquire(AcquireAssetAccessReq request, StreamObserver<AcquireAssetAccessResp> responseObserver) {
                capturedRequest.set(request);
                requestCount.incrementAndGet();
                var callDeadline = Context.current().getDeadline();
                capturedDeadlineMillis.set(callDeadline == null ? null : callDeadline.timeRemaining(TimeUnit.MILLISECONDS));
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

            @Override
            public void reportCounterPartyFailure(ReportCounterPartyFailureReq request,
                                                  StreamObserver<Empty> responseObserver) {
                capturedReport.set(request);
                reportReceived.countDown();
                if (reportHoldLatch != null) {
                    try {
                        reportHoldLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
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
        when(clientProperties.cache()).thenReturn(cacheProperties);
        when(cacheProperties.enabled()).thenReturn(true);
        when(cacheProperties.defaultTtl()).thenReturn(Duration.ofMinutes(5));
        when(cacheProperties.maximumSize()).thenReturn(10_000L);
        when(cacheProperties.margin()).thenReturn(Duration.ofSeconds(5));

        client = new AssetAccessRpcClient(rpcChannelFactory, channelProperties, clientProperties, refusalNanos::get);
        client.init();
    }

    @AfterEach
    void tearDown() {
        if (reportHoldLatch != null) {
            reportHoldLatch.countDown();
        }
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private static AcquireAssetAccessResp.Builder responseBuilder() {
        return AcquireAssetAccessResp.newBuilder()
                .setWinner(AssetAccessWinner.newBuilder()
                        .setCounterPartyId("provider-1")
                        .setCounterPartyAddress("http://provider/dsp"));
    }

    private void stubProtocol() {
        when(clientProperties.protocol()).thenReturn("http-dsp-profile-2025-1");
        when(clientProperties.acquisitionDeadlineMargin()).thenReturn(MARGIN);
    }

    @Test
    void acquireSuccessReturnsTypedEndpointAndAuthorization() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-abc-123")
                .build();

        var result = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(result.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result.authorization()).isEqualTo("token-abc-123");

        var request = capturedRequest.get();
        assertThat(request.getParticipantContextId()).isEqualTo("test-participant-ctx");
        assertThat(request.getAssetId()).isEqualTo("asset-1");
        assertThat(request.getCandidatesList()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCounterPartyId()).isEqualTo("provider-1");
            assertThat(candidate.getCounterPartyAddress()).isEqualTo("http://provider/dsp");
        });
        assertThat(request.getProtocol()).isEqualTo("http-dsp-profile-2025-1");
    }

    @Test
    void acquireSuccessWithNullAuthorizationReturnsNullAuthorization() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var result = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(result.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result.authorization()).isNull();
    }

    @Test
    void acquireFailureThrowsStatusRuntimeException() {
        stubProtocol();
        configuredError = new StatusRuntimeException(Status.INTERNAL);

        assertThatThrownBy(() -> client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    @Test
    void acquireSuccessCacheHitReturnsWithoutSecondGrpcCall() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setAuthorization("token-abc-123")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        var result1 = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        var result2 = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(result1.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.authorization()).isEqualTo("token-abc-123");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccessDifferentKeyCausesCacheMiss() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        client.acquireAssetAccess("test-participant-ctx", "asset-2", PROVIDER, null, TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessDifferentParticipantContextCausesCacheMiss() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("ctx-a", "asset-1", PROVIDER, null, TIMEOUT);
        client.acquireAssetAccess("ctx-b", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessDifferentCounterPartyAddressCausesCacheMiss() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1",
                List.of(new AcquisitionCandidate("provider-1", "http://providerA/dsp")), null, TIMEOUT);
        client.acquireAssetAccess("test-participant-ctx", "asset-1",
                List.of(new AcquisitionCandidate("provider-1", "http://providerB/dsp")), null, TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessWithoutExpiresAtUsesDefaultTtl() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .build();

        var result1 = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        var result2 = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(result1.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(result2.endpoint()).isEqualTo("http://provider/api/data");
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireSuccessConcurrentCallsForSameKeyCollapseToSingleGrpcRoundTrip() throws Exception {
        stubProtocol();
        configuredResponse = responseBuilder()
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
                    return client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
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
        stubProtocol();
        configuredResponse = responseBuilder()
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

        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSuccessCallOptionsCarryDeadline() {
        stubProtocol();
        configuredResponse = responseBuilder()
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

        client.acquireAssetAccess("test-participant-ctx", "asset-1",
                List.of(new AcquisitionCandidate("provider-1", "http://p/dsp")), null, TIMEOUT);

        assertThat(deadlineRef.get()).isNotNull();
    }

    @Test
    void acquireForwardsConsumerClientId() {
        stubProtocol();
        configuredResponse = responseBuilder().setEndpoint("http://provider/api/data").build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER,
                ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT"), TIMEOUT);

        assertThat(capturedRequest.get().hasClientId()).isTrue();
        assertThat(capturedRequest.get().getClientId()).isEqualTo("DEV:COM:222:TESTCLIENT");
    }

    @Test
    void acquireWithoutConsumerClientIdLeavesTheFieldUnset() {
        stubProtocol();
        configuredResponse = responseBuilder().setEndpoint("http://provider/api/data").build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(capturedRequest.get().hasClientId()).isFalse();
    }

    @Test
    void acquireSuccessDifferentConsumerClientCausesCacheMiss() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER,
                ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT"), TIMEOUT);
        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER,
                ClientId.Conf.create("DEV", "COM", "222", "OTHER"), TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void acquireSameCandidatesInADifferentOrderHitTheCache() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();
        var a = new AcquisitionCandidate("provider-a", "http://provider-a/dsp");
        var b = new AcquisitionCandidate("provider-b", "http://provider-b/dsp");
        client.acquireAssetAccess("test-participant-ctx", "asset-1", List.of(a, b), null, TIMEOUT);
        client.acquireAssetAccess("test-participant-ctx", "asset-1", List.of(b, a), null, TIMEOUT);
        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(capturedRequest.get().getCandidatesList()).extracting("counterPartyId")
                .containsExactly("provider-a", "provider-b");
    }

    @Test
    void acquireSeparatelyBuiltCandidatesEqualInValueHitTheCache() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();
        var first = List.of(
                new AcquisitionCandidate("provider-a", "http://provider-a/dsp"),
                new AcquisitionCandidate("provider-b", "http://provider-b/dsp"));
        var second = List.of(
                new AcquisitionCandidate(String.valueOf("provider-a".toCharArray()),
                        String.join("", "http://", "provider-a", "/dsp")),
                new AcquisitionCandidate(String.valueOf("provider-b".toCharArray()),
                        String.join("", "http://", "provider-b", "/dsp")));

        assertThat(second.get(0)).isNotSameAs(first.get(0));
        assertThat(second.get(1)).isNotSameAs(first.get(1));
        assertThat(second).isEqualTo(first);

        client.acquireAssetAccess("test-participant-ctx", "asset-1", first, null, TIMEOUT);
        client.acquireAssetAccess("test-participant-ctx", "asset-1", second, null, TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void acquireCarriesTheWinnerOnAFreshAcquisitionAndOnACacheHit() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .setWinner(AssetAccessWinner.newBuilder()
                        .setCounterPartyId("provider-1")
                        .setCounterPartyAddress("https://ss-a.example:8183/dsp"))
                .build();

        var fresh = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        var cached = client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(List.of(fresh, cached)).allSatisfy(result -> {
            assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
            assertThat(result.winner().counterPartyAddress()).isEqualTo("https://ss-a.example:8183/dsp");
        });
    }

    @Test
    void invalidatingByWinnerDropsTheCachedAccessWonByTheServerAndKeepsTheSameHostOnAnotherPort() {
        stubProtocol();
        configuredResponse = responseBuilder()
                .setEndpoint("http://provider/api/data")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .setWinner(AssetAccessWinner.newBuilder()
                        .setCounterPartyId("provider-1")
                        .setCounterPartyAddress("https://ss-a.example:8183/dsp"))
                .build();
        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);

        client.invalidateAssetAccessWonBy("https://ss-a.example:9999/dsp");
        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        assertThat(requestCount.get()).isEqualTo(1);

        client.invalidateAssetAccessWonBy("https://SS-A.example:8183/other");
        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void withTheCacheDisabledInvalidatingByWinnerIsANoOpAndTheReportIsStillSent() throws Exception {
        client.close();
        channel.shutdownNow();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        Mockito.reset(rpcChannelFactory);
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);
        when(cacheProperties.enabled()).thenReturn(false);
        client = new AssetAccessRpcClient(rpcChannelFactory, channelProperties, clientProperties);
        client.init();

        assertThatCode(() -> client.invalidateAssetAccessWonBy("https://ss-a.example:8183/dsp"))
                .doesNotThrowAnyException();
        client.reportCounterPartyFailure("provider-1", "https://ss-a.example:8183/dsp");

        assertThat(reportReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedReport.get().getCounterPartyAddress()).isEqualTo("https://ss-a.example:8183/dsp");
    }

    @Test
    void acquireTellsTheControlPlaneItsTimeoutAndWaitsAMarginLongerThanThat() {
        stubProtocol();
        configuredResponse = responseBuilder().setEndpoint("http://provider/api/data").build();
        client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null, TIMEOUT);
        assertThat(capturedRequest.get().getTimeoutMillis()).isEqualTo(TIMEOUT.toMillis());
        assertThat(capturedDeadlineMillis.get())
                .isGreaterThan(TIMEOUT.toMillis())
                .isLessThanOrEqualTo(TIMEOUT.plus(MARGIN).toMillis());
    }
    @Test
    void acquireGivesUpOnASlowControlPlaneOnceTheTimeoutAndMarginHavePassed() {
        stubProtocol();
        when(clientProperties.acquisitionDeadlineMargin()).thenReturn(Duration.ofMillis(100));
        holdLatch = new CountDownLatch(1);
        var startedAt = System.nanoTime();
        try {
            assertThatThrownBy(() -> client.acquireAssetAccess("test-participant-ctx", "asset-1", PROVIDER, null,
                    Duration.ofMillis(100)))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode()).endsWith(NETWORK_ERROR.code()));
        } finally {
            holdLatch.countDown();
        }
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(3));
    }
    @Test
    void reportDeliversCounterPartyIdAndAddress() throws Exception {
        client.reportCounterPartyFailure("provider-1", "http://provider/dsp");

        assertThat(reportReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedReport.get().getCounterPartyId()).isEqualTo("provider-1");
        assertThat(capturedReport.get().getCounterPartyAddress()).isEqualTo("http://provider/dsp");
    }

    @Test
    void reportReturnsBeforeTheServerAnswers() throws Exception {
        reportHoldLatch = new CountDownLatch(1);

        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> client.reportCounterPartyFailure("provider-1", "http://provider/dsp"));

        assertThat(reportReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reportHoldLatch.getCount()).isEqualTo(1);
    }

    @Test
    void reportCallCarriesAShortDeadlineAndNoWaitForReady() throws Exception {
        var reportCallOptions = new AtomicReference<CallOptions>();
        var capturingInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                if ("ReportCounterPartyFailure".equals(method.getBareMethodName())) {
                    reportCallOptions.set(callOptions);
                }
                return next.newCall(method, callOptions);
            }
        };

        client.close();
        channel.shutdownNow();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .intercept(capturingInterceptor)
                .build();
        Mockito.reset(rpcChannelFactory);
        when(rpcChannelFactory.createChannel(channelProperties)).thenReturn(channel);
        client = new AssetAccessRpcClient(rpcChannelFactory, channelProperties, clientProperties);
        client.init();

        client.reportCounterPartyFailure("provider-1", "http://provider/dsp");

        assertThat(reportReceived.await(5, TimeUnit.SECONDS)).isTrue();
        var options = reportCallOptions.get();
        assertThat(options.isWaitForReady()).isFalse();
        assertThat(options.getDeadline()).isNotNull();
        assertThat(options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(5_000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dsp_access_refused", "dsp_dataset_not_found", "dsp_offers_not_found"})
    void aRefusedCallRepeatedWithinTheLifetimeIsRefusedAgainWithoutContactingTheControlPlane(String refusalCode) {
        stubProtocol();
        configuredError = controlPlaneError("dataspace." + refusalCode);

        var first = acquireRefused(SS0, TESTCLIENT);
        var repeat = acquireRefused(SS0, TESTCLIENT);

        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(repeat.getCode()).isEqualTo(first.getCode()).endsWith(refusalCode);
    }

    @Test
    void aTransferRefusalIsNotRememberedAndTheRepeatAsksTheControlPlaneAgain() {
        stubProtocol();
        configuredError = controlPlaneError("dataspace." + DSP_TRANSFER_FAILED.code());

        var first = acquireRefused(SS0, TESTCLIENT);
        var repeat = acquireRefused(SS0, TESTCLIENT);

        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(repeat.getCode()).isEqualTo(first.getCode()).endsWith(DSP_TRANSFER_FAILED.code());
    }

    @Test
    void aTransportFaultOfTheControlPlaneCallIsNotRemembered() {
        stubProtocol();
        configuredError = new StatusRuntimeException(Status.UNAVAILABLE);

        assertThatThrownBy(() -> client.acquireAssetAccess(CONSUMER_CTX, PETSTORE, SS0, TESTCLIENT, TIMEOUT))
                .isInstanceOf(StatusRuntimeException.class);
        assertThatThrownBy(() -> client.acquireAssetAccess(CONSUMER_CTX, PETSTORE, SS0, TESTCLIENT, TIMEOUT))
                .isInstanceOf(StatusRuntimeException.class);

        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void aRefusedCallAsksTheControlPlaneAgainOnceTheMemoryHasExpiredAndCachesTheGrant() {
        stubProtocol();
        configuredError = controlPlaneError("dataspace." + DSP_ACCESS_REFUSED.code());
        acquireRefused(SS0, TESTCLIENT);
        refusalNanos.addAndGet(RefusalMemory.LIFETIME.minusMillis(1).toNanos());
        acquireRefused(SS0, TESTCLIENT);
        configuredError = null;
        configuredResponse = responseBuilder()
                .setEndpoint("https://ss0.example.org/pets/1")
                .setExpiresAtEpochSeconds(Instant.now().getEpochSecond() + 3600)
                .build();

        refusalNanos.addAndGet(Duration.ofMillis(1).toNanos());
        var granted = client.acquireAssetAccess(CONSUMER_CTX, PETSTORE, SS0, TESTCLIENT, TIMEOUT);
        var cached = client.acquireAssetAccess(CONSUMER_CTX, PETSTORE, SS0, TESTCLIENT, TIMEOUT);

        assertThat(granted.endpoint()).isEqualTo("https://ss0.example.org/pets/1");
        assertThat(cached.endpoint()).isEqualTo("https://ss0.example.org/pets/1");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void aRefusalForOneCandidateSetDoesNotKeepAnotherCandidateSetFromAsking() {
        stubProtocol();
        configuredError = controlPlaneError("dataspace." + DSP_ACCESS_REFUSED.code());
        acquireRefused(SS0, TESTCLIENT);
        configuredError = null;
        configuredResponse = responseBuilder().setEndpoint("https://ss1.example.org/pets/1").build();

        var result = client.acquireAssetAccess(CONSUMER_CTX, PETSTORE, SS0_AND_SS1, TESTCLIENT, TIMEOUT);

        assertThat(result.endpoint()).isEqualTo("https://ss1.example.org/pets/1");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    void aRefusalIsNotRememberedForAnotherCaller() {
        stubProtocol();
        configuredError = controlPlaneError("dataspace." + DSP_ACCESS_REFUSED.code());

        acquireRefused(SS0, TESTCLIENT);
        acquireRefused(SS0, ClientId.Conf.create("DEV", "COM", "222", "OTHERCLIENT"));

        assertThat(requestCount.get()).isEqualTo(2);
    }

    private XrdRuntimeException acquireRefused(List<AcquisitionCandidate> candidates, ClientId consumer) {
        var failure = catchThrowable(() ->
                client.acquireAssetAccess(CONSUMER_CTX, PETSTORE, candidates, consumer, TIMEOUT));
        assertThat(failure).isInstanceOf(XrdRuntimeException.class);
        return (XrdRuntimeException) failure;
    }

    private static StatusRuntimeException controlPlaneError(String code) {
        return StatusProto.toStatusRuntimeException(com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.INTERNAL.value())
                .addDetails(Any.pack(XrdRuntimeExceptionProto.newBuilder()
                        .setIdentifier("control-plane-failure")
                        .setErrorCode(code)
                        .build()))
                .build());
    }
}
