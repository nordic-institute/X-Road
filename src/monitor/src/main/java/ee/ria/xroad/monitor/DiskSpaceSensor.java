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
import ee.ria.xroad.monitor.common.SystemMetricNames;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.io.File;
import java.time.Duration;

/**
 * Collects disk space information
 */
@Slf4j
public class DiskSpaceSensor extends AbstractSensor {

    /**
     * Constructor
     */
    public DiskSpaceSensor(TaskScheduler taskScheduler) {
        super(taskScheduler);
        log.info("Creating sensor, measurement interval: {}", getInterval());
        updateMetrics();
        scheduleSingleMeasurement(getInterval());
    }

    private void updateMetrics() {
        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) {
            final MetricRegistryHolder registryHolder = MetricRegistryHolder.getInstance();
            for (File drive : roots) {
                SimpleSensor<Long> total = registryHolder.getOrCreateSimpleSensor(
                        String.format("%s_%s", SystemMetricNames.DISK_SPACE_TOTAL, drive));

                SimpleSensor<Long> free = registryHolder.getOrCreateSimpleSensor(
                        String.format("%s_%s", SystemMetricNames.DISK_SPACE_FREE, drive));

                total.update(drive.getTotalSpace());
                free.update(drive.getFreeSpace());
            }
        }
    }

    @Override
    protected void measure() {
        log.debug("Updating metrics");
        updateMetrics();
        scheduleSingleMeasurement(getInterval());
    }

    @Override
    protected Duration getInterval() {
        return Duration.ofSeconds(SystemProperties.getEnvMonitorDiskSpaceSensorInterval());
    }

}
