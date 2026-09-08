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
package org.niis.xroad.ss.test.api.admin;

import io.restassured.response.ValidatableResponse;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the Security Server backup admin API resources:
 * list, create, delete, download, upload, and restore.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class BackupsAdminClient {

    private final AdminApiSession session;

    public BackupsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Returns all backups as raw maps (avoids OffsetDateTime deserialization issues).
     * Each map contains at least {@code filename} and {@code created_at} fields.
     */
    public List<Map<String, Object>> listBackupsRaw() {
        return session.given()
                .get("/backups")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Creates a new backup and returns the response for status assertion and filename extraction.
     * On success the server returns 201 with a {@code filename} field in the JSON body.
     */
    public ValidatableResponse createBackup() {
        return session.given()
                .post("/backups")
                .then();
    }

    /**
     * Deletes the backup identified by {@code filename}.
     */
    public ValidatableResponse deleteBackup(String filename) {
        return session.given()
                .delete("/backups/{filename}", filename)
                .then();
    }

    /**
     * Downloads the backup identified by {@code filename} as raw bytes.
     */
    public byte[] downloadBackup(String filename) {
        return session.given()
                .get("/backups/{filename}/download", filename)
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    /**
     * Uploads a backup archive. Pass {@code ignoreWarnings=true} to overwrite an existing
     * file with the same name; {@code false} causes a 400 warning when the name collides.
     * Returns the response for status assertion and filename extraction.
     */
    public ValidatableResponse uploadBackup(String filename, byte[] content, boolean ignoreWarnings) {
        return session.given()
                .queryParam("ignore_warnings", ignoreWarnings)
                .multiPart("backup", filename, content, "application/octet-stream")
                .post("/backups/upload")
                .then();
    }

    /**
     * Triggers a restore of the named backup. Returns 200 with a {@code TokensLoggedOut} body
     * when the restore script completes successfully.
     */
    public ValidatableResponse restoreBackup(String filename) {
        return session.given()
                .put("/backups/{filename}/restore", filename)
                .then();
    }

    /**
     * Returns {@code true} if the backup list contains an entry with the given filename.
     */
    public boolean backupExists(String filename) {
        return listBackupsRaw().stream()
                .anyMatch(b -> filename.equals(b.get("filename")));
    }
}
