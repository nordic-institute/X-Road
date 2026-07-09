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
package org.niis.xroad.e2e.glue;

import io.cucumber.java.en.Step;
import io.restassured.response.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.niis.xroad.e2e.E2eEnvironment;
import org.niis.xroad.e2e.MessagelogDbOps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection", "checkstyle:magicnumber"})
public class MonitoringStepDefs extends BaseE2EStepDefs {

    @Autowired
    private MessagelogDbOps messagelogDbOps;

    private Response lastProxymonitorResponse;

    @Step("{string} owner client internal connection type is set to {string}")
    public void setOwnerClientConnectionType(String env, String connectionType) {
        var ownerClientId = switch (env) {
            case "ss0" -> "DEV:COM:1234";
            case "ss1" -> "DEV:COM:4321";
            default -> throw new IllegalArgumentException("Unknown env for owner client: " + env);
        };
        var mapping = envSetup.getContainerMapping(env, "ui", E2eEnvironment.Port.UI);
        var baseUrl = "https://%s:%s".formatted(mapping.host(), mapping.port());

        var loginResponse = given()
                .relaxedHTTPSValidation()
                .formParam("username", "xrd")
                .formParam("password", "secret123!")
                .post(baseUrl + "/login");
        assertThat(loginResponse.getStatusCode()).isEqualTo(200);

        var xsrfToken = loginResponse.getCookie("XSRF-TOKEN");
        var sessionCookies = loginResponse.getCookies();

        var patchResponse = given()
                .relaxedHTTPSValidation()
                .cookies(sessionCookies)
                .header("X-XSRF-TOKEN", xsrfToken)
                .header("Content-Type", "application/json")
                .body("{\"connection_type\": \"%s\"}".formatted(connectionType))
                .patch(baseUrl + "/api/v1/clients/" + ownerClientId);
        assertThat(patchResponse.getStatusCode())
                .as("update owner client connection type to %s", connectionType)
                .isBetween(200, 299);
    }

    @Step("proxymonitor getSecurityServerMetrics request is sent to {string} with queryId {string}")
    public void sendProxymonitorRequest(String env, String queryId) {
        var mapping = envSetup.getContainerMapping(env, "proxy", E2eEnvironment.Port.PROXY);
        lastProxymonitorResponse = given()
                .header("Content-Type", "text/xml")
                .body(buildMetricsRequestBody(queryId, null))
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()));
    }

    @Step("proxymonitor getSecurityServerMetrics request for metric {string} is sent to {string} with queryId {string}")
    public void sendProxymonitorRequestForMetric(String metricName, String env, String queryId) {
        var mapping = envSetup.getContainerMapping(env, "proxy", E2eEnvironment.Port.PROXY);
        lastProxymonitorResponse = given()
                .header("Content-Type", "text/xml")
                .body(buildMetricsRequestBody(queryId, metricName))
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()));
    }

    @Step("proxymonitor response contains metricSet name {string}")
    public void assertProxymonitorMetricSetName(String expectedName) {
        assertThat(lastProxymonitorResponse.getStatusCode()).isEqualTo(200);
        var actual = evalXpath(lastProxymonitorResponse.asString(),
                "//monitoring:getSecurityServerMetricsResponse/monitoring:metricSet/monitoring:name");
        assertThat(actual).isEqualTo(expectedName);
    }

    @Step("proxymonitor response contains a numeric value for metric {string}")
    public void assertProxymonitorNumericMetric(String metricName) {
        assertThat(lastProxymonitorResponse.getStatusCode()).isEqualTo(200);
        var value = evalXpath(lastProxymonitorResponse.asString(),
                "//monitoring:getSecurityServerMetricsResponse//monitoring:numericMetric"
                        + "[monitoring:name/text()='" + metricName + "']/monitoring:value");
        assertThat(value)
                .as("numericMetric value for %s", metricName)
                .isNotEmpty()
                .containsOnlyDigits();
    }

    @Step("{string} messagelog contains {int} encrypted entries for queryId {string}")
    public void assertMessagelogEncryptedEntries(String env, int expectedCount, String queryId) {
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
                        var parts = messagelogDbOps.execMessagelogSql(env, sql).split("\\|");
                        counts.set(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
                        return counts.get()[1] >= expectedCount;
                    });
        } catch (ConditionTimeoutException e) {
            var result = counts.get();
            throw new ConditionTimeoutException(
                    ("Timed out waiting for %d encrypted messagelog entries for queryId %s on %s "
                            + "(last observed: %d total rows, %d encrypted)")
                            .formatted(expectedCount, queryId, env, result[0], result[1]), e);
        }

        var result = counts.get();
        assertThat(result[0]).as("logrecord rows for queryId %s", queryId).isEqualTo(expectedCount);
        assertThat(result[1]).as("encrypted logrecord rows for queryId %s", queryId).isEqualTo(expectedCount);
    }

    @Step("REST request targeted at unsaved {string} API endpoint is attempted on {string} {string}")
    public void attemptUnsavedRestEndpoint(String apiPath, String env, String service) {
        var mapping = envSetup.getContainerMapping(env, service, E2eEnvironment.Port.PROXY);
        try {
            given()
                    .header("Content-Type", "application/json")
                    .header("x-road-client", "DEV/COM/4321/TestClient")
                    .get("http://%s:%s/r1/DEV/COM/1234/TestService/restapi/%s"
                            .formatted(mapping.host(), mapping.port(), apiPath.replaceFirst("^/", "")))
                    .then();
        } catch (Exception e) {
            log.info("Error for unsaved endpoint {}: {}", apiPath, e.getMessage());
        }
    }

    @SneakyThrows
    private String evalXpath(String body, String xpathExpr) {
        var xpath = XPathFactory.newInstance().newXPath();
        var ns = new SimpleNamespaceContext();
        ns.bindNamespaceUri("soap", "http://schemas.xmlsoap.org/soap/envelope/");
        ns.bindNamespaceUri("monitoring", "http://x-road.eu/xsd/monitoring");
        xpath.setNamespaceContext(ns);
        return xpath.evaluate(xpathExpr, new InputSource(new ByteArrayInputStream(body.getBytes())));
    }

    private String buildMetricsRequestBody(String queryId, String metricName) {
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
                            <iden:memberCode>4321</iden:memberCode>
                        </xro:client>
                        <xro:service iden:objectType="SERVICE">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>4321</iden:memberCode>
                            <iden:serviceCode>getSecurityServerMetrics</iden:serviceCode>
                        </xro:service>
                        <xro:securityServer iden:objectType="SERVER">
                            <iden:xRoadInstance>DEV</iden:xRoadInstance>
                            <iden:memberClass>COM</iden:memberClass>
                            <iden:memberCode>4321</iden:memberCode>
                            <iden:serverCode>SS1</iden:serverCode>
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
                """.formatted(queryId, outputSpec);
    }
}
