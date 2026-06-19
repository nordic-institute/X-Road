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
package org.niis.xroad.ss.test.api.healthcheck;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;
import static org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.given;

@DisplayName("Quarkus service readiness checks are UP")
@SuppressWarnings("checkstyle:magicnumber")
class QuarkusReadinessTest extends SsApiTest {

    private static final String READINESS_PATH = "/q/health/ready";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @DisplayName("signer readiness endpoint reports overall UP with SIGNER_DATABASE and TOKEN_REGISTRY checks UP")
    void signerReadinessIsUp(SsApiTestContainerSetup stack) {
        var readiness = pollReadinessUntilUp(stack, SsApiTestContainerSetup.SIGNER);

        then("the overall signer readiness status is UP", () ->
                assertThat(readiness.getString("status")).isEqualTo("UP"));
        then("the SIGNER_DATABASE_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "SIGNER_DATABASE_READINESS_CHECK"));
        then("the TOKEN_REGISTRY_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "TOKEN_REGISTRY_READINESS_CHECK"));
    }

    @Test
    @DisplayName("configuration-client readiness endpoint reports overall UP with GLOBALCONF_READINESS_CHECK UP")
    void configurationClientReadinessIsUp(SsApiTestContainerSetup stack) {
        var readiness = pollReadinessUntilUp(stack, SsApiTestContainerSetup.CONFIGURATION_CLIENT);

        then("the overall configuration-client readiness status is UP", () ->
                assertThat(readiness.getString("status")).isEqualTo("UP"));
        then("the GLOBALCONF_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "GLOBALCONF_READINESS_CHECK"));
    }

    @Test
    @DisplayName("op-monitor readiness endpoint reports overall UP with OP_MONITOR_DATABASE_READINESS_CHECK UP")
    void opMonitorReadinessIsUp(SsApiTestContainerSetup stack) {
        var readiness = pollReadinessUntilUp(stack, SsApiTestContainerSetup.OP_MONITOR);

        then("the overall op-monitor readiness status is UP", () ->
                assertThat(readiness.getString("status")).isEqualTo("UP"));
        then("the OP_MONITOR_DATABASE_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "OP_MONITOR_DATABASE_READINESS_CHECK"));
    }

    @Test
    @DisplayName("auxiliary-service readiness endpoint reports overall UP")
    void auxiliaryServiceReadinessIsUp(SsApiTestContainerSetup stack) {
        var readiness = pollReadinessUntilUp(stack, SsApiTestContainerSetup.AUXILIARY_SERVICE);

        then("the overall auxiliary-service readiness status is UP", () ->
                assertThat(readiness.getString("status")).isEqualTo("UP"));
    }

    private JsonPath pollReadinessUntilUp(SsApiTestContainerSetup stack, String serviceName) {
        var mapping = stack.getContainerMapping(serviceName, Port.QUARKUS_HEALTH);
        var readinessUrl = "http://%s:%d%s".formatted(mapping.host(), mapping.port(), READINESS_PATH);

        return when("the %s readiness endpoint is polled until UP".formatted(serviceName), () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .until(() -> given().get(readinessUrl).jsonPath(),
                                json -> "UP".equals(json.getString("status"))));
    }

    private void assertCheckUp(JsonPath readiness, String checkName) {
        var status = readiness.getString("checks.find { it.name == '%s' }.status".formatted(checkName));
        assertThat(status)
                .as("status of readiness check '%s'", checkName)
                .isEqualTo("UP");
    }
}
