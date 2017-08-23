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

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.monitor.executablelister.*;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Sensor which collects data by running external commands and
 * parsing output from those.
 */
@Slf4j
public class ExecListingSensor extends AbstractSensor {

    /**
     * Constructor
     */
    public <T extends Metric> ExecListingSensor() {
        log.info("Creating sensor, measurement interval: {}", getInterval());
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        JmxStringifiedData<ProcessInfo> processes = new ProcessLister().list();
        JmxStringifiedData<ProcessInfo> xroadProcesses = new XroadProcessLister().list();
        JmxStringifiedData<PackageInfo> packages = new PackageLister().list();
        JmxStringifiedData<String> operatingSystemInfo = new OsInfoLister().list();
        metricRegistry.register(SystemMetricNames.PROCESSES, createParsedMetric(processes));
        metricRegistry.register(SystemMetricNames.PROCESS_STRINGS, createJmxMetric(processes));
        metricRegistry.register(SystemMetricNames.XROAD_PROCESSES, createParsedMetric(xroadProcesses));
        metricRegistry.register(SystemMetricNames.XROAD_PROCESS_STRINGS, createJmxMetric(xroadProcesses));
        metricRegistry.register(SystemMetricNames.PACKAGES, createParsedMetric(packages));
        metricRegistry.register(SystemMetricNames.PACKAGE_STRINGS, createJmxMetric(packages));
        metricRegistry.register(SystemMetricNames.OS_INFO, createOsStringMetric(operatingSystemInfo));
        scheduleSingleMeasurement(getInterval(), new ProcessMeasure());
    }

    private Metric createParsedMetric(JmxStringifiedData data) {
        SimpleSensor<JmxStringifiedData> sensor = new SimpleSensor<>();
        sensor.update(data);
        return sensor;
    }
    private Metric createJmxMetric(JmxStringifiedData data) {
        SimpleSensor<ArrayList> sensor = new SimpleSensor<>();
        sensor.update(data.getJmxStringData());
        return sensor;
    }
    private Metric createOsStringMetric(JmxStringifiedData<String> data) {
        SimpleSensor<String> sensor = new SimpleSensor<>();
        sensor.update(data.getJmxStringData().get(0));
        return sensor;
    }

    private void updateMetrics() {
        MetricRegistry metricRegistry = MetricRegistryHolder.getInstance().getMetrics();
        JmxStringifiedData<ProcessInfo> processes = new ProcessLister().list();
        JmxStringifiedData<ProcessInfo> xroadProcesses = new XroadProcessLister().list();
        JmxStringifiedData<PackageInfo> packages = new PackageLister().list();
        JmxStringifiedData<String> operatingSystemInfo = new OsInfoLister().list();
        String osString = operatingSystemInfo.getJmxStringData().get(0);
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.PROCESSES))
                .update(processes);
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.PROCESS_STRINGS))
                .update(processes.getJmxStringData());
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.XROAD_PROCESSES))
                .update(xroadProcesses);
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.XROAD_PROCESS_STRINGS))
                .update(xroadProcesses.getJmxStringData());
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.PACKAGES))
                .update(packages);
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.PACKAGE_STRINGS))
                .update(packages.getJmxStringData());
        ((SimpleSensor) metricRegistry.getMetrics().get(SystemMetricNames.OS_INFO))
                .update(osString);
    }

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof ProcessMeasure) {
            log.debug("Updating metrics");
            updateMetrics();
            scheduleSingleMeasurement(getInterval(), new ProcessMeasure());
        }
    }

    @Override
    protected FiniteDuration getInterval() {
        return Duration.create(SystemProperties.getEnvMonitorExecListingSensorInterval(), TimeUnit.SECONDS);
    }

    private static class ProcessMeasure { }

}
