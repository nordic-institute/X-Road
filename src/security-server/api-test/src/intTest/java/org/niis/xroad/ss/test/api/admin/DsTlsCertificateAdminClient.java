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

/**
 * RestAssured client for the shared {@code /ds-tls-certificate} admin API resource (common-admin-api,
 * consumed identically by Central Server and Security Server).
 */
@SuppressWarnings("checkstyle:magicnumber")
public class DsTlsCertificateAdminClient {

    private final AdminApiSession session;

    public DsTlsCertificateAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Gets the DS TLS certificate slot's status (whether a key has been generated and, once acquired,
     * the certificate's details).
     */
    public ValidatableResponse getStatus() {
        return session.given()
                .get("/ds-tls-certificate")
                .then();
    }

    /**
     * Generates a new DS TLS private key, kept server-side.
     */
    public ValidatableResponse generateKey() {
        return session.given()
                .post("/ds-tls-certificate/key")
                .then();
    }

    /**
     * Generates a certificate signing request for the stored DS TLS key. Returns the raw CSR bytes.
     */
    public byte[] generateCsr(String distinguishedName) {
        return session.given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + distinguishedName + "\"}")
                .post("/ds-tls-certificate/csr")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    /**
     * Generates a certificate signing request without asserting the response status, for negative-path tests.
     */
    public ValidatableResponse generateCsrRaw(String distinguishedName) {
        return session.given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + distinguishedName + "\"}")
                .post("/ds-tls-certificate/csr")
                .then();
    }

    /**
     * Uploads a certificate chain (leaf first) obtained for the DS TLS key.
     */
    public ValidatableResponse uploadCertificate(byte[] certificateBytes) {
        return session.given()
                .multiPart("certificate", "ds-https.crt", certificateBytes, "application/octet-stream")
                .post("/ds-tls-certificate/certificate")
                .then();
    }

    /**
     * Downloads the DS TLS certificate as a gzip compressed tar archive.
     */
    public ValidatableResponse downloadCertificate() {
        return session.given()
                .get("/ds-tls-certificate/certificate")
                .then();
    }
}
