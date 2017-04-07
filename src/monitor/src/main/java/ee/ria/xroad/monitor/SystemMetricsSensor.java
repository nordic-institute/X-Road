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

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.Identify;
import akka.actor.Terminated;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.monitor.common.StatsRequest;
import ee.ria.xroad.monitor.common.StatsResponse;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * System metrics sensor collects information such as
 * memory, cpu, swap and file descriptors.
 */
@Slf4j
public class SystemMetricsSensor extends AbstractSensor {

    public static final int MINUTES_IN_HOUR = 60;
    public static final int SYSTEM_CPU_LOAD_MULTIPLIER = 100;
    private final SimpleSensor<Long> totalPhysicalMemorySize = new SimpleSensor<>();
    private final SimpleSensor<Long> totalSwapSpaceSize = new SimpleSensor<>();
    private final SimpleSensor<Long> maxFileDescriptorCount = new SimpleSensor<>();

    private ActorRef agent;
    private final FiniteDuration interval
            = Duration.create(SystemProperties.getEnvMonitorSystemMetricsSensorInterval(), TimeUnit.SECONDS);

    /**
     * Constructor
     */
    public SystemMetricsSensor() {
        log.info("Creating sensor, measurement interval: {}", getInterval());
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        metricRegistry.register(SystemMetricNames.SYSTEM_CPU_LOAD, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.FREE_PHYSICAL_MEMORY, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.FREE_SWAP_SPACE, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.OPEN_FILE_DESCRIPTOR_COUNT, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.COMMITTED_VIRTUAL_MEMORY, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.MAX_FILE_DESCRIPTOR_COUNT, maxFileDescriptorCount);
        metricRegistry.register(SystemMetricNames.TOTAL_SWAP_SPACE, totalPhysicalMemorySize);
        metricRegistry.register(SystemMetricNames.TOTAL_PHYSICAL_MEMORY, totalPhysicalMemorySize);

        context().system().actorSelection("akka.tcp://xroad-monitor@127.0.0.1:5567/user/ProxyMonitorAgent")
                .tell(new Identify(self()), self());
        scheduleSingleMeasurement(getInterval(), MEASURE_MESSAGE);
    }

    private Histogram createDefaultHistogram() {
        return new Histogram(new SlidingTimeWindowReservoir(MINUTES_IN_HOUR, TimeUnit.MINUTES));
    }

    /**
     * Update sensor metrics
     */
    private void updateMetrics(StatsResponse stats) {
        MetricRegistry metrics = MetricRegistryHolder.getInstance().getMetrics();
        metrics.getHistograms().get(SystemMetricNames.OPEN_FILE_DESCRIPTOR_COUNT)
                .update(stats.getOpenFileDescriptorCount());
        metrics.getHistograms().get(SystemMetricNames.COMMITTED_VIRTUAL_MEMORY)
                .update(stats.getCommittedVirtualMemorySize());
        metrics.getHistograms().get(SystemMetricNames.FREE_SWAP_SPACE).update(stats.getFreeSwapSpaceSize());
        metrics.getHistograms().get(SystemMetricNames.FREE_PHYSICAL_MEMORY).update(stats.getFreePhysicalMemorySize());
        metrics.getHistograms().get(SystemMetricNames.SYSTEM_CPU_LOAD)
                .update((long) (stats.getSystemCpuLoad() * SYSTEM_CPU_LOAD_MULTIPLIER));

        maxFileDescriptorCount.update(stats.getMaxFileDescriptorCount());
        totalPhysicalMemorySize.update(stats.getTotalPhysicalMemorySize());
        totalSwapSpaceSize.update(stats.getTotalSwapSpaceSize());
    }

    @Override
    public void onReceive(Object o) throws Exception {

        if (o instanceof ActorIdentity) {
            final ActorIdentity identity = (ActorIdentity) o;
            if (identity.correlationId().equals(self())) {
                if (identity.getRef() != null && !identity.getRef().equals(agent)) {
                    if (agent != null) {
                        context().unwatch(agent);
                    }
                    log.debug("ProxyMonitorAgent enabled");
                    agent = identity.getRef();
                    context().watch(agent);
                } else {
                    log.debug("ProxyMonitorAgent not found");
                }
            }
        }

        if (o instanceof Terminated) {
            final Terminated terminated = (Terminated) o;
            if (terminated.getActor().equals(agent)) {
                log.debug("ProxyMonitorAgent terminated");
                context().unwatch(agent);
                agent = null;
            }
        }

        if (o instanceof StatsResponse) {
            updateMetrics((StatsResponse) o);
        }

        if (MEASURE_MESSAGE == o) {
            if (agent == null) {
                context().system()
                        .actorSelection("akka.tcp://Proxy@127.0.0.1:5567/user/ProxyMonitorAgent")
                        .tell(new Identify(self()), self());
            } else {
                agent.tell(STATS_REQUEST, self());
            }
            scheduleSingleMeasurement(getInterval(), MEASURE_MESSAGE);
        }

    }

    @Override
    protected FiniteDuration getInterval() {
        return interval;
    }

    private static final Object MEASURE_MESSAGE = new Object();
    private static final StatsRequest STATS_REQUEST = new StatsRequest();
}
