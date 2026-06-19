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

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.DiagnosticsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Security Server diagnostics overview: globalconf, timestamping, OCSP, addon status,
 * backup encryption, message log encryption, proxy memory usage, diagnostics report download.
 */
@DisplayName("Diagnostics overview — status checks, encryption, report download")
@SuppressWarnings("checkstyle:magicnumber")
class DiagnosticsOverviewTest extends SsApiTest {

    private static final String[] EXPECTED_REPORT_ITEMS = {
            "X-Road and Java version",
            "JAVA Processes",
            "Installed X-Road packages",
            "Timestamping",
            "Runs in container",
            "OS version",
            "OCSP responders",
            "Global configuration",
            "Configuration overrides from local.yaml",
            "Authentication certificates",
            "Maintenance mode"
    };

    @Test
    @ResourceLock("timestamping")
    @DisplayName("All diagnostic status endpoints report OK or expected non-error values")
    void diagnosticsChecksAreSuccessful(SsBaselineSeeder seeder) {
        var diag = new DiagnosticsAdminClient(seeder.newSession());

        given("the Security Server is initialized and the diagnostic endpoints are reachable", () -> {
            then("globalconf status_class is OK", () ->
                    diag.getGlobalConf()
                            .statusCode(200)
                            .body("status_class", equalTo("OK")));

            and("no configured timestamping service reports a FAIL status", () -> {
                var tsa = diag.getTimestampingServicesRaw();
                var failed = tsa.stream()
                        .map(t -> (String) t.get("status_class"))
                        .filter("FAIL"::equals)
                        .toList();
                assertThat(failed)
                        .as("no timestamping service should be in FAIL state on the warm baseline")
                        .isEmpty();
            });

            and("no OCSP responder reports status_class FAIL", () -> {
                var ocsp = diag.getOcspRespondersRaw();
                for (var ca : ocsp) {
                    @SuppressWarnings("unchecked")
                    var responders = (List<Map<String, Object>>) ca.get("ocsp_responders");
                    if (responders != null) {
                        for (var r : responders) {
                            assertThat(r.get("status_class"))
                                    .as("OCSP responder %s status_class", r.get("url"))
                                    .isNotEqualTo("FAIL");
                        }
                    }
                }
            });

            and("proxy memory usage is OK (is_used_over_threshold is false)", () -> {
                var mem = diag.getProxyMemoryUsageRaw();
                assertThat(mem.get("is_used_over_threshold"))
                        .as("proxy memory usage should not exceed threshold")
                        .isEqualTo(false);
                assertThat(mem.get("max_memory")).as("max_memory is present").isNotNull();
            });

            and("addon-status reports messagelog enabled", () -> {
                var addon = diag.getAddonStatusRaw();
                assertThat(addon.get("messagelog_enabled")).isEqualTo(true);
            });

            and("backup encryption is enabled and has 3 configured keys", () -> {
                var backup = diag.getBackupEncryptionStatusRaw();
                assertThat(backup.get("backup_encryption_status")).isEqualTo(true);
                @SuppressWarnings("unchecked")
                var keys = (List<String>) backup.get("backup_encryption_keys");
                assertThat(keys).hasSize(3);
            });

            and("Java version is supported (using_supported_java_version is true)", () -> {
                var version = diag.getVersionInfoRaw();
                assertThat(version.get("using_supported_java_version"))
                        .as("Java version must be in the supported range")
                        .isEqualTo(true);
            });

            and("mail notification config is present (host configured in local.yaml)", () -> {
                var mail = diag.getMailNotificationStatusRaw();
                assertThat(mail.get("configuration_present"))
                        .as("mail notification must be configured on the test stack")
                        .isEqualTo(true);
            });
        });
    }

    @Test
    @DisplayName("Message log archive and database encryption are enabled with grouping NONE")
    void messageLogEncryptionIsEnabled(SsBaselineSeeder seeder) {
        var diag = new DiagnosticsAdminClient(seeder.newSession());

        var status = when("message log encryption status is retrieved", () ->
                diag.getMessageLogEncryptionStatusRaw());

        then("archive encryption is enabled", () ->
                assertThat(status.get("message_log_archive_encryption_status")).isEqualTo(true));

        and("database encryption is enabled", () ->
                assertThat(status.get("message_log_database_encryption_status")).isEqualTo(true));

        and("grouping rule is NONE", () ->
                assertThat(status.get("message_log_grouping_rule")).isEqualTo("NONE"));
    }

    @Test
    @DisplayName("Diagnostics report download returns 200 with a JSON array containing the expected section names (API slice)")
    void administratorCanDownloadDiagnosticsReport(SsBaselineSeeder seeder) {
        var diag = new DiagnosticsAdminClient(seeder.newSession());

        var report = when("the diagnostics report is downloaded via GET /diagnostics/info/download", () ->
                diag.downloadDiagnosticsReport()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .<Map<String, Object>>getList("$"));

        then("the report is a non-empty JSON array", () ->
                assertThat(report).isNotEmpty());

        and("the report contains all expected section names", () -> {
            var names = report.stream()
                    .map(item -> (String) item.get("name"))
                    .toList();
            assertThat(names).contains(EXPECTED_REPORT_ITEMS);
        });

        and("every section has a non-null value", () -> {
            for (var item : report) {
                var name = (String) item.get("name");
                assertThat(item.get("value"))
                        .as("section '%s' must have a non-null value", name)
                        .isNotNull();
            }
        });
    }

    @Test
    @DisplayName("Sending a test mail via PUT /mail/send-test-mail returns status 200 with result 'success'")
    void sendingTestMailIsSuccess(SsBaselineSeeder seeder) {
        var session = seeder.newSession();

        var result = when("a test mail is sent to the configured recipient", () ->
                session.given()
                        .contentType(ContentType.JSON)
                        .body("{\"mail_address\":\"test@example.org\"}")
                        .put("/mail/send-test-mail")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getString("status"));

        then("the response status field is 'success'", () ->
                assertThat(result).isEqualTo("success"));
    }

}
