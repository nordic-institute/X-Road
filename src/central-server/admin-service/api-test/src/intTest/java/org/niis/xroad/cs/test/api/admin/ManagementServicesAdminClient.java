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

/**
 * RestAssured client for the Central Server management services admin API.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class ManagementServicesAdminClient {

    private final AdminApiSession session;

    public ManagementServicesAdminClient(AdminApiSession session) {
        this.session = session;
    }

    public ValidatableResponse getConfiguration() {
        return session.given()
                .get("/management-services-configuration")
                .then();
    }

    public ValidatableResponse updateConfiguration(String serviceProviderId) {
        return session.given()
                .contentType(ContentType.JSON)
                .body("{\"service_provider_id\":\"" + serviceProviderId + "\"}")
                .patch("/management-services-configuration")
                .then();
    }

    public ValidatableResponse registerProvider(String securityServerId) {
        return session.given()
                .contentType(ContentType.JSON)
                .body("{\"security_server_id\":\"" + securityServerId + "\"}")
                .post("/management-services-configuration/register-provider")
                .then();
    }

    public ValidatableResponse getCertificate() {
        return session.given()
                .get("/management-services-configuration/certificate")
                .then();
    }

    public ValidatableResponse generateKeyAndCertificate() {
        return session.given()
                .post("/management-services-configuration/certificate")
                .then();
    }

    public ValidatableResponse downloadCertificate() {
        return session.given()
                .get("/management-services-configuration/download-certificate")
                .then();
    }

    public ValidatableResponse generateCsr(String dn) {
        return session.given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + dn + "\"}")
                .post("/management-services-configuration/generate-csr")
                .then();
    }

    public ValidatableResponse uploadCertificate(byte[] certBytes) {
        return session.given()
                .multiPart("certificate", "certificate.cer", certBytes, "application/octet-stream")
                .post("/management-services-configuration/upload-certificate")
                .then();
    }
}
