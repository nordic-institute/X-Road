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
import ee.ria.xroad.monitor.common.StatsRequest;
import ee.ria.xroad.monitor.common.StatsResponse;
import ee.ria.xroad.monitor.common.SystemMetricNames;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

/**
 * Test for SystemMetricsSensor
 */
public class SystemMetricsSensorTest {

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void init() {
        System.setProperty(SystemProperties.ENV_MONITOR_SYSTEM_METRICS_SENSOR_INTERVAL, "1");
        actorSystem = ActorSystem.create("AkkaTestServer", ConfigFactory.load());
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Test
    public void testSystemMetricsSensor() {
        final MetricRegistry registry = new MetricRegistry();
        MetricRegistryHolder.getInstance().setMetrics(registry);

        final TestKit agent = new TestKit(actorSystem);
        final ActorRef sensor = TestActorRef.create(actorSystem, Props.create(SystemMetricsSensor.class,
                agent.getRef().path().toString()));
        agent.expectMsgClass(StatsRequest.class);
        sensor.tell(new StatsResponse(0, 0, 1.0, 0, 0, 0, 0, 0), agent.getRef());

        for (Map.Entry<String, Histogram> e : registry.getHistograms().entrySet()) {
            if (SystemMetricNames.SYSTEM_CPU_LOAD.equalsIgnoreCase(e.getKey())) {
                Assert.assertEquals(100, e.getValue().getSnapshot().getValues()[0]);
            } else {
                Assert.assertEquals(0, e.getValue().getSnapshot().getValues()[0]);
            }
        }

    }

}

