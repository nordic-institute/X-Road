/**
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
import ee.ria.xroad.monitor.common.SystemMetricsRequest;
import ee.ria.xroad.monitor.common.SystemMetricsResponse;
import ee.ria.xroad.monitor.common.dto.HistogramDto;
import ee.ria.xroad.monitor.common.dto.MetricDto;
import ee.ria.xroad.monitor.common.dto.MetricSetDto;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * MetricsProviderActorTest
 */
@Slf4j
public class MetricsProviderActorTest {

    private static ActorSystem actorSystem;
    private MetricRegistry metricsRegistry;

    private static final String HISTOGRAM_NAME = "TestHistogram";
    private static final String GAUGE_NAME = "TestGauge";

    @Rule
    public final ProvideSystemProperty p = new ProvideSystemProperty(
            SystemProperties.ENV_MONITOR_LIMIT_REMOTE_DATA_SET,
            "true");

    /**
     * Before test handler
     */
    @Before
    public void init() {


        actorSystem = ActorSystem.create("AkkaRemoteServer", ConfigFactory.load());
        metricsRegistry = new MetricRegistry();

        Histogram testHistogram = metricsRegistry.histogram(HISTOGRAM_NAME);
        testHistogram.update(100);
        testHistogram.update(10);

        //MetricRegistry.MetricSupplier<Gauge> x;
        Gauge g = metricsRegistry.gauge(GAUGE_NAME, () -> new SimpleSensor<String>("Test gauge String value."));

        MetricRegistryHolder.getInstance().setMetrics(metricsRegistry);
    }

    /**
     * Shut down actor system and wait for clean up, so that other tests are not disturbed
     */
    @After
    public void tearDown() throws Exception {
        Await.ready(actorSystem.terminate(), Duration.Inf());
    }

    @Test
    public void testAllSystemMetricsRequest() throws Exception {
        final Props props = Props.create(MetricsProviderActor.class);
        final TestActorRef<MetricsProviderActor> ref = TestActorRef.create(actorSystem, props, "testActorRef");
        Future<Object> future = Patterns.ask(ref, new SystemMetricsRequest(null, true),
                Timeout.apply(1, TimeUnit.MINUTES));
        Object result = Await.result(future, Duration.apply(1, TimeUnit.MINUTES));
        assertTrue(future.isCompleted());
        assertTrue(result instanceof SystemMetricsResponse);
        SystemMetricsResponse response = (SystemMetricsResponse) result;
        MetricSetDto metricSetDto = response.getMetrics();
        Set<MetricDto> dtoSet = metricSetDto.getMetrics();

        log.info("metricSetDto: " + metricSetDto);
        assertEquals(2, dtoSet.stream().count());

        for (MetricDto metricDto : dtoSet) {

            // Order of entries is undefined -> Must handle by name
            switch (metricDto.getName()) {
                case HISTOGRAM_NAME:
                    log.info("metricDto: " + metricDto);
                    assertEquals(HISTOGRAM_NAME, metricDto.getName());
                    assertTrue(metricDto instanceof HistogramDto);
                    HistogramDto h = (HistogramDto) metricDto;
                    assertEquals(100L, (long) h.getMax());
                    assertEquals(10L, (long) h.getMin());
                    assertEquals(55L, (long) h.getMean());
                    break;
                case GAUGE_NAME:
                    log.info("metricDto: " + metricDto);
                    assertEquals(GAUGE_NAME, metricDto.getName());
                    break;
                default:
                    Assert.fail("Unknown metric found in response.");

            }
        }


    }

    @Test
    public void testLimitedSystemMetricsRequest() throws Exception {

        final Props props = Props.create(MetricsProviderActor.class);
        final TestActorRef<MetricsProviderActor> ref = TestActorRef.create(actorSystem, props, "testActorRef");
        Future<Object> future = Patterns.ask(ref, new SystemMetricsRequest(null, false),
                Timeout.apply(1, TimeUnit.MINUTES));
        Object result = Await.result(future, Duration.apply(1, TimeUnit.MINUTES));
        assertTrue(future.isCompleted());
        assertTrue(result instanceof SystemMetricsResponse);
        SystemMetricsResponse response = (SystemMetricsResponse) result;
        MetricSetDto metricSetDto = response.getMetrics();
        Set<MetricDto> dtoSet = metricSetDto.getMetrics();

        log.info("metricSetDto: " + metricSetDto);
        //assertEquals(2, dtoSet.stream().count());

        for (MetricDto metricDto : dtoSet) {

            // Order of entries is undefined -> Must handle by name
            switch (metricDto.getName()) {
                case HISTOGRAM_NAME:
                    Assert.fail("Should not have histrogram.");
                    break;
                case GAUGE_NAME:
                    Assert.fail("Should not have histrogram gauge.");
                    break;
                default:
                    Assert.fail("Unknown metric found in response.");
                    break;
            }
        }
    }

    @Test
    public void testParametrizedSystemMetricsRequest() throws Exception {
        final Props props = Props.create(MetricsProviderActor.class);
        final TestActorRef<MetricsProviderActor> ref = TestActorRef.create(actorSystem, props, "testActorRef");

        Future<Object> future = Patterns.ask(
                ref,
                new SystemMetricsRequest(Arrays.asList(HISTOGRAM_NAME), true),
                Timeout.apply(1, TimeUnit.MINUTES));

        Object result = Await.result(future, Duration.apply(1, TimeUnit.MINUTES));
        assertTrue(future.isCompleted());
        assertTrue(result instanceof SystemMetricsResponse);
        SystemMetricsResponse response = (SystemMetricsResponse) result;
        MetricSetDto metricSetDto = response.getMetrics();
        Set<MetricDto> dtoSet = metricSetDto.getMetrics();

        log.info("metricSetDto: " + metricSetDto);
        assertEquals(1, dtoSet.stream().count());

        // Note: findFirst() works only because of single result
        MetricDto metricDto = dtoSet.stream().findFirst().get();
        assertEquals(HISTOGRAM_NAME, metricDto.getName());
        assertTrue(metricDto instanceof HistogramDto);
        HistogramDto h = (HistogramDto) metricDto;
        assertEquals(100L, (long) h.getMax());
        assertEquals(10L, (long) h.getMin());
        assertEquals(55L, (long) h.getMean());
    }
}
