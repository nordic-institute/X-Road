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
package org.niis.xroad.cs.test.api.backups;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.BackupsAdminClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Central Server backup management: create, upload, download, delete,
 * filename validation, not-found, RBAC-forbidden cases, and restore-from-backup (destructive).
 */
@DisplayName("Backups — lifecycle, filename validation, RBAC")
@SuppressWarnings("checkstyle:magicnumber")
class BackupsApiTest extends CsApiTest {

    @Test
    @ResourceLock("backups")
    @DisplayName("Backup created via API is present in list; creating returns 201 with filename")
    void backupCanBeCreated(CsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newSession());

        final var filename = given("a new backup is created", () ->
                backups.createBackup()
                        .statusCode(201)
                        .body("compatible", equalTo(true))
                        .body("filename", notNullValue())
                        .extract()
                        .jsonPath()
                        .getString("filename"));

        try {
            then("the created backup is present in GET /backups by filename", () ->
                    assertThat(backups.backupExists(filename)).isTrue());
        } finally {
            if (backups.backupExists(filename)) {
                backups.deleteBackup(filename);
            }
        }
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("test_backup.gpg uploaded via API is present in list")
    void backupCanBeUploaded(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var fileBytes = loadFile("files/backups/test_backup.gpg");

        given("test_backup.gpg is uploaded", () ->
                backups.uploadBackup("test_backup.gpg", fileBytes, false)
                        .statusCode(201)
                        .body("filename", equalTo("test_backup.gpg")));

        try {
            then("test_backup.gpg is present in GET /backups", () ->
                    assertThat(backups.backupExists("test_backup.gpg")).isTrue());
        } finally {
            if (backups.backupExists("test_backup.gpg")) {
                backups.deleteBackup("test_backup.gpg");
            }
        }
    }

    @Test
    @DisplayName("Uploading invalid filenames returns 400 invalid_filename; files do not appear in list")
    void backupNameValidatedBeforeUpload(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var incorrectBackupBytes = loadFile("files/backups/incorrect.backup");
        var dotIncorrectGpgBytes = loadFile("files/backups/.incorrect.gpg");

        when("incorrect.backup (missing .gpg extension) is uploaded", () ->
                backups.uploadBackup("incorrect.backup", incorrectBackupBytes, false)
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_filename")));

        then("incorrect.backup is absent from the list", () ->
                assertThat(backups.backupExists("incorrect.backup")).isFalse());

        when(".incorrect.gpg (double extension / dot prefix) is uploaded", () ->
                backups.uploadBackup(".incorrect.gpg", dotIncorrectGpgBytes, false)
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_filename")));

        then(".incorrect.gpg is absent from the list", () ->
                assertThat(backups.backupExists(".incorrect.gpg")).isFalse());
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Uploaded backup can be downloaded; download returns 200 with non-empty bytes")
    void backupCanBeDownloaded(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var fileBytes = loadFile("files/backups/test_backup.gpg");

        given("test_backup.gpg is uploaded", () ->
                backups.uploadBackup("test_backup.gpg", fileBytes, false)
                        .statusCode(201));

        try {
            final var downloadedBytes = when("test_backup.gpg is downloaded", () ->
                    backups.downloadBackup("test_backup.gpg"));

            then("downloaded bytes are non-empty", () ->
                    assertThat(downloadedBytes).isNotEmpty());
        } finally {
            if (backups.backupExists("test_backup.gpg")) {
                backups.deleteBackup("test_backup.gpg");
            }
        }
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Uploaded backup can be deleted; deleted backup is absent from list")
    void backupCanBeDeleted(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var fileBytes = loadFile("files/backups/test_backup.gpg");

        given("test_backup.gpg is uploaded", () ->
                backups.uploadBackup("test_backup.gpg", fileBytes, false)
                        .statusCode(201));

        try {
            when("test_backup.gpg is deleted", () ->
                    backups.deleteBackup("test_backup.gpg")
                            .statusCode(204));

            then("test_backup.gpg is absent from GET /backups", () ->
                    assertThat(backups.backupExists("test_backup.gpg")).isFalse());
        } finally {
            if (backups.backupExists("test_backup.gpg")) {
                backups.deleteBackup("test_backup.gpg");
            }
        }
    }

    @Test
    @DisplayName("Downloading a non-existent backup returns 404 backup_file_not_found")
    void backupNotFoundForDownload(CsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newSession());

        when("non-existent backup is downloaded", () ->
                backups.downloadBackupRaw("doesnt-exist-test-backup.gpg")
                        .statusCode(404)
                        .body("error.code", equalTo("backup_file_not_found")));
    }

    @Test
    @DisplayName("Backup upload returns 403 for REGISTRATION_OFFICER")
    void backupUploadForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newRegistrationOfficerSession());
        var fileBytes = loadFile("files/backups/test_backup.gpg");

        when("upload is attempted as REGISTRATION_OFFICER", () ->
                backups.uploadBackup("test_backup.gpg", fileBytes, false)
                        .statusCode(403));
    }

    @Test
    @DisplayName("Backup download returns 403 for REGISTRATION_OFFICER")
    void backupDownloadForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newRegistrationOfficerSession());

