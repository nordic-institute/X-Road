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

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
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
import java.util.Map;
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
    private static final String TMP_DIR = "build/tmp";
    private static final int CHANNEL_LIMIT = 1000;

    private final AtomicReference<QueryOperationalDataReq> received = new AtomicReference<>();
    private volatile Status failure;
    private volatile XrdRuntimeException answeredError;
    private volatile String responseContentType = RESPONSE_CONTENT_TYPE;
    private volatile int responseSerializedSize;
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
                        if (answeredError != null) {
                            new RpcResponseHandler().handleException(answeredError, responseObserver);
                            return;
                        }
                        responseObserver.onNext(responseSerializedSize > 0
                                ? responseOfSerializedSize(responseSerializedSize)
                                : QueryOperationalDataResp.newBuilder()
                                        .setResponse(ByteString.copyFromUtf8(RESPONSE))
                                        .setContentType(responseContentType)
                                        .build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext()
                .maxInboundMessageSize(CHANNEL_LIMIT).build();
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
        var proto = opMonitoringData.toProto();
        assertThat(proto.hasRequestOutTs()).isTrue();
        assertThat(proto.hasResponseInTs()).isTrue();
    }

    @Test
    void queryContentTypeIsTheOneOfTheProxyMessageAndResponseContentTypeIsReturnedAsReceived() throws Exception {
        var proxyMessage = new ProxyMessage("application/xml", TMP_DIR);
        var soap = mock(SoapMessageImpl.class);
        when(soap.getBytes()).thenReturn(QUERY.getBytes(StandardCharsets.UTF_8));
        proxyMessage.soap(soap, Map.of());
        responseContentType = "text/xml; charset=utf-8";

        var result = handler.startHandling(null, proxyMessage, new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L));

        assertThat(received.get().getContentType()).isEqualTo("text/xml; charset=UTF-8");
        assertThat(received.get().getSoapQuery().toStringUtf8()).isEqualTo(QUERY);
        assertThat(result.responseContentType()).isEqualTo("text/xml; charset=utf-8");
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"INTERNAL", "UNKNOWN", "UNIMPLEMENTED", "RESOURCE_EXHAUSTED", "INVALID_ARGUMENT"})
    void errorStatusAnsweredByOpMonitorIsReportedAsServiceFailureWithResponseTimestamp(Status.Code code) {
        failure = code.toStatus();
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).startsWith(X_SERVICE_FAILED_X + "."));

        var proto = opMonitoringData.toProto();
        assertThat(proto.hasRequestOutTs()).isTrue();
        assertThat(proto.hasResponseInTs()).isTrue();
    }

    @Test
    void errorAnsweredByOpMonitorKeepsItsErrorCodeAndSetsResponseTimestamp() {
        answeredError = XrdRuntimeException.systemException(ErrorCode.DATABASE_ERROR).details("op-monitor database is down").build();
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + ".op_monitor." + ErrorCode.DATABASE_ERROR.code()));

        assertThat(opMonitoringData.toProto().hasResponseInTs()).isTrue();
    }

    @Test
    void responseWithoutContentTypeIsReportedAsInvalidContentTypeWithResponseTimestamp() {
        responseContentType = "";
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + "." + ErrorCode.INVALID_CONTENT_TYPE.code()));

        assertThat(opMonitoringData.toProto().hasResponseInTs()).isTrue();
    }

    @Test
    void responseOfExactlyTheChannelLimitIsReturned() throws IOException {
        responseSerializedSize = CHANNEL_LIMIT;

        var result = handler.startHandling(null, proxyMessage(), new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L));

        assertThat(result.responseContent().readAllBytes()).hasSize(responseOfSerializedSize(CHANNEL_LIMIT).getResponse().size());
    }

    @Test
    void responseOneByteOverTheChannelLimitIsReportedAsTooLargeWithTheLimitAndTheSize() {
        responseSerializedSize = CHANNEL_LIMIT + 1;
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + "." + ErrorCode.OPERATIONAL_DATA_RESPONSE_TOO_LARGE.code());
                    assertThat(e.getDetails())
                            .contains("1001 bytes")
                            .contains("1000 bytes")
                            .contains(CommonRpcConfigKeys.CHANNEL_OP_MONITOR_MAX_INBOUND_MESSAGE_SIZE.key())
                            .contains(OpMonitorConfigKeys.MAX_RECORDS_IN_PAYLOAD.key());
                    assertThat(SoapFault.createFaultXml(e)).contains(e.getDetails());
                });

        var proto = opMonitoringData.toProto();
        assertThat(proto.hasRequestOutTs()).isTrue();
        assertThat(proto.hasResponseInTs()).isTrue();
    }

    @Test
    void resourceExhaustedWithoutTheSizeTextKeepsItsMapping() {
        failure = Status.RESOURCE_EXHAUSTED.withDescription("quota exceeded");
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.getCode()).startsWith(X_SERVICE_FAILED_X + ".");
                    assertThat(e.getCode()).doesNotContain(ErrorCode.OPERATIONAL_DATA_RESPONSE_TOO_LARGE.code());
                });

        assertThat(opMonitoringData.toProto().hasResponseInTs()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Status.Code.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "CANCELLED"})
    void transportStatusIsReportedAsNetworkErrorWithoutResponseTimestamp(Status.Code code) {
        failure = code.toStatus();
        var opMonitoringData = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L);

        assertThatThrownBy(() -> handler.startHandling(null, proxyMessage(), opMonitoringData))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(X_SERVICE_FAILED_X + "." + ErrorCode.NETWORK_ERROR.code()));

        var proto = opMonitoringData.toProto();
        assertThat(proto.hasRequestOutTs()).isTrue();
        assertThat(proto.hasResponseInTs()).isFalse();
    }

    private static QueryOperationalDataResp responseOfSerializedSize(int serializedSize) {
        var empty = QueryOperationalDataResp.newBuilder().setContentType(RESPONSE_CONTENT_TYPE).build();
        int responseBytes = serializedSize - empty.getSerializedSize() - 3;
        var response = QueryOperationalDataResp.newBuilder(empty)
                .setResponse(ByteString.copyFrom(new byte[responseBytes]))
                .build();
        assertThat(response.getSerializedSize()).isEqualTo(serializedSize);
        return response;
    }

    static ProxyMessage proxyMessage() throws IOException {
        var proxyMessage = mock(ProxyMessage.class, RETURNS_DEEP_STUBS);
        when(proxyMessage.getSoapContentType()).thenReturn(QUERY_CONTENT_TYPE);
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(0).write(QUERY.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(proxyMessage).writeSoapContent(any(OutputStream.class));
        return proxyMessage;
    }
}
