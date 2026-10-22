/*
 * The MIT License
 *
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
package org.niis.xroad.opmonitor.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.opmonitor.api.AccessScope;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.ServiceIdConverter;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup.CURL_CONNECTION_REFUSED;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * 0100 - Op monitoring data in intervals: queries op-monitor over gRPC for operational-data intervals,
 * filtered by security server type, member, and/or service, against the traffic data seeded by
 * {@code test-data/baseline-intTest.xml}.
 * Store cases write records over gRPC for their own service ten days in the past, outside the seeded window.
 */
@ExtendWith(ApiStackExtension.class)
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorIntTest {

    private static final String CLIENT_SERVER_TYPE = "Client";
    private static final String MEMBER_SYSTEM1 = "DEV:COM:1234:System1";
    private static final String SERVICE_GET_TOP_SECRET = "DEV:COM:1234:Service9:getTopSecret.v2";
    private static final String SERVICE_XROAD_GET_RANDOM = "DEV:COM:4321:Service1:xroadGetRandom.v1";
    private static final String STORE_CLIENT = "DEV:COM:222:TestClient";
    private static final String SERVICE_STORED_OVER_GRPC = "DEV:COM:222:TestService:storedOverGrpc.v1";
    private static final String SERVICE_REJECTED_BATCH = "DEV:COM:222:TestService:rejectedBatch.v1";
    private static final Instant STORE_WINDOW = Instant.now().minus(10, ChronoUnit.DAYS);

    private static final String QUERY_CONTENT_TYPE = "text/xml; charset=utf-8";
    private static final String QUERY_ENVELOPE = """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd" xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd"
                    xmlns:id="http://x-road.eu/xsd/identifiers">
                <SOAP-ENV:Header>
                    <xroad:client id:objectType="MEMBER">
                        <id:xRoadInstance>DEV</id:xRoadInstance>
                        <id:memberClass>COM</id:memberClass>
                        <id:memberCode>1234</id:memberCode>
                    </xroad:client>
                    <xroad:service id:objectType="SERVICE">
                        <id:xRoadInstance>DEV</id:xRoadInstance>
                        <id:memberClass>COM</id:memberClass>
                        <id:memberCode>1234</id:memberCode>
                        <id:serviceCode>%s</id:serviceCode>
                    </xroad:service>
                    <xroad:securityServer id:objectType="SERVER">
                        <id:xRoadInstance>DEV</id:xRoadInstance>
                        <id:memberClass>COM</id:memberClass>
                        <id:memberCode>1234</id:memberCode>
                        <id:serverCode>SS0</id:serverCode>
                    </xroad:securityServer>
                    <xroad:id>op-monitor-int-test</xroad:id>
                    <xroad:protocolVersion>4.0</xroad:protocolVersion>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body>%s</SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
            """;

    private static final int REMOVED_REST_PORT = 2080;
    private static final int QUARKUS_HEALTH_PORT = 4099;
    private static final Pattern STARTUP_TIMESTAMP = Pattern.compile("<om:monitoringStartupTimestamp>(\\d+)<");

    private static Instant stackReadyAt;

    private OpMonitorContainerSetup opMonitorSetup;
    private OpMonitorClient opMonitorClient;

    @BeforeAll
    static void stackIsReadyBeforeTheFirstQuery() {
        stackReadyAt = Instant.now();
    }

    @BeforeEach
    void opMonitorClientInitialized(OpMonitorContainerSetup setup) {
        given("op-monitor client is initialized", () -> {
            opMonitorSetup = setup;
            opMonitorClient = setup.opMonitorClient();
        });
    }

    @Test
    @DisplayName("Nothing listens on the removed REST port")
    void nothingListensOnTheRemovedRestPort() {
        var healthExitCode = when("the Quarkus health port is probed from inside the op-monitor container",
                () -> opMonitorSetup.probeFromInside(QUARKUS_HEALTH_PORT));
        var restExitCode = when("the removed REST port " + REMOVED_REST_PORT + " is probed from inside the op-monitor container",
                () -> opMonitorSetup.probeFromInside(REMOVED_REST_PORT));
        then("the health port answers and the REST port refuses the connection", () -> {
            assertThat(healthExitCode).isZero();
            assertThat(restExitCode).isEqualTo(CURL_CONNECTION_REFUSED);
        });
    }

    @Test
    @DisplayName("Query with no optional filters")
    void queryWithNoOptionalFilters() {
        var intervals = when("user asks for traffic data of last 1 hour in 30 minute intervals",
                () -> trafficData(1, 30, null, null, null));
        then("the query returns 4 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 4, 1));
    }

    @Test
    @DisplayName("Query with security server type")
    void queryWithSecurityServerType() {
        var intervals = when("user asks for traffic data of last hour in 45 minute intervals where security server was "
                        + "\"Client\"",
                () -> trafficData(1, 45, CLIENT_SERVER_TYPE, null, null));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Query with member")
    void queryWithMember() {
        var intervals = when("user asks for traffic data of last hour in 60 minute intervals where one of the participants "
                        + "was \"DEV:COM:1234:System1\"",
                () -> trafficData(1, 60, null, MEMBER_SYSTEM1, null));
        then("the query returns 3 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 3, 1));
    }

    @Test
    @DisplayName("Query with service")
    void queryWithService() {
        var intervals = when("user asks for traffic data of last hour in 30 minute intervals where requested service was "
                        + "\"DEV:COM:1234:Service9:getTopSecret.v2\"",
                () -> trafficData(1, 30, null, null, SERVICE_GET_TOP_SECRET));
        then("the query returns 1 successful requests and 0 failed requests",
                () -> assertTotals(intervals, 1, 0));
    }

    @Test
    @DisplayName("Query with security server type and member")
    void queryWithSecurityServerTypeAndMember() {
        var intervals = when("user asks for traffic data of last two hour in 30 minute intervals where \"Client\" was "
                        + "\"DEV:COM:1234:System1\"",
                () -> trafficData(2, 30, CLIENT_SERVER_TYPE, MEMBER_SYSTEM1, null));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Query with member and service")
    void queryWithMemberAndService() {
        var intervals = when("user asks for traffic data of last two hour in 30 minute intervals where one of the "
                        + "participants was \"DEV:COM:1234:System1\" and requested service was "
                        + "\"DEV:COM:4321:Service1:xroadGetRandom.v1\"",
                () -> trafficData(2, 30, null, MEMBER_SYSTEM1, SERVICE_XROAD_GET_RANDOM));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Results in different time buckets")
    void resultsInDifferentTimeBuckets() {
        var intervals = when("user asks for traffic data of last 2 hour in 30 minute intervals",
                () -> trafficData(2, 30, null, null, null));
        then("the query returns intervals with correct success and failure counts",
                () -> assertIntervalBuckets(intervals));
    }

    @Test
    @DisplayName("Store operational data over gRPC")
    void storeOperationalDataOverGrpc() {
        var records = List.of(
                storedRecord(SERVICE_STORED_OVER_GRPC, 0, true),
                storedRecord(SERVICE_STORED_OVER_GRPC, 1, true),
                storedRecord(SERVICE_STORED_OVER_GRPC, 2, false));

        when("the security server stores 2 successful and 1 failed record for \"" + SERVICE_STORED_OVER_GRPC + "\"",
                () -> opMonitorClient.storeOperationalData(records));

        var intervals = when("user asks for traffic data of that service around the stored records",
                () -> storedTrafficData(SERVICE_STORED_OVER_GRPC));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Store rejects a batch with an invalid record as a whole")
    void storeRejectsBatchWithInvalidRecord() {
        var valid = storedRecord(SERVICE_REJECTED_BATCH, 0, true);
        var withoutRequestInTs = storedRecord(SERVICE_REJECTED_BATCH, 1, true).toBuilder().clearRequestInTs().build();

        then("storing a batch where one record has no request-in timestamp fails",
                () -> assertThatThrownBy(() -> opMonitorClient.storeOperationalData(List.of(valid, withoutRequestInTs)))
                        .isInstanceOf(RuntimeException.class));

        var intervals = when("user asks for traffic data of that service",
                () -> storedTrafficData(SERVICE_REJECTED_BATCH));
        then("no record of the rejected batch was stored",
                () -> assertTotals(intervals, 0, 0));
    }

    @Test
    @DisplayName("Query operational data over gRPC")
    void queryOperationalDataOverGrpc() {
        var response = when("user queries the operational data of the security server owner",
                () -> query("getSecurityServerOperationalData", operationalDataQueryBody(), QUERY_CONTENT_TYPE));
        then("the response is a multipart message with the operational data response and its records attachment", () -> {
            assertThat(response.getContentType()).startsWith("multipart/related");
            assertThat(text(response)).contains("getSecurityServerOperationalDataResponse")
                    .contains("operational-monitoring-data.json.gz");
        });
    }

    @Test
    @DisplayName("Query health data over gRPC")
    void queryHealthDataOverGrpc() {
        var response = when("user queries the health data of the security server",
                () -> query("getSecurityServerHealthData", "<om:getSecurityServerHealthData/>", QUERY_CONTENT_TYPE));
        then("the response is the health data response", () -> {
            assertThat(response.getContentType()).startsWith("text/xml");
            assertThat(text(response)).contains("getSecurityServerHealthDataResponse").contains("monitoringStartupTimestamp");
        });
        then("the monitoring start timestamp is the boot time, earlier than the first query", () ->
                assertThat(monitoringStartupTimestamp(response)).isBefore(stackReadyAt));
    }

    @Test
    @DisplayName("Query with an unsupported content type is answered with a SOAP fault")
    void queryWithUnsupportedContentTypeIsAnsweredWithSoapFault() {
        var response = when("user queries health data with the content type text/plain",
                () -> query("getSecurityServerHealthData", "<om:getSecurityServerHealthData/>", "text/plain"));
        then("the response is a SOAP fault as payload", () -> {
            assertThat(response.getContentType()).startsWith("text/xml");
            assertThat(text(response)).contains("<faultcode>").contains("invalid_content_type");
        });
    }

    private QueryOperationalDataResp query(String serviceCode, String body, String contentType) {
        var query = QUERY_ENVELOPE.formatted(serviceCode, body).getBytes(StandardCharsets.UTF_8);
        var accessScope = "getSecurityServerOperationalData".equals(serviceCode)
                ? AccessScope.FULL_VIEW
                : AccessScope.ACCESS_SCOPE_UNSPECIFIED;
        return opMonitorClient.queryOperationalData(query, contentType, accessScope);
    }

    private static String operationalDataQueryBody() {
        return """
                <om:getSecurityServerOperationalData>
                    <om:searchCriteria>
                        <om:recordsFrom>0</om:recordsFrom>
                        <om:recordsTo>%d</om:recordsTo>
                    </om:searchCriteria>
                </om:getSecurityServerOperationalData>""".formatted(Instant.now().getEpochSecond());
    }

    private static Instant monitoringStartupTimestamp(QueryOperationalDataResp response) {
        var matcher = STARTUP_TIMESTAMP.matcher(text(response));
        assertThat(matcher.find()).as("monitoringStartupTimestamp in the health data response").isTrue();
        return Instant.ofEpochMilli(Long.parseLong(matcher.group(1)));
    }

    private static String text(QueryOperationalDataResp response) {
        return response.getResponse().toString(StandardCharsets.ISO_8859_1);
    }

    private static OperationalDataRecordProto storedRecord(String serviceId, int index, boolean succeeded) {
        long requestInTs = STORE_WINDOW.plusSeconds(index).toEpochMilli();
        var data = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, requestInTs);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setClientId(new ClientIdConverter().convertId(STORE_CLIENT));
        data.setServiceId(new ServiceIdConverter().convertId(serviceId));
        data.setResponseOutTs(requestInTs + 100, false);
        data.setSucceeded(succeeded);
        return data.toProto();
    }

    private List<OperationalDataInterval> storedTrafficData(String serviceId) {
        return opMonitorClient.getOperationalDataIntervals(
                STORE_WINDOW.minus(30, ChronoUnit.MINUTES).toEpochMilli(),
                STORE_WINDOW.plus(30, ChronoUnit.MINUTES).toEpochMilli(),
                60, null, null, new ServiceIdConverter().convertId(serviceId));
    }

    private List<OperationalDataInterval> trafficData(int windowInHours, int intervalMinutes,
            String securityServerType, String memberId, String serviceId) {
        var now = Instant.now();
        var from = now.minus(windowInHours, ChronoUnit.HOURS).toEpochMilli();
        var to = now.toEpochMilli();
        return opMonitorClient.getOperationalDataIntervals(from, to, intervalMinutes, securityServerType,
                memberId == null ? null : new ClientIdConverter().convertId(memberId),
                serviceId == null ? null : new ServiceIdConverter().convertId(serviceId));
    }

    private void assertTotals(List<OperationalDataInterval> intervals, long successful, long failed) {
        var successTotal = intervals.stream().map(OperationalDataInterval::getSuccessCount).reduce(0L, Long::sum);
        var failureTotal = intervals.stream().map(OperationalDataInterval::getFailureCount).reduce(0L, Long::sum);
        assertThat(successTotal).isEqualTo(successful);
        assertThat(failureTotal).isEqualTo(failed);
    }

    private void assertIntervalBuckets(List<OperationalDataInterval> intervals) {
        var zeroSuccessZeroFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 0L && interval.getFailureCount() == 0L)
                .count();
        var oneSuccessOneFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 1L && interval.getFailureCount() == 1L)
                .count();
        var twoSuccessZeroFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 2L && interval.getFailureCount() == 0L)
                .count();
        var twoSuccessOneFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 2L && interval.getFailureCount() == 1L)
                .count();
        assertThat(oneSuccessOneFailure).isEqualTo(1);
        assertThat(twoSuccessZeroFailure).isEqualTo(1);
        assertThat(twoSuccessOneFailure).isEqualTo(1);
        assertThat(zeroSuccessZeroFailure).isGreaterThanOrEqualTo(1);
    }
}
