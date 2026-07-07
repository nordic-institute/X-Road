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
import org.niis.xroad.cs.openapi.model.SecurityServerAddressDto;

import java.util.Map;

/**
 * RestAssured client for the {@code /security-servers} admin API resource.
 */
public class SecurityServersAdminClient {

    private final AdminApiSession session;

    public SecurityServersAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Finds security servers with optional filter and paging parameters (GET /security-servers).
     */
    public ValidatableResponse findSecurityServers(Map<String, Object> params) {
        return session.given()
                .queryParams(params)
                .get("/security-servers")
                .then();
    }

    /**
     * Gets a security server by its encoded identifier (GET /security-servers/{server_id}).
     */
    public ValidatableResponse getSecurityServer(String serverId) {
        return session.given()
                .get("/security-servers/{server_id}", serverId)
                .then();
    }

    /**
     * Updates the address of a security server (PATCH /security-servers/{server_id}).
     */
    public ValidatableResponse updateSecurityServerAddress(String serverId, String newAddress) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new SecurityServerAddressDto().serverAddress(newAddress))
                .patch("/security-servers/{server_id}", serverId)
                .then();
    }

    /**
     * Deletes a security server (DELETE /security-servers/{server_id}).
     */
    public ValidatableResponse deleteSecurityServer(String serverId) {
        return session.given()
                .delete("/security-servers/{server_id}", serverId)
                .then();
    }

    /**
     * Returns the authentication certificates of a security server
     * (GET /security-servers/{server_id}/authentication-certificates).
     */
    public ValidatableResponse getAuthCerts(String serverId) {
        return session.given()
                .get("/security-servers/{server_id}/authentication-certificates", serverId)
                .then();
    }

    /**
     * Deletes an authentication certificate from a security server
     * (DELETE /security-servers/{server_id}/authentication-certificates/{certificate_id}).
     */
    public ValidatableResponse deleteAuthCert(String serverId, int certId) {
        return session.given()
                .delete("/security-servers/{server_id}/authentication-certificates/{certificate_id}",
                        serverId, certId)
                .then();
    }

    /**
     * Returns the clients registered on a security server (GET /security-servers/{server_id}/clients).
     */
    public ValidatableResponse getClients(String serverId) {
        return session.given()
                .get("/security-servers/{server_id}/clients", serverId)
                .then();
    }
}
