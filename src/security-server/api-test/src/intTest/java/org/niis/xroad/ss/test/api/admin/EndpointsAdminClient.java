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
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdateDto;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for endpoint resources on a service:
 * {@code /services/{id}/endpoints} and {@code /endpoints/{id}}.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class EndpointsAdminClient {

    private final AdminApiSession session;

    public EndpointsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Adds an endpoint to the given service.
     *
     * @param serviceId full service identifier (e.g. {@code DEV:COM:1234:sub:svc})
     * @param request   endpoint to create
     */
    public ValidatableResponse addEndpoint(String serviceId, EndpointDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/services/{id}/endpoints", serviceId)
                .then();
    }

    /**
     * Updates an endpoint's method or path.
     *
     * @param endpointId numeric endpoint identifier returned by the API
     * @param request    updated method and path
     */
    public ValidatableResponse updateEndpoint(String endpointId, EndpointUpdateDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .patch("/endpoints/{id}", endpointId)
                .then();
    }

    /**
     * Deletes an endpoint by its numeric id.
     */
    public ValidatableResponse deleteEndpoint(String endpointId) {
        return session.given()
                .delete("/endpoints/{id}", endpointId)
                .then();
    }

    /**
     * Lists all endpoints for the given service.
     * Endpoints are returned as part of the {@code GET /services/{id}} response.
     */
    public List<EndpointView> listEndpoints(String serviceId) {
        List<Map<String, Object>> raw = session.given()
                .get("/services/{id}", serviceId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("endpoints");
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(EndpointView::from).toList();
    }

    /**
     * Lightweight, read-only view of an endpoint returned by the admin API.
     *
     * @param id      the numeric endpoint identifier
     * @param method  HTTP method (e.g. {@code GET}, {@code PATCH})
     * @param path    relative path (e.g. {@code /new/path/})
     * @param generated whether this endpoint was generated from an OpenAPI description
     */
    public record EndpointView(String id, String method, String path, boolean generated) {

        @SuppressWarnings("unchecked")
        static EndpointView from(Map<String, Object> raw) {
            return new EndpointView(
                    (String) raw.get("id"),
                    (String) raw.get("method"),
                    (String) raw.get("path"),
                    Boolean.TRUE.equals(raw.get("generated"))
            );
        }
    }
}
