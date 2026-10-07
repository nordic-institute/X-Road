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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RepresentedParty;

import io.grpc.stub.StreamObserver;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.ManagedRpcServer;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;
import org.niis.xroad.opmonitor.api.StoreOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.RPC_MAX_INBOUND_MESSAGE_SIZE;

/**
 * Store delivery when op-monitor's inbound limit is below the proxy's batch cap, over the production RPC server,
 * channel factory and op-monitor client. A batch larger than the HTTP/2 flow-control window is rejected by the
 * server and has to be split until every record is stored.
 */
class OpMonitorStoreSizeLimitTest {
    private static final int RECORDS_IN_BATCH = 100;

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final List<OperationalDataRecordProto> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger storeCalls = new AtomicInteger();
    private ManagedRpcServer server;
    private OpMonitorClient opMonitorClient;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (opMonitorClient != null) {
            opMonitorClient.close();
        }
        if (server != null) {
            server.destroy();
        }
    }

    @Test
    void batchAboveTheSmallestAllowedServerLimitIsSplitUntilEveryRecordIsStored() throws IOException {
        int port = freePort();
        var config = config(Map.of(
                "xroad.op-monitor.rpc.max-inbound-message-size", String.valueOf(OpMonitorConfigKeys.MIN_STORE_MESSAGE_SIZE),
                "xroad.common-rpc.channel.op-monitor.host", "127.0.0.1",
                "xroad.common-rpc.channel.op-monitor.port", String.valueOf(port)));
        startServer(config, port);
        opMonitorClient = new OpMonitorClient(new RpcChannelFactory(credentials), new OpMonitorRpcChannelProperties(config));
        opMonitorClient.init();

        int proxyBatchCap = new OpMonitorBufferProperties(config).maxMessageSize();
        var batch = IntStream.range(0, RECORDS_IN_BATCH).mapToObj(OpMonitorStoreSizeLimitTest::largeRecord).toList();
        var expected = batch.stream().map(OpMonitoringData::toProto).toList();
        int batchSize = StoreOperationalDataReq.newBuilder().addAllRecords(expected).build().getSerializedSize();
        assertThat(batchSize)
                .isGreaterThan(1024 * 1024)
                .isLessThanOrEqualTo(proxyBatchCap);

        var buffer = new RecordingOpMonitoringBuffer();
        var sender = new OpMonitoringDaemonSender(buffer, new OpMonitorStoreClient(() -> opMonitorClient), proxyBatchCap);
        sender.sendMessage(batch);

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .until(() -> !buffer.successes.isEmpty() || !buffer.failures.isEmpty());
        sender.destroy();
        assertThat(buffer.failures).isEmpty();
        assertThat(buffer.successes).containsExactly(RECORDS_IN_BATCH);
        assertThat(received).containsExactlyElementsOf(expected);
        assertThat(storeCalls.get()).isGreaterThan(batchSize / OpMonitorConfigKeys.MIN_STORE_MESSAGE_SIZE);
    }

    private void startServer(XRoadConfig config, int port) throws IOException {
        var serverProperties = new RpcServerProperties() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String listenAddress() {
                return "127.0.0.1";
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public Optional<Integer> maxInboundMessageSize() {
                return config.valueOpt(RPC_MAX_INBOUND_MESSAGE_SIZE);
            }
        };
        server = new ManagedRpcServer(List.of(new RecordingStoreService()), serverProperties, credentials) {
        };
        server.init();
    }

    private static OpMonitoringData largeRecord(int index) {
        String text = "€".repeat(300);
        var data = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1_700_000_000_000L + index);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setClientId(ClientId.Conf.create(text, text, text, text));
        data.setServiceId(ServiceId.Conf.create(text, text, text, text, text, text));
        data.setRepresentedParty(new RepresentedParty(text, text));
        data.setMessageId("message-" + index);
        data.setMessageUserId(text);
        data.setMessageIssue(text);
        data.setResponseOutTs(1_700_000_000_500L + index, false);
        return data;
    }

    private static XRoadConfig config(Map<String, String> overrides) {
        return XRoadConfigBuilder.create()
                .register(OpMonitorConfigKeys.instance())
                .register(CommonRpcConfigKeys.instance())
                .register(ProxyConfigKeys.instance())
                .overrides(overrides)
                .build();
    }

    private static int freePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final class RecordingStoreService extends OpMonitorServiceGrpc.OpMonitorServiceImplBase {
        @Override
        public void storeOperationalData(StoreOperationalDataReq request,
                                         StreamObserver<StoreOperationalDataResp> responseObserver) {
            storeCalls.incrementAndGet();
            received.addAll(request.getRecordsList());
            responseObserver.onNext(StoreOperationalDataResp.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
