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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.TestPortUtils;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;

import java.io.IOException;
import java.util.Arrays;

import static ee.ria.xroad.common.util.TimeUtils.getEpochSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

class OpMonitorRpcServiceStoreTest extends BaseTestUsingDB {

    private final InsecureRpcCredentialsConfigurer credentials = new InsecureRpcCredentialsConfigurer();
    private final HealthDataMetrics healthDataMetrics = new HealthDataMetrics(OP_MONITOR_PROPERTIES);
    private final HealthDataRegistry healthDataRegistry = new HealthDataRegistry(healthDataMetrics);
    private final OperationalDataStore operationalDataStore =
            new OperationalDataStore(operationalDataRecordManager, healthDataMetrics, healthDataRegistry);

    private RpcServer rpcServer;
    private ManagedChannel channel;
    private OpMonitorServiceGrpc.OpMonitorServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());

        int port = TestPortUtils.findRandomPort();
        rpcServer = new RpcServer("127.0.0.1", port, credentials.createServerCredentials(),
                builder -> builder.addService(new OpMonitorRpcService(operationalDataRecordManager, operationalDataStore)));
        rpcServer.init();
        channel = new RpcChannelFactory(credentials).createChannel(channelProperties(port));
        stub = OpMonitorServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow();
        rpcServer.destroy();
    }

    @Test
    void storedRecordHasSameFieldValuesAsRestStorePath() {
        var data = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);

        store(data);

        var stored = operationalDataRecordManager.queryAllRecords().getRecords();
        assertThat(stored).singleElement()
                .usingRecursiveComparison()
                .ignoringFields("id", "monitoringDataTs")
                .isEqualTo(OpMonitoringDataFixtures.viaRestJson(data));
    }

    @Test
    void oversizedTextIsStoredInOneCallWithTheSameRowAsRestStorePath() throws Exception {
        var data = OpMonitoringDataFixtures.withOversizedText(PRODUCER);

        store(data);
        var viaGrpc = operationalDataRecordManager.queryAllRecords().getRecords().getFirst();
        DATABASE_CTX.doInTransaction(session -> session.createMutationQuery("delete OperationalDataRecordEntity").executeUpdate());
        new StoreRequestProcessor(StoreRequestProcessorTest.jsonRequest(data), operationalDataStore).process();
        var viaRest = operationalDataRecordManager.queryAllRecords().getRecords().getFirst();

        assertThat(viaGrpc).usingRecursiveComparison().ignoringFields("id", "monitoringDataTs").isEqualTo(viaRest);
        assertThat(viaGrpc.getMessageUserId()).hasSize(255);
    }

    @Test
    void opMonitorAssignsIdAndMonitoringDataTimestamp() {
        long before = getEpochSecond();

        store(OpMonitoringDataFixtures.fullyPopulated(PRODUCER));

        var stored = operationalDataRecordManager.queryAllRecords().getRecords().getFirst();
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getMonitoringDataTs()).isBetween(before, getEpochSecond());
    }

    @Test
    void storedProducerRecordsUpdateHealthCounters() {
        var succeeded = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);
        var failed = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);
        failed.setSucceeded(false);

        store(succeeded, succeeded, failed);

        var serviceId = HealthDataMetricsUtil.getServiceId(OpMonitoringDataFixtures.viaRestJson(succeeded));
        assertThat(requestCount(serviceId, true)).isEqualTo(2);
        assertThat(requestCount(serviceId, false)).isEqualTo(1);
    }

    @Test
    void storedClientRecordsDoNotUpdateHealthCounters() {
        var data = OpMonitoringDataFixtures.fullyPopulated(CLIENT);

        store(data);

        var serviceId = HealthDataMetricsUtil.getServiceId(OpMonitoringDataFixtures.viaRestJson(data));
        assertThat(HealthDataMetricsUtil.findCounter(healthDataRegistry.getRegistry(),
                HealthDataMetricsUtil.getRequestCounterName(serviceId, true))).isNull();
    }

    @Test
    void invalidRecordFailsWholeBatchWithGrpcStatusAndStoresNothing() {
        var valid = OpMonitoringDataFixtures.fullyPopulated(PRODUCER);
        var withoutRequestInTs = valid.toProto().toBuilder().clearRequestInTs().build();

        assertThatThrownBy(() -> store(valid.toProto(), withoutRequestInTs))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL));

        assertThat(operationalDataRecordManager.queryAllRecords().getRecords()).isEmpty();
        var serviceId = HealthDataMetricsUtil.getServiceId(OpMonitoringDataFixtures.viaRestJson(valid));
        assertThat(HealthDataMetricsUtil.findCounter(healthDataRegistry.getRegistry(),
                HealthDataMetricsUtil.getRequestCounterName(serviceId, true))).isNull();
    }

    private long requestCount(ServiceId serviceId, boolean success) {
        return HealthDataMetricsUtil.findCounter(healthDataRegistry.getRegistry(),
                HealthDataMetricsUtil.getRequestCounterName(serviceId, success)).getCount();
    }

    private void store(OpMonitoringData... records) {
        store(Arrays.stream(records).map(OpMonitoringData::toProto).toArray(OperationalDataRecordProto[]::new));
    }

    private void store(OperationalDataRecordProto... records) {
        stub.storeOperationalData(StoreOperationalDataReq.newBuilder().addAllRecords(Arrays.asList(records)).build());
    }

    private static RpcChannelProperties channelProperties(int port) {
        return new RpcChannelProperties() {
            @Override
            public String host() {
                return "127.0.0.1";
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public int deadlineAfter() {
                return 10_000;
            }
        };
    }
}
