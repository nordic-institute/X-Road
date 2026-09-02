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
package org.niis.xroad.e2e;

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.opmonitor.core.OperationalDataRecord;
import org.niis.xroad.opmonitor.core.OperationalDataRecords;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import tools.jackson.databind.json.JsonMapper;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Runs last: the operational/health data tests assert on op-monitor records that accumulate from
 * every call made across the whole run.
 *
 * <p>{@code proxymonitorRespondsWithCorrectResponse} and
 * {@code proxymonitorRespondsWithCorrectResponseFromManagementSecurityServer} are this suite's case-5
 * self-targeted-built-ins coverage: an owner member querying its own server's {@code
 * getSecurityServerMetrics} through that same server's proxy — first as a plain (non-management)
 * owner on ss1, then as the management Security Server's own owner on ss0, since ss0's self-targeted
 * calls currently travel through the {@code -mgmt} companion context (ADR-41) and so exercise a
 * different call path than ss1's. Both assert externally observable behavior only (HTTP outcome,
 * response payload), not which participant context carried the call.
 */
@DisplayName("SS monitoring - proxy metrics, operational data and health data")
@Order(400)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
class SsMonitoringTest extends E2eTest {

    private static final String OPERATIONAL_DATA_JSON = "operational-monitoring-data.json.gz";
    private static final String OP_MONITORING_XSD = "http://x-road.eu/xsd/op-monitoring.xsd";
    private static final Duration OP_MONITOR_SETTLE_DELAY = Duration.ofSeconds(2);
    private static final Duration OP_MONITOR_RETRY_INTERVAL = Duration.ofSeconds(3);
    private static final Duration OP_MONITOR_RETRY_TIMEOUT = Duration.ofSeconds(90);
    private static final String SSL_AUTH_FAULT_CODE = "ssl_authentication_failed";
    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";

    private Response lastOpMonitorResponse;
    private OperationalDataRecords lastOperationalDataRecords;

    @Test
    @Order(1)
    @DisplayName("Call REST and OPENAPI3 methods")
    void callRestAndOpenapi3Methods(E2eEnvironment env) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var restResponse = when("a REST request is sent to ss1 proxy", () -> sendMockRestRequest(env, "ss1"));
        then("the response is 200 with the expected POST service message", () ->
                restResponse.statusCode(200).body("message", equalTo("Hello, world from POST service!")));

        var apiResponse = when("a REST request targeted at the /api/members API endpoint is sent to ss1 proxy", () ->
                sendApiMembersRequest(env, "ss1"));
        then("the response is 200 with the first member's name", () ->
                apiResponse.statusCode(200).body("[0].name",
                        equalTo("MTÜ Nordic Institute for Interoperability Solutions")));

