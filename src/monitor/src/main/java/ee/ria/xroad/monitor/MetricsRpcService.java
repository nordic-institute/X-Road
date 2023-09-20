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
import ee.ria.xroad.monitor.executablelister.PackageInfo;
import ee.ria.xroad.monitor.executablelister.ProcessInfo;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.google.common.collect.Lists;
import com.google.protobuf.util.Timestamps;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.monitor.common.HistogramMetrics;
import org.niis.xroad.monitor.common.Metrics;
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.common.MetricsServiceGrpc;
import org.niis.xroad.monitor.common.SingleMetrics;
import org.niis.xroad.monitor.common.SystemMetricsReq;
import org.niis.xroad.monitor.common.SystemMetricsResp;

import java.util.List;
import java.util.Map;

/**
 * Actor for providing system metrics data
 */
@Slf4j
public class MetricsRpcService extends MetricsServiceGrpc.MetricsServiceImplBase {
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
    public static class SystemMetricsFilter implements MetricFilter {

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
    public void getMetrics(SystemMetricsReq req, StreamObserver<SystemMetricsResp> responseObserver) {
        log.info("Received SystemMetricsRequest: " + req);
        if (!req.getMetricNamesList().isEmpty()) {
            log.info("Specified metrics requested: " + req.getMetricNamesList());
            log.info("Is owner of security server: " + req.getIsClientOwner());
        }

        MetricRegistry metrics = MetricRegistryHolder.getInstance().getMetrics();
        var responseBuilder = SystemMetricsResp.newBuilder();
        responseBuilder.getMetricsBuilder().setName("systemMetrics");

        collectMetrics(responseBuilder, metrics, req.getMetricNamesList(), req.getIsClientOwner());

        if (req.getIsClientOwner() || !SystemProperties.getEnvMonitorLimitRemoteDataSet()) {
            collectOwnerMetrics(responseBuilder, metrics, req.getMetricNamesList());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private void collectMetrics(SystemMetricsResp.Builder builder, MetricRegistry metrics, List<String> metricNames,
                                boolean clientOwner) {
        SystemMetricsFilter certificateMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> SystemMetricNames.CERTIFICATES.equals(name));

        SystemMetricsFilter simpleMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> filterPackageOrCertifates(clientOwner, name));

        for (Map.Entry<String, Gauge> e : metrics.getGauges(certificateMetricFilter).entrySet()) {
            builder.getMetricsBuilder().addMetrics(toCertificateMetricSetDTO(e.getKey(), e.getValue()));
        }

        for (Map.Entry<String, Gauge> e : metrics.getGauges(simpleMetricFilter).entrySet()) {
            builder.getMetricsBuilder().addMetrics(toSimpleMetricDto(e.getKey(), e.getValue()));
        }
    }

    private void collectOwnerMetrics(SystemMetricsResp.Builder builder, MetricRegistry metrics, List<String> metricNames) {
        SystemMetricsFilter histogramMetricFilter = new SystemMetricsFilter(metricNames,
                null);

        SystemMetricsFilter processMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> SystemMetricNames.PROCESSES.equals(name)
                        || SystemMetricNames.XROAD_PROCESSES.equals(name));

        SystemMetricsFilter packageMetricFilter = new SystemMetricsFilter(metricNames,
                (name, metric) -> SystemMetricNames.PACKAGES.equals(name));

        for (Map.Entry<String, Histogram> e : metrics.getHistograms(histogramMetricFilter).entrySet()) {
            builder.getMetricsBuilder().addMetrics(toHistogramDto(e.getKey(), e.getValue().getSnapshot()));
        }

        // dont handle processes, packages and certificates gauges normally,
        // they have have special conversions to dto
        // *_STRINGS gauges are only for JMX reporting
        for (Map.Entry<String, Gauge> e : metrics.getGauges(processMetricFilter).entrySet()) {
            builder.getMetricsBuilder().addMetrics(toProcessMetricSetDto(e.getKey(), e.getValue()));
        }


        for (Map.Entry<String, Gauge> e : metrics.getGauges(packageMetricFilter).entrySet()) {
            builder.getMetricsBuilder().addMetrics(toPackageMetricSetDto(e.getKey(), e.getValue()));
        }
    }

