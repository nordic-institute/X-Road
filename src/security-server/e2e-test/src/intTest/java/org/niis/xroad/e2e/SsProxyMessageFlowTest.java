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

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.e2e.container.E2eEnvSetup;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import static io.restassured.config.XmlConfig.xmlConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Runs first: {@link SsMessagelogArchiveTest} asserts on the messagelog records this class's
 * SOAP/REST traffic produces on ss1.
 */
@DisplayName("SS proxy - baseline message flow")
@Order(100)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyMessageFlowTest extends E2eTest {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";
    private static final String GET_RANDOM_RESPONSE_PATTERN = "(?s).*<.*getRandomResponse.*>.+</.*getRandomResponse.*>.*";

    private static final String SOAP_REQUEST_FROM_TEST_CLIENT = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
                <soapenv:Header>
                    <xro:client iden:objectType="SUBSYSTEM">
                        <iden:xRoadInstance>DEV</iden:xRoadInstance>
                        <iden:memberClass>COM</iden:memberClass>
                        <iden:memberCode>4321</iden:memberCode>
                        <iden:subsystemCode>TestClient</iden:subsystemCode>
                    </xro:client>
                    <xro:service iden:objectType="SERVICE">
                        <iden:xRoadInstance>DEV</iden:xRoadInstance>
                        <iden:memberClass>COM</iden:memberClass>
                        <iden:memberCode>1234</iden:memberCode>
                        <iden:subsystemCode>TestService</iden:subsystemCode>
                        <iden:serviceCode>getRandom</iden:serviceCode>
                        <iden:serviceVersion>v1</iden:serviceVersion>
                    </xro:service>
                    <xro:id>ID-SOAP-1</xro:id>
                    <xro:userId>EE1234567890</xro:userId>
                    <xro:protocolVersion>4.0</xro:protocolVersion>
                </soapenv:Header>
                <soapenv:Body>
                    <prod:getRandom xmlns:prod="http://test.x-road.fi/producer">
                        <prod:request/>
                    </prod:getRandom>
                </soapenv:Body>
            </soapenv:Envelope>
            """;

    private static final String SOAP_REQUEST_FROM_TEST_CONSUMER = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
                <soapenv:Header>
                    <xro:client iden:objectType="SUBSYSTEM">
                        <iden:xRoadInstance>DEV</iden:xRoadInstance>
                        <iden:memberClass>COM</iden:memberClass>
                        <iden:memberCode>1234</iden:memberCode>
                        <iden:subsystemCode>test-consumer</iden:subsystemCode>
                    </xro:client>
                    <xro:service iden:objectType="SERVICE">
                        <iden:xRoadInstance>DEV</iden:xRoadInstance>
                        <iden:memberClass>COM</iden:memberClass>
                        <iden:memberCode>1234</iden:memberCode>
                        <iden:subsystemCode>TestService</iden:subsystemCode>
                        <iden:serviceCode>getRandom</iden:serviceCode>
                        <iden:serviceVersion>v1</iden:serviceVersion>
                    </xro:service>
                    <xro:id>ID-SOAP-2</xro:id>
                    <xro:userId>EE1234567891</xro:userId>
                    <xro:protocolVersion>4.0</xro:protocolVersion>
                </soapenv:Header>
                <soapenv:Body>
                    <prod:getRandom xmlns:prod="http://test.x-road.fi/producer">
                        <prod:request/>
                    </prod:getRandom>
                </soapenv:Body>
            </soapenv:Envelope>
            """;

    private static final String REST_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;

    @Test
    @Order(1)
    @DisplayName("Soap request is successful over proxy")
    void soapRequestIsSuccessfulOverProxy(E2eEnvSetup env) {
        given("the environment is initialized", () -> assertThat(env.isAuxHurlRunning()).isFalse());

        var firstResponse = given("a SOAP getRandom request is sent to ss1 proxy as TestClient", () ->
                sendSoapRequest(env, SOAP_REQUEST_FROM_TEST_CLIENT));

        then("the response is 200 and its body matches a getRandomResponse", () ->
                firstResponse.statusCode(200).body(matchesPattern(GET_RANDOM_RESPONSE_PATTERN)));

        var secondResponse = given("a second SOAP getRandom request is sent to ss1 proxy as test-consumer", () ->
                sendSoapRequest(env, SOAP_REQUEST_FROM_TEST_CONSUMER));

        then("the second response is also 200 and its body matches a getRandomResponse", () ->
                secondResponse.statusCode(200).body(matchesPattern(GET_RANDOM_RESPONSE_PATTERN)));
    }

    @Test
    @Order(2)
    @DisplayName("REST request is successfully transferred over X-Road proxy")
    void restRequestIsSuccessfullyTransferredOverProxy(E2eEnvSetup env) {
        var mapping = env.getContainerMapping("ss1", SsStackSetup.PROXY, SsStackSetup.Port.PROXY);

        var response = given("a REST request is sent to ss1 proxy", () ->
                RestAssuredFactory.given()
                        .body(REST_REQUEST_BODY)
                        .header("Content-Type", "application/json")
                        .header("x-road-client", "DEV/COM/4321/TestClient")
                        .post("http://%s:%s/r1/DEV/COM/1234/TestService/mock1".formatted(mapping.host(), mapping.port()))
                        .then());

        then("the response is 200 with the expected POST service message", () ->
                response.statusCode(200).body("message", equalTo("Hello, world from POST service!")));
    }

    @Test
    @Order(3)
    @DisplayName("REST request with valid API path permission is successfully transferred over X-Road proxy")
    void restRequestWithValidApiPathPermissionIsSuccessfullyTransferredOverProxy(E2eEnvSetup env) {
        var mapping = env.getContainerMapping("ss1", SsStackSetup.PROXY, SsStackSetup.Port.PROXY);

        var response = given("a REST request targeted at the /api/members API endpoint is sent to ss1 proxy", () ->
                RestAssuredFactory.given()
                        .header("Content-Type", "application/json")
                        .header("x-road-client", "DEV/COM/4321/TestClient")
                        .get("http://%s:%s/r1/DEV/COM/1234/TestService/restapi/api/members".formatted(mapping.host(), mapping.port()))
                        .then());

        then("the response is 200 with the first member's name", () ->
                response.statusCode(200).body("[0].name", equalTo("MTÜ Nordic Institute for Interoperability Solutions")));
    }

    @Test
    @Order(4)
    @DisplayName("Admin login against the ss1 UI container succeeds")
    void adminLoginAgainstSs1UiSucceeds(E2eEnvSetup env) {
        var mapping = env.getContainerMapping("ss1", SsStackSetup.UI, SsStackSetup.Port.UI);
        var baseUrl = "https://%s:%s".formatted(mapping.host(), mapping.port());

        var loginResponse = given("an admin login request is sent to the ss1 UI", () ->
                RestAssuredFactory.given()
                        .formParam("username", ADMIN_USERNAME)
                        .formParam("password", ADMIN_PASSWORD)
                        .post(baseUrl + "/login"));

        then("the login succeeds and issues an XSRF token", () -> {
            loginResponse.then().statusCode(200);
            assertThat(loginResponse.cookie("XSRF-TOKEN")).isNotBlank();
        });
    }

    private ValidatableResponse sendSoapRequest(E2eEnvSetup env, String body) {
        var mapping = env.getContainerMapping("ss1", SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .config(RestAssured.config()
                        .xmlConfig(xmlConfig()
                                .namespaceAware(true)
                                .declareNamespace("soapenv", "http://schemas.xmlsoap.org/soap/envelope/")))
                .body(body)
                .header("Content-Type", "text/xml")
                .post("http://%s:%s".formatted(mapping.host(), mapping.port()))
                .then();
    }
}
