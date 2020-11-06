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

import akka.actor.ActorSelection;
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

    private static final int SYSTEM_CPU_LOAD_MULTIPLIER = 100;
    private static final Object MEASURE_MESSAGE = new Object();
    private static final StatsRequest STATS_REQUEST = new StatsRequest();

    private final FiniteDuration interval
            = Duration.create(SystemProperties.getEnvMonitorSystemMetricsSensorInterval(), TimeUnit.SECONDS);

    private static final String DEFAULT_AGENT_PATH =
            "akka://Proxy@127.0.0.1:" + SystemProperties.getProxyActorSystemPort() + "/user/ProxyMonitorAgent";

    private final ActorSelection agent;

    /**
     * Create new Sensor with a default agent path.
     */
    public SystemMetricsSensor() {
        this(DEFAULT_AGENT_PATH);
    }

    /**
     * Create new Sensor with a custom agent path
     * @param agentPath
     */
    public SystemMetricsSensor(String agentPath) {
        log.info("Creating sensor, measurement interval: {}", getInterval());
        this.agent = context().actorSelection(agentPath);
        scheduleSingleMeasurement(getInterval(), MEASURE_MESSAGE);
    }

    /**
     * Update sensor metrics
     */
    private void updateMetrics(StatsResponse stats) {
        MetricRegistryHolder registryHolder = MetricRegistryHolder.getInstance();
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.SYSTEM_CPU_LOAD)
                .update((long)(stats.getSystemCpuLoad() * SYSTEM_CPU_LOAD_MULTIPLIER));
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.FREE_PHYSICAL_MEMORY)
                .update(stats.getFreePhysicalMemorySize());
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.FREE_SWAP_SPACE)
                .update(stats.getFreeSwapSpaceSize());
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.OPEN_FILE_DESCRIPTOR_COUNT)
                .update(stats.getOpenFileDescriptorCount());
        registryHolder
                .getOrCreateHistogram(SystemMetricNames.COMMITTED_VIRTUAL_MEMORY)
                .update(stats.getCommittedVirtualMemorySize());
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.MAX_FILE_DESCRIPTOR_COUNT)
                .update(stats.getMaxFileDescriptorCount());
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.TOTAL_SWAP_SPACE)
                .update(stats.getTotalSwapSpaceSize());
        registryHolder
                .getOrCreateSimpleSensor(SystemMetricNames.TOTAL_PHYSICAL_MEMORY)
                .update(stats.getTotalPhysicalMemorySize());
    }

    @Override
    public void onReceive(final Object message) {
        log.trace("onReceive({})", message);
        if (MEASURE_MESSAGE == message) {
            agent.tell(STATS_REQUEST, self());
            scheduleSingleMeasurement(getInterval(), MEASURE_MESSAGE);
        } else if (message instanceof StatsResponse) {
            updateMetrics((StatsResponse) message);
        } else {
            unhandled(message);
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        return interval;
    }
}
