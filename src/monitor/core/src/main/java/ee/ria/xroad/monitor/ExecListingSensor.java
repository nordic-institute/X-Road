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
import ee.ria.xroad.monitor.executablelister.OsInfoLister;
import ee.ria.xroad.monitor.executablelister.PackageLister;
import ee.ria.xroad.monitor.executablelister.ProcessLister;
import ee.ria.xroad.monitor.executablelister.XroadProcessLister;

import com.codahale.metrics.Metric;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Sensor which collects data by running external commands and
 * parsing output from those.
 */
@Slf4j
public class ExecListingSensor extends AbstractSensor {

    private MetricRegistryHolder registryHolder;

    /**
     * Constructor
     */
    public <T extends Metric> ExecListingSensor(TaskScheduler taskScheduler) {
        super(taskScheduler);
        log.info("Creating sensor, measurement interval: {}", getInterval());
        updateMetrics();
        scheduleSingleMeasurement(getInterval());
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
        sensor.update(data.getJmxStringData().get(0));

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

    @Override
    public void measure() {
        log.debug("Updating metrics");
        updateMetrics();
        scheduleSingleMeasurement(getInterval());

    }

    @Override
    protected Duration getInterval() {
        return Duration.ofSeconds(SystemProperties.getEnvMonitorExecListingSensorInterval());
    }

}
