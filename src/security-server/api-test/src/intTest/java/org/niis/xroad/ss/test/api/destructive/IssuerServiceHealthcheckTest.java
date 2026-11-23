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
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckNoErrors;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckStatus;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.healthUrl;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that the Issuer Service readiness reports the database as the cause when its database is stopped
 * and recovers after the database is started again.
 */
@DisplayName("Issuer Service healthcheck: database-down scenario")
@SuppressWarnings("checkstyle:magicnumber")
class IssuerServiceHealthcheckTest extends SsSharedStackDestructiveTest {

    private static final String READINESS_PATH = "/q/health/ready";
    private static final String CHECK_NAME = "EDC_READINESS_CHECK";
    private static final String DATABASE_COMPONENT = "database";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(120);

    @Test
    @DisplayName("readiness reports the database DOWN when db-ds-issuer-service is stopped and recovers after start")
    void readinessGoesDownWhenDatabaseStopsAndRecoversAfterStart(SsApiTestContainerSetup stack) {
        var url = healthUrl(stack, SsApiTestContainerSetup.DS_ISSUER_SERVICE, Port.QUARKUS_HEALTH, READINESS_PATH);

        given("the Issuer Service readiness is UP initially", () ->
                assertHealthcheckNoErrors(url, POLL_INTERVAL, POLL_TIMEOUT));

        when("the db-ds-issuer-service container is stopped", () ->
                stack.stopService(SsApiTestContainerSetup.DB_DS_ISSUER_SERVICE));

        then("the readiness is DOWN with the database cause and no connection details", () ->
                await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .untilAsserted(() -> assertDatabaseDown(url)));

        when("the db-ds-issuer-service container is started again", () ->
                stack.startService(SsApiTestContainerSetup.DB_DS_ISSUER_SERVICE));

        then("the readiness is UP after the database recovers", () ->
                assertHealthcheckNoErrors(url, POLL_INTERVAL, POLL_TIMEOUT));
    }

    private static void assertDatabaseDown(String url) {
        assertHealthcheckStatus(url, CHECK_NAME, "DOWN");
        var response = RestAssuredFactory.given().get(url);
        var cause = response.jsonPath().getString("checks.find { it.name == '%s' }.data.%s"
                .formatted(CHECK_NAME, DATABASE_COMPONENT));
        assertThat(cause).as("cause of the database component").isNotBlank();
        assertThat(response.asString()).doesNotContain("jdbc:");
    }
}
