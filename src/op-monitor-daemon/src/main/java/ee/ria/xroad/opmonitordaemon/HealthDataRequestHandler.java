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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.opmonitordaemon.message.FilterCriteriaType;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerHealthDataResponseType;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerHealthDataType;
import ee.ria.xroad.opmonitordaemon.message.LastPeriodStatisticsType;
import ee.ria.xroad.opmonitordaemon.message.ServiceEventsType;
import ee.ria.xroad.opmonitordaemon.message.ServicesEventsType;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static ee.ria.xroad.opmonitordaemon.HealthDataMetrics.MONITORING_STARTUP_TIMESTAMP;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetrics.STATISTICS_PERIOD_SECONDS;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.findCounter;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.findGauge;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.findHistogram;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getLastRequestTimestampGaugeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestCounterName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestDurationName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getRequestSizeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getResponseSizeName;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.getServiceTypeName;

/**
 * Query handler for health data requests.
 */
@Slf4j
@RequiredArgsConstructor
public class HealthDataRequestHandler extends QueryRequestHandler {

    private static final int SERVICE_ID_NUM_PARTS = 6;
    private static final int SERVICE_ID_VERSION_PART = 5;
    private static final int SERVICE_ID_CODE_PART = 4;
    private static final int SERVICE_ID_SUBSYSTEM_PART = 3;

    private static final String IDENTIFIER_SEPARATOR = "/";

    /** The registry of health data. */
    private final MetricRegistry healthMetricRegistry;

    @Override
    public void handle(SoapMessageImpl requestSoap, OutputStream out,
            Consumer<String> contentTypeCallback) throws Exception {
        log.trace("handle()");

        ClientId.Conf clientId = requestSoap.getClient();
        GetSecurityServerHealthDataType requestData = getRequestData(
                requestSoap, GetSecurityServerHealthDataType.class);

        Optional<ClientId.Conf> provider = Optional.ofNullable(
                requestData.getFilterCriteria())
                .map(FilterCriteriaType::getClient);

        log.debug("Handle getSecurityServerHealthData: clientId: {}, "
                + "filterCriteria.client: {}", clientId, provider.orElse(null));

        SoapMessageImpl response = createResponse(requestSoap,
                buildHealthDataResponse(provider));

        contentTypeCallback.accept(response.getContentType());
        out.write(response.getBytes());
    }

    @SuppressWarnings("unchecked")
    private JAXBElement<?> buildHealthDataResponse(
            Optional<ClientId.Conf> provider) throws Exception {
        GetSecurityServerHealthDataResponseType healthDataResponse =
                OBJECT_FACTORY.createGetSecurityServerHealthDataResponseType();

        Optional<Gauge<Long>> timestamp = Optional.ofNullable(
                findGauge(healthMetricRegistry, MONITORING_STARTUP_TIMESTAMP));

        healthDataResponse.setMonitoringStartupTimestamp(
                timestamp.orElseThrow(this::missingTimestamp).getValue());
        Optional<Gauge<Integer>> statisticsPeriodSeconds = Optional.ofNullable(
                findGauge(healthMetricRegistry, STATISTICS_PERIOD_SECONDS));
        healthDataResponse.setStatisticsPeriodSeconds(
                statisticsPeriodSeconds.orElseThrow(
                        this::missingPeriod).getValue());

        healthDataResponse.setServicesEvents(buildServicesEvents(provider));

        return OBJECT_FACTORY.createGetSecurityServerHealthDataResponse(
                healthDataResponse);
    }

    private Exception missingTimestamp() {
        return new IllegalStateException("Monitoring startup timestamp is"
                + " missing in health metrics registry!");
    }

    private Exception missingPeriod() {
        return new IllegalStateException("Health statistics period is missing"
                + " in health metrics registry!");
    }

    private Stream<ServiceId.Conf> servicesWithAvailableMetrics() {
        return healthMetricRegistry.getMetrics().entrySet().stream()
                .map(HealthDataRequestHandler::extractIdentifier)
                .filter(Objects::nonNull)
                .distinct()
                .map(HealthDataRequestHandler::convertIdentifier);
    }

    private ServicesEventsType buildServicesEvents(
            Optional<ClientId.Conf> provider) {
        ServicesEventsType servicesEvents =
                OBJECT_FACTORY.createServicesEventsType();

        servicesWithAvailableMetrics()
                // If a client ID was provided in the request then
                // only include service metrics for that provider
                .filter(id -> provider.map(id.getClientId()::equals)
                        .orElse(true))
                .forEach(service -> servicesEvents.getServiceEvents().add(
                        buildServiceEvents(service)));

        return servicesEvents;
    }

