/*
 * The MIT License
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
package org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy;

import com.google.protobuf.ByteString;
import io.grpc.ConnectivityState;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImplTest.globalConfProvider;
import static org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImplTest.proxyMessage;

/**
 * Queries sent to an op-monitor that is not there, goes away or does not answer, over a real gRPC channel with
 * a short deadline.
 */
class OpMonitoringServiceHandlerOutageTest {
    private static final int DEADLINE_MILLIS = 500;
    private static final int MARGIN_MILLIS = 3000;
    private static final int LONG_DEADLINE_MILLIS = 20_000;
    private static final String NETWORK_ERROR_CODE = "service_failed.network_error";
    private static final String RESPONSE = "<response/>";
    private static final String LOOPBACK = "127.0.0.1";

    private final List<Server> servers = new ArrayList<>();
    private final List<OpMonitoringServiceHandlerImpl> handlers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        handlers.forEach(OpMonitoringServiceHandlerImpl::destroy);
        servers.forEach(Server::shutdownNow);
    }

    @Test
    void queryToClosedPortFailsWithNetworkErrorWhenTheDeadlineExpires() throws IOException {
        var handler = handler(closedPort(), DEADLINE_MILLIS);
        var opMonitoringData = opMonitoringData();

        var elapsed = failedQuery(handler, opMonitoringData, NETWORK_ERROR_CODE).elapsed();

        assertThat(elapsed).isBetween(Duration.ofMillis(DEADLINE_MILLIS), Duration.ofMillis(DEADLINE_MILLIS + MARGIN_MILLIS));
        assertQueryWasSentButNoResponseArrived(opMonitoringData);
    }

    @Test
    void queryToServerThatNeverRepliesFailsWithNetworkErrorWhenTheDeadlineExpires() throws IOException {
        var started = new CountDownLatch(1);
        var port = startServer(observer -> started.countDown());
        var handler = handler(port, DEADLINE_MILLIS);
        var opMonitoringData = opMonitoringData();

        var elapsed = failedQuery(handler, opMonitoringData, NETWORK_ERROR_CODE).elapsed();

        assertThat(started.getCount()).isZero();
        assertThat(elapsed).isBetween(Duration.ofMillis(DEADLINE_MILLIS), Duration.ofMillis(DEADLINE_MILLIS + MARGIN_MILLIS));
        assertQueryWasSentButNoResponseArrived(opMonitoringData);
    }

    @Test
    void queryFailsWithNetworkErrorAtOnceWhenOpMonitorStopsDuringTheCall() throws Exception {
        var started = new CountDownLatch(1);
        var port = startServer(observer -> started.countDown());
        var handler = handler(port, LONG_DEADLINE_MILLIS);
        var opMonitoringData = opMonitoringData();

        var query = CompletableFuture.supplyAsync(() -> failedQuery(handler, opMonitoringData, NETWORK_ERROR_CODE));
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        servers.getFirst().shutdownNow();

        var failure = query.get(10, TimeUnit.SECONDS);
        assertThat(failure.elapsed()).isLessThan(Duration.ofMillis(LONG_DEADLINE_MILLIS / 2));
        assertThat(failure.error().getCause()).isInstanceOfSatisfying(StatusRuntimeException.class,
                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.CANCELLED));
        assertQueryWasSentButNoResponseArrived(opMonitoringData);
    }

    @Test
    void queryFailsWithNetworkErrorWhenTheDeadlineExpiresWhileWaitingForAReadyTransport() throws Exception {
        var port = startServer(observer -> respond(observer, RESPONSE));
        var client = client(port, DEADLINE_MILLIS);
        var handler = handler(client);
        handler.startHandling(null, proxyMessage(), opMonitoringData());
        stopServerAndAwaitChannelNotReady(client);
        var opMonitoringData = opMonitoringData();

        var elapsed = failedQuery(handler, opMonitoringData, NETWORK_ERROR_CODE).elapsed();

        assertThat(elapsed).isBetween(Duration.ofMillis(DEADLINE_MILLIS), Duration.ofMillis(DEADLINE_MILLIS + MARGIN_MILLIS));
        assertQueryWasSentButNoResponseArrived(opMonitoringData);
    }

    @Test
    void querySucceedsAfterOpMonitorRestarts() throws Exception {
        var port = startServer(observer -> respond(observer, RESPONSE));
        var client = client(port, LONG_DEADLINE_MILLIS);
        var handler = handler(client);
        handler.startHandling(null, proxyMessage(), opMonitoringData());
        stopServerAndAwaitChannelNotReady(client);

        var query = CompletableFuture.supplyAsync(() -> succeededQueryDuration(handler));
        MILLISECONDS.sleep(DEADLINE_MILLIS);
        assertThat(query.isDone()).isFalse();
        startServer(port, observer -> respond(observer, RESPONSE));

        assertThat(query.get(15, TimeUnit.SECONDS)).isLessThan(Duration.ofMillis(LONG_DEADLINE_MILLIS / 2));
    }

    @Test
    void queryCarriesTheChannelDeadlineOfTheDefaultConfiguration() throws Exception {
        var remainingMillis = new AtomicReference<Long>();
        var deadlineReader = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                       ServerCallHandler<ReqT, RespT> next) {
                remainingMillis.set(Context.current().getDeadline().timeRemaining(MILLISECONDS));
                return next.startCall(call, headers);
            }
        };
        var port = startServer(deadlineReader, observer -> respond(observer, RESPONSE));
        var config = XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(Map.of("xroad.common-rpc.channel.op-monitor.port", String.valueOf(port)))
                .build();
        var handler = new OpMonitoringServiceHandlerImpl(mock(ServerConfProvider.class), globalConfProvider(),
                () -> OpMonitorClient.create(new RpcChannelFactory(new InsecureRpcCredentialsConfigurer()), config));
        handlers.add(handler);

        handler.startHandling(null, proxyMessage(), opMonitoringData());

        assertThat(remainingMillis.get()).isGreaterThan(59_000L).isLessThanOrEqualTo(60_000L);
    }

    private static FailedQuery failedQuery(OpMonitoringServiceHandlerImpl handler, OpMonitoringData opMonitoringData,
                                           String expectedCode) {
        var error = new AtomicReference<XrdRuntimeException>();
        var start = System.nanoTime();
        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(expectedCode);
                    error.set(e);
                });
        return new FailedQuery(Duration.ofNanos(System.nanoTime() - start), error.get());
    }

    private static Duration succeededQueryDuration(OpMonitoringServiceHandlerImpl handler) {
        var start = System.nanoTime();
        try {
            var result = handler.startHandling(null, proxyMessage(), opMonitoringData());
            assertThat(new String(result.responseContent().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(RESPONSE);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
        return Duration.ofNanos(System.nanoTime() - start);
    }

    private static void assertQueryWasSentButNoResponseArrived(OpMonitoringData opMonitoringData) {
        var proto = opMonitoringData.toProto();
        assertThat(proto.hasRequestOutTs()).isTrue();
        assertThat(proto.hasResponseInTs()).isFalse();
    }

    private static OpMonitoringData opMonitoringData() {
        return new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);
    }

    private OpMonitoringServiceHandlerImpl handler(int port, int deadlineMillis) {
        return handler(client(port, deadlineMillis));
    }

    private OpMonitoringServiceHandlerImpl handler(OpMonitorClient client) {
        var handler = new OpMonitoringServiceHandlerImpl(mock(ServerConfProvider.class), globalConfProvider(),
                () -> client);
        handlers.add(handler);
        return handler;
    }

    private static OpMonitorClient client(int port, int deadlineMillis) {
        var channelProperties = new OpMonitorRpcChannelProperties() {
            @Override
            public String host() {
                return LOOPBACK;
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public int deadlineAfter() {
                return deadlineMillis;
            }
        };
        var client = new OpMonitorClient(new RpcChannelFactory(new InsecureRpcCredentialsConfigurer()), channelProperties);
        client.init();
        return client;
    }

    private void stopServerAndAwaitChannelNotReady(OpMonitorClient client) throws InterruptedException {
        servers.getFirst().shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(10))
                .until(() -> client.getChannel().getState(false) != ConnectivityState.READY);
        assertThat(client.getChannel().getState(false)).isNotEqualTo(ConnectivityState.READY);
    }

    private static int closedPort() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))) {
            return socket.getLocalPort();
        }
    }

    private int startServer(QueryBehaviour behaviour) throws IOException {
        return bind(0, service(behaviour).bindService());
    }

    private void startServer(int port, QueryBehaviour behaviour) throws IOException {
        bind(port, service(behaviour).bindService());
    }

    private int startServer(ServerInterceptor interceptor, QueryBehaviour behaviour) throws IOException {
        return bind(0, ServerInterceptors.intercept(service(behaviour), interceptor));
    }

    private int bind(int port, ServerServiceDefinition service) throws IOException {
        var server = NettyServerBuilder.forAddress(new InetSocketAddress(LOOPBACK, port))
                .addService(service)
                .build()
                .start();
        servers.add(server);
        return server.getPort();
    }

    private static OpMonitorServiceGrpc.OpMonitorServiceImplBase service(QueryBehaviour behaviour) {
        return new OpMonitorServiceGrpc.OpMonitorServiceImplBase() {
            @Override
            public void queryOperationalData(QueryOperationalDataReq request,
                                             StreamObserver<QueryOperationalDataResp> responseObserver) {
                behaviour.handle(responseObserver);
            }
        };
    }

    private static void respond(StreamObserver<QueryOperationalDataResp> observer, String response) {
        observer.onNext(QueryOperationalDataResp.newBuilder()
                .setResponse(ByteString.copyFromUtf8(response))
                .setContentType("text/xml;charset=utf-8")
                .build());
        observer.onCompleted();
    }

    private record FailedQuery(Duration elapsed, XrdRuntimeException error) {
    }

    @FunctionalInterface
    private interface QueryBehaviour {
        void handle(StreamObserver<QueryOperationalDataResp> observer);
    }
}
