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
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckNoErrors;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckStatus;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.healthcheckUrl;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that the proxy healthcheck reports DOWN when signer is stopped and recovers after restart.
 */
@DisplayName("Proxy healthcheck: signer-down scenario")
@SuppressWarnings("checkstyle:magicnumber")
class SignerDownHealthcheckTest extends SsSharedStackDestructiveTest {

    private static final String SIGNER_CHANNEL_CHECK = "SIGNER_CHANNEL_READINESS_CHECK";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(90);

    @Test
    @DisplayName("healthcheck reports DOWN when signer is stopped and recovers after restart")
    void healthcheckGoesDownWhenSignerStopsAndRecoversAfterRestart(SsApiTestContainerSetup stack) {
        var url = healthcheckUrl(stack);

        given("the proxy healthcheck has no errors initially", () ->
                assertHealthcheckNoErrors(url, POLL_INTERVAL, POLL_TIMEOUT));

        when("the signer container is stopped", () ->
                stack.stopService(SsApiTestContainerSetup.SIGNER));

        then("the SIGNER_CHANNEL_READINESS_CHECK is DOWN", () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .untilAsserted(() ->
                                assertHealthcheckStatus(url, SIGNER_CHANNEL_CHECK, "DOWN")));

        when("the signer container is restarted", () ->
                stack.startService(SsApiTestContainerSetup.SIGNER));

        then("the proxy healthcheck has no errors after signer recovers", () ->
                assertHealthcheckNoErrors(url, POLL_INTERVAL, POLL_TIMEOUT));
    }
}
