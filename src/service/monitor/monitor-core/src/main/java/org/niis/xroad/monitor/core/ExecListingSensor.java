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
import org.niis.xroad.monitor.core.executablelister.OsInfoLister;
import org.niis.xroad.monitor.core.executablelister.PackageLister;
import org.niis.xroad.monitor.core.executablelister.ProcessLister;
import org.niis.xroad.monitor.core.executablelister.XroadProcessLister;

import java.util.ArrayList;

/**
 * Sensor which collects data by running external commands and
 * parsing output from those.
 */
@Slf4j
@ApplicationScoped
public class ExecListingSensor {

    private MetricRegistryHolder registryHolder;

    public ExecListingSensor(EnvMonitorProperties envMonitorProperties) {
        log.info("Creating sensor, measurement interval: {}", envMonitorProperties.execListingSensorInterval());
    }

    private void createOrUpdateMetricPair(String parsedName, String jmxName, JmxStringifiedData data) {
        createOrUpdateParsedMetric(parsedName, data);
        createJmxMetric(jmxName, data);
    }

    private void createOrUpdateParsedMetric(String metricName, JmxStringifiedData data) {
        SimpleSensor<JmxStringifiedData> sensor = registryHolder.getOrCreateSimpleSensor(metricName);
        sensor.update(data);
    }

    private void createJmxMetric(String metricName, JmxStringifiedData data) {
        SimpleSensor<ArrayList> sensor = registryHolder.getOrCreateSimpleSensor(metricName);
        sensor.update(data.getJmxStringData());
    }

    private void createOsStringMetric(String metricName, JmxStringifiedData<String> data) {
        SimpleSensor<String> sensor = registryHolder.getOrCreateSimpleSensor(metricName);
        sensor.update(data.getJmxStringData().getFirst());
    }

    private void updateMetrics() {
        registryHolder = MetricRegistryHolder.getInstance();

        createOrUpdateMetricPair(
                SystemMetricNames.PROCESSES,
                SystemMetricNames.PROCESS_STRINGS,
                new ProcessLister().list()
        );

        createOrUpdateMetricPair(
                SystemMetricNames.XROAD_PROCESSES,
                SystemMetricNames.XROAD_PROCESS_STRINGS,
                new XroadProcessLister().list()
        );

        createOrUpdateMetricPair(
                SystemMetricNames.PACKAGES,
                SystemMetricNames.PACKAGE_STRINGS,
                new PackageLister().list()
        );

        createOsStringMetric(SystemMetricNames.OS_INFO, new OsInfoLister().list());
    }

    @Scheduled(every = "${xroad.env-monitor.exec-listing-sensor-interval}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            skipExecutionIf = Scheduled.ApplicationNotRunning.class)
    public void measure() {
        log.debug("Updating metrics");
        updateMetrics();
    }

}
