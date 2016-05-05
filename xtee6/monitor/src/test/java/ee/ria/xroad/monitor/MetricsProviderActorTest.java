/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.TestActorRef;
import akka.util.Timeout;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.ConfigFactory;
import ee.ria.xroad.monitor.common.SystemMetricsRequest;
import ee.ria.xroad.monitor.common.SystemMetricsResponse;
import ee.ria.xroad.monitor.common.dto.HistogramDto;
import ee.ria.xroad.monitor.common.dto.MetricDto;
import ee.ria.xroad.monitor.common.dto.MetricSetDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * MetricsProviderActorTest
 */
public class MetricsProviderActorTest {

    private static ActorSystem actorSystem;
    private MetricRegistry metrics;

    /**
     * Before test handler
     */
    @Before
    public void init() {
        actorSystem = ActorSystem.create("AkkaRemoteServer", ConfigFactory.load());
        metrics = new MetricRegistry();
        Histogram testHistogram = metrics.histogram("testHistogram");
        testHistogram.update(100);
        testHistogram.update(10);
        MetricRegistryHolder.getInstance().setMetrics(metrics);
    }

    @After
    public void tearDown() {
        actorSystem.shutdown();
    }

    @Test
    public void testSystemMetricsRequest() throws Exception {
        final Props props = Props.create(MetricsProviderActor.class);
        final TestActorRef<MetricsProviderActor> ref = TestActorRef.create(actorSystem, props, "testActorRef");
        Future<Object> future = Patterns.ask(ref, new SystemMetricsRequest(), Timeout.apply(1, TimeUnit.MINUTES));
        Object result = Await.result(future, Duration.apply(1, TimeUnit.MINUTES));
        assertTrue(future.isCompleted());
        assertTrue(result instanceof SystemMetricsResponse);
        SystemMetricsResponse response = (SystemMetricsResponse) result;
        MetricSetDto metricSetDto = response.getMetrics();
        Set<MetricDto> dtoSet = metricSetDto.getMetrics();
        assertEquals(1, dtoSet.stream().count());
        MetricDto metricDto = dtoSet.stream().findFirst().get();
        assertEquals(metricDto.getName(), "testHistogram");
        assertTrue(metricDto instanceof HistogramDto);
        HistogramDto h = (HistogramDto) metricDto;
        assertEquals(100L, (long)h.getMax());
        assertEquals(10L, (long) h.getMin());
        assertEquals(55L, (long) h.getMean());
    }
}
