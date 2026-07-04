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

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the {@code /api-keys} admin API resource.
 *
 * <p>The endpoint is served under a dedicated {@code SecurityFilterChain} that accepts HTTP Basic
 * authentication only (PAM users). The standard API-key session from {@link AdminApiSession} is not
 * accepted here. This client always authenticates via preemptive HTTP Basic auth and never attaches
 * a session cookie.
 *
 * <p>Two pre-configured factory methods cover the two test users present in the CS Docker image:
 * <ul>
 *   <li>{@link #fullPrivilegeClient(String)} — {@code xrd} user, member of
 *       {@code xroad-security-officer}, {@code xroad-registration-officer}, and
 *       {@code xroad-system-administrator} groups.</li>
 *   <li>{@link #systemAdminOnlyClient(String)} — {@code xrd-sys} user, member of
 *       {@code xroad-system-administrator} only.</li>
 * </ul>
 */
public class ApiKeysAdminClient {

    static final String FULL_PRIVILEGE_USERNAME = "xrd";
    static final String SYSTEM_ADMIN_ONLY_USERNAME = "xrd-sys";
    private static final String TEST_PASSWORD = "secret123!";

    private final String baseUrl;
    private final String username;
    private final String password;

    /**
     * Creates a client authenticated as the given user via HTTP Basic auth.
     */
    public ApiKeysAdminClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Returns a client authenticated as the full-privilege {@code xrd} user (all CS roles).
     */
    public static ApiKeysAdminClient fullPrivilegeClient(String baseUrl) {
        return new ApiKeysAdminClient(baseUrl, FULL_PRIVILEGE_USERNAME, TEST_PASSWORD);
    }

    /**
     * Returns a client authenticated as the {@code xrd-sys} user (SYSTEM_ADMINISTRATOR only).
     */
    public static ApiKeysAdminClient systemAdminOnlyClient(String baseUrl) {
        return new ApiKeysAdminClient(baseUrl, SYSTEM_ADMIN_ONLY_USERNAME, TEST_PASSWORD);
    }

    /**
     * Creates a new API key with the given roles.
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
     * Revokes (deletes) an API key by ID.
     */
    public ValidatableResponse revokeKey(long id) {
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
     * Returns the raw list of key maps from GET /api-keys.
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
                .auth().preemptive().basic(username, password)
                .baseUri(baseUrl)
                .basePath("/api/v1");
    }
}
