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

import io.restassured.response.ValidatableResponse;

/**
 * RestAssured client for the {@code /certification-services} admin API resource.
 */
public class CertificationServicesAdminClient {

    private final AdminApiSession session;

    public CertificationServicesAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Adds a new certification service (multipart/form-data with certificate binary + settings).
     */
    public ValidatableResponse addCertificationService(
            byte[] certificateBytes,
            String certificateProfileInfo,
            String defaultCsrFormat,
            String tlsAuth) {
        var spec = session.given()
                .multiPart("certificate", "certificate.der", certificateBytes);
        if (defaultCsrFormat != null) {
            spec = spec.multiPart("default_csr_format", defaultCsrFormat);
        }
        if (certificateProfileInfo != null) {
            spec = spec.multiPart("certificate_profile_info", certificateProfileInfo);
        }
        if (tlsAuth != null) {
            spec = spec.multiPart("tls_auth", tlsAuth);
        }
        return spec.post("/certification-services").then();
    }

    /**
     * Gets a certification service by its id.
     */
    public ValidatableResponse getCertificationService(int id) {
        return session.given()
                .get("/certification-services/{id}", id)
                .then();
    }

    /**
     * Lists all certification services.
     */
    public ValidatableResponse listCertificationServices() {
        return session.given()
                .get("/certification-services")
                .then();
    }

    /**
     * Updates the settings of a certification service. {@code certificateProfileInfo} may be null to trigger
     * validation errors.
     */
    public ValidatableResponse updateCertificationService(int id, String tlsAuth, String certificateProfileInfo) {
        var profilePart = certificateProfileInfo != null
                ? ",\"certificate_profile_info\":\"%s\"".formatted(certificateProfileInfo)
                : "";
        return session.given()
                .contentType("application/json")
                .body("{\"tls_auth\":\"%s\",\"default_csr_format\":\"DER\"%s}".formatted(tlsAuth, profilePart))
                .patch("/certification-services/{id}", id)
                .then();
    }

    /**
     * Deletes a certification service by its id.
     */
    public ValidatableResponse deleteCertificationService(int id) {
        return session.given()
                .delete("/certification-services/{id}", id)
                .then();
    }

    /**
     * Gets the certificate details for a certification service.
     */
    public ValidatableResponse getCertificationServiceCertificate(int id) {
        return session.given()
                .get("/certification-services/{id}/certificate", id)
                .then();
    }

    /**
     * Lists the OCSP responders of a certification service.
     */
    public ValidatableResponse listCertificationServiceOcspResponders(int id) {
        return session.given()
                .get("/certification-services/{id}/ocsp-responders", id)
                .then();
    }

    /**
     * Adds an OCSP responder to a certification service.
     */
    public ValidatableResponse addCertificationServiceOcspResponder(int certServiceId, String url, byte[] certificateBytes) {
        return session.given()
                .multiPart("url", url)
                .multiPart("cost_type", "UNDEFINED")
                .multiPart("certificate", "certificate.pem", certificateBytes)
                .post("/certification-services/{id}/ocsp-responders", certServiceId)
                .then();
    }

    /**
     * Adds an intermediate CA certificate to a certification service.
     */
    public ValidatableResponse addIntermediateCa(int certServiceId, byte[] certificateBytes) {
        return session.given()
                .multiPart("certificate", "certificate.der", certificateBytes)
                .post("/certification-services/{id}/intermediate-cas", certServiceId)
                .then();
    }

    /**
     * Lists the intermediate CAs of a certification service.
     */
    public ValidatableResponse listIntermediateCas(int certServiceId) {
        return session.given()
                .get("/certification-services/{id}/intermediate-cas", certServiceId)
                .then();
    }
}
