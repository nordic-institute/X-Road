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
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the {@code /clients} and {@code /clients/{id}/service-descriptions} admin API resources.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class ClientsAdminClient {

    private final AdminApiSession session;

    public ClientsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Lists all clients on the Security Server.
     */
    public ValidatableResponse listClients() {
        return session.given()
                .get("/clients")
                .then();
    }

    /**
     * Adds a client (subsystem) and returns the response for further assertion or extraction.
     */
    public ValidatableResponse addClient(ClientAddDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/clients")
                .then();
    }

    /**
     * Finds an existing client by its full X-Road identifier, or returns null if not present.
     */
    public ClientDto findClientByIdentifier(String clientId) {
        var clients = session.given()
                .get("/clients")
                .then()
                .statusCode(200)
                .extract()
                .as(ClientDto[].class);
        return Arrays.stream(clients)
                .filter(c -> clientId.equals(c.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Lists service descriptions for the given client as raw maps to avoid Jackson date-time
     * deserialization issues in the test classpath (no JSR-310 module present).
     *
     * <p>Each map contains the raw JSON fields from the API: {@code id}, {@code url}, {@code type},
     * {@code services} (list of maps), etc.
     */
    public List<ServiceDescriptionView> listServiceDescriptions(String clientId) {
        List<Map<String, Object>> raw = session.given()
                .get("/clients/{id}/service-descriptions", clientId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
        return raw.stream()
                .map(ServiceDescriptionView::from)
                .toList();
    }

    /**
     * Sends a registration request for the given client to the Central Server.
     * Returns the response for assertion — expect 204 on success or 400/500 when management request fails
     * (e.g. no valid authentication certificate is available).
     */
    public ValidatableResponse registerClient(String clientId) {
        return session.given()
                .put("/clients/{id}/register", clientId)
                .then();
    }

    /**
     * Adds a service description to the given client.
     */
    public ValidatableResponse addServiceDescription(String clientId, ServiceDescriptionAddDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/clients/{id}/service-descriptions", clientId)
                .then();
    }

    /**
     * Lightweight, read-only view of a service description from the admin API.
     * Extracted from raw JSON to avoid Jackson date-time module requirements in the test tier.
     *
     * @param id       the service description identifier
     * @param url      the service description URL (WSDL URL for WSDL type; base URL for REST/OpenAPI3)
     * @param services the list of services contained in this description
     */
    public record ServiceDescriptionView(String id, String url, List<ServiceView> services) {

        @SuppressWarnings("unchecked")
        static ServiceDescriptionView from(Map<String, Object> raw) {
            var id = (String) raw.get("id");
            var url = (String) raw.get("url");
            var servicesList = (List<Map<String, Object>>) raw.getOrDefault("services", List.of());
            var services = servicesList.stream()
                    .map(ServiceView::from)
                    .toList();
            return new ServiceDescriptionView(id, url, services);
        }
    }

    /**
     * Lightweight, read-only view of a single service entry within a service description.
     *
     * @param serviceCode the service code identifier
     * @param url         the service base URL
     */
    public record ServiceView(String serviceCode, String url) {

        static ServiceView from(Map<String, Object> raw) {
            return new ServiceView(
                    (String) raw.get("service_code"),
                    (String) raw.get("url")
            );
        }
    }
}
