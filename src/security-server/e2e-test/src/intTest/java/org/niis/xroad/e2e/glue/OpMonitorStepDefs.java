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

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;

import io.cucumber.java.en.Step;
import io.restassured.response.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.niis.xroad.e2e.EnvSetup;
import org.niis.xroad.opmonitor.core.OperationalDataRecord;
import org.niis.xroad.opmonitor.core.OperationalDataRecords;
import org.w3c.dom.Element;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SuppressWarnings(value = {"SpringJavaInjectionPointsAutowiringInspection", "checkstyle:magicnumber"})
public class OpMonitorStepDefs extends BaseE2EStepDefs {

    private static final String OPERATIONAL_DATA_JSON = "operational-monitoring-data.json.gz";
    private static final String OP_MONITORING_XSD = "http://x-road.eu/xsd/op-monitoring.xsd";
    private static final Duration OP_MONITOR_SETTLE_DELAY = Duration.ofSeconds(2);
    private static final Duration OP_MONITOR_SETTLE_TIMEOUT = Duration.ofSeconds(5);

    private Response lastOpMonitorResponse;
    private OperationalDataRecords lastOperationalDataRecords;

    @Step("getSecurityServerOperationalData request is sent to {string}")
    public void sendGetSecurityServerOperationalData(String env) {
        Awaitility.await().pollDelay(OP_MONITOR_SETTLE_DELAY).timeout(OP_MONITOR_SETTLE_TIMEOUT).until(() -> true);
        var mapping = envSetup.getContainerMapping(env, "proxy", EnvSetup.Port.PROXY);
        lastOpMonitorResponse = given()
                .header("Content-Type", "text/xml")
                .body(buildOperationalDataRequestBody())
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()));
    }

    @Step("getSecurityServerHealthData request is sent to {string}")
    public void sendGetSecurityServerHealthData(String env) {
        Awaitility.await().pollDelay(OP_MONITOR_SETTLE_DELAY).timeout(OP_MONITOR_SETTLE_TIMEOUT).until(() -> true);
        var mapping = envSetup.getContainerMapping(env, "proxy", EnvSetup.Port.PROXY);
        lastOpMonitorResponse = given()
                .header("Content-Type", "text/xml")
                .body(buildHealthDataRequestBody())
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()));
    }

    @SneakyThrows
    @Step("operational data response contains records with serviceSecurityServerAddress {string}")
    public void assertOperationalDataRecordsAddress(String expectedAddress) {
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
            public void attachment(String attachContentType, InputStream content,
                                   Map<String, String> additionalHeaders) throws IOException {
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

    @SneakyThrows
    @Step("health data response has statisticsPeriodSeconds {int} and at least {int} successfulRequestCount")
    public void assertHealthDataResponse(int expectedPeriodSeconds, int minSuccessfulRequests) {
        assertThat(lastOpMonitorResponse.getStatusCode()).isEqualTo(200);

        var body = lastOpMonitorResponse.asString();
        var periodSeconds = evalOpMonitorXpath(body, "//om:statisticsPeriodSeconds");
        var successCount = evalOpMonitorXpath(body, "//om:successfulRequestCount");

        assertThat(periodSeconds)
                .as("statisticsPeriodSeconds")
                .isEqualTo(String.valueOf(expectedPeriodSeconds));
        assertThat(Integer.parseInt(successCount))
                .as("successfulRequestCount >= %d", minSuccessfulRequests)
                .isGreaterThanOrEqualTo(minSuccessfulRequests);
    }

    @SneakyThrows
    private String evalOpMonitorXpath(String body, String xpathExpr) {
        var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        var ns = new org.springframework.util.xml.SimpleNamespaceContext();
        ns.bindNamespaceUri("soap", "http://schemas.xmlsoap.org/soap/envelope/");
        ns.bindNamespaceUri("om", OP_MONITORING_XSD);
        xpath.setNamespaceContext(ns);
        return xpath.evaluate(xpathExpr, new org.xml.sax.InputSource(new ByteArrayInputStream(body.getBytes())));
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
                var reader = new BufferedReader(new InputStreamReader(gzipIn))) {
            var sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
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
}
