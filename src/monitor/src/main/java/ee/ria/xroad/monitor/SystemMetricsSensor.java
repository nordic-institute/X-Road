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

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * System metrics sensor collects information such as
 * memory, cpu, swap and file descriptors.
 */
@Slf4j
public class SystemMetricsSensor extends AbstractSensor {

    private static final int MINUTES_IN_HOUR = 60;
    private static final int SYSTEM_CPU_LOAD_MULTIPLIER = 100;
    private static final Object MEASURE_MESSAGE = new Object();
    private static final StatsRequest STATS_REQUEST = new StatsRequest();

    private final SimpleSensor<Long> totalPhysicalMemorySize = new SimpleSensor<>();
    private final SimpleSensor<Long> totalSwapSpaceSize = new SimpleSensor<>();
    private final SimpleSensor<Long> maxFileDescriptorCount = new SimpleSensor<>();
    private final FiniteDuration interval
            = Duration.create(SystemProperties.getEnvMonitorSystemMetricsSensorInterval(), TimeUnit.SECONDS);
    private final String agentPath =
            "akka.tcp://Proxy@127.0.0.1:" + SystemProperties.getProxyActorSystemPort() + "/user/ProxyMonitorAgent";

    private ActorRef agent;
    private long correlationId = 1;

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

        identifyAgent();
        scheduleSingleMeasurement(getInterval(), MEASURE_MESSAGE);
    }

    private Histogram createDefaultHistogram() {
        return new Histogram(new SlidingTimeWindowReservoir(MINUTES_IN_HOUR, TimeUnit.MINUTES));
    }

    /**
     * Update sensor metrics
     */
    private void updateMetrics(StatsResponse stats) {
        final Map<String, Histogram> histograms = MetricRegistryHolder.getInstance().getMetrics().getHistograms();
        histograms.get(SystemMetricNames.OPEN_FILE_DESCRIPTOR_COUNT).update(stats.getOpenFileDescriptorCount());
        histograms.get(SystemMetricNames.COMMITTED_VIRTUAL_MEMORY).update(stats.getCommittedVirtualMemorySize());
        histograms.get(SystemMetricNames.FREE_SWAP_SPACE).update(stats.getFreeSwapSpaceSize());
        histograms.get(SystemMetricNames.FREE_PHYSICAL_MEMORY).update(stats.getFreePhysicalMemorySize());
        histograms.get(SystemMetricNames.SYSTEM_CPU_LOAD)
                .update((long) (stats.getSystemCpuLoad() * SYSTEM_CPU_LOAD_MULTIPLIER));

        maxFileDescriptorCount.update(stats.getMaxFileDescriptorCount());
        totalPhysicalMemorySize.update(stats.getTotalPhysicalMemorySize());
        totalSwapSpaceSize.update(stats.getTotalSwapSpaceSize());
    }

    @Override
    public void onReceive(final Object message) {

        if (MEASURE_MESSAGE == message) {
            if (agent == null) {
                identifyAgent();
            } else {
                agent.tell(STATS_REQUEST, self());
            }
            scheduleSingleMeasurement(getInterval(), MEASURE_MESSAGE);
        } else if (message instanceof StatsResponse) {
            updateMetrics((StatsResponse) message);
        } else if (message instanceof ActorIdentity) {
            attachAgent((ActorIdentity) message);
        } else if (message instanceof Terminated) {
            detachAgent((Terminated) message);
        } else {
            unhandled(message);
        }
    }

    private void detachAgent(final Terminated message) {
        if (message.getActor().equals(agent)) {
            log.info("ProxyMonitorAgent detached");
            context().unwatch(agent);
            agent = null;
        }
    }

    private void attachAgent(final ActorIdentity message) {
        if (message.correlationId().equals(correlationId)) {
            if (agent != null) {
                context().unwatch(agent);
            }
            agent = message.getRef();
            if (agent != null) {
                context().watch(agent);
                log.info("ProxyMonitorAgent attached");
            }
        }
    }

    private void identifyAgent() {
        correlationId++;
        context().system().actorSelection(agentPath).tell(new Identify(correlationId), self());
    }

    @Override
    protected FiniteDuration getInterval() {
        return interval;
    }
}