    @SuppressWarnings("unchecked")
    private ServiceEventsType buildServiceEvents(ServiceId.Conf service) {
        ServiceEventsType serviceEvents =
                OBJECT_FACTORY.createServiceEventsType();

        serviceEvents.setService(service);

        Optional<Gauge<Long>> lastSuccessfulRequestTimestamp =
                Optional.ofNullable(findGauge(healthMetricRegistry,
                        getLastRequestTimestampGaugeName(service, true)));
        lastSuccessfulRequestTimestamp.ifPresent(
                g -> serviceEvents.setLastSuccessfulRequestTimestamp(
                        g.getValue()));

        Optional<Gauge<Long>> lastUnsuccessfulRequestTimestamp =
                Optional.ofNullable(findGauge(healthMetricRegistry,
                        getLastRequestTimestampGaugeName(service, false)));
        lastUnsuccessfulRequestTimestamp.ifPresent(
                g -> serviceEvents.setLastUnsuccessfulRequestTimestamp(
                        g.getValue()));

        Optional<Gauge<String>> serviceType =
                Optional.ofNullable(findGauge(healthMetricRegistry, getServiceTypeName(service)));
        serviceType.ifPresent(g -> serviceEvents.setServiceType(g.getValue()));

        serviceEvents.setLastPeriodStatistics(buildLastPeriodStats(service));

        return serviceEvents;
    }

    private LastPeriodStatisticsType buildLastPeriodStats(ServiceId service) {
        LastPeriodStatisticsType lastPeriodStats =
                OBJECT_FACTORY.createLastPeriodStatisticsType();

        lastPeriodStats.setSuccessfulRequestCount(0);
        Optional<Counter> successfulEventCount =
                Optional.ofNullable(findCounter(healthMetricRegistry,
                        getRequestCounterName(service, true)));
        successfulEventCount.ifPresent(
                c -> lastPeriodStats.setSuccessfulRequestCount(
                        (int) c.getCount()));

        lastPeriodStats.setUnsuccessfulRequestCount(0);
        Optional<Counter> unsuccessfulEventCount =
                Optional.ofNullable(findCounter(healthMetricRegistry,
                        getRequestCounterName(service, false)));
        unsuccessfulEventCount.ifPresent(
                c -> lastPeriodStats.setUnsuccessfulRequestCount(
                        (int) c.getCount()));

        if (lastPeriodStats.getSuccessfulRequestCount() > 0) {
            Optional<Histogram> requestDuration =
                    Optional.ofNullable(findHistogram(healthMetricRegistry,
                            getRequestDurationName(service)));
            requestDuration.ifPresent(h -> {
                lastPeriodStats.setRequestMinDuration(h.getSnapshot().getMin());
                lastPeriodStats.setRequestAverageDuration(
                        h.getSnapshot().getMean());
                lastPeriodStats.setRequestMaxDuration(h.getSnapshot().getMax());
                lastPeriodStats.setRequestDurationStdDev(
                        h.getSnapshot().getStdDev());
            });

            Optional<Histogram> requestSize =
                    Optional.ofNullable(findHistogram(healthMetricRegistry,
                            getRequestSizeName(service)));
            requestSize.ifPresent(h -> {
                lastPeriodStats.setRequestMinSize(h.getSnapshot().getMin());
                lastPeriodStats.setRequestAverageSize(
                        h.getSnapshot().getMean());
                lastPeriodStats.setRequestMaxSize(h.getSnapshot().getMax());
                lastPeriodStats.setRequestSizeStdDev(
                        h.getSnapshot().getStdDev());
            });

            Optional<Histogram> responseSize =
                    Optional.ofNullable(findHistogram(healthMetricRegistry,
                            getResponseSizeName(service)));
            responseSize.ifPresent(h -> {
                lastPeriodStats.setResponseMinSize(h.getSnapshot().getMin());
                lastPeriodStats.setResponseAverageSize(
                        h.getSnapshot().getMean());
                lastPeriodStats.setResponseMaxSize(h.getSnapshot().getMax());
                lastPeriodStats.setResponseSizeStdDev(
                        h.getSnapshot().getStdDev());
            });
        }

        return lastPeriodStats;
    }

    private static String extractIdentifier(Entry<String, Metric> metric) {
        String serviceName = null;
        String metricName = metric.getKey();

        if (metricName.contains("(")) {
            serviceName = metricName.substring(metricName.indexOf('(') + 1,
                    metricName.lastIndexOf(')'));
        }

        return serviceName;
    }

    private static ServiceId.Conf convertIdentifier(String id) {
        // Construct a valid service identifier so we can compare it's
        // provider to the optionally provided exchange partner's client ID
        String[] idParts = id.split(IDENTIFIER_SEPARATOR,
                SERVICE_ID_NUM_PARTS);

        for (int i = 0; i < idParts.length; i++) {
            idParts[i] = StringEscapeUtils.unescapeHtml4(idParts[i]);

            if (StringUtils.isBlank(idParts[i])) {
                idParts[i] = null;
            }
        }

        return idParts.length > SERVICE_ID_NUM_PARTS - 1
                ? ServiceId.Conf.create(idParts[0], idParts[1], idParts[2],
                        idParts[SERVICE_ID_SUBSYSTEM_PART],
                        idParts[SERVICE_ID_CODE_PART],
                        idParts[SERVICE_ID_VERSION_PART])
                : ServiceId.Conf.create(idParts[0], idParts[1], idParts[2],
                        idParts[SERVICE_ID_SUBSYSTEM_PART],
                        idParts[SERVICE_ID_CODE_PART]);
    }
}
