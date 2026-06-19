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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.BackupsAdminClient;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder.SS_OWNER_CLASS;
import static org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder.SS_OWNER_CODE;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Destructive-lane test for backup restore: triggers a full restore from a known-good backup,
 * waits for the Security Server admin UI to restart, and asserts that config added after the backup
 * was created is absent post-restore.
 *
 * <p>Runs last in the destructive phase of the shared phased intTest suite. With the post-restore
 * grant re-apply in place the serverconf DB is left healthy, so subsequent tests in the same stack
 * are not affected.
 */
@Slf4j
@DisplayName("Backup restore — config-revert via API (destructive lane)")
@SuppressWarnings("checkstyle:magicnumber")
class BackupRestoreDestructiveTest extends SsDestructiveTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(120);

    private static final String RESTORE_SUBSYSTEM_CODE = "restore-test-sub";
    private static final String RESTORE_CLIENT_ID =
            "DEV:%s:%s:%s".formatted(SS_OWNER_CLASS, SS_OWNER_CODE, RESTORE_SUBSYSTEM_CODE);

    @Test
    @DisplayName("Configuration restored from backup reverts config changes made after the backup was created")
    void configurationRestoredFromBackupRevertsSubsequentChanges(DestructiveStackSetup stack) {
        var uiMapping = stack.getContainerMapping(DestructiveStackSetup.UI, Port.UI);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        var session = new AdminApiSession(uiBaseUrl);
        var backups = new BackupsAdminClient(session);
        var clients = new ClientsAdminClient(session);

        var filename = given("a backup of the current baseline configuration is created", () ->
                backups.createBackup()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getString("filename"));

        then("the created backup is present in the list", () ->
                assertThat(backups.backupExists(filename)).isTrue());

        and("the restore-test subsystem is absent before the config change", () ->
                assertThat(clients.findClientByIdentifier(RESTORE_CLIENT_ID)).isNull());

        when("the restore-test subsystem is added via API (change that the backup does NOT contain)", () ->
                clients.addClient(new ClientAddDto(
                        new ClientDto(SS_OWNER_CLASS, SS_OWNER_CODE)
                                .subsystemCode(RESTORE_SUBSYSTEM_CODE)
                                .connectionType(ConnectionTypeDto.HTTP))
                        .ignoreWarnings(true))
                        .statusCode(201));

        then("the restore-test subsystem is now present", () ->
                assertThat(clients.findClientByIdentifier(RESTORE_CLIENT_ID)).isNotNull());

        when("the backup is restored via PUT /backups/{filename}/restore", () ->
                backups.restoreBackup(filename).statusCode(200));

        and("the admin service restarts; waiting for the UI login endpoint to become reachable", () ->
                waitForAdminServiceRestart(uiBaseUrl));

        var postRestoreSession = then("a fresh admin session is established after restart", () ->
                new AdminApiSession(uiBaseUrl));

        then("the restore-test subsystem added after the backup is GONE (config reverted)", () -> {
            var postRestoreClients = new ClientsAdminClient(postRestoreSession);
            assertThat(postRestoreClients.findClientByIdentifier(RESTORE_CLIENT_ID)).isNull();
        });
    }

    private void waitForAdminServiceRestart(String uiBaseUrl) {
        log.info("Waiting for admin service to come back up at {}", uiBaseUrl);
        await()
                .pollDelay(Duration.ofSeconds(8))
                .pollInterval(POLL_INTERVAL)
                .atMost(POLL_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var status = RestAssuredFactory.givenSilent()
                            .relaxedHTTPSValidation()
                            .formParam("username", "xrd")
                            .formParam("password", "secret123!")
                            .post(uiBaseUrl + "/login")
                            .statusCode();
                    assertThat(status).isEqualTo(200);
                });
        log.info("Admin service is back up");
    }
}
