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
package org.niis.xroad.cs.test.api.admin;

import io.restassured.response.ValidatableResponse;

/**
 * RestAssured client for the {@code /trusted-anchors} admin API resource.
 */
public class TrustedAnchorsAdminClient {

    private final AdminApiSession session;

    public TrustedAnchorsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Previews a trusted anchor upload without persisting it.
     */
    public ValidatableResponse previewTrustedAnchor(String filename, byte[] fileBytes) {
        return session.given()
                .multiPart("anchor", filename, fileBytes)
                .post("/trusted-anchors/preview")
                .then();
    }

    /**
     * Uploads and persists a trusted anchor.
     */
    public ValidatableResponse uploadTrustedAnchor(String filename, byte[] fileBytes) {
        return session.given()
                .multiPart("anchor", filename, fileBytes)
                .post("/trusted-anchors")
                .then();
    }

    /**
     * Lists all trusted anchors.
     */
    public ValidatableResponse getTrustedAnchors() {
        return session.given()
                .get("/trusted-anchors")
                .then();
    }

    /**
     * Deletes the trusted anchor with the given hash.
     */
    public ValidatableResponse deleteTrustedAnchor(String hash) {
        return session.given()
                .delete("/trusted-anchors/{hash}", hash)
                .then();
    }

    /**
     * Downloads the trusted anchor with the given hash as binary.
     */
    public ValidatableResponse downloadTrustedAnchor(String hash) {
        return session.given()
                .get("/trusted-anchors/{hash}/download", hash)
                .then();
    }
}
