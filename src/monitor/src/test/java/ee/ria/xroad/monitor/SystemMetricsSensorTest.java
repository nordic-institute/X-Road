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
package ee.ria.xroad.monitor;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestPortUtils;
import ee.ria.xroad.monitor.common.SystemMetricNames;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.monitor.common.MonitorServiceGrpc;
import org.niis.xroad.monitor.common.StatsReq;
import org.niis.xroad.monitor.common.StatsResp;
import org.springframework.scheduling.TaskScheduler;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for SystemMetricsSensor
 */
@ExtendWith(MockitoExtension.class)
class SystemMetricsSensorTest {
    private static final int PORT;

    private static RpcServer rpcServer;
    private static StatsResp response;

    @Spy
    private MetricRegistry metricRegistry = new MetricRegistry();

    static {
        try {
            PORT = TestPortUtils.findRandomPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.setProperty(SystemProperties.ENV_MONITOR_SYSTEM_METRICS_SENSOR_INTERVAL, "1");
        System.setProperty(SystemProperties.PROXY_GRPC_PORT, String.valueOf(PORT));
        System.setProperty(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.FALSE.toString());
    }

    @BeforeAll
    public static void init() throws Exception {
        rpcServer = RpcServer.newServer(SystemProperties.getGrpcInternalHost(), PORT, serverBuilder ->
                serverBuilder.addService(new MonitorServiceGrpc.MonitorServiceImplBase() {
                    @Override
                    public void getStats(StatsReq request, StreamObserver<StatsResp> responseObserver) {
                        responseObserver.onNext(response);
                        responseObserver.onCompleted();
                    }
                }));
        rpcServer.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        rpcServer.stop();
    }

    @Test
    void testSystemMetricsSensor() throws Exception {
        MetricRegistryHolder.getInstance().setMetrics(metricRegistry);

        var taskScheduler = spy(TaskScheduler.class);
        when(taskScheduler.getClock()).thenReturn(Clock.systemDefaultZone());

        SystemMetricsSensor systemMetricsSensor = new SystemMetricsSensor(taskScheduler);

        response = StatsResp.newBuilder()
                .setOpenFileDescriptorCount(0)
                .setMaxFileDescriptorCount(0)
                .setSystemCpuLoad(1.0d)
                .setCommittedVirtualMemorySize(0)
                .setFreePhysicalMemorySize(0)
                .setTotalPhysicalMemorySize(0)
                .setFreeSwapSpaceSize(0)
                .setTotalSwapSpaceSize(0)
                .build();

        systemMetricsSensor.measure();

        await()
                .atMost(Duration.ofSeconds(30))
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> verify(metricRegistry, times(1))
                        .gauge(eq(SystemMetricNames.TOTAL_PHYSICAL_MEMORY), any()));

        for (Map.Entry<String, Histogram> e : metricRegistry.getHistograms().entrySet()) {
            if (SystemMetricNames.SYSTEM_CPU_LOAD.equalsIgnoreCase(e.getKey())) {
                assertEquals(100, e.getValue().getSnapshot().getValues()[0]);
            } else {
                assertEquals(0, e.getValue().getSnapshot().getValues()[0]);
            }
        }
    }

}

