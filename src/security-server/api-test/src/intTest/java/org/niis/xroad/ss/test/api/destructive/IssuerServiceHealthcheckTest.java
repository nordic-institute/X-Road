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
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.DsTlsCertificateAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckNoErrors;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.assertHealthcheckStatus;
import static org.niis.xroad.ss.test.api.destructive.ProxyHealthcheckSupport.healthUrl;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies the Issuer Service health endpoint: readiness names the database as the cause while its database is
 * stopped, and names connector TLS as the cause while the shared connector TLS slot holds no certificate. Each
 * case recovers once the dependency is back.
 */
@DisplayName("Issuer Service healthcheck: database-down and no-certificate scenarios")
@SuppressWarnings("checkstyle:magicnumber")
class IssuerServiceHealthcheckTest extends SsSharedStackDestructiveTest {

    private static final String READINESS_PATH = "/q/health/ready";
    private static final String LIVENESS_PATH = "/q/health/live";
    private static final String CHECK_NAME = "EDC_READINESS_CHECK";
    private static final String DATABASE_COMPONENT = "database";
    private static final String CONNECTOR_TLS_COMPONENT = "connector-tls";
    private static final String CONNECTOR_TLS_CAUSE = "Connector TLS is not served";
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

    @Test
    @DisplayName("a restarted Issuer Service is live but not ready without the connector TLS certificate "
            + "and turns ready once the certificate is seeded")
    void livenessIsUpAndReadinessNamesConnectorTlsWhileTheCertificateIsMissing(SsApiTestContainerSetup stack) {
        var readinessUrl = healthUrl(stack, SsApiTestContainerSetup.DS_ISSUER_SERVICE, Port.QUARKUS_HEALTH, READINESS_PATH);
        var livenessUrl = healthUrl(stack, SsApiTestContainerSetup.DS_ISSUER_SERVICE, Port.QUARKUS_HEALTH, LIVENESS_PATH);
        var seeder = new SsBaselineSeeder(stack);
        given("the Issuer Service readiness is UP initially", () ->
                assertHealthcheckNoErrors(readinessUrl, POLL_INTERVAL, POLL_TIMEOUT));
        try {
            when("the connector TLS key is regenerated, leaving the slot without a certificate", () ->
                    new DsTlsCertificateAdminClient(adminSession(stack)).generateKey().statusCode(201));
            and("the Issuer Service is restarted", () ->
                    stack.restartService(SsApiTestContainerSetup.DS_ISSUER_SERVICE));
            then("liveness is UP", () ->
                    assertHealthcheckNoErrors(livenessUrl, POLL_INTERVAL, POLL_TIMEOUT));
            and("readiness is DOWN with the connector TLS cause", () ->
                    await()
                            .pollDelay(Duration.ZERO)
                            .pollInterval(POLL_INTERVAL)
                            .atMost(POLL_TIMEOUT)
                            .ignoreExceptions()
                            .untilAsserted(() -> assertConnectorTlsNotServed(readinessUrl)));
        } finally {
            and("the connector TLS certificate is seeded again", seeder::ensureBaseline);
        }
        then("readiness is UP once the certificate is served", () ->
                assertHealthcheckNoErrors(readinessUrl, POLL_INTERVAL, POLL_TIMEOUT));
    }
    private static AdminApiSession adminSession(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return new AdminApiSession("https://%s:%d".formatted(uiMapping.host(), uiMapping.port()));
    }
    private static void assertConnectorTlsNotServed(String url) {
        assertHealthcheckStatus(url, CHECK_NAME, "DOWN");
        var cause = RestAssuredFactory.given().get(url).jsonPath()
                .getString("checks.find { it.name == '%s' }.data['%s']".formatted(CHECK_NAME, CONNECTOR_TLS_COMPONENT));
        assertThat(cause).as("cause of the connector TLS component").contains(CONNECTOR_TLS_CAUSE);
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