        when("download is attempted as REGISTRATION_OFFICER", () ->
                backups.downloadBackupRaw("test_backup.gpg")
                        .statusCode(403));
    }

    @Test
    @DisplayName("Backup listing returns 403 for REGISTRATION_OFFICER")
    void backupListingForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var session = seeder.newRegistrationOfficerSession();

        when("GET /backups is attempted as REGISTRATION_OFFICER", () ->
                session.given()
                        .get("/backups")
                        .then()
                        .statusCode(403));
    }

    @Test
    @DisplayName("Backup creation returns 403 for REGISTRATION_OFFICER")
    void backupCreationForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newRegistrationOfficerSession());

        when("POST /backups is attempted as REGISTRATION_OFFICER", () ->
                backups.createBackup()
                        .statusCode(403));
    }

    @Test
    @DisplayName("Backup deletion returns 403 for REGISTRATION_OFFICER")
    void backupDeletionForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newRegistrationOfficerSession());

        when("DELETE /backups/{filename} is attempted as REGISTRATION_OFFICER", () ->
                backups.deleteBackup("test_backup.gpg")
                        .statusCode(403));
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Re-uploading an existing backup with ignoreWarnings=true overwrites in place; list stays at 1 entry")
    void existingBackupOverwrittenOnUpload(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var fileBytes = loadFile("files/backups/test_backup.gpg");

        given("test_backup.gpg is uploaded for the first time", () ->
                backups.uploadBackup("test_backup.gpg", fileBytes, false)
                        .statusCode(201)
                        .body("filename", equalTo("test_backup.gpg")));

        try {
            when("test_backup.gpg is re-uploaded with ignoreWarnings=false", () ->
                    backups.uploadBackup("test_backup.gpg", fileBytes, false)
                            .statusCode(400)
                            .body("warnings[0].code", equalTo("warning_file_already_exists")));

            then("re-upload with ignoreWarnings=true overwrites and returns 201", () ->
                    backups.uploadBackup("test_backup.gpg", fileBytes, true)
                            .statusCode(201)
                            .body("filename", equalTo("test_backup.gpg")));

            then("exactly one entry with that filename exists in GET /backups", () -> {
                var count = backups.listBackupsRaw().stream()
                        .filter(b -> "test_backup.gpg".equals(b.get("filename")))
                        .count();
                assertThat(count).isEqualTo(1L);
            });
        } finally {
            if (backups.backupExists("test_backup.gpg")) {
                backups.deleteBackup("test_backup.gpg");
            }
        }
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Backup uploaded with incompatible format is marked incompatible in response and in list")
    void uploadedIncompatibleBackupIsMarkedIncompatible(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var fileBytes = loadFile("files/backups/cs-backup-incompatible.gpg");

        given("cs-backup-incompatible.gpg is uploaded", () ->
                backups.uploadBackup("cs-backup-incompatible.gpg", fileBytes, false)
                        .statusCode(201)
                        .body("compatible", equalTo(false)));

        try {
            then("the uploaded backup is shown as incompatible in GET /backups", () -> {
                var compatible = backups.listBackupsRaw().stream()
                        .filter(b -> "cs-backup-incompatible.gpg".equals(b.get("filename")))
                        .map(b -> b.get("compatible"))
                        .findFirst()
                        .orElse(null);
                assertThat(compatible).isEqualTo(false);
            });
        } finally {
            if (backups.backupExists("cs-backup-incompatible.gpg")) {
                backups.deleteBackup("cs-backup-incompatible.gpg");
            }
        }
    }

    @Test
    @Tag("destructive")
    @DisplayName("Central server can be restored from backup; restore returns 200 with hsm_tokens_logged_out")
    void restoreCentralServerConfigFromBackup(CsBaselineSeeder seeder) throws IOException {
        var backups = new BackupsAdminClient(seeder.newSession());
        var fileBytes = loadFile("files/backups/test_backup.gpg");

        given("test_backup.gpg is uploaded", () ->
                backups.uploadBackup("test_backup.gpg", fileBytes, true)
                        .statusCode(201));

        seeder.mockExpectation(GET_TOKENS_WITH_HARDWARE_JSON);

        try {
            then("PUT /backups/test_backup.gpg/restore returns 200 with hsm_tokens_logged_out=true", () ->
                    backups.restoreBackup("test_backup.gpg")
                            .statusCode(200)
                            .body("hsm_tokens_logged_out", equalTo(true)));
        } finally {
            seeder.clearMockExpectations("/getTokens");
            if (backups.backupExists("test_backup.gpg")) {
                backups.deleteBackup("test_backup.gpg");
            }
        }
    }

    private static final String GET_TOKENS_WITH_HARDWARE_JSON = """
            {
              "httpRequest": {"method": "GET", "path": "/getTokens"},
              "httpResponse": {
                "statusCode": 200,
                "headers": {"Content-Type": ["application/json"]},
                "body": {
                  "type": "JSON",
                  "json": [
                    {"id":"0","active":true,"type":"SOFTWARE","friendlyName":"softToken",
                     "readOnly":false,"available":true,"serialNumber":"0","label":"","status":"OK"},
                    {"id":"1","active":true,"type":"HARDWARE","friendlyName":"hwToken",
                     "readOnly":false,"available":true,"serialNumber":"1","label":"","status":"OK"}
                  ]
                }
              }
            }
            """;

    private static byte[] loadFile(String resourcePath) throws IOException {
        try (var stream = ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return stream.readAllBytes();
        }
    }
}