    private boolean filterPackageOrCertifates(boolean isOwner, String name) {
        if (isOwner || !SystemProperties.getEnvMonitorLimitRemoteDataSet()) {
            return !PACKAGE_OR_CERTIFICATE_METRIC_NAMES.contains(name);
        } else {
            return name.equals("OperatingSystem");
        }
    }

    private Metrics toProcessMetricSetDto(String name,
                                          Gauge<JmxStringifiedData<ProcessInfo>> processSensor) {
        JmxStringifiedData<ProcessInfo> p = processSensor.getValue();

        var metricsGroup = MetricsGroup.newBuilder()
                .setName(name);
        for (ProcessInfo process : p.getDtoData()) {
            var processMetrics = MetricsGroup.newBuilder()
                    .setName(process.getProcessId())
                    .addMetrics(toSingleMetrics("processId", process.getProcessId()))
                    .addMetrics(toSingleMetrics("command", process.getCommand()))
                    .addMetrics(toSingleMetrics("cpuLoad", process.getCpuLoad()))
                    .addMetrics(toSingleMetrics("memUsed", process.getMemUsed()))
                    .addMetrics(toSingleMetrics("startTime", process.getStartTime()))
                    .addMetrics(toSingleMetrics("userId", process.getUserId()));

            metricsGroup.addMetrics(Metrics.newBuilder()
                    .setMetricsGroup(processMetrics)
                    .build());
        }
        return Metrics.newBuilder()
                .setMetricsGroup(metricsGroup)
                .build();
    }


    private Metrics toCertificateMetricSetDTO(
            String name,
            Gauge<JmxStringifiedData<CertificateMonitoringInfo>> certificateSensor) {
        JmxStringifiedData<CertificateMonitoringInfo> c = certificateSensor.getValue();

        var metricsGroup = MetricsGroup.newBuilder()
                .setName(name);
        for (CertificateMonitoringInfo cert : c.getDtoData()) {
            var certMetrics = MetricsGroup.newBuilder()
                    .setName("certificate-" + cert.getSha1hash())
                    .addMetrics(toSingleMetrics("sha1Hash", cert.getSha1hash()))
                    .addMetrics(toSingleMetrics("notBefore", cert.getNotBefore()))
                    .addMetrics(toSingleMetrics("notAfter", cert.getNotAfter()))
                    .addMetrics(toSingleMetrics("certificateType", cert.getType().name()))
                    .addMetrics(toSingleMetrics("active", String.valueOf(cert.isActive())));

            metricsGroup.addMetrics(Metrics.newBuilder()
                    .setMetricsGroup(certMetrics)
                    .build());
        }

        return Metrics.newBuilder()
                .setMetricsGroup(metricsGroup)
                .build();
    }

    private Metrics.Builder toPackageMetricSetDto(String name,
                                                  Gauge<JmxStringifiedData<PackageInfo>> packageSensor) {
        JmxStringifiedData<PackageInfo> p = packageSensor.getValue();

        var packageMetrics = MetricsGroup.newBuilder()
                .setName(name);
        for (PackageInfo pac : p.getDtoData()) {
            packageMetrics.addMetrics(toSingleMetrics(pac.getName(), pac.getVersion()));
        }

        return Metrics.newBuilder().setMetricsGroup(packageMetrics);
    }

    private Metrics.Builder toSimpleMetricDto(String key, Gauge<?> value) {
        return toSingleMetrics(key, String.valueOf(value.getValue()));
    }

    private Metrics.Builder toSingleMetrics(String key, String value) {
        return Metrics.newBuilder().setSingleMetrics(SingleMetrics.newBuilder()
                .setName(key)
                .setValue(value));
    }

    private Metrics.Builder toHistogramDto(String name, Snapshot snapshot) {
        var histogram = HistogramMetrics.newBuilder()
                .setName(name)
                .setUpdateDateTime(Timestamps.now())
                .setDistribution75ThPercentile(snapshot.get75thPercentile())
                .setDistribution95ThPercentile(snapshot.get95thPercentile())
                .setDistribution98ThPercentile(snapshot.get98thPercentile())
                .setDistribution99ThPercentile(snapshot.get99thPercentile())
                .setDistribution999ThPercentile(snapshot.get999thPercentile())
                .setMax(snapshot.getMax())
                .setMean(snapshot.getMean())
                .setMedian(snapshot.getMedian())
                .setMin(snapshot.getMin())
                .setStdDev(snapshot.getStdDev());

        return Metrics.newBuilder().setSingleHistogram(histogram);
    }

}
