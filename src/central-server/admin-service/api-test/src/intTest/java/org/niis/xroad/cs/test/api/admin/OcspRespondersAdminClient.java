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
 * RestAssured client for the {@code /ocsp-responders} admin API resource.
 */
public class OcspRespondersAdminClient {

    private final AdminApiSession session;

    public OcspRespondersAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Updates an OCSP responder's URL (and optionally its certificate).
     */
    public ValidatableResponse updateOcspResponder(int ocspResponderId, String newUrl, byte[] newCertificateBytes) {
        var spec = session.given()
                .multiPart("url", newUrl)
                .multiPart("cost_type", "UNDEFINED");
        if (newCertificateBytes != null) {
            spec = spec.multiPart("certificate", "certificate.pem", newCertificateBytes);
        }
        return spec.patch("/ocsp-responders/{id}", ocspResponderId).then();
    }

    /**
     * Deletes an OCSP responder by its id.
     */
    public ValidatableResponse deleteOcspResponder(int ocspResponderId) {
        return session.given()
                .delete("/ocsp-responders/{id}", ocspResponderId)
                .then();
    }

    /**
     * Gets the certificate details of an OCSP responder.
     */
    public ValidatableResponse getOcspResponderCertificate(int ocspResponderId) {
        return session.given()
                .get("/ocsp-responders/{id}/certificate", ocspResponderId)
                .then();
    }
}
