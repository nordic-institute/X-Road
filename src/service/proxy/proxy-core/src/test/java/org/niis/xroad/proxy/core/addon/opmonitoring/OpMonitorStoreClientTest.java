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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;
import org.niis.xroad.opmonitor.api.StoreOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpMonitorStoreClientTest {
    private static final Logger SENDER_LOGGER = Logger.getLogger(OpMonitoringDaemonSender.class.getName());
    private Runnable restoreLogger = () -> { };
    private final List<OperationalDataRecordProto> received = new CopyOnWriteArrayList<>();
    private volatile Status failure;
    private final AtomicInteger storeCalls = new AtomicInteger();
    private Server server;
    private ManagedChannel channel;
    private OpMonitorClient opMonitorClient;

    @AfterEach
    void tearDown() {
        restoreLogger.run();
        opMonitorClient.close();
        server.shutdownNow();
    }

    @Test
    void recordsAreStored() throws IOException {
        var client = connect(Integer.MAX_VALUE);
        var records = protos(3);

        assertThat(client.store(records)).isEqualTo(OperationalDataStoreClient.Result.STORED);
        assertThat(received).containsExactlyElementsOf(records);
    }

    @Test
    void requestAboveServerInboundLimitIsReportedAsTooLarge() throws IOException {
        var records = protos(10);
        var client = connect(StoreOperationalDataReq.newBuilder().addAllRecords(records.subList(0, 5)).build().getSerializedSize());

        assertThat(client.store(records)).isEqualTo(OperationalDataStoreClient.Result.TOO_LARGE);
        assertThat(received).isEmpty();
    }

    @Test
    void otherFailuresAreThrown() throws IOException {
        failure = Status.UNAVAILABLE;
        var client = connect(Integer.MAX_VALUE);

        assertThatThrownBy(() -> client.store(protos(1))).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Bandwidth exhausted", "quota exceeded"})
    void resourceExhaustedThatIsNotAnInboundSizeRejectionIsThrown(String description) throws IOException {
        failure = Status.RESOURCE_EXHAUSTED.withDescription(description);
        var client = connect(Integer.MAX_VALUE);

        assertThatThrownBy(() -> client.store(protos(1)))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    }

    @Test
    void senderRequeuesTheWholeBatchWhenResourceExhaustedIsNotAnInboundSizeRejection() throws IOException {
        failure = Status.RESOURCE_EXHAUSTED.withDescription("Bandwidth exhausted");
        var batch = OpMonitoringDaemonSenderTest.records(5);
        var buffer = new RecordingOpMonitoringBuffer();
        var sender = new OpMonitoringDaemonSender(buffer, connect(Integer.MAX_VALUE), 3 * 1024 * 1024);
        var errors = captureErrors();

        sender.sendMessage(batch);

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !buffer.successes.isEmpty() || !buffer.failures.isEmpty());
        sender.destroy();
        assertThat(buffer.failures).singleElement().isEqualTo(batch);
        assertThat(buffer.successes).isEmpty();
        assertThat(storeCalls).hasValue(1);
        assertThat(errors).noneMatch(message -> message.contains("dropped"));
    }

    @Test
    void failedClientCreationFailsThatStoreAndIsRetriedOnTheNextStore() throws IOException {
        var connected = connect(Integer.MAX_VALUE);
        var attempts = new AtomicInteger();
        var client = new OpMonitorStoreClient(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw XrdRuntimeException.systemInternalError("credentials not available yet");
            }
            return opMonitorClient;
        });

        assertThatThrownBy(() -> client.store(protos(1))).isInstanceOf(XrdRuntimeException.class);
        assertThat(client.store(protos(1))).isEqualTo(OperationalDataStoreClient.Result.STORED);
        assertThat(attempts).hasValue(2);
        assertThat(connected).isNotNull();
    }

    @Test
    void senderStoresEveryRecordWhenServerLimitIsSmallerThanOneBatch() throws IOException {
        var batch = OpMonitoringDaemonSenderTest.records(50);
        int serverLimit = StoreOperationalDataReq.newBuilder()
                .addAllRecords(batch.subList(0, 7).stream().map(OpMonitoringData::toProto).toList()).build().getSerializedSize();
        var buffer = new RecordingOpMonitoringBuffer();
        var sender = new OpMonitoringDaemonSender(buffer, connect(serverLimit), 3 * 1024 * 1024);

        sender.sendMessage(batch);

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !buffer.successes.isEmpty() || !buffer.failures.isEmpty());
        sender.destroy();
        assertThat(received).containsExactlyElementsOf(batch.stream().map(OpMonitoringData::toProto).toList());
        assertThat(buffer.successes).containsExactly(50);
        assertThat(buffer.failures).isEmpty();
    }

    @Test
    void senderDropsOnlyTheRecordLargerThanAnInvalidServerLimitAndDeliversTheRest() throws IOException {
        var small = OpMonitoringDaemonSenderTest.records(5);
        var oversized = OpMonitoringDaemonSenderTest.record(9);
        oversized.setMessageIssue("i".repeat(255));
        var batch = List.of(small.get(0), small.get(1), oversized, small.get(2), small.get(3), small.get(4));
        int invalidTestOnlyServerLimit = StoreOperationalDataReq.newBuilder().addRecords(small.getFirst().toProto()).build()
                .getSerializedSize();
        var buffer = new RecordingOpMonitoringBuffer();
        var sender = new OpMonitoringDaemonSender(buffer, connect(invalidTestOnlyServerLimit), 3 * 1024 * 1024);
        var errors = captureErrors();

        sender.sendMessage(batch);

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> !buffer.successes.isEmpty() || !buffer.failures.isEmpty());
        sender.destroy();
        assertThat(received).containsExactlyElementsOf(small.stream().map(OpMonitoringData::toProto).toList());
        assertThat(buffer.failures).isEmpty();
        assertThat(buffer.successes).containsExactly(6);
        assertThat(errors).singleElement().satisfies(message -> assertThat(message)
                .contains("dropped and not retried")
                .contains("message-9"));
    }

    private List<String> captureErrors() {
        var messages = new CopyOnWriteArrayList<String>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    messages.add(new SimpleFormatter().formatMessage(logRecord));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        var previousLevel = SENDER_LOGGER.getLevel();
        SENDER_LOGGER.setLevel(Level.SEVERE);
        SENDER_LOGGER.addHandler(handler);
        restoreLogger = () -> {
            SENDER_LOGGER.removeHandler(handler);
            SENDER_LOGGER.setLevel(previousLevel);
        };
        return messages;
    }

    private OpMonitorStoreClient connect(int serverMaxInboundMessageSize) throws IOException {
        server = ServerBuilder.forPort(0)
                .maxInboundMessageSize(serverMaxInboundMessageSize)
                .addService(new OpMonitorServiceGrpc.OpMonitorServiceImplBase() {
                    @Override
                    public void storeOperationalData(StoreOperationalDataReq request,
                                                     StreamObserver<StoreOperationalDataResp> responseObserver) {
                        storeCalls.incrementAndGet();
                        if (failure != null) {
                            responseObserver.onError(failure.asRuntimeException());
                            return;
                        }
                        received.addAll(request.getRecordsList());
                        responseObserver.onNext(StoreOperationalDataResp.getDefaultInstance());
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
        return new OpMonitorStoreClient(() -> opMonitorClient);
    }

    private static List<OperationalDataRecordProto> protos(int count) {
        return OpMonitoringDaemonSenderTest.records(count).stream().map(OpMonitoringData::toProto).toList();
    }
}