        and("a REST request targeted at the unsaved /notexist/test API endpoint is attempted on ss1 proxy",
                () -> attemptUnsavedRestEndpoint(env, "ss1", "/notexist/test"));
    }

    @Test
    @Order(2)
    @DisplayName("Proxymonitor responds with correct response")
    void proxymonitorRespondsWithCorrectResponse(E2eEnvironment env) {
        given("ss1 owner client internal connection type is set to HTTP", () ->
                setOwnerClientConnectionType(env, "ss1", "HTTP"));

        var response = when("a proxymonitor getSecurityServerMetrics request is sent to ss1 with queryId PMID-E2E-1", () ->
                sendProxymonitorRequest(env, "ss1", "PMID-E2E-1", null));

        then("the proxymonitor response contains metricSet name SERVER:DEV/COM/4321/SS1", () -> {
            assertThat(response.getStatusCode()).isEqualTo(200);
            var actual = evalMonitoringXpath(response.asString(),
                    "//monitoring:getSecurityServerMetricsResponse/monitoring:metricSet/monitoring:name");
            assertThat(actual).isEqualTo("SERVER:DEV/COM/4321/SS1");
        });
    }

    @Test
    @Order(3)
    @DisplayName("Proxymonitor responds with correct response for TotalPhysicalMemory request")
    void proxymonitorRespondsWithTotalPhysicalMemory(E2eEnvironment env) {
        given("ss1 owner client internal connection type is set to HTTP", () ->
                setOwnerClientConnectionType(env, "ss1", "HTTP"));

        var response = when("a proxymonitor getSecurityServerMetrics request for metric TotalPhysicalMemory "
                + "is sent to ss1 with queryId PMID-E2E-2", () ->
                sendProxymonitorRequest(env, "ss1", "PMID-E2E-2", "TotalPhysicalMemory"));

        then("the proxymonitor response contains a numeric value for metric TotalPhysicalMemory", () -> {
            assertThat(response.getStatusCode()).isEqualTo(200);
            var value = evalMonitoringXpath(response.asString(),
                    "//monitoring:getSecurityServerMetricsResponse//monitoring:numericMetric"
                            + "[monitoring:name/text()='TotalPhysicalMemory']/monitoring:value");
            assertThat(value).as("numericMetric value for TotalPhysicalMemory").isNotEmpty().containsOnlyDigits();
        });
    }

    @Test
    @Order(4)
    @DisplayName("Proxymonitor responds with correct response from management Security Server")
    void proxymonitorRespondsWithCorrectResponseFromManagementSecurityServer(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; case-5 self-targeted built-ins on the "
                        + "management Security Server are only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));

        given("ss0 owner client internal connection type is set to HTTP", () ->
                setOwnerClientConnectionType(env, "ss0", "HTTP"));

        var response = when("a proxymonitor getSecurityServerMetrics request is sent to ss0 with queryId PMID-E2E-SS0-1", () ->
                sendProxymonitorRequest(env, "ss0", "PMID-E2E-SS0-1", null));

        then("the proxymonitor response contains metricSet name SERVER:DEV/COM/1234/SS0", () -> {
            assertThat(response.getStatusCode()).isEqualTo(200);
            var actual = evalMonitoringXpath(response.asString(),
                    "//monitoring:getSecurityServerMetricsResponse/monitoring:metricSet/monitoring:name");
            assertThat(actual).isEqualTo("SERVER:DEV/COM/1234/SS0");
        });
    }

    @Test
    @Order(5)
    @DisplayName("Messagelog contains metrics requests")
    void messagelogContainsMetricsRequests(E2eEnvironment env, MessagelogDbOps messagelogDbOps) {
        given("ss1 owner client internal connection type is set to HTTP", () ->
                setOwnerClientConnectionType(env, "ss1", "HTTP"));

        when("a proxymonitor getSecurityServerMetrics request is sent to ss1 with queryId MSGLOG-E2E-UNIQUE-9f3a", () ->
                sendProxymonitorRequest(env, "ss1", "MSGLOG-E2E-UNIQUE-9f3a", null));

        then("ss1 messagelog contains 4 encrypted entries for queryId MSGLOG-E2E-UNIQUE-9f3a", () ->
                assertMessagelogEncryptedEntries(messagelogDbOps, "ss1", 4, "MSGLOG-E2E-UNIQUE-9f3a"));
    }

    @Test
    @Order(6)
    @DisplayName("Retrieving Operational Data of Security Server")
    void retrievingOperationalDataOfSecurityServer(E2eEnvironment env) {
        given("ss0 owner client internal connection type is set to HTTP", () ->
                setOwnerClientConnectionType(env, "ss0", "HTTP"));

        var restResponse = when("a REST request is sent to ss1 proxy", () -> sendMockRestRequest(env, "ss1"));
        then("the response is 200 with the expected POST service message", () ->
                restResponse.statusCode(200).body("message", equalTo("Hello, world from POST service!")));

        var apiResponse = when("a REST request targeted at the /api/members API endpoint is sent to ss1 proxy", () ->
                sendApiMembersRequest(env, "ss1"));
        then("the response is 200 with the first member's name", () ->
                apiResponse.statusCode(200).body("[0].name",
                        equalTo("MTÜ Nordic Institute for Interoperability Solutions")));

        and("a REST request targeted at the unsaved /notexist/test API endpoint is attempted on ss1 proxy",
                () -> attemptUnsavedRestEndpoint(env, "ss1", "/notexist/test"));

        when("a getSecurityServerOperationalData request is sent to ss0", () -> sendGetSecurityServerOperationalData(env, "ss0"));

        then("the operational data response contains records served by security server ss0", () ->
                assertOperationalDataRecordsAddress(env.securityServerAddress("ss0")));
    }

    @Test
    @Order(7)
    @DisplayName("Retrieving Health Data of Security Server")
    void retrievingHealthDataOfSecurityServer(E2eEnvironment env) {
        given("ss0 owner client internal connection type is set to HTTP", () ->
                setOwnerClientConnectionType(env, "ss0", "HTTP"));

        var restResponse = when("a REST request is sent to ss1 proxy", () -> sendMockRestRequest(env, "ss1"));
        then("the response is 200 with the expected POST service message", () ->
                restResponse.statusCode(200).body("message", equalTo("Hello, world from POST service!")));

        when("a getSecurityServerHealthData request is sent to ss0", () -> sendGetSecurityServerHealthData(env, "ss0"));

        then("the health data response has statisticsPeriodSeconds 600 and at least 1 successfulRequestCount", () ->
                assertHealthDataResponse(600, 1));
    }

    private ValidatableResponse sendMockRestRequest(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body("{\"data\": 1.0, \"service\": \"random\"}")
                .header("Content-Type", "application/json")
                .header("x-road-client", "DEV/COM/4321/TestClient")
                .post("http://%s:%s/r1/DEV/COM/1234/TestService/mock1".formatted(mapping.host(), mapping.port()))
                .then();
    }

    private ValidatableResponse sendApiMembersRequest(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .header("Content-Type", "application/json")
                .header("x-road-client", "DEV/COM/4321/TestClient")
                .get("http://%s:%s/r1/DEV/COM/1234/TestService/restapi/api/members".formatted(mapping.host(), mapping.port()))
                .then();
    }

    private void attemptUnsavedRestEndpoint(E2eEnvironment env, String envName, String apiPath) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        try {
            RestAssuredFactory.given()
                    .header("Content-Type", "application/json")
                    .header("x-road-client", "DEV/COM/4321/TestClient")
                    .get("http://%s:%s/r1/DEV/COM/1234/TestService/restapi/%s"
                            .formatted(mapping.host(), mapping.port(), apiPath.replaceFirst("^/", "")))
                    .then();
        } catch (Exception e) {
            log.info("Error for unsaved endpoint {}: {}", apiPath, e.getMessage());
        }
    }

    private void setOwnerClientConnectionType(E2eEnvironment env, String envName, String connectionType) {
        var ownerClientId = "DEV:COM:" + ownerMemberCode(envName);
        var mapping = env.getContainerMapping(envName, SsStackSetup.UI, SsStackSetup.Port.UI);
        var baseUrl = "https://%s:%s".formatted(mapping.host(), mapping.port());

        var loginResponse = RestAssuredFactory.given()
                .formParam("username", ADMIN_USERNAME)
                .formParam("password", ADMIN_PASSWORD)
                .post(baseUrl + "/login");
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);

        var xsrfToken = loginResponse.getCookie("XSRF-TOKEN");
        var sessionCookies = loginResponse.getCookies();

        var patchResponse = RestAssuredFactory.given()
                .cookies(sessionCookies)
                .header("X-XSRF-TOKEN", xsrfToken)
                .header("Content-Type", "application/json")
                .body("{\"connection_type\": \"%s\"}".formatted(connectionType))
                .patch(baseUrl + "/api/v1/clients/" + ownerClientId);
        assertThat(patchResponse.getStatusCode())
                .as("update owner client connection type to %s", connectionType)
                .isBetween(200, 299);
    }

    private String ownerMemberCode(String envName) {
        return switch (envName) {
            case "ss0" -> "1234";
            case "ss1" -> "4321";
            default -> throw new IllegalArgumentException("Unknown env for owner client: " + envName);
        };
    }

    private Response sendProxymonitorRequest(E2eEnvironment env, String envName, String queryId, String metricName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var ownerMemberCode = ownerMemberCode(envName);
        var serverCode = envName.toUpperCase(Locale.ROOT);
        return RestAssuredFactory.given()
                .header("Content-Type", "text/xml")
                .body(buildMetricsRequestBody(queryId, metricName, ownerMemberCode, serverCode))
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()));
    }

    private void assertMessagelogEncryptedEntries(MessagelogDbOps messagelogDbOps, String envName, int expectedCount, String queryId) {
        var sql = ("SELECT count(*), count(*) FILTER (WHERE keyid IS NOT NULL AND message IS NULL) "
                + "FROM logrecord WHERE queryid = '%s'").formatted(queryId);
        var counts = new AtomicReference<>(new int[]{0, 0});
        try {
            Awaitility.await()
                    .pollDelay(Duration.ofSeconds(1))
                    .pollInterval(Duration.ofSeconds(2))
                    .timeout(Duration.ofSeconds(60))
                    .ignoreExceptions()
                    .until(() -> {
                        var parts = messagelogDbOps.execMessagelogSql(envName, sql).split("\\|");
                        counts.set(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
                        return counts.get()[1] >= expectedCount;
                    });
        } catch (ConditionTimeoutException e) {
            var observed = counts.get();
            throw new ConditionTimeoutException(
                    ("Timed out waiting for %d encrypted messagelog entries for queryId %s on %s "
                            + "(last observed: %d total rows, %d encrypted)")
                            .formatted(expectedCount, queryId, envName, observed[0], observed[1]), e);
        }

        var result = counts.get();
        assertThat(result[0]).as("logrecord rows for queryId %s", queryId).isEqualTo(expectedCount);
        assertThat(result[1]).as("encrypted logrecord rows for queryId %s", queryId).isEqualTo(expectedCount);
    }

    private void sendGetSecurityServerOperationalData(E2eEnvironment env, String envName) {
        lastOpMonitorResponse = sendOpMonitorRequestUntilReady(env, envName, buildOperationalDataRequestBody(),
                response -> !isSslAuthenticationFault(response),
                "an operational-data response that is not an internal-TLS authentication fault");
    }

    private void sendGetSecurityServerHealthData(E2eEnvironment env, String envName) {
        lastOpMonitorResponse = sendOpMonitorRequestUntilReady(env, envName, buildHealthDataRequestBody(),
                response -> !isSslAuthenticationFault(response) && !healthDataSuccessCount(response).isEmpty(),
                "a health-data response carrying a successfulRequestCount");
    }

    /**
     * Re-sends the request until the response is usable. Two conditions race here and neither is
     * observable from the client: the owner client's connection type has to reach the proxy, which
     * reads it through its own serverconf cache with no invalidation signal from the admin service,
     * and op-monitor has to flush its buffer before the queried records exist.
     *
     * <p>Only the op-monitor queries are retried. The proxymonitor requests race the same way, but
     * re-sending them is not safe: each one writes messagelog records, and
     * {@code messagelogContainsMetricsRequests} asserts an exact row count for its query id.
     */
    private Response sendOpMonitorRequestUntilReady(E2eEnvironment env, String envName, String requestBody,
                                                    Predicate<Response> ready, String description) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var url = "http://%s:%s".formatted(mapping.host(), mapping.port());
        var last = new AtomicReference<Response>();
        try {
            Awaitility.await()
                    .pollDelay(OP_MONITOR_SETTLE_DELAY)
                    .pollInterval(OP_MONITOR_RETRY_INTERVAL)
                    .timeout(OP_MONITOR_RETRY_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        last.set(RestAssuredFactory.given()
                                .header("Content-Type", "text/xml")
                                .body(requestBody)
                                .post(url));
                        return ready.test(last.get());
                    });
        } catch (ConditionTimeoutException e) {
            var observed = last.get();
            throw new ConditionTimeoutException(
                    "Timed out waiting for %s from %s (last status: %s)".formatted(
                            description, envName, observed == null ? "no response" : observed.getStatusCode()), e);
        }
        return last.get();
    }

    private boolean isSslAuthenticationFault(Response response) {
        return response.asString().contains(SSL_AUTH_FAULT_CODE);
    }

    private String healthDataSuccessCount(Response response) {
        return evalOpMonitorXpath(response.asString(), "//om:successfulRequestCount");
    }

    @SneakyThrows
    private void assertOperationalDataRecordsAddress(String expectedAddress) {
        assertThat(lastOpMonitorResponse.getStatusCode()).isEqualTo(200);

        var contentType = lastOpMonitorResponse.getContentType();
        var responseBytes = lastOpMonitorResponse.asByteArray();

        var decoder = new SoapMessageDecoder(contentType, new SoapMessageDecoder.Callback() {
            @Override
            public void soap(SoapMessage message, Map<String, String> headers) {
                var recordsRef = findOpDataElementText(message, "records");
                log.info("op-data records attachment ref: {}", recordsRef);
                assertThat(recordsRef).isNotBlank();
            }

            @Override
            public void attachment(String attachContentType, InputStream content, Map<String, String> additionalHeaders)
                    throws IOException {
                String expectedCid = "<" + OPERATIONAL_DATA_JSON + ">";
                assertThat(additionalHeaders.get("content-id")).isEqualTo(expectedCid);

                lastOperationalDataRecords = JsonMapper.builder().build()
                        .readValue(readGzipContent(content), OperationalDataRecords.class);
            }

            @Override
            public void fault(SoapFault fault) {
                throw fault.toXrdRuntimeException();
            }

            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Exception t) throws Exception {
                throw t;
            }
        });
        decoder.parse(new ByteArrayInputStream(responseBytes));

        assertThat(lastOperationalDataRecords).isNotNull();
        assertThat(lastOperationalDataRecords.getRecords()).isNotEmpty();

        List<String> addresses = lastOperationalDataRecords.getRecords().stream()
                .map(OperationalDataRecord::getServiceSecurityServerAddress)
                .distinct()
                .toList();
        log.info("serviceSecurityServerAddress values in op-data: {}", addresses);
        assertThat(lastOperationalDataRecords.getRecords())
                .extracting(OperationalDataRecord::getServiceSecurityServerAddress)
                .contains(expectedAddress);
    }

    private void assertHealthDataResponse(int expectedPeriodSeconds, int minSuccessfulRequests) {
        assertThat(lastOpMonitorResponse.getStatusCode()).isEqualTo(200);

        var body = lastOpMonitorResponse.asString();
        var periodSeconds = evalOpMonitorXpath(body, "//om:statisticsPeriodSeconds");
        var successCount = evalOpMonitorXpath(body, "//om:successfulRequestCount");

        assertThat(periodSeconds).as("statisticsPeriodSeconds").isEqualTo(String.valueOf(expectedPeriodSeconds));
        assertThat(Integer.parseInt(successCount))
                .as("successfulRequestCount >= %d", minSuccessfulRequests)
                .isGreaterThanOrEqualTo(minSuccessfulRequests);
    }

    @SneakyThrows
    private String evalMonitoringXpath(String body, String xpathExpr) {
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new MapNamespaceContext(Map.of(
                "soap", "http://schemas.xmlsoap.org/soap/envelope/",
                "monitoring", "http://x-road.eu/xsd/monitoring")));
        return xpath.evaluate(xpathExpr, new InputSource(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
    }

    @SneakyThrows
    private String evalOpMonitorXpath(String body, String xpathExpr) {
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new MapNamespaceContext(Map.of(
                "soap", "http://schemas.xmlsoap.org/soap/envelope/",
                "om", OP_MONITORING_XSD)));
        return xpath.evaluate(xpathExpr, new InputSource(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
    }

    @SneakyThrows
    private static String findOpDataElementText(SoapMessage message, String elementTagName) {
        Element responseElement = (Element) message.getSoap().getSOAPBody()
                .getElementsByTagNameNS(OP_MONITORING_XSD, "getSecurityServerOperationalDataResponse")
                .item(0);
        return responseElement.getElementsByTagNameNS(OP_MONITORING_XSD, elementTagName)
                .item(0)
                .getTextContent();
    }

    private static String readGzipContent(InputStream inputStream) throws IOException {
        try (var gzipIn = new GZIPInputStream(new ByteArrayInputStream(inputStream.readAllBytes()));
                var reader = new BufferedReader(new InputStreamReader(gzipIn, StandardCharsets.UTF_8))) {
            var sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private String buildMetricsRequestBody(String queryId, String metricName, String ownerMemberCode, String serverCode) {
        var outputSpec = metricName != null && !metricName.isBlank()
                ? """
                <monitoring:outputSpec>
                    <monitoring:outputField>%s</monitoring:outputField>
                </monitoring:outputSpec>
                """.formatted(metricName)
                : "";
        return """
                <soapenv:Envelope
                        xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
                        xmlns:iden="http://x-road.eu/xsd/identifiers"
                        xmlns:monitoring="http://x-road.eu/xsd/monitoring">
                    <soapenv:Header>
                        <xro:client iden:objectType="MEMBER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>%s</iden:memberCode>
                        </xro:client>
                        <xro:service iden:objectType="SERVICE">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>%s</iden:memberCode>
                            <iden:serviceCode>getSecurityServerMetrics</iden:serviceCode>
                        </xro:service>
                        <xro:securityServer iden:objectType="SERVER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>%s</iden:memberCode>
                            <iden:serverCode>%s</iden:serverCode>
                        </xro:securityServer>
                        <xro:id>%s</xro:id>
                        <xro:protocolVersion>4.0</xro:protocolVersion>
                    </soapenv:Header>
                    <soapenv:Body>
                        <monitoring:getSecurityServerMetrics>
                            %s
                        </monitoring:getSecurityServerMetrics>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(ownerMemberCode, ownerMemberCode, ownerMemberCode, serverCode, queryId, outputSpec);
    }

    private String buildOperationalDataRequestBody() {
        long now = System.currentTimeMillis() / 1000;
        return """
                <soapenv:Envelope
                        xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
                        xmlns:iden="http://x-road.eu/xsd/identifiers"
                        xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd">
                    <soapenv:Header>
                        <xro:client iden:objectType="MEMBER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>1234</iden:memberCode>
                        </xro:client>
                        <xro:service iden:objectType="SERVICE">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>1234</iden:memberCode>
                            <iden:serviceCode>getSecurityServerOperationalData</iden:serviceCode>
                        </xro:service>
                        <xro:securityServer iden:objectType="SERVER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>1234</iden:memberCode>
                            <iden:serverCode>SS0</iden:serverCode>
                        </xro:securityServer>
                        <xro:id>E2E-OPDATA-%d</xro:id>
                        <xro:protocolVersion>4.0</xro:protocolVersion>
                    </soapenv:Header>
                    <soapenv:Body>
                        <om:getSecurityServerOperationalData>
                            <om:searchCriteria>
                                <om:recordsFrom>0</om:recordsFrom>
                                <om:recordsTo>%d</om:recordsTo>
                            </om:searchCriteria>
                        </om:getSecurityServerOperationalData>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(now, now);
    }

    private String buildHealthDataRequestBody() {
        return """
                <soapenv:Envelope
                        xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
                        xmlns:iden="http://x-road.eu/xsd/identifiers"
                        xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd">
                    <soapenv:Header>
                        <xro:client iden:objectType="MEMBER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>1234</iden:memberCode>
                        </xro:client>
                        <xro:service iden:objectType="SERVICE">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>1234</iden:memberCode>
                            <iden:serviceCode>getSecurityServerHealthData</iden:serviceCode>
                        </xro:service>
                        <xro:securityServer iden:objectType="SERVER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>1234</iden:memberCode>
                            <iden:serverCode>SS0</iden:serverCode>
                        </xro:securityServer>
                        <xro:id>E2E-HEALTH-1</xro:id>
                        <xro:protocolVersion>4.0</xro:protocolVersion>
                    </soapenv:Header>
                    <soapenv:Body>
                        <om:getSecurityServerHealthData>
                            <om:filterCriteria>
                                <om:client iden:objectType="MEMBER">
                                    <iden:xRoadInstance>DEV</iden:xRoadInstance>
                                    <iden:memberClass>COM</iden:memberClass>
                                    <iden:memberCode>1234</iden:memberCode>
                                </om:client>
                            </om:filterCriteria>
                        </om:getSecurityServerHealthData>
                    </soapenv:Body>
                </soapenv:Envelope>
                """;
    }

    private static final class MapNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixToUri;

        private MapNamespaceContext(Map<String, String> prefixToUri) {
            this.prefixToUri = prefixToUri;
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixToUri.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return Collections.emptyIterator();
        }
    }
}
