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
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeWrapperDto;

import java.util.List;

/**
 * RestAssured client for internal-server (connection type + TLS certificate) admin API resources.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class InternalServersAdminClient {

    private final AdminApiSession session;

    public InternalServersAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Updates the connection type for the given client.
     */
    public ValidatableResponse updateConnectionType(String clientId, ConnectionTypeWrapperDto request) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .patch("/clients/{id}", clientId)
                .then();
    }

    /**
     * Returns the connection type for the given client, extracted from the client response.
     */
    public String getConnectionType(String clientId) {
        return session.given()
                .get("/clients/{id}", clientId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("connection_type");
    }

    /**
     * Uploads a TLS certificate for the given client from the provided bytes.
     * Returns the response for assertion or hash extraction.
     */
    public ValidatableResponse addTlsCertificate(String clientId, byte[] certBytes, String filename) {
        return session.given()
                .multiPart("certificate", filename, certBytes, "application/octet-stream")
                .post("/clients/{id}/tls-certificates", clientId)
                .then();
    }

    /**
     * Deletes the TLS certificate with the given hash from the given client.
     */
    public ValidatableResponse deleteTlsCertificate(String clientId, String certHash) {
        return session.given()
                .delete("/clients/{id}/tls-certificates/{hash}", clientId, certHash)
                .then();
    }

    /**
     * Lists TLS certificates for the given client and returns the raw JSON hash list.
     */
    public List<String> listTlsCertificateHashes(String clientId) {
        return session.given()
                .get("/clients/{id}/tls-certificates", clientId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("hash", String.class);
    }

    /**
     * Exports (downloads) the server TLS certificate of the Security Server itself.
     * Returns the certificate bytes.
     */
    public byte[] exportServerTlsCertificate() {
        return session.given()
                .get("/system/certificate/export")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }
}
