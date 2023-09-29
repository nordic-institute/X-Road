/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData.SecurityServerType;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getLastRequestTimestampGaugeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestCounterName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestDurationName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestSizeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getResponseSizeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getServiceTypeName;

/**
 * Health data metrics forwarded over JMX. Also, these metrics are used when
 * health data is requested using the getSecurityServerHealthData SOAP request.
 */
@Slf4j
final class HealthDataMetrics {

    private static final int OP_MONITOR_HEALTH_STATS_PERIOD_SECONDS =
            OpMonitoringSystemProperties
                    .getOpMonitorHealthStatisticsPeriodSeconds();

    // The names of metrics that are registered when the operational
    // monitoring daemon starts.
    static final String STATISTICS_PERIOD_SECONDS = "statisticsPeriodSeconds";
    static final String MONITORING_STARTUP_TIMESTAMP =
            "monitoringStartupTimestamp";

    // The timestamps of last successful and unsuccessful requests are stored
    // for each service that is handled for, and are provided when the
    // respective gauge is queried.
    private static Map<String, Long> requestTimestamps = new HashMap<>();

    // Stores the service types of the services
    private static Map<String, String> serviceTypes = new HashMap<>();

    private HealthDataMetrics() {
    }

    /**
     * Register the metrics of health data known at startup.
     * @param registry                 the metric registry of the operational monitoring daemon
     * @param startupTimestampProvider a Supplier instance whose get() method
     *                                 is called when the startup timestamp gauge is queried for data
     */
    static void registerInitialMetrics(MetricRegistry registry,
            Supplier<Long> startupTimestampProvider) {
        registerMonitoringStartupTimestampGauge(registry,
                startupTimestampProvider);
        registerHealthStatisticsPeriodSecondsGauge(registry);
    }

    /**
     * Pick the required health data from all the records and update the
     * metrics registry. If necessary, new metrics are registered.
     * @param registry the metric registry of the operational monitoring daemon
     * @param records  a list of operational data records that will be
     *                 analyzed for health metrics
     */
    static void processRecords(MetricRegistry registry,
            List<OperationalDataRecord> records) {
        for (OperationalDataRecord rec : records) {
            if (!SecurityServerType.PRODUCER.equals(
                    SecurityServerType.fromString(rec.getSecurityServerType()))) {
                // Health data is computed over the requests that are handled
                // in the producer role only.
                continue;
            }

            ServiceId serviceId = HealthDataMetricsUtil.getServiceId(rec);

            if (serviceId == null) {
                // Ignore records without service ID.
                continue;
            }

            registerOrUpdateGauges(registry, serviceId, rec);
            registerOrUpdateCounters(registry, serviceId, rec);

            if (rec.getSucceeded()) {
                // Statistics of request duration and the sizes of the request
                // and response are computed over the successful requests only.
                registerOrUpdateHistograms(registry, serviceId, rec);
            }
        }
    }

    private static void registerOrUpdateGauges(MetricRegistry registry,
            ServiceId serviceId, OperationalDataRecord rec) {
        // last request timestamp
        String expectedGaugeName = getLastRequestTimestampGaugeName(serviceId,
                rec.getSucceeded());
        requestTimestamps.put(expectedGaugeName, rec.getResponseOutTs());
        Gauge gauge = HealthDataMetricsUtil.findGauge(registry,
                expectedGaugeName);
        if (gauge == null) {
            registry.register(expectedGaugeName,
                    (Gauge<Long>) () -> requestTimestamps.get(
                            expectedGaugeName));
        }

        // service type
        String serviceTypeGaugeName = getServiceTypeName(serviceId);
        serviceTypes.put(serviceTypeGaugeName, rec.getServiceType());
        Gauge serviceTypeGauge = HealthDataMetricsUtil.findGauge(registry,
                serviceTypeGaugeName);
        if (serviceTypeGauge == null) {
            registry.register(serviceTypeGaugeName,
                    (Gauge<String>) () -> serviceTypes.get(serviceTypeGaugeName));
        }
    }

    private static void registerOrUpdateCounters(MetricRegistry registry,
            ServiceId serviceId, OperationalDataRecord rec) {
        String expectedCounterName = getRequestCounterName(serviceId,
                rec.getSucceeded());
        Counter counter = HealthDataMetricsUtil.findCounter(registry,
                expectedCounterName);

        if (counter == null) {
            // Register and increment a new counter.
            counter = new SlidingTimeWindowCounter(
                    OP_MONITOR_HEALTH_STATS_PERIOD_SECONDS, TimeUnit.SECONDS);
            registry.register(expectedCounterName, counter);
        }

        counter.inc();
    }

    private static void registerOrUpdateHistograms(MetricRegistry registry,
            ServiceId serviceId, OperationalDataRecord rec) {
        registerOrUpdateHistogram(registry, getRequestDurationName(serviceId), getRequestDuration(rec));

        registerOrUpdateHistogram(registry, getRequestSizeName(serviceId), rec.getRequestSize());
        registerOrUpdateHistogram(registry, getResponseSizeName(serviceId), rec.getResponseSize());
    }

    private static void registerOrUpdateHistogram(MetricRegistry registry, String histogramName, Long newValue) {

        if (newValue == null) return;

        Histogram histogram = HealthDataMetricsUtil.findHistogram(registry, histogramName);

        if (histogram == null) {
            // Add a histogram corresponding to the service and update it.
            histogram = registry.register(histogramName,
                    new Histogram(new SlidingTimeWindowReservoir(
                            OP_MONITOR_HEALTH_STATS_PERIOD_SECONDS,
                            TimeUnit.SECONDS)));
        }

        histogram.update(newValue);
    }

    /**
     * @param record an operational data record
     * @return the duration of the request (the difference between the
     * response out timestamp and the request in timestamp of the request.
     */
    private static Long getRequestDuration(OperationalDataRecord record) {
        return record.getResponseOutTs() - record.getRequestInTs();
    }

    /**
     * Registers the gauge that returns the period of gathering health
     * statistics.
     * @param registry metric registry
     */
    private static void registerHealthStatisticsPeriodSecondsGauge(
            MetricRegistry registry) {
        registry.register(STATISTICS_PERIOD_SECONDS,
                (Gauge<Integer>) () -> OP_MONITOR_HEALTH_STATS_PERIOD_SECONDS);
    }

    /**
     * Registers the gauge that returns the timestamp of the moment when
     * the current instance of the operational monitoring daemon was started.
     * @param registry                 metric registry
     * @param startupTimestampProvider startup timestamp provider
     */
    private static void registerMonitoringStartupTimestampGauge(
            MetricRegistry registry, Supplier<Long> startupTimestampProvider) {
        registry.register(MONITORING_STARTUP_TIMESTAMP,
                (Gauge<Long>) startupTimestampProvider::get);
    }

}
