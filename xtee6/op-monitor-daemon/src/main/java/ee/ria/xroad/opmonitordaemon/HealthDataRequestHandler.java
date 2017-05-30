/**
 * The MIT License
 * Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.xml.bind.JAXBElement;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.opmonitordaemon.message.FilterCriteriaType;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerHealthDataResponseType;
import ee.ria.xroad.opmonitordaemon.message.GetSecurityServerHealthDataType;
import ee.ria.xroad.opmonitordaemon.message.LastPeriodStatisticsType;
import ee.ria.xroad.opmonitordaemon.message.ServiceEventsType;
import ee.ria.xroad.opmonitordaemon.message.ServicesEventsType;

import static ee.ria.xroad.opmonitordaemon.HealthDataMetrics.MONITORING_STARTUP_TIMESTAMP;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetrics.STATISTICS_PERIOD_SECONDS;
import static ee.ria.xroad.opmonitordaemon.HealthDataMetricsUtil.*;

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

        ClientId clientId = requestSoap.getClient();
        GetSecurityServerHealthDataType requestData = getRequestData(
                requestSoap, GetSecurityServerHealthDataType.class);

        Optional<ClientId> provider = Optional.ofNullable(
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
            Optional<ClientId> provider) throws Exception {
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

    private Stream<ServiceId> servicesWithAvailableMetrics() {
        return healthMetricRegistry.getMetrics().entrySet().stream()
                .map(HealthDataRequestHandler::extractIdentifier)
                .filter(Objects::nonNull)
                .distinct()
                .map(HealthDataRequestHandler::convertIdentifier);
    }

    private ServicesEventsType buildServicesEvents(
            Optional<ClientId> provider) {
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
    private ServiceEventsType buildServiceEvents(ServiceId service) {
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

            Optional<Histogram> requestSoapSize =
                    Optional.ofNullable(findHistogram(healthMetricRegistry,
                            getRequestSoapSizeName(service)));
            requestSoapSize.ifPresent(h -> {
                lastPeriodStats.setRequestMinSoapSize(h.getSnapshot().getMin());
                lastPeriodStats.setRequestAverageSoapSize(
                        h.getSnapshot().getMean());
                lastPeriodStats.setRequestMaxSoapSize(h.getSnapshot().getMax());
                lastPeriodStats.setRequestSoapSizeStdDev(
                        h.getSnapshot().getStdDev());
            });

            Optional<Histogram> responseSoapSize =
                    Optional.ofNullable(findHistogram(healthMetricRegistry,
                            getResponseSoapSizeName(service)));
            responseSoapSize.ifPresent(h -> {
                lastPeriodStats.setResponseMinSoapSize(h.getSnapshot().getMin());
                lastPeriodStats.setResponseAverageSoapSize(
                        h.getSnapshot().getMean());
                lastPeriodStats.setResponseMaxSoapSize(h.getSnapshot().getMax());
                lastPeriodStats.setResponseSoapSizeStdDev(
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

    private static ServiceId convertIdentifier(String id) {
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
                ? ServiceId.create(idParts[0], idParts[1], idParts[2],
                        idParts[SERVICE_ID_SUBSYSTEM_PART],
                        idParts[SERVICE_ID_CODE_PART],
                        idParts[SERVICE_ID_VERSION_PART])
                : ServiceId.create(idParts[0], idParts[1], idParts[2],
                        idParts[SERVICE_ID_SUBSYSTEM_PART],
                        idParts[SERVICE_ID_CODE_PART]);
    }
}
