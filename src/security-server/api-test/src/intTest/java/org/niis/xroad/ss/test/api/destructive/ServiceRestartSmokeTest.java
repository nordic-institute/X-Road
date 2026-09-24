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

import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Infra smoke test for the destructive-lifecycle lane. Stops a core service on the shared stack,
 * asserts the health endpoint goes away, restarts the service, and asserts health recovers.
 * Does not migrate any audit-ledger scenario — this is purely lane infrastructure validation.
 */
@DisplayName("Destructive lane: service stop and restart recovers health")
@SuppressWarnings("checkstyle:magicnumber")
class ServiceRestartSmokeTest extends SsSharedStackDestructiveTest {

    private static final String SERVICE = SsApiTestContainerSetup.TESTCA;
    private static final int TESTCA_PORT = 8888;
    private static final String TESTCA_HEALTH_PATH = "/testca/certs/";

    // acme2certifier is a separate uwsgi process inside the testca container (see development/docker/testca's
    // README - it is restarted independently, via "supervisorctl restart uwsgi"), not exposed to the host the
    // way TESTCA_PORT is, and TESTCA_HEALTH_PATH alone can report "recovered" while it is still starting up. A
    // curl run from inside the container against its own directory endpoint is the only way to confirm it.
    private static final String ACME_DIRECTORY_URL = "http://localhost:8887";

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @DisplayName("testca stops, then restarts and health endpoint returns 200")
    // "testca-acme-server": also held (READ) by the destructive lane's DS TLS ACME-ordering tests
    // (DsTlsCertificateLifecycleDestructiveTest, DsTlsAcmeEnrollmentTest), the same reader/writer @ResourceLock
    // convention DsTlsCertificateApiTest uses on the Central Server side. Those tests already inherit the
    // coarser class-level "destructive-stack" lock, which alone already rules out literally overlapping this
    // restart - this finer lock exists so the specific coupling to testca's ACME service stays correct if that
    // coarser lock's scope is ever narrowed.
    @ResourceLock(value = "testca-acme-server", mode = ResourceAccessMode.READ_WRITE)
    void testcaHealthRecoversAfterRestart(SsApiTestContainerSetup stack) {
        var mapping = stack.getContainerMapping(SERVICE, TESTCA_PORT);
        var healthUrl = "http://%s:%d%s".formatted(mapping.host(), mapping.port(), TESTCA_HEALTH_PATH);

        given("the testca health endpoint is initially reachable", () -> {
            int status = RestAssured.get(healthUrl).statusCode();
            assertThat(status).isEqualTo(200);
        });

        when("the testca service container is stopped", () ->
                stack.stopService(SERVICE));

        then("the testca health endpoint becomes unreachable", () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .until(() -> {
                            try {
                                return RestAssured.get(healthUrl).statusCode() != 200;
                            } catch (Exception e) {
                                return true;
                            }
                        }));

        when("the testca service container is restarted", () ->
                stack.startService(SERVICE));

        then("the testca health endpoint returns 200 and its ACME service is ready for orders after recovery", () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .until(() -> testcaHealthCheckPasses(healthUrl) && acmeServiceIsReady(stack)));
    }

    private static boolean testcaHealthCheckPasses(String healthUrl) {
        try {
            return RestAssured.get(healthUrl).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean acmeServiceIsReady(SsApiTestContainerSetup stack) {
        try {
            return stack.execInContainer(SERVICE, "curl", "-sf", "-o", "/dev/null", ACME_DIRECTORY_URL)
                    .getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
