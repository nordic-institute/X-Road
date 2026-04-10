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

package org.niis.xroad.softtoken.signer.test.glue;

import io.cucumber.java.en.Step;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup.Port.HEALTH_PORT;
import static org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup.SIGNER;
import static org.niis.xroad.softtoken.signer.test.SoftTokenSignerIntTestContainerSetup.SOFTTOKEN_SIGNER;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class HealthCheckStepDefs extends BaseSoftTokenSignerStepDefs {

    private static final String HEALTH_RESPONSE_KEY = "healthResponse";

    private String buildHealthBaseUrl() {
        var mapping = containerSetup.getContainerMapping(SOFTTOKEN_SIGNER, HEALTH_PORT);
        return "http://%s:%d".formatted(mapping.host(), mapping.port());
    }

    private Response queryHealthEndpoint(String path) {
        var url = buildHealthBaseUrl() + path;
        log.info("Querying health endpoint: {}", url);

        var response = given()
                .get(url)
                .then()
                .extract()
                .response();

        log.info("Health response ({}): status={}, body={}",
                path, response.getStatusCode(), response.getBody().asString());
        putStepData(HEALTH_RESPONSE_KEY, response);
        testReportService.attachJson("Health response " + path, response.getBody().asString());
        return response;
    }

    @Step("softtoken-signer liveness endpoint is queried")
    public void livenessEndpointIsQueried() {
        queryHealthEndpoint("/q/health/live");
    }

    @Step("softtoken-signer readiness endpoint is queried")
    public void readinessEndpointIsQueried() {
        queryHealthEndpoint("/q/health/ready");
    }

    @Step("softtoken-signer health endpoint is queried")
    public void healthEndpointIsQueried() {
        queryHealthEndpoint("/q/health");
    }

    @Step("the health response status is {string}")
    public void healthResponseStatusIs(String expectedStatus) {
        Response response = getStepData(HEALTH_RESPONSE_KEY);
        var actualStatus = response.jsonPath().getString("status");

        assertThat(actualStatus)
                .as("Health response top-level status")
                .isEqualTo(expectedStatus);
    }

    @Step("the health response contains check {string} with status {string}")
    public void healthResponseContainsCheckWithStatus(String checkName, String expectedStatus) {
        Response response = getStepData(HEALTH_RESPONSE_KEY);
        var checks = response.jsonPath().getList("checks");

        assertThat(checks).as("Health checks list").isNotEmpty();

        var check = findCheckByName(response, checkName);
        assertThat(check).as("Check '%s' should exist", checkName).isNotNull();

        var actualStatus = ((java.util.Map<?, ?>) check).get("status");
        assertThat(actualStatus)
                .as("Check '%s' status", checkName)
                .isEqualTo(expectedStatus);
    }

    @Step("the health response contains check {string} with data {string} equal to {int}")
    public void healthResponseContainsCheckWithDataEqualTo(
            String checkName, String dataKey, int expectedValue) {
        Response response = getStepData(HEALTH_RESPONSE_KEY);
        var check = findCheckByName(response, checkName);
        assertThat(check).as("Check '%s' should exist", checkName).isNotNull();

        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) ((java.util.Map<?, ?>) check).get("data");
        assertThat(data).as("Check '%s' data", checkName).containsKey(dataKey);

        var actualValue = ((Number) data.get(dataKey)).intValue();
        assertThat(actualValue)
                .as("Check '%s' data key '%s'", checkName, dataKey)
                .isEqualTo(expectedValue);
    }

    @Step("the health response contains check {string} with data key {string}")
    public void healthResponseContainsCheckWithDataKey(String checkName, String dataKey) {
        Response response = getStepData(HEALTH_RESPONSE_KEY);
        var check = findCheckByName(response, checkName);
        assertThat(check).as("Check '%s' should exist", checkName).isNotNull();

        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) ((java.util.Map<?, ?>) check).get("data");
        assertThat(data)
                .as("Check '%s' data should contain key '%s'", checkName, dataKey)
                .containsKey(dataKey);
    }

    @Step("signer service is stopped")
    public void signerServiceIsStopped() {
        log.info("Stopping signer service container...");
        containerSetup.stopContainer(SIGNER);
        log.info("Signer service container stopped.");
    }

    @Step("signer service is started")
    public void signerServiceIsStarted() {
        log.info("Starting signer service container...");
        containerSetup.startContainer(SIGNER, true);
        log.info("Signer service container started and healthy.");
    }

    @Step("health check failure threshold is reached")
    public void healthCheckFailureThresholdIsReached() {
        log.info("Waiting for liveness probe to report DOWN (failure threshold reached)...");
        var url = buildHealthBaseUrl() + "/q/health/live";
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> given().get(url).then().statusCode(503));
        log.info("Liveness probe reports DOWN - failure threshold reached.");
    }

    @Step("sync age threshold is exceeded")
    public void syncAgeThresholdIsExceeded() {
        log.info("Waiting for readiness probe to report DOWN (sync age exceeded)...");
        var url = buildHealthBaseUrl() + "/q/health/ready";
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> given().get(url).then().statusCode(503));
        log.info("Readiness probe reports DOWN - sync age threshold exceeded.");
    }

    @Step("keys are synchronized after recovery")
    public void keysAreSynchronizedAfterRecovery() {
        log.info("Waiting for liveness probe to recover to UP...");
        var url = buildHealthBaseUrl() + "/q/health/live";
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> given().get(url).then().statusCode(200));
        log.info("Liveness probe recovered to UP - keys synchronized.");
    }

    @Step("the health response HTTP status code is {int}")
    public void healthResponseHttpStatusCodeIs(int expectedStatusCode) {
        Response response = getStepData(HEALTH_RESPONSE_KEY);
        assertThat(response.getStatusCode())
                .as("Health response HTTP status code")
                .isEqualTo(expectedStatusCode);
    }

    private Object findCheckByName(Response response, String checkName) {
        var checks = response.jsonPath().getList("checks");
        return checks.stream()
                .filter(c -> checkName.equals(((java.util.Map<?, ?>) c).get("name")))
                .findFirst()
                .orElse(null);
    }
}
