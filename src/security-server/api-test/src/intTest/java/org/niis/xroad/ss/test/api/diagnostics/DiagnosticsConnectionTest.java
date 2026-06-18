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
package org.niis.xroad.ss.test.api.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.DiagnosticsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Security Server diagnostics connection testing:
 * Central Server connection status checks and other-SS / management-SS connection tests.
 */
@DisplayName("Diagnostics connection testing — CS status and SS proxy connection tests")
@SuppressWarnings("checkstyle:magicnumber")
class DiagnosticsConnectionTest extends SsApiTest {

    private static final String SS_OWNER_ID = "DEV:COM:1234";
    private static final String MGMT_SUBSYSTEM_ID = "DEV:COM:1234:MANAGEMENT";
    private static final String MGMT_SECURITY_SERVER_ID = "DEV:COM:1234:SS0";
    private static final String EXPECTED_ERROR_CODE = "server.clientproxy";

    // MIGRATED-FROM: 0920-ss-diagnostics-connection-testing.feature :: "Central Server connection check tests should run"
    @Test
    @DisplayName("CS global-conf download URLs report FAIL and auth-cert registration service reports FAIL on single-SS stack")
    void centralServerConnectionCheckTestsShouldRun(SsBaselineSeeder seeder) {
        var diag = new DiagnosticsAdminClient(seeder.newSession());

        var globalConfStatuses = given("global-conf connection statuses are retrieved", () ->
                diag.getGlobalConfStatusRaw());

        then("global-conf status list is non-empty (has configured CS URLs)", () ->
                assertThat(globalConfStatuses).isNotEmpty());

        and("every global-conf download URL has a non-OK connection_status (no reachable CS in single-SS stack)", () -> {
            for (var entry : globalConfStatuses) {
                @SuppressWarnings("unchecked")
                var connectionStatus = (Map<String, Object>) entry.get("connection_status");
                assertThat(connectionStatus).as("connection_status must be present for URL %s", entry.get("download_url"))
                        .isNotNull();
                assertThat(connectionStatus.get("status_class"))
                        .as("CS URL %s must report non-OK on a single-SS stack", entry.get("download_url"))
                        .isNotEqualTo("OK");
            }
        });

        var authCertStatus = when("auth-cert registration request status is retrieved", () ->
                diag.getAuthCertReqStatusRaw());

        then("auth-cert registration service status_class is not OK (no reachable CS)", () ->
                assertThat(authCertStatus.get("status_class"))
                        .as("auth-cert registration must not be OK without a reachable Central Server")
                        .isNotEqualTo("OK"));
    }

    // MIGRATED-FROM: 0920-ss-diagnostics-connection-testing.feature :: "Other Security Server connection test can be run"
    @Test
    @DisplayName("Other-SS REST connection test for DEV:COM:1234 → DEV:COM:1234:MANAGEMENT reports non-OK with a server.clientproxy-family error code")
    void otherSecurityServerConnectionTestCanBeRun(SsBaselineSeeder seeder) {
        var diag = new DiagnosticsAdminClient(seeder.newSession());

        var status = when("other-SS REST connection test is run for the MANAGEMENT subsystem", () ->
                diag.getOtherSecurityServerStatus(
                                "REST",
                                SS_OWNER_ID,
                                MGMT_SUBSYSTEM_ID,
                                MGMT_SECURITY_SERVER_ID)
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getMap("$"));

        then("the connection test reports a non-OK status_class", () ->
                assertThat(status.get("status_class"))
                        .as("other-SS connection test must fail on a single-SS stack")
                        .isNotEqualTo("OK"));

        and("the error code starts with 'server.clientproxy'", () -> {
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) status.get("error");
            assertThat(error).as("error field must be present").isNotNull();
            var code = (String) error.get("code");
            assertThat(code)
                    .as("error code must contain " + EXPECTED_ERROR_CODE)
                    .contains(EXPECTED_ERROR_CODE);
        });
    }

    // MIGRATED-FROM: 0920-ss-diagnostics-connection-testing.feature :: "Management Security Server test fails"
    @Test
    @DisplayName("Management-SS SOAP connection test for DEV:COM:1234 → DEV:COM:1234:MANAGEMENT reports non-OK with a server.clientproxy-family error code")
    void managementSecurityServerTestFails(SsBaselineSeeder seeder) {
        var diag = new DiagnosticsAdminClient(seeder.newSession());

        var status = when("management-SS SOAP connection test is run", () ->
                diag.getOtherSecurityServerStatus(
                                "SOAP",
                                SS_OWNER_ID,
                                MGMT_SUBSYSTEM_ID,
                                MGMT_SECURITY_SERVER_ID)
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getMap("$"));

        then("the connection test reports a non-OK status_class", () ->
                assertThat(status.get("status_class"))
                        .as("management-SS connection test must fail on a single-SS stack")
                        .isNotEqualTo("OK"));

        and("the error code starts with 'server.clientproxy'", () -> {
            @SuppressWarnings("unchecked")
            var error = (Map<String, Object>) status.get("error");
            assertThat(error).as("error field must be present").isNotNull();
            var code = (String) error.get("code");
            assertThat(code)
                    .as("error code must contain " + EXPECTED_ERROR_CODE)
                    .contains(EXPECTED_ERROR_CODE);
        });
    }
}
