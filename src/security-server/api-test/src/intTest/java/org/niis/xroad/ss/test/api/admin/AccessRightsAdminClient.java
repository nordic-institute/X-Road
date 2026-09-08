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
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRightDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRightsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientsDto;

import java.util.List;

/**
 * RestAssured client for service-level access rights ({@code /services/{id}/service-clients})
 * and client-level service-client access rights ({@code /clients/{id}/service-clients}).
 *
 * <p>Service-perspective: grants or revokes access per service for given subjects.
 * Client-perspective: lists which subjects have access to any of a client's services,
 * and adds or removes specific service codes from a given subject.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class AccessRightsAdminClient {

    private final AdminApiSession session;

    public AccessRightsAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Returns the service clients that have access to the given service.
     */
    public ValidatableResponse listServiceClients(String serviceId) {
        return session.given()
                .get("/services/{id}/service-clients", serviceId)
                .then();
    }

    /**
     * Grants access to the given service for one or more service clients.
     *
     * @param serviceId  full service identifier (e.g. {@code DEV:COM:1234:sub:svc})
     * @param subjectIds X-Road identifiers of the subjects to grant access to
     */
    public ValidatableResponse addServiceClients(String serviceId, String... subjectIds) {
        var body = buildServiceClientsDto(subjectIds);
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/services/{id}/service-clients", serviceId)
                .then();
    }

    /**
     * Revokes access to the given service from the specified service clients.
     *
     * @param serviceId  full service identifier
     * @param subjectIds X-Road identifiers of the subjects to revoke
     */
    public ValidatableResponse removeServiceClients(String serviceId, String... subjectIds) {
        var body = buildServiceClientsDto(subjectIds);
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/services/{id}/service-clients/delete", serviceId)
                .then();
    }

    private ServiceClientsDto buildServiceClientsDto(String[] subjectIds) {
        var dto = new ServiceClientsDto();
        for (var id : subjectIds) {
            dto.addItemsItem(new ServiceClientDto(id));
        }
        return dto;
    }

    /**
     * Convenience: lists service client IDs having access to the given service.
     */
    public List<String> listServiceClientIds(String serviceId) {
        return listServiceClients(serviceId)
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
    }

    // --- Client-perspective service-client access rights ---

    /**
     * Lists all service clients (subjects) that have access to any service owned by the given client.
     *
     * @param clientId full client identifier (e.g. {@code DEV:COM:1234:subsystem})
     */
    public ValidatableResponse listClientServiceClients(String clientId) {
        return session.given()
                .get("/clients/{id}/service-clients", clientId)
                .then();
    }

    /**
     * Convenience: lists subject IDs of all service clients for the given client.
     */
    public List<String> listClientServiceClientIds(String clientId) {
        return listClientServiceClients(clientId)
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
    }

    /**
     * Returns access rights (service codes) granted to a specific service client for a given client's services.
     *
     * @param clientId        full client identifier
     * @param serviceClientId X-Road identifier of the subject
     */
    public ValidatableResponse listClientServiceClientAccessRights(String clientId, String serviceClientId) {
        return session.given()
                .get("/clients/{id}/service-clients/{sc_id}/access-rights", clientId, serviceClientId)
                .then();
    }

    /**
     * Convenience: lists the service codes granted to a service client under the given client.
     */
    public List<String> listClientServiceClientServiceCodes(String clientId, String serviceClientId) {
        return listClientServiceClientAccessRights(clientId, serviceClientId)
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("service_code");
    }

    /**
     * Adds access rights (service codes) to a service client under a given client.
     * Creates the service-client association if it does not yet exist.
     *
     * @param clientId        full client identifier
     * @param serviceClientId X-Road identifier of the subject
     * @param serviceCodes    service codes to grant
     */
    public ValidatableResponse addClientServiceClientAccessRights(String clientId, String serviceClientId,
                                                                  String... serviceCodes) {
        var body = buildAccessRightsDto(serviceCodes);
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/clients/{id}/service-clients/{sc_id}/access-rights", clientId, serviceClientId)
                .then();
    }

    /**
     * Removes access rights (service codes) from a service client under a given client.
     *
     * @param clientId        full client identifier
     * @param serviceClientId X-Road identifier of the subject
     * @param serviceCodes    service codes to revoke
     */
    public ValidatableResponse removeClientServiceClientAccessRights(String clientId, String serviceClientId,
                                                                     String... serviceCodes) {
        var body = buildAccessRightsDto(serviceCodes);
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .post("/clients/{id}/service-clients/{sc_id}/access-rights/delete", clientId, serviceClientId)
                .then();
    }

    private AccessRightsDto buildAccessRightsDto(String[] serviceCodes) {
        var dto = new AccessRightsDto();
        for (var code : serviceCodes) {
            dto.addItemsItem(new AccessRightDto(code));
        }
        return dto;
    }
}
