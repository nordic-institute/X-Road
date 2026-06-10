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

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the API key management endpoints.
 *
 * <p>These endpoints live under a separate {@code SecurityFilterChain} that uses HTTP Basic
 * authentication with {@code SessionCreationPolicy.NEVER}. The standard form-login session from
 * {@link AdminApiSession} is not accepted here. This client always uses preemptive HTTP Basic auth
 * and never attaches a session cookie or XSRF header.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class ApiKeysAdminClient {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";

    private final String baseUrl;

    public ApiKeysAdminClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a new API key with the given roles.
     * Returns the full response for further assertion or value extraction.
     */
    public ValidatableResponse createKey(List<String> roles) {
        return spec()
                .contentType(ContentType.JSON)
                .body(roles)
                .post("/api-keys")
                .then();
    }

    /**
     * Updates an existing API key to the given role set.
     */
    public ValidatableResponse updateKey(long id, List<String> roles) {
        return spec()
                .contentType(ContentType.JSON)
                .body(roles)
                .put("/api-keys/{id}", id)
                .then();
    }

    /**
     * Deletes (revokes) an API key by ID.
     */
    public ValidatableResponse deleteKey(long id) {
        return spec()
                .delete("/api-keys/{id}", id)
                .then();
    }

    /**
     * Lists all API keys.
     */
    public ValidatableResponse listKeys() {
        return spec()
                .get("/api-keys")
                .then();
    }

    /**
     * Gets a single API key by ID.
     */
    public ValidatableResponse getKey(long id) {
        return spec()
                .get("/api-keys/{id}", id)
                .then();
    }

    /**
     * Extracts all role lists from a GET /api-keys list response as a map of key id → roles.
     */
    public List<Map<String, Object>> listKeysRaw() {
        return listKeys()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    private RequestSpecification spec() {
        return RestAssuredFactory.given()
                .auth().preemptive().basic(ADMIN_USERNAME, ADMIN_PASSWORD)
                .baseUri(baseUrl)
                .basePath("/api/v1");
    }
}
