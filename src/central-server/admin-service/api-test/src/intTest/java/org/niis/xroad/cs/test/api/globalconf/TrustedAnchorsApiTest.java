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
package org.niis.xroad.cs.test.api.globalconf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.TrustedAnchorsAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings("checkstyle:magicnumber")
class TrustedAnchorsApiTest extends CsApiTest {

    private static final String HASH_ANCHOR_1 = "40:2A:4F:94:05:D2:9B:ED:C9:EE:A2:6D:EC:EC:11:94:5D:C9:A8:3E:29:1F:B2:92:A6:E4:DF:1D";
    private static final String HASH_ANCHOR_2 = "95:6C:C8:A5:9B:B5:51:5A:FB:9F:9C:84:38:C0:62:6B:93:48:AE:D7:54:44:16:0C:83:28:59:54";

    @Test
    @ResourceLock("trusted-anchors")
    void userUploadsTrustedAnchorFileForPreview(CsBaselineSeeder seeder) throws IOException {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new TrustedAnchorsAdminClient(session);

        var anchorCountBefore = Step.given("anchor count captured before preview", () ->
                client.getTrustedAnchors()
                        .statusCode(200)
                        .extract().jsonPath().getList("$").size());

        Step.when("valid anchor is previewed", () ->
                client.previewTrustedAnchor("trusted-anchor.xml", loadFile("files/trusted-anchor/trusted-anchor.xml"))
                        .statusCode(200)
                        .body("instance_identifier", equalTo("CS0"))
                        .body("hash", equalTo(HASH_ANCHOR_1))
                        .body("generated_at", notNullValue()));

        Step.and("invalid anchor 1 returns malformed_anchor", () ->
                client.previewTrustedAnchor("trusted-anchor-invalid-1.xml",
                                loadFile("files/trusted-anchor/trusted-anchor-invalid-1.xml"))
                        .statusCode(400)
                        .body("error.code", equalTo("malformed_anchor")));

        Step.and("invalid anchor 2 returns malformed_anchor", () ->
                client.previewTrustedAnchor("trusted-anchor-invalid-2.xml",
                                loadFile("files/trusted-anchor/trusted-anchor-invalid-2.xml"))
                        .statusCode(400)
                        .body("error.code", equalTo("malformed_anchor")));

        Step.and("script.sh with .sh extension returns invalid_file_extension", () ->
                client.previewTrustedAnchor("script.sh", loadFile("files/attack/script.sh"))
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_file_extension")));

        Step.and("script.sh with double extension returns double_file_extension", () ->
                client.previewTrustedAnchor("script.sh.xml", loadFile("files/attack/script.sh"))
                        .statusCode(400)
                        .body("error.code", equalTo("double_file_extension")));

        Step.and("script.sh named as xml returns invalid_file_content_type", () ->
                client.previewTrustedAnchor("trusted-anchor.xml", loadFile("files/attack/script.sh"))
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_file_content_type")));

        Step.then("anchor count is unchanged after preview — preview does not persist", () ->
                client.getTrustedAnchors()
                        .statusCode(200)
                        .body("$", hasSize(anchorCountBefore)));
    }

    @Test
    @ResourceLock("trusted-anchors")
    void uploadingTrustedAnchorFile(CsBaselineSeeder seeder) throws IOException {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new TrustedAnchorsAdminClient(session);

        var hash = Step.when("trusted anchor is uploaded", () ->
                client.uploadTrustedAnchor("trusted-anchor.xml", loadFile("files/trusted-anchor/trusted-anchor.xml"))
                        .statusCode(201)
                        .body("instance_identifier", equalTo("CS0"))
                        .body("hash", equalTo(HASH_ANCHOR_1))
                        .body("generated_at", notNullValue())
                        .extract().jsonPath().getString("hash"));

        Step.then("uploaded anchor can be downloaded", () -> {
            var bytes = client.downloadTrustedAnchor(hash)
                    .statusCode(200)
                    .header("Content-Disposition", notNullValue())
                    .extract().asByteArray();
            assert bytes.length > 0;
        });
    }

    @Test
    @ResourceLock("trusted-anchors")
    void getTrustedAnchorsList(CsBaselineSeeder seeder) throws IOException {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new TrustedAnchorsAdminClient(session);
        var anchor1Bytes = loadFile("files/trusted-anchor/trusted-anchor.xml");
        var anchor2Bytes = loadFile("files/trusted-anchor/trusted-anchor-2.xml");

        Step.given("anchor 1 is uploaded", () ->
                client.uploadTrustedAnchor("trusted-anchor.xml", anchor1Bytes)
                        .statusCode(201));

        Step.and("anchor 2 is uploaded twice (idempotent)", () -> {
            client.uploadTrustedAnchor("trusted-anchor-2.xml", anchor2Bytes).statusCode(201);
            client.uploadTrustedAnchor("trusted-anchor-2.xml", anchor2Bytes).statusCode(201);
        });

        Step.when("trusted anchors list is retrieved", () ->
                client.getTrustedAnchors()
                        .statusCode(200)
                        .body("hash", hasItem(HASH_ANCHOR_1))
                        .body("hash", hasItem(HASH_ANCHOR_2)));

        Step.and("anchor 1 is deleted", () ->
                client.deleteTrustedAnchor(HASH_ANCHOR_1)
                        .statusCode(204));

        Step.then("anchor 1 is gone but anchor 2 remains", () ->
                client.getTrustedAnchors()
                        .statusCode(200)
                        .body("hash", org.hamcrest.Matchers.not(hasItem(HASH_ANCHOR_1)))
                        .body("hash", hasItem(HASH_ANCHOR_2)));
    }

    @Test
    void deletingNonExistingTrustedAnchor(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new TrustedAnchorsAdminClient(session);

        Step.when("non-existing anchor hash is deleted", () ->
                client.deleteTrustedAnchor("non:existing")
                        .statusCode(404)
                        .body("error.code", equalTo("trusted_anchor_not_found")));
    }

    @Test
    void uploadForbiddenForSystemAdministrator(CsBaselineSeeder seeder) throws IOException {
        var session = Step.given("system administrator only session opened", seeder::newSystemAdministratorOnlySession);
        var client = new TrustedAnchorsAdminClient(session);
        var dummyBytes = loadFile("files/trusted-anchor/trusted-anchor.xml");

        Step.when("preview is attempted", () ->
                client.previewTrustedAnchor("trusted-anchor.xml", dummyBytes)
                        .statusCode(403));

        Step.and("upload is attempted", () ->
                client.uploadTrustedAnchor("trusted-anchor.xml", dummyBytes)
                        .statusCode(403));

        Step.and("delete is attempted", () ->
                client.deleteTrustedAnchor("any")
                        .statusCode(403));
    }

    @Test
    void uploadForbiddenForRegistrationOfficer(CsBaselineSeeder seeder) throws IOException {
        var session = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var client = new TrustedAnchorsAdminClient(session);
        var dummyBytes = loadFile("files/trusted-anchor/trusted-anchor.xml");

        Step.when("preview is attempted", () ->
                client.previewTrustedAnchor("trusted-anchor.xml", dummyBytes)
                        .statusCode(403));

        Step.and("upload is attempted", () ->
                client.uploadTrustedAnchor("trusted-anchor.xml", dummyBytes)
                        .statusCode(403));

        Step.and("list is attempted", () ->
                client.getTrustedAnchors()
                        .statusCode(403));

        Step.and("delete is attempted", () ->
                client.deleteTrustedAnchor("any")
                        .statusCode(403));
    }

    @Test
    void downloadForbiddenForRegistrationOfficer(CsBaselineSeeder seeder) {
        var session = Step.given("registration officer session opened", seeder::newRegistrationOfficerSession);
        var client = new TrustedAnchorsAdminClient(session);

        Step.when("download of any anchor hash is attempted", () ->
                client.downloadTrustedAnchor(HASH_ANCHOR_2)
                        .statusCode(403));
    }

    private static byte[] loadFile(String resourcePath) throws IOException {
        try (var stream = ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return stream.readAllBytes();
        }
    }
}
