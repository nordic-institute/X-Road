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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.identifier.ServiceId;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.SortedMap;
import java.util.regex.Pattern;

/**
 * Helper utilities for preparing and processing health data metrics.
 */
@Slf4j
final class HealthDataMetricsUtil {

    // The template of the names of the metrics that are registered each time
    // a new service ID and request status pair is encountered, for storing
    // the last request timestamp. We use the response out timestamp of the
    // respective operational data record.
    private static final String LAST_REQUEST_TIMESTAMP_TEMPLATE =
            "last%sRequestTimestamp(%s)";

    // The template for the names of the sliding window counters that are
    // registered each time a new service ID and request status pair is
    // encountered, for storing the request count.
    private static final String REQUEST_COUNT_TEMPLATE = "%sRequestCount(%s)";

    // The template for the names of the sliding window histograms that are
    // registered each time a new service ID is encountered (successful
    // requests only), for storing the sizes of requests.
    private static final String REQUEST_SIZE_TEMPLATE = "requestSize(%s)";

    // The template for the names of the sliding window histograms that are
    // registered each time a new service ID is encountered (successful
    // requests only), for storing the sizes of responses.
    private static final String RESPONSE_SIZE_TEMPLATE = "responseSize(%s)";

    // The template for the names of the sliding window histograms that are
    // registered each time a new service ID is encountered (successful
    // requests only), for storing the duration of requests.
    private static final String REQUEST_DURATION_TEMPLATE = "requestDuration(%s)";

    // The template for the names of the sliding window histograms that are
    // registered each time a new service ID is encountered (successful
    // requests only), for storing the service type of the services.
    private static final String SERVICE_TYPE_TEMPLATE = "serviceType(%s)";

    private HealthDataMetricsUtil() { }

