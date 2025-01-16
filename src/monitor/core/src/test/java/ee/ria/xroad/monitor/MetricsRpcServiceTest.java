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

import ee.ria.xroad.common.TestPortUtils;
import ee.ria.xroad.common.properties.CommonRpcProperties;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.MetricsServiceGrpc;
import org.niis.xroad.monitor.common.SystemMetricsReq;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * MetricsProviderActorTest
 */
@Slf4j
class MetricsRpcServiceTest {
    private static final String HISTOGRAM_NAME = "TestHistogram";
    private static final String GAUGE_NAME = "TestGauge";

    private RpcServer rpcServer;

    private ManagedChannel channel;
    private MetricsServiceGrpc.MetricsServiceBlockingStub metricsServiceBlockingStub;

    /**
     * Before test handler
     */
    @BeforeEach
    public void init() throws Exception {
        EnvMonitorProperties envMonitorProperties = new EnvMonitorProperties() {
            @Override
            public Duration certificateInfoSensorInterval() {
                return Duration.ofDays(1);
            }

            @Override
            public Duration diskSpaceSensorInterval() {
                return Duration.ofSeconds(60);
            }

            @Override
            public Duration execListingSensorInterval() {
                return Duration.ofSeconds(60);
            }

            @Override
            public Duration systemMetricsSensorInterval() {
                return Duration.ofSeconds(5);
            }

            @Override
            public boolean limitRemoteDataSet() {
                return true;
            }
        };

        RpcCredentialsConfigurer rpcCredentialsConfigurer = new RpcCredentialsConfigurer(
                mock(CommonRpcProperties.class), null);

        int port = TestPortUtils.findRandomPort();

        rpcServer = new RpcServer("localhost", port, rpcCredentialsConfigurer.createServerCredentials(),
                builder -> builder.addService(new MetricsRpcService(envMonitorProperties)));
        rpcServer.afterPropertiesSet();

        channel = new RpcChannelFactory(rpcCredentialsConfigurer).createChannel(
                new RpcChannelProperties() {
                    @Override
                    public String host() {
                        return "localhost";
                    }

                    @Override
                    public int port() {
                        return port;
                    }

                    @Override
                    public int deadlineAfter() {
                        return 5000;
                    }
                }
        );
        metricsServiceBlockingStub = MetricsServiceGrpc.newBlockingStub(channel).withWaitForReady();

        MetricRegistry metricsRegistry = new MetricRegistry();
        Histogram testHistogram = metricsRegistry.histogram(HISTOGRAM_NAME);
        testHistogram.update(100);
        testHistogram.update(10);

        //MetricRegistry.MetricSupplier<Gauge> x;
        metricsRegistry.gauge(GAUGE_NAME, () -> new SimpleSensor<>("Test gauge String value."));

        MetricRegistryHolder.getInstance().setMetrics(metricsRegistry);
    }

    /**
     * Shut down actor system and wait for clean up, so that other tests are not disturbed
     */
    @AfterEach
    public void tearDown() throws Exception {
        channel.shutdownNow();
        rpcServer.destroy();
    }

    @Test
    void testAllSystemMetricsRequest() {
        var request = SystemMetricsReq.newBuilder().setIsClientOwner(true).build();
        var response = metricsServiceBlockingStub.getMetrics(request);

        assertNotNull(response);

        MetricsGroup metricSetDto = response.getMetrics();
        List<Metrics> dtoSet = metricSetDto.getMetricsList();

        log.info("metricSetDto: {}", metricSetDto);
        assertEquals(2, dtoSet.size());

        for (Metrics metricDto : dtoSet) {
            if (metricDto.hasSingleHistogram()) {
                var histogram = metricDto.getSingleHistogram();
                log.info("metricDto: {}", histogram);
                assertEquals(HISTOGRAM_NAME, histogram.getName());
                assertEquals(100L, (long) histogram.getMax());
                assertEquals(10L, (long) histogram.getMin());
                assertEquals(55L, (long) histogram.getMean());
            } else if (metricDto.hasSingleMetrics()) {
                var singleMetrics = metricDto.getSingleMetrics();
                log.info("metricDto: {}", singleMetrics);
                assertEquals(GAUGE_NAME, singleMetrics.getName());
            } else {
                fail("Unknown metric found in response.");
            }
        }
    }

    @Test
    void testLimitedSystemMetricsRequest() {
        var request = SystemMetricsReq.newBuilder().setIsClientOwner(false).build();
        var response = metricsServiceBlockingStub.getMetrics(request);

        MetricsGroup metricSetDto = response.getMetrics();
        List<Metrics> dtoSet = metricSetDto.getMetricsList();

        log.info("metricSetDto: {}", metricSetDto);

        for (Metrics metricDto : dtoSet) {
            String name = getMetricsName(metricDto);
            switch (name) {
                case HISTOGRAM_NAME:
                    fail("Should not have histrogram.");
                    break;
                case GAUGE_NAME:
                    fail("Should not have histrogram gauge.");
                    break;
                default:
                    fail("Unknown metric found in response.");
                    break;
            }
        }
    }

    private String getMetricsName(Metrics metrics) {
        if (metrics.hasSingleMetrics()) {
            return metrics.getSingleMetrics().getName();
        } else if (metrics.hasSingleHistogram()) {
            return metrics.getSingleHistogram().getName();
        }
        return fail("Unknown metric found in response.");
    }

    @Test
    void testParametrizedSystemMetricsRequest() {
        var request = SystemMetricsReq.newBuilder()
                .addMetricNames(HISTOGRAM_NAME)
                .setIsClientOwner(true)
                .build();

        var response = metricsServiceBlockingStub.getMetrics(request);

        MetricsGroup metricSetDto = response.getMetrics();
        List<Metrics> dtoSet = metricSetDto.getMetricsList();

        log.info("metricSetDto: {}", metricSetDto);
        assertEquals(1, dtoSet.size());

        // Note: findFirst() works only because of single result
        Metrics metricDto = dtoSet.stream().findFirst().get();
        assertTrue(metricDto.hasSingleHistogram());
        var histogram = metricDto.getSingleHistogram();
        assertEquals(HISTOGRAM_NAME, histogram.getName());

        assertEquals(100L, (long) histogram.getMax());
        assertEquals(10L, (long) histogram.getMin());
        assertEquals(55L, (long) histogram.getMean());
    }

}
