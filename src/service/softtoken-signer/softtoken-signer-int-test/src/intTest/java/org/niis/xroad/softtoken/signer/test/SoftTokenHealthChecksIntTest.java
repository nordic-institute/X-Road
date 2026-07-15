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
package org.niis.xroad.softtoken.signer.test;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.softtoken.signer.test.container.SoftTokenSignerContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;
import static org.niis.xroad.softtoken.signer.test.container.SoftTokenSignerContainerSetup.SIGNER;
import static org.niis.xroad.softtoken.signer.test.container.SoftTokenSignerContainerSetup.SIGNER_AVAILABILITY_LOCK;

/**
 * 0200 - Softtoken-Signer: Health Check Probes. Queries the softtoken-signer Quarkus SmallRye health
 * endpoints ({@code /q/health/live}, {@code /q/health/ready}, {@code /q/health}) directly over HTTP via
 * RestAssured - the transport is unchanged from the legacy Cucumber suite.
 *
 * <p>Runs single-threaded and holds a write lock on {@link SoftTokenSignerContainerSetup#SIGNER_AVAILABILITY_LOCK}:
 * several scenarios stop and restart the shared signer container, which would otherwise break
 * {@link SoftTokenKeySyncIntTest} (or other health-check scenarios) running concurrently against the same
 * stack.
 */
