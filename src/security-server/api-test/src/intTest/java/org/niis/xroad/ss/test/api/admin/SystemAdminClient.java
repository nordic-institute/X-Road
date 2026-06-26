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
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DistinguishedNameDto;

/**
 * RestAssured client for the system-level admin API resources: TLS key/certificate management.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class SystemAdminClient {

    private final AdminApiSession session;

    public SystemAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Gets the current TLS certificate information.
     */
    public CertificateDetailsDto getSystemCertificate() {
        return session.given()
                .get("/system/certificate")
                .then()
                .statusCode(200)
                .extract()
                .as(CertificateDetailsDto.class);
    }

    /**
     * Generates a new TLS key and self-signed certificate, replacing the current one.
     */
    public ValidatableResponse generateTlsKeyAndCertificate() {
        return session.given()
                .post("/system/certificate")
                .then();
    }

    /**
     * Downloads the current TLS certificate as a gzip-compressed tar archive.
     */
    public byte[] exportCertificate() {
        return session.given()
                .get("/system/certificate/export")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    /**
     * Generates a CSR for the given distinguished name.
     * Returns the raw CSR bytes.
     */
    public byte[] generateCsr(String distinguishedName) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new DistinguishedNameDto().name(distinguishedName))
                .post("/system/certificate/csr")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    /**
     * Imports a signed TLS certificate (PEM or DER bytes) as the new server TLS certificate.
     */
    public ValidatableResponse importCertificate(byte[] certBytes) {
        return session.given()
                .multiPart("certificate", "certificate.pem", certBytes, "application/octet-stream")
                .post("/system/certificate/import")
                .then();
    }
}
