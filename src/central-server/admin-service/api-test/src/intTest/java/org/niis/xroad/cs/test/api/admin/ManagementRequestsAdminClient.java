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

import java.util.Map;

/**
 * RestAssured client for the {@code /management-requests} admin API resource.
 */
public class ManagementRequestsAdminClient {

    private final AdminApiSession session;

    public ManagementRequestsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Adds a management request (POST /management-requests).
     */
    public ValidatableResponse addManagementRequest(Object body) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/management-requests")
                .then();
    }

    /**
     * Approves a management request (POST /management-requests/{id}/approval).
     */
    public ValidatableResponse approveRequest(int id) {
        return session.given()
                .post("/management-requests/{id}/approval", id)
                .then();
    }

    /**
     * Revokes/declines a management request (DELETE /management-requests/{id}).
     */
    public ValidatableResponse revokeRequest(int id) {
        return session.given()
                .delete("/management-requests/{id}", id)
                .then();
    }

    /**
     * Gets management request details (GET /management-requests/{id}).
     */
    public ValidatableResponse getRequest(int id) {
        return session.given()
                .get("/management-requests/{id}", id)
                .then();
    }

    /**
     * Finds management requests with filter parameters (GET /management-requests).
     */
    public ValidatableResponse findRequests(Map<String, Object> params) {
        return session.given()
                .queryParams(params)
                .get("/management-requests")
                .then();
    }
}
