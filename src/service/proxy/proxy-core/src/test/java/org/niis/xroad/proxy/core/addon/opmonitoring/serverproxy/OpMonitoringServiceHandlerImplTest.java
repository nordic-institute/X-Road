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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.AccessScope;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpMonitoringServiceHandlerImplTest {
    private static final String QUERY = "<query/>";
    private static final String QUERY_CONTENT_TYPE = "text/xml; charset=UTF-8";
    private static final String RESPONSE = "<response/>";
    private static final String RESPONSE_CONTENT_TYPE = "text/xml;charset=utf-8";

    private final AtomicReference<QueryOperationalDataReq> received = new AtomicReference<>();
    private volatile Status failure;
    private Server server;
    private ManagedChannel channel;
    private OpMonitorClient opMonitorClient;
    private OpMonitoringServiceHandlerImpl handler;

    @BeforeEach
    void setUp() throws IOException {
        server = ServerBuilder.forPort(0)
                .addService(new OpMonitorServiceGrpc.OpMonitorServiceImplBase() {
                    @Override
                    public void queryOperationalData(QueryOperationalDataReq request,
                                                     StreamObserver<QueryOperationalDataResp> responseObserver) {
                        received.set(request);
                        if (failure != null) {
                            responseObserver.onError(failure.asRuntimeException());
                            return;
                        }
                        responseObserver.onNext(QueryOperationalDataResp.newBuilder()
                                .setResponse(ByteString.copyFromUtf8(RESPONSE))
                                .setContentType(RESPONSE_CONTENT_TYPE)
                                .build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        var channelFactory = mock(RpcChannelFactory.class);
        var channelProperties = mock(OpMonitorRpcChannelProperties.class);
        when(channelFactory.createChannel(channelProperties)).thenReturn(channel);
        opMonitorClient = new OpMonitorClient(channelFactory, channelProperties);
        opMonitorClient.init();
        handler = new OpMonitoringServiceHandlerImpl(mock(ServerConfProvider.class), mock(GlobalConfProvider.class),
                () -> opMonitorClient);
    }

    @AfterEach
    void tearDown() {
        handler.destroy();
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void queryIsSentToOpMonitorOverGrpcAndItsResponseIsReturned() throws IOException {
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        var result = handler.startHandling(null, proxyMessage(), opMonitoringData);

        assertThat(received.get().getSoapQuery().toStringUtf8()).isEqualTo(QUERY);
        assertThat(received.get().getContentType()).isEqualTo(QUERY_CONTENT_TYPE);
        assertThat(received.get().getAccessScope()).isEqualTo(AccessScope.ACCESS_SCOPE_UNSPECIFIED);
        assertThat(result.responseContentType()).isEqualTo(RESPONSE_CONTENT_TYPE);
        assertThat(new String(result.responseContent().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(RESPONSE);
        var record = opMonitoringData.toProto();
        assertThat(record.hasRequestOutTs()).isTrue();
        assertThat(record.hasResponseInTs()).isTrue();
    }

    @Test
    void failedQueryIsReportedAsServiceFailureWithoutResponseTimestamp() {
        failure = Status.INTERNAL;
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).startsWith(X_SERVICE_FAILED_X + "."));

        var record = opMonitoringData.toProto();
        assertThat(record.hasRequestOutTs()).isTrue();
        assertThat(record.hasResponseInTs()).isFalse();
    }

    private static ProxyMessage proxyMessage() throws IOException {
        var proxyMessage = mock(ProxyMessage.class, RETURNS_DEEP_STUBS);
        when(proxyMessage.getSoapContentType()).thenReturn(QUERY_CONTENT_TYPE);
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(0).write(QUERY.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(proxyMessage).writeSoapContent(any(OutputStream.class));
        return proxyMessage;
    }
}
