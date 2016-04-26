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

import com.codahale.metrics.MetricRegistry;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Collects disk space information
 */
@Slf4j
public class DiskSpaceSensor extends AbstractSensor {

    private Map<String, SensorPair> drives = new HashMap<>();

    public DiskSpaceSensor() {
        scheduleSingleMeasurement(getInterval(), new DiskSpaceMeasure());
    }

    private void updateMetrics() {
        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) {
            for (File drive: roots) {
                final String id = drive.toString();
                registerDiskSpaceGaugesIfNeeded(id);
                drives.get(id).getTotal().update(drive.getTotalSpace());
                drives.get(id).getFree().update(drive.getFreeSpace());
            }
        }
    }

    private void registerDiskSpaceGaugesIfNeeded(String drive) {
        if (!drives.containsKey(drive)) {
            SimpleSensor<Long> total = new SimpleSensor<>();
            SimpleSensor<Long> free = new SimpleSensor<>();
            MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
            metricRegistry.register(String.format("%s_%s", SystemMetricNames.DISK_SPACE_TOTAL, drive), total);
            metricRegistry.register(String.format("%s_%s", SystemMetricNames.DISK_SPACE_FREE, drive), free);
            drives.put(drive.toString(), new SensorPair(total, free));
        }
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof DiskSpaceMeasure) {
            updateMetrics();
            scheduleSingleMeasurement(getInterval(), new DiskSpaceMeasure());
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        return Duration.create(1, TimeUnit.MINUTES);
    }

    private static class DiskSpaceMeasure { }

    @AllArgsConstructor
    @Getter
    private static class SensorPair {
        private final SimpleSensor<Long> total;
        private final SimpleSensor<Long> free;
    }
}
