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
 * RestAssured client for the {@code /configuration-sources} admin API resource.
 */
public class ConfigurationSourcesAdminClient {

    private final AdminApiSession session;

    public ConfigurationSourcesAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Lists configuration parts for the given configuration type (internal or external).
     */
    public ValidatableResponse getConfigurationParts(String configurationType) {
        return session.given()
                .get("/configuration-sources/{type}/configuration-parts", configurationType)
                .then();
    }

    /**
     * Uploads an optional configuration part file with the given content identifier.
     */
    public ValidatableResponse uploadConfigurationParts(String configurationType, String contentIdentifier, byte[] fileBytes) {
        return session.given()
                .multiPart("file", "file.xml", fileBytes)
                .multiPart("content_identifier", contentIdentifier)
                .post("/configuration-sources/{type}/configuration-parts", configurationType)
                .then();
    }

    /**
     * Gets the global download URL for the given configuration type.
     */
    public ValidatableResponse getDownloadUrl(String configurationType) {
        return session.given()
                .get("/configuration-sources/{type}/download-url", configurationType)
                .then();
    }

    /**
     * Downloads the configuration anchor for the given configuration type as binary.
     */
    public ValidatableResponse downloadAnchor(String configurationType) {
        return session.given()
                .get("/configuration-sources/{type}/anchor/download", configurationType)
                .then();
    }

    /**
     * Downloads a specific configuration part as binary.
     */
    public ValidatableResponse downloadConfigurationPart(String configurationType, String contentIdentifier, int version) {
        return session.given()
                .get("/configuration-sources/{type}/configuration-parts/{contentId}/{version}/download",
                        configurationType, contentIdentifier, version)
                .then();
    }
}
