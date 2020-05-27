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
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.monitor.common.SystemMetricsRequest;
import ee.ria.xroad.monitor.common.SystemMetricsResponse;
import ee.ria.xroad.monitor.common.dto.HistogramDto;
import ee.ria.xroad.monitor.common.dto.MetricDto;
import ee.ria.xroad.monitor.common.dto.MetricSetDto;
import ee.ria.xroad.monitor.common.dto.SimpleMetricDto;
import ee.ria.xroad.monitor.executablelister.PackageInfo;
import ee.ria.xroad.monitor.executablelister.ProcessInfo;

import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Actor for providing system metrics data
 */
public class MetricsProviderActor extends UntypedAbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private static final List<String> PACKAGE_OR_CERTIFICATE_METRIC_NAMES = Lists.newArrayList(
            SystemMetricNames.PROCESSES,
            SystemMetricNames.PROCESS_STRINGS,
            SystemMetricNames.XROAD_PROCESSES,
            SystemMetricNames.XROAD_PROCESS_STRINGS,
            SystemMetricNames.PACKAGES,
            SystemMetricNames.PACKAGE_STRINGS,
            SystemMetricNames.CERTIFICATES,
            SystemMetricNames.CERTIFICATES_STRINGS
    );

    /**
     * Two phase filter for checking user requested metric names and additional chained filter for
     * application defined metric names (histogram/process/certificate/package/etc).
     */
    public class SystemMetricsFilter implements MetricFilter {

        private final List<String> metricNames;
        private final MetricFilter chainedFilter;

        /**
         * Must match metricNames if not null AND must match chainedFilter if not null.
         *
         * @param metricNames   list of requested metrics. Null for all.
         * @param chainedFilter specialized additional filter. Null for ignore additional filter.
         */
        public SystemMetricsFilter(List<String> metricNames, MetricFilter chainedFilter) {
            this.metricNames = metricNames;
            this.chainedFilter = chainedFilter;
        }

        /**
         * Match any in case metricNames not defined or empty.
         *
         * @param name
         * @param metric
         * @return
         */
        @Override
        public boolean matches(String name, Metric metric) {
            return isRequestedParameterName(name) && isMatchByChainedFilter(name, metric);
        }

        private boolean isRequestedParameterName(String name) {
            return metricNames == null || metricNames.size() == 0 || metricNames.contains(name);
        }

        private boolean isMatchByChainedFilter(String name, Metric metric) {
            return chainedFilter != null ? chainedFilter.matches(name, metric) : true;
        }
    }

    @Override
    public void onReceive(Object o) throws Exception {

        if (o instanceof SystemMetricsRequest) {

            final SystemMetricsRequest req = (SystemMetricsRequest) o;
            log.info("Received SystemMetricsRequest: " + req);

            if (req.getMetricNames() != null && req.getMetricNames().size() > 0) {
                log.info("Specified metrics requested: " + req.getMetricNames());
                log.info("Is owner of security server: " + req.isClientOwner());
            }

            MetricRegistry metrics = MetricRegistryHolder.getInstance().getMetrics();
            final MetricSetDto.Builder builder = new MetricSetDto.Builder("systemMetrics");

            collectMetrics(builder, metrics, req.getMetricNames(), req.isClientOwner());

            if (req.isClientOwner() || !SystemProperties.getEnvMonitorLimitRemoteDataSet()) {
                collectOwnerMetrics(builder, metrics, req.getMetricNames());
            }

            MetricSetDto metricSet = builder.build();
            final SystemMetricsResponse response = new SystemMetricsResponse(metricSet);
            getSender().tell(response, getSelf());

        } else {
            unhandled(o);
        }
    }

    private void collectMetrics(MetricSetDto.Builder builder, MetricRegistry metrics, List<String> metricNames,
                                boolean clientOwner) {
        SystemMetricsFilter certificateMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> SystemMetricNames.CERTIFICATES.equals(name));

        SystemMetricsFilter simpleMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> filterPackageOrCertifates(clientOwner, name));

        for (Map.Entry<String, Gauge> e : metrics.getGauges(certificateMetricFilter).entrySet()) {
            builder.withMetric(toCertificateMetricSetDTO(e.getKey(), e.getValue()));
        }

        for (Map.Entry<String, Gauge> e : metrics.getGauges(simpleMetricFilter).entrySet()) {
            builder.withMetric(toSimpleMetricDto(e.getKey(), e.getValue()));
        }
    }

    private void collectOwnerMetrics(MetricSetDto.Builder builder, MetricRegistry metrics, List<String> metricNames) {
        SystemMetricsFilter histogramMetricFilter = new SystemMetricsFilter(metricNames,
                null);

        SystemMetricsFilter processMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> SystemMetricNames.PROCESSES.equals(name)
                        || SystemMetricNames.XROAD_PROCESSES.equals(name));

        SystemMetricsFilter packageMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> SystemMetricNames.PACKAGES.equals(name));

        for (Map.Entry<String, Histogram> e : metrics.getHistograms(histogramMetricFilter).entrySet()) {
            builder.withMetric(toHistogramDto(e.getKey(), e.getValue().getSnapshot()));
        }

        // dont handle processes, packages and certificates gauges normally,
        // they have have special conversions to dto
        // *_STRINGS gauges are only for JMX reporting
        for (Map.Entry<String, Gauge> e : metrics.getGauges(processMetricFilter).entrySet()) {
            builder.withMetric(toProcessMetricSetDto(e.getKey(), e.getValue()));
        }


        for (Map.Entry<String, Gauge> e : metrics.getGauges(packageMetricFilter).entrySet()) {
            builder.withMetric(toPackageMetricSetDto(e.getKey(), e.getValue()));
        }
    }

    private boolean filterPackageOrCertifates(boolean isOwner, String name) {
        if (isOwner || !SystemProperties.getEnvMonitorLimitRemoteDataSet()) {
            return !PACKAGE_OR_CERTIFICATE_METRIC_NAMES.contains(name);
        } else {
            return name.equals("OperatingSystem");
        }
    }

    private MetricSetDto toProcessMetricSetDto(String name,
                                               Gauge<JmxStringifiedData<ProcessInfo>> processSensor) {
        JmxStringifiedData<ProcessInfo> p = processSensor.getValue();
        MetricSetDto.Builder mainBuilder = new MetricSetDto.Builder(name);
        for (ProcessInfo process : p.getDtoData()) {
            MetricSetDto.Builder processBuilder = new MetricSetDto.Builder(process.getProcessId());
            mainBuilder.withMetric(processBuilder
                    .withSimpleMetric("processId", process.getProcessId())
                    .withSimpleMetric("command", process.getCommand())
                    .withSimpleMetric("cpuLoad", process.getCpuLoad())
                    .withSimpleMetric("memUsed", process.getMemUsed())
                    .withSimpleMetric("startTime", process.getStartTime())
                    .withSimpleMetric("userId", process.getUserId())
                    .build());
        }
        return mainBuilder.build();
    }


    private MetricSetDto toCertificateMetricSetDTO(
            String name,
            Gauge<JmxStringifiedData<CertificateMonitoringInfo>> certificateSensor) {
        JmxStringifiedData<CertificateMonitoringInfo> c = certificateSensor.getValue();
        MetricSetDto.Builder mainBuilder = new MetricSetDto.Builder(name);
        for (CertificateMonitoringInfo cert : c.getDtoData()) {
            MetricSetDto.Builder certBuilder = new MetricSetDto.Builder("certificate-" + cert.getSha1hash());
            mainBuilder.withMetric(certBuilder
                    .withSimpleMetric("sha1Hash", cert.getSha1hash())
                    .withSimpleMetric("notBefore", cert.getNotBefore())
                    .withSimpleMetric("notAfter", cert.getNotAfter())
                    .withSimpleMetric("certificateType", cert.getType().name())
                    .withSimpleMetric("active", cert.isActive())
                    .build());
        }
        return mainBuilder.build();
    }

    private MetricSetDto toPackageMetricSetDto(String name,
                                               Gauge<JmxStringifiedData<PackageInfo>> packageSensor) {
        JmxStringifiedData<PackageInfo> p = packageSensor.getValue();
        MetricSetDto.Builder mainBuilder = new MetricSetDto.Builder(name);
        for (PackageInfo pac : p.getDtoData()) {
            mainBuilder.withSimpleMetric(pac.getName(), pac.getVersion());
        }
        return mainBuilder.build();
    }

    private <T extends Serializable> SimpleMetricDto<T> toSimpleMetricDto(String key, Gauge<T> value) {
        return new SimpleMetricDto<>(key, value.getValue());
    }

    private MetricDto toHistogramDto(String name, Snapshot snapshot) {
        return new HistogramDto(
                name,
                snapshot.get75thPercentile(),
                snapshot.get95thPercentile(),
                snapshot.get98thPercentile(),
                snapshot.get99thPercentile(),
                snapshot.get999thPercentile(),
                snapshot.getMax(),
                snapshot.getMean(),
                snapshot.getMedian(),
                snapshot.getMin(),
                snapshot.getStdDev()
        );
    }

}
