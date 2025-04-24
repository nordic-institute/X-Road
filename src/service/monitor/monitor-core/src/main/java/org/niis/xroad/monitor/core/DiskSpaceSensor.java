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
package org.niis.xroad.monitor.core;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.monitor.core.common.SystemMetricNames;

import java.io.File;

/**
 * Collects disk space information
 */
@Slf4j
@ApplicationScoped
public class DiskSpaceSensor {

    /**
     * Constructor
     */
    public DiskSpaceSensor(EnvMonitorProperties envMonitorProperties) {
        log.info("Creating sensor, measurement interval: {}", envMonitorProperties.diskSpaceSensorInterval());
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

    @Scheduled(every = "${xroad.env-monitor.disk-space-sensor-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    protected void measure() {
        log.debug("Updating metrics");
        updateMetrics();
    }

}
