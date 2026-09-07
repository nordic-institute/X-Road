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

@DisplayName("Proxy readiness checks are UP")
class ProxyReadinessTest extends SsApiTest {

    private static final String PROXY = "proxy";
    private static final String READINESS_PATH = "/q/health/ready";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @DisplayName("proxy readiness endpoint reports overall UP with all expected checks UP")
    void proxyReadinessIsUp(SsApiTestContainerSetup stack) {
        var mapping = stack.getContainerMapping(PROXY, Port.PROXY_HEALTHCHECK);
        var readinessUrl = "http://%s:%d%s".formatted(mapping.host(), mapping.port(), READINESS_PATH);

        var readiness = when("the proxy readiness endpoint is polled until UP", () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .until(() -> given().get(readinessUrl).jsonPath(), json -> "UP".equals(json.getString("status"))));

        then("the overall readiness status is UP", () ->
                assertThat(readiness.getString("status")).isEqualTo("UP"));

        then("the PROXY_SERVERCONF_DATABASE_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "PROXY_SERVERCONF_DATABASE_READINESS_CHECK"));
        then("the PROXY_GLOBALCONF_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "PROXY_GLOBALCONF_READINESS_CHECK"));
        then("the SIGNER_CHANNEL_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "SIGNER_CHANNEL_READINESS_CHECK"));
        then("the CONFCLIENT_CHANNEL_READINESS_CHECK is UP", () ->
                assertCheckUp(readiness, "CONFCLIENT_CHANNEL_READINESS_CHECK"));
    }

    private void assertCheckUp(JsonPath readiness, String checkName) {
        var status = readiness.getString("checks.find { it.name == '%s' }.status".formatted(checkName));
        assertThat(status)
                .as("status of readiness check '%s'", checkName)
                .isEqualTo("UP");
    }
}
