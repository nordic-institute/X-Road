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
package org.niis.xroad.ss.test.api.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.BackupsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Security Server backup management: create, delete, download, upload, and overwrite.
 * Restore-from-backup (destructive-lifecycle, restarts the server) is covered in
 * {@link org.niis.xroad.ss.test.api.destructive.BackupRestoreDestructiveTest}.
 */
@DisplayName("Backups — create, delete, download/upload roundtrip, overwrite on upload")
@SuppressWarnings("checkstyle:magicnumber")
class BackupsTest extends SsApiTest {

    @Test
    @ResourceLock("backups")
    @DisplayName("Backup created via API is present in list; deleting it removes it from the list")
    void backupCreatedAndDeleted(SsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newSession());

        final var filename = given("a new backup is created", () ->
                backups.createBackup()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getString("filename"));

        try {
            then("the created backup is present in GET /backups by filename", () ->
                    assertThat(backups.backupExists(filename)).isTrue());

            when("the backup is deleted", () ->
                    backups.deleteBackup(filename).statusCode(204));

            then("the deleted backup is absent from GET /backups", () ->
                    assertThat(backups.backupExists(filename)).isFalse());
        } finally {
            if (backups.backupExists(filename)) {
                backups.deleteBackup(filename);
            }
        }
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Backup downloaded then re-uploaded is present in the list again by its filename")
    void backupDownloadedAndUploaded(SsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newSession());

        final var filename = given("a new backup is created", () ->
                backups.createBackup()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getString("filename"));

        try {
            final var backupBytes = when("the backup is downloaded", () ->
                    backups.downloadBackup(filename));

            then("the downloaded bytes are non-empty", () ->
                    assertThat(backupBytes).isNotEmpty());

            when("the original backup is deleted", () ->
                    backups.deleteBackup(filename).statusCode(204));

            then("the backup is now absent from the list", () ->
                    assertThat(backups.backupExists(filename)).isFalse());

            when("the downloaded bytes are re-uploaded", () ->
                    backups.uploadBackup(filename, backupBytes, false).statusCode(201));

            then("the re-uploaded backup is present in GET /backups by its original filename", () ->
                    assertThat(backups.backupExists(filename)).isTrue());
        } finally {
            if (backups.backupExists(filename)) {
                backups.deleteBackup(filename);
            }
        }
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Uploading a backup with an already-existing filename and ignore_warnings=true succeeds and the backup remains once")
    void backupOverwrittenOnUpload(SsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newSession());

        final var filename = given("a new backup is created", () ->
                backups.createBackup()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getString("filename"));

        try {
            final var backupBytes = when("the backup is downloaded", () ->
                    backups.downloadBackup(filename));

            then("the downloaded bytes are non-empty", () ->
                    assertThat(backupBytes).isNotEmpty());

            when("the same backup is re-uploaded with ignore_warnings=true (overwrite path)", () ->
                    backups.uploadBackup(filename, backupBytes, true).statusCode(201));

            then("the backup is still present in the list exactly once", () -> {
                var matches = backups.listBackupsRaw().stream()
                        .filter(b -> filename.equals(b.get("filename")))
                        .count();
                assertThat(matches).isEqualTo(1);
            });
        } finally {
            if (backups.backupExists(filename)) {
                backups.deleteBackup(filename);
            }
        }
    }

    @Test
    @ResourceLock("backups")
    @DisplayName("Backup created via API is present in the list (API create-slice of filtered-view scenario)")
    void backupCreatableForFilteredView(SsBaselineSeeder seeder) {
        var backups = new BackupsAdminClient(seeder.newSession());

        final var filename = when("a new backup is created", () ->
                backups.createBackup()
                        .statusCode(201)
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
}
