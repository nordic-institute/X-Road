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
package org.niis.xroad.ss.test.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.DiagnosticsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.time.Instant;

import static org.awaitility.Awaitility.await;

/**
 * Sends one request through the proxy and waits until op-monitor has stored it, before any test runs.
 * The proxy opens its op-monitor channel on the first recorded request and keeps the trust it resolved then,
 * so opening it here keeps tests that temporarily import an op-monitor CA from deciding that trust.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("checkstyle:magicnumber")
final class OpMonitorChannelWarmUp {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration WINDOW_MARGIN = Duration.ofMinutes(1);

    static void awaitRecordedRequest(SsApiTestContainerSetup stack) {
        var ui = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var diagnostics = new DiagnosticsAdminClient(AdminApiSession.silent("https://%s:%d".formatted(ui.host(), ui.port())));
        var now = Instant.now();
        var recordsFrom = now.minus(WINDOW_MARGIN);
        var recordsTo = now.plus(WINDOW_MARGIN);
        var countBefore = recordedRequestCount(diagnostics, recordsFrom, recordsTo);

        var proxy = stack.getContainerMapping(SsApiTestContainerSetup.PROXY, Port.PROXY_HTTP);
        var clientPath = "DEV/%s/%s".formatted(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE);
        var status = RestAssuredFactory.givenSilent()
                .header("X-Road-Client", clientPath)
                .get("http://%s:%d/r1/%s/warmUp/opMonitorChannel".formatted(proxy.host(), proxy.port(), clientPath))
                .statusCode();
        log.info("Op-monitor channel warm-up request answered {}, waiting for op-monitor to store it", status);

        try {
            await("proxy stores a request in op-monitor")
                    .pollDelay(Duration.ZERO)
                    .pollInterval(POLL_INTERVAL)
                    .atMost(TIMEOUT)
                    .until(() -> recordedRequestCount(diagnostics, recordsFrom, recordsTo) > countBefore);
        } catch (ConditionTimeoutException e) {
            throw new IllegalStateException(("Op-monitor stored no request within %s of a proxy request; the proxy cannot send"
                    + " operational data to op-monitor. Check the proxy log for 'Trusting only the imported op-monitor CA'"
                    + " and 'Sending operational monitoring data failed'.").formatted(TIMEOUT), e);
        }
        log.info("Proxy to op-monitor store path is up");
    }

    private static int recordedRequestCount(DiagnosticsAdminClient diagnostics, Instant recordsFrom, Instant recordsTo) {
        return DiagnosticsAdminClient.totalRequestCount(diagnostics.getOperationalMonitoringIntervals(recordsFrom, recordsTo, 1)
                .statusCode(200)
                .extract()
                .jsonPath());
    }
}