    /**
     * @param record an operational data record
     * @return the service ID the record describes or null if the relevant
     * fields do not have a value.
     */
    static ServiceId getServiceId(OperationalDataRecord record) {
        try {
            return ServiceId.Conf.create(record.getServiceXRoadInstance(),
                    record.getServiceMemberClass(),
                    record.getServiceMemberCode(),
                    record.getServiceSubsystemCode(), record.getServiceCode(),
                    record.getServiceVersion());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Escape the minimum set of reserved characters in XML so our metrics
     * can be forwarded over JMX. Plus, because of the syntax of Zabbix
     * parameters, escape the dot, the space, the comma, the backslash and
     * square brackets manually.
     * Note that the character set of the XRoad identifiers has not been
     * limited strictly. However, to be able to convert the identifier string
     * with forward slashes as separators, we escape the forward slash inside
     * identifier elements.
     * For consistency, we use HTML escape sequences for all the replacements.
     * If the subsystem code is missing, the generated short string contains an
     * empty character between forward slash separators where the subsystem code
     * would generally be.
     * @param serviceId the service ID as obtained using getServiceIdInRecord()
     * @return the escaped short form of the service ID suitable for using
     * inside a JMX parameter name.
     */
    static String escapeServiceId(ServiceId serviceId) {
        StringBuilder sb = new StringBuilder();
        sb.append(escapeString(serviceId.getXRoadInstance()));
        sb.append('/');
        sb.append(escapeString(serviceId.getMemberClass()));
        sb.append('/');
        sb.append(escapeString(serviceId.getMemberCode()));
        sb.append('/');

        if (!StringUtils.isEmpty(serviceId.getSubsystemCode())) {
            sb.append(escapeString(serviceId.getSubsystemCode()));
        }

        sb.append('/');
        sb.append(escapeString(serviceId.getServiceCode()));

        if (!StringUtils.isEmpty(serviceId.getServiceVersion())) {
            sb.append('/');
            sb.append(escapeString(serviceId.getServiceVersion()));
        }

        return sb.toString();
    }

    private static String escapeString(String string) {
        return StringEscapeUtils.escapeXml11(string)
                // For Zabbix: escape the dots that are part of the service
                // ID so Zabbix won't split the value when processing the
                // key of the item such as:
                // jmx[com.example:Type=Hello,all.fruits.apple.weight]
                .replace(".", "&#46;")
                // For Zabbix: escape the backslash, the space and the comma:
                .replace("\\", "&#92;")
                .replace(" ", "&#32;")
                .replace(",", "&#44;")
                // For Zabbix: escape the square brackets that are used as
                // the containers of the JMX parameter name and attribute
                // such as:
                // jmx[<parameter name [with square brackets], attribute>].
                .replace("[", "&#91;").replace("]", "&#93;")
                // Replace forward slashes to be able to convert the identifier
                // string back into a service identifier object
                .replace("/", "&#47;");
    }

    /**
     * @param metricName an metric name where the service ID part has been
     * escaped using escapeServiceId().
     * @return the quoted metric name suitable for passing to a regular
     * expression matcher method
     */
    static String formatMetricMatchRegexp(String metricName) {
        return String.format("^%s$", Pattern.quote(metricName));
    }

    /**
     * @param serviceId the service ID as obtained using getServiceIdInRecord()
     * @param parameterKeyTemplate template string of the JMX parameter name
     * @param requestSucceeded set to true for a successfully mediated request
     * @return the key of the parameter suitable for forwarding over JMX to
     * Zabbix.
     */
    private static String formatParameterAndStatusKey(ServiceId serviceId,
            String parameterKeyTemplate, boolean requestSucceeded,
            boolean uppercase) {
        return String.format(parameterKeyTemplate,
                requestSucceeded ? (uppercase ? "Successful" : "successful")
                        : (uppercase ? "Unsuccessful" : "unsuccessful"),
                escapeServiceId(serviceId));
    }

    static String getRequestCounterName(ServiceId serviceId, boolean success) {
        return formatParameterAndStatusKey(serviceId, REQUEST_COUNT_TEMPLATE,
                success, false);
    }

    static String getLastRequestTimestampGaugeName(ServiceId serviceId,
            boolean success) {
        return formatParameterAndStatusKey(serviceId,
                LAST_REQUEST_TIMESTAMP_TEMPLATE, success, true);
    }

    static String getRequestDurationName(ServiceId serviceId) {
        return formatParameterKey(serviceId, REQUEST_DURATION_TEMPLATE);
    }

    static String getRequestSizeName(ServiceId serviceId) {
        return formatParameterKey(serviceId, REQUEST_SIZE_TEMPLATE);
    }

    static String getResponseSizeName(ServiceId serviceId) {
        return formatParameterKey(serviceId, RESPONSE_SIZE_TEMPLATE);
    }

    static String getServiceTypeName(ServiceId serviceId) {
        return formatParameterKey(serviceId, SERVICE_TYPE_TEMPLATE);
    }

    /**
     * @param serviceId the service ID as obtained using getServiceIdInRecord()
     * @param parameterKeyTemplate template string of the JMX parameter name
     * @return the key of the parameter suitable for forwarding over JMX to
     * Zabbix.
     */
    static String formatParameterKey(ServiceId serviceId,
            String parameterKeyTemplate) {
        return String.format(parameterKeyTemplate, escapeServiceId(serviceId));
    }

    /**
     * @param registry the metric registry where the gauge should be looked up
     * @param expectedGaugeName the gauge name to find
     * @return the found gauge or null if it does not exist
     */
    static Gauge findGauge(MetricRegistry registry, String expectedGaugeName) {
        SortedMap<String, Gauge> gauges = registry.getGauges(
                (name, metric) -> name.matches(HealthDataMetricsUtil
                        .formatMetricMatchRegexp(expectedGaugeName))
        );

        if (gauges.size() > 1) {
            // Should not happen because we use a strict regexp.
            log.warn("Multiple gauges matched the name "
                    + expectedGaugeName);
        }

        return gauges.isEmpty() ? null : gauges.values().iterator().next();
    }

    /**
     * @param registry the metric registry where the counter should be looked up
     * @param expectedCounterName the counter name to find
     * @return the found counter or null if it does not exist
     */
    static Counter findCounter(MetricRegistry registry,
            String expectedCounterName) {
        SortedMap<String, Counter> counters = registry.getCounters(
                (name, metric) -> name.matches(HealthDataMetricsUtil
                        .formatMetricMatchRegexp(expectedCounterName))
        );

        if (counters.size() > 1) {
            // Should not happen because we use a strict regexp.
            log.warn("Multiple counters matched the name "
                    + expectedCounterName);
        }

        return counters.isEmpty() ? null : counters.values().iterator().next();
    }

    /**
     * @param registry the metric registry where the histogram should be
     * looked up
     * @param expectedHistogramName the counter name to find
     * @return the found histogram or null if it does not exist
     */
    static Histogram findHistogram(MetricRegistry registry,
            String expectedHistogramName) {
        SortedMap<String, Histogram> histograms = registry.getHistograms(
                (name, metric) -> name.matches(HealthDataMetricsUtil
                        .formatMetricMatchRegexp(expectedHistogramName))
        );

        if (histograms.size() > 1) {
            // Should not happen because we use a strict regexp.
            log.warn("Multiple histograms matched the name "
                    + expectedHistogramName);
        }

        return histograms.isEmpty() ? null
                : histograms.values().iterator().next();
    }
}
