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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.sun.management.UnixOperatingSystemMXBean;
import ee.ria.xroad.common.util.SystemMetrics;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * System metrics sensor collects information such as
 * memory, cpu, swap and file descriptors.
 */
public class SystemMetricsSensor extends AbstractSensor {

    public static final int MINUTES_IN_HOUR = 60;
    public static final int SYSTEM_CPU_LOAD_MULTIPLIER = 100;
    public static final int MEASUREMENT_INTERVAL_SECONDS = 5;
    private final SimpleSensor<Long> totalPhysicalMemorySize = new SimpleSensor<>();
    private final SimpleSensor<Long> totalSwapSpaceSize = new SimpleSensor<>();
    private final SimpleSensor<Long> maxFileDescriptorCount = new SimpleSensor<>();

    /**
     * Constructor
     */
    public SystemMetricsSensor() {
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        metricRegistry.register(SystemMetricNames.SYSTEM_CPU_LOAD, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.FREE_PHYSICAL_MEMORY, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.FREE_SWAP_SPACE, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.OPEN_FILE_DESCRIPTOR_COUNT, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.COMMITTED_VIRTUAL_MEMORY, createDefaultHistogram());
        metricRegistry.register(SystemMetricNames.MAX_FILE_DESCRIPTOR_COUNT, maxFileDescriptorCount);
        metricRegistry.register(SystemMetricNames.TOTAL_SWAP_SPACE, totalPhysicalMemorySize);
        metricRegistry.register(SystemMetricNames.TOTAL_PHYSICAL_MEMORY, totalPhysicalMemorySize);

        scheduleSingleMeasurement(getInterval(), new SystemMetricsMeasure());
    }

    private Histogram createDefaultHistogram() {
        return new Histogram(new SlidingTimeWindowReservoir(MINUTES_IN_HOUR, TimeUnit.MINUTES));
    }

    /**
     * Update sensor metrics
     */
    private void updateMetrics() {
        MetricRegistry metrics = MetricRegistryHolder.getInstance().getMetrics();
        UnixOperatingSystemMXBean stats = SystemMetrics.getStats();
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
        if (o instanceof SystemMetricsMeasure) {
            updateMetrics();
            scheduleSingleMeasurement(getInterval(), new SystemMetricsMeasure());
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        return Duration.create(MEASUREMENT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static class SystemMetricsMeasure { }

}
