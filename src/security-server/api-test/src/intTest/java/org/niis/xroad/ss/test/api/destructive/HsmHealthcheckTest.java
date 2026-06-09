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
package org.niis.xroad.ss.test.api.destructive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;

import java.time.Duration;

import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckNoErrors;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.healthcheckUrl;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that the proxy healthcheck reports no errors when the HSM health check is enabled
 * via the configurable system property and the proxy is restarted.
 *
 * <p>Runs on the disposable destructive lane because the proxy restart would disrupt any
 * concurrent test sharing the warm substrate.
 */
// MIGRATED-FROM: 2300-ss-proxy-healthcheck.feature :: "HSM healthcheck has no errors when HSM health check is enabled"
@DisplayName("Proxy healthcheck: HSM check enabled via configurable property")
@SuppressWarnings("checkstyle:magicnumber")
class HsmHealthcheckTest extends SsDestructiveTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(90);

    @Test
    @DisplayName("healthcheck has no errors after HSM health check is enabled and proxy is restarted")
    void healthcheckOkAfterHsmHealthCheckEnabled(DestructiveStackSetup stack) {
        var uiMapping = stack.getContainerMapping(DestructiveStackSetup.UI, DestructiveStackSetup.UI_PORT);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());
        var session = new AdminApiSession(uiBaseUrl);
        var healthUrl = healthcheckUrl(stack);

        given("the proxy healthcheck has no errors initially", () ->
                assertHealthcheckNoErrors(healthUrl, POLL_INTERVAL, POLL_TIMEOUT));

        when("HSM health check is enabled via the configurable system property", () ->
                enableHsmHealthCheck(session));

        when("the proxy service is restarted", () ->
                stack.restartService(DestructiveStackSetup.PROXY));

        then("the proxy healthcheck has no errors after HSM check is enabled and proxy restarts", () ->
                assertHealthcheckNoErrors(healthUrl, POLL_INTERVAL, POLL_TIMEOUT));
    }

    private void enableHsmHealthCheck(AdminApiSession session) {
        session.given()
                .contentType("application/json")
                .body("""
                        {"property_name":"xroad.proxy.hsm-health-check-enabled","property_value":"true","scope":"proxy"}
                        """)
                .patch("/system/property")
                .then()
                .statusCode(204);
    }
}
