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
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import static io.restassured.config.XmlConfig.xmlConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Reverse-direction cross-server message flow: ss0-hosted {@code TestSaved} initiates SOAP and REST
 * calls against ss1-hosted {@code TestClient}, which the fixture also gives a provider role
 * alongside its existing consumer role. Every scenario in {@link SsProxyMessageFlowTest} initiates
 * at ss1 against an ss0-hosted service; this class is the only one where ss0 acts as the initiating
 * consumer and ss1 as the provider.
 *
 * <p>Runs after {@link SsMessagelogArchiveTest} (@{@code Order(200)}), which asserts exact
 * pre-archive messagelog counts on both servers: this class's traffic would otherwise be counted by
 * those assertions. {@link SsProxyDspSelfCallTest} (@{@code Order(300)}) is the precedent for this
 * ordering constraint.
 */
@DisplayName("SS proxy - reverse-direction message flow (ss0 as consumer)")
@Order(350)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("checkstyle:magicnumber")
class SsReverseProxyMessageFlowTest extends E2eTest {

    private static final String GET_RANDOM_RESPONSE_PATTERN = "(?s).*<.*getRandomResponse.*>.+</.*getRandomResponse.*>.*";

    private static final String SOAP_REQUEST_FROM_TEST_SAVED = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
                <soapenv:Header>
                    <xro:client iden:objectType="SUBSYSTEM">
                        <iden:xRoadInstance>DEV</iden:xRoadInstance>
                        <iden:memberClass>COM</iden:memberClass>
                        <iden:memberCode>1234</iden:memberCode>
                        <iden:subsystemCode>TestSaved</iden:subsystemCode>
                    </xro:client>
                    <xro:service iden:objectType="SERVICE">
                        <iden:xRoadInstance>DEV</iden:xRoadInstance>
                        <iden:memberClass>COM</iden:memberClass>
                        <iden:memberCode>4321</iden:memberCode>
                        <iden:subsystemCode>TestClient</iden:subsystemCode>
                        <iden:serviceCode>getRandom</iden:serviceCode>
                        <iden:serviceVersion>v1</iden:serviceVersion>
                    </xro:service>
                    <xro:id>ID-SOAP-REVERSE-1</xro:id>
                    <xro:userId>EE1234567892</xro:userId>
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
    @DisplayName("SOAP request initiated at ss0 is successful against ss1's hosted service")
    void soapRequestFromSs0IsSuccessfulAgainstSs1(E2eEnvironment env) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var response = given("a SOAP getRandom request is sent to ss0 proxy as TestSaved", () ->
                sendSoapRequest(env, SOAP_REQUEST_FROM_TEST_SAVED));

        then("the response is 200 and its body matches a getRandomResponse", () ->
                response.statusCode(200).body(matchesPattern(GET_RANDOM_RESPONSE_PATTERN)));
    }

    @Test
    @Order(2)
    @DisplayName("REST request initiated at ss0 is successfully transferred to ss1's hosted service")
    void restRequestFromSs0IsSuccessfullyTransferredToSs1(E2eEnvironment env) {
        var mapping = env.getContainerMapping("ss0", SsStackSetup.PROXY, SsStackSetup.Port.PROXY);

        var response = given("a REST request is sent to ss0 proxy", () ->
                RestAssuredFactory.given()
                        .body(REST_REQUEST_BODY)
                        .header("Content-Type", "application/json")
                        .header("x-road-client", "DEV/COM/1234/TestSaved")
                        .post("http://%s:%s/r1/DEV/COM/4321/TestClient/mock1".formatted(mapping.host(), mapping.port()))
                        .then());

        then("the response is 200 with the expected POST service message", () ->
                response.statusCode(200).body("message", equalTo("Hello, world from POST service!")));
    }

    private ValidatableResponse sendSoapRequest(E2eEnvironment env, String body) {
        var mapping = env.getContainerMapping("ss0", SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
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