@Slf4j
@ExtendWith(ApiStackExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(value = SIGNER_AVAILABILITY_LOCK, mode = READ_WRITE)
class SoftTokenHealthChecksIntTest {

    private static final String PIN = "1234";
    private static final String TOKEN_SOFT_000 = "soft-token-000";
    private static final int KEYS_SYNC_RATE_SECONDS = 3;
    private static final int KEYS_SYNC_BUFFER_SECONDS = 1;
    private static final int AWAIT_TIMEOUT_SECONDS = 30;
    private static final int AWAIT_POLL_SECONDS = 1;
    private static final int LIVENESS_THRESHOLD = 3;
    private static final int HTTP_OK = 200;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private SoftTokenSignerContainerSetup containerSetup;

    @BeforeEach
    void signerIsInitializedAndKeysSynchronized(SoftTokenSignerContainerSetup setup) {
        containerSetup = setup;
        Step.given("signer is initialized and keys are synchronized", () -> {
            var signerClient = setup.getSignerClient();
            signerClient.initSoftwareToken(PIN.toCharArray());
            var token = tokenByFriendlyName(signerClient);
            signerClient.activateToken(token.getId(), PIN.toCharArray());
            waitForSynchronization();
        });
    }

    /**
     * Ensures the signer container is running again after each health-check scenario. A scenario that stops
     * the signer and then fails an assertion would otherwise leave it stopped, hanging the next scenario's
     * setup on the signer RPC deadline.
     */
    @AfterEach
    void ensureSignerRunning() {
        if (!containerSetup.isRunning(SIGNER)) {
            log.info("Signer container not running after scenario; restarting to isolate teardown.");
            containerSetup.startContainer(SIGNER, true);
        }
    }

    @Test
    @DisplayName("Liveness probe reports UP after successful sync")
    void livenessProbeReportsUpAfterSuccessfulSync() {
        var response = Step.when("softtoken-signer liveness endpoint is queried", () -> queryHealthEndpoint("/q/health/live"));
        Step.then("the health response status is \"UP\"", () -> assertStatus(response, "UP"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_LIVENESS\" with status \"UP\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_LIVENESS", "UP"));
    }

    @Test
    @DisplayName("Readiness probe reports UP after successful sync")
    void readinessProbeReportsUpAfterSuccessfulSync() {
        var response = Step.when("softtoken-signer readiness endpoint is queried", () -> queryHealthEndpoint("/q/health/ready"));
        Step.then("the health response status is \"UP\"", () -> assertStatus(response, "UP"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with status \"UP\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_READINESS", "UP"));
    }

    @Test
    @DisplayName("Combined health endpoint reports UP")
    void combinedHealthEndpointReportsUp() {
        var response = Step.when("softtoken-signer health endpoint is queried", () -> queryHealthEndpoint("/q/health"));
        Step.then("the health response status is \"UP\"", () -> assertStatus(response, "UP"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_LIVENESS\" with status \"UP\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_LIVENESS", "UP"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with status \"UP\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_READINESS", "UP"));
    }

    @Test
    @DisplayName("Liveness probe includes threshold data")
    void livenessProbeIncludesThresholdData() {
        var response = Step.when("softtoken-signer liveness endpoint is queried", () -> queryHealthEndpoint("/q/health/live"));
        Step.then("the health response contains check \"SOFTTOKEN_SYNC_LIVENESS\" with data \"threshold\" equal to 3",
                () -> assertCheckDataEquals(response, "SOFTTOKEN_SYNC_LIVENESS", "threshold", LIVENESS_THRESHOLD));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_LIVENESS\" with data \"consecutive_failures\" equal to 0",
                () -> assertCheckDataEquals(response, "SOFTTOKEN_SYNC_LIVENESS", "consecutive_failures", 0));
    }

    @Test
    @DisplayName("Readiness probe includes sync timing data")
    void readinessProbeIncludesSyncTimingData() {
        var response = Step.when("softtoken-signer readiness endpoint is queried", () -> queryHealthEndpoint("/q/health/ready"));
        Step.then("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with data key \"last_successful_sync\"",
                () -> assertCheckHasDataKey(response, "SOFTTOKEN_SYNC_READINESS", "last_successful_sync"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with data key \"threshold_seconds\"",
                () -> assertCheckHasDataKey(response, "SOFTTOKEN_SYNC_READINESS", "threshold_seconds"));
    }

    @Test
    @DisplayName("Liveness probe reports DOWN after signer service is stopped")
    void livenessProbeReportsDownAfterSignerServiceIsStopped() {
        Step.when("signer service is stopped", () -> containerSetup.stopContainer(SIGNER));
        Step.and("health check failure threshold is reached",
                () -> awaitCheckStatus("/q/health/live", "SOFTTOKEN_SYNC_LIVENESS", "DOWN"));
        var response = Step.and("softtoken-signer liveness endpoint is queried", () -> queryHealthEndpoint("/q/health/live"));
        Step.then("the health response HTTP status code is 503", () -> assertHttpStatus(response, HTTP_SERVICE_UNAVAILABLE));
        Step.and("the health response status is \"DOWN\"", () -> assertStatus(response, "DOWN"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_LIVENESS\" with status \"DOWN\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_LIVENESS", "DOWN"));
        Step.and("signer service is started", () -> containerSetup.startContainer(SIGNER, true));
    }

    @Test
    @DisplayName("Readiness probe reports DOWN after sync age exceeds threshold")
    void readinessProbeReportsDownAfterSyncAgeExceedsThreshold() {
        Step.when("signer service is stopped", () -> containerSetup.stopContainer(SIGNER));
        // Wait on the SOFTTOKEN_SYNC_READINESS check specifically. The aggregate /q/health/ready also
        // includes SIGNER_CHANNEL_READINESS_CHECK, which trips DOWN as soon as the signer channel hits
        // TRANSIENT_FAILURE - well before the sync-age threshold elapses.
        Step.and("sync age threshold is exceeded",
                () -> awaitCheckStatus("/q/health/ready", "SOFTTOKEN_SYNC_READINESS", "DOWN"));
        var response = Step.and("softtoken-signer readiness endpoint is queried", () -> queryHealthEndpoint("/q/health/ready"));
        Step.then("the health response HTTP status code is 503", () -> assertHttpStatus(response, HTTP_SERVICE_UNAVAILABLE));
        Step.and("the health response status is \"DOWN\"", () -> assertStatus(response, "DOWN"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with status \"DOWN\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_READINESS", "DOWN"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with data key \"elapsed_seconds\"",
                () -> assertCheckHasDataKey(response, "SOFTTOKEN_SYNC_READINESS", "elapsed_seconds"));
        Step.and("signer service is started", () -> containerSetup.startContainer(SIGNER, true));
    }

    @Test
    @DisplayName("Health probes recover after signer service is restarted")
    void healthProbesRecoverAfterSignerServiceIsRestarted() {
        Step.when("signer service is stopped", () -> containerSetup.stopContainer(SIGNER));
        Step.and("health check failure threshold is reached",
                () -> awaitCheckStatus("/q/health/live", "SOFTTOKEN_SYNC_LIVENESS", "DOWN"));
        Step.and("signer service is started", () -> containerSetup.startContainer(SIGNER, true));
        Step.and("keys are synchronized after recovery",
                () -> awaitCheckStatus("/q/health/live", "SOFTTOKEN_SYNC_LIVENESS", "UP"));
        var response = Step.and("softtoken-signer health endpoint is queried", () -> queryHealthEndpoint("/q/health"));
        Step.then("the health response HTTP status code is 200", () -> assertHttpStatus(response, HTTP_OK));
        Step.and("the health response status is \"UP\"", () -> assertStatus(response, "UP"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_LIVENESS\" with status \"UP\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_LIVENESS", "UP"));
        Step.and("the health response contains check \"SOFTTOKEN_SYNC_READINESS\" with status \"UP\"",
                () -> assertCheckStatus(response, "SOFTTOKEN_SYNC_READINESS", "UP"));
    }

    private Response queryHealthEndpoint(String path) {
        var url = containerSetup.healthBaseUrl() + path;
        return given().get(url).then().extract().response();
    }

    private void assertStatus(Response response, String expectedStatus) {
        assertThat(response.jsonPath().getString("status")).as("Health response top-level status").isEqualTo(expectedStatus);
    }

    private void assertHttpStatus(Response response, int expectedStatusCode) {
        assertThat(response.getStatusCode()).as("Health response HTTP status code").isEqualTo(expectedStatusCode);
    }

    private void assertCheckStatus(Response response, String checkName, String expectedStatus) {
        var check = findCheckByName(response, checkName);
        assertThat(check).as("Check '%s' should exist", checkName).isNotNull();
        assertThat(check.get("status")).as("Check '%s' status", checkName).isEqualTo(expectedStatus);
    }

    private void assertCheckDataEquals(Response response, String checkName, String dataKey, int expectedValue) {
        var check = findCheckByName(response, checkName);
        assertThat(check).as("Check '%s' should exist", checkName).isNotNull();
        var data = dataOf(check);
        assertThat(data).as("Check '%s' data", checkName).containsKey(dataKey);
        assertThat(((Number) data.get(dataKey)).intValue()).as("Check '%s' data key '%s'", checkName, dataKey).isEqualTo(expectedValue);
    }

    private void assertCheckHasDataKey(Response response, String checkName, String dataKey) {
        var check = findCheckByName(response, checkName);
        assertThat(check).as("Check '%s' should exist", checkName).isNotNull();
        assertThat(dataOf(check)).as("Check '%s' data should contain key '%s'", checkName, dataKey).containsKey(dataKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Map<String, Object> check) {
        return (Map<String, Object>) check.get("data");
    }

    private Map<String, Object> findCheckByName(Response response, String checkName) {
        var checks = response.jsonPath().<Map<String, Object>>getList("checks");
        return checks.stream()
                .filter(c -> checkName.equals(c.get("name")))
                .findFirst()
                .orElse(null);
    }

    private void awaitCheckStatus(String path, String checkName, String expectedStatus) {
        await().atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(AWAIT_POLL_SECONDS, TimeUnit.SECONDS)
                .untilAsserted(() -> assertCheckStatus(queryHealthEndpoint(path), checkName, expectedStatus));
    }

    private TokenInfo tokenByFriendlyName(SignerRpcClient signerClient) {
        var tokens = signerClient.getTokens();
        Optional<TokenInfo> token = tokens.stream().filter(t -> TOKEN_SOFT_000.equals(t.getFriendlyName())).findFirst();
        if (token.isEmpty()) {
            token = tokens.stream().filter(t -> "0".equals(t.getId())).findFirst();
        }
        return token.orElseThrow(() -> new AssertionError("Token not found: " + TOKEN_SOFT_000));
    }

    private void waitForSynchronization() {
        try {
            Thread.sleep(Duration.ofSeconds(KEYS_SYNC_RATE_SECONDS + KEYS_SYNC_BUFFER_SECONDS).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Synchronization wait interrupted", e);
        }
    }
}
