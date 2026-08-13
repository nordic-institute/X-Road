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

import java.util.ArrayList;
import java.util.List;

/**
 * RestAssured client for the {@code /ds-tls-certification-authorities} admin API resource.
 */
public class DsTlsCertificationAuthoritiesAdminClient {

    private final AdminApiSession session;

    public DsTlsCertificationAuthoritiesAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Adds a new DS TLS certification authority (multipart/form-data with certificate binary + settings).
     * ACME fields may be {@code null} to omit them (manual-path entry, no auto-enrollment).
     */
    public ValidatableResponse addDsTlsCertificationAuthority(
            byte[] certificateBytes,
            String name,
            String acmeServerDirectoryUrl,
            String dsTlsCertificateProfileId) {
        return addDsTlsCertificationAuthority("certificate.der", certificateBytes, name, acmeServerDirectoryUrl, dsTlsCertificateProfileId);
    }

    /**
     * Same as {@link #addDsTlsCertificationAuthority(byte[], String, String, String)} but with an explicit
     * upload filename, so malformed-content-type/extension cases can be exercised.
     */
    public ValidatableResponse addDsTlsCertificationAuthority(
            String filename,
            byte[] certificateBytes,
            String name,
            String acmeServerDirectoryUrl,
            String dsTlsCertificateProfileId) {
        var spec = session.given()
                .multiPart("certificate", filename, certificateBytes);
        if (name != null) {
            spec = spec.multiPart("name", name);
        }
        if (acmeServerDirectoryUrl != null) {
            spec = spec.multiPart("acme_server_directory_url", acmeServerDirectoryUrl);
        }
        if (dsTlsCertificateProfileId != null) {
            spec = spec.multiPart("ds_tls_certificate_profile_id", dsTlsCertificateProfileId);
        }
        return spec.post("/ds-tls-certification-authorities").then();
    }

    /**
     * Gets a DS TLS certification authority by its id.
     */
    public ValidatableResponse getDsTlsCertificationAuthority(int id) {
        return session.given()
                .get("/ds-tls-certification-authorities/{id}", id)
                .then();
    }

    /**
     * Lists all DS TLS certification authorities.
     */
    public ValidatableResponse listDsTlsCertificationAuthorities() {
        return session.given()
                .get("/ds-tls-certification-authorities")
                .then();
    }

    /**
     * Updates the settings of a DS TLS certification authority. Any of {@code name},
     * {@code acmeServerDirectoryUrl} or {@code dsTlsCertificateProfileId} may be {@code null} to leave that
     * field unchanged; pass {@code ""} to clear an ACME field.
     */
    public ValidatableResponse updateDsTlsCertificationAuthority(
            int id, String name, String acmeServerDirectoryUrl, String dsTlsCertificateProfileId) {
        List<String> fields = new ArrayList<>();
        if (name != null) {
            fields.add("\"name\":\"%s\"".formatted(name));
        }
        if (acmeServerDirectoryUrl != null) {
            fields.add("\"acme_server_directory_url\":\"%s\"".formatted(acmeServerDirectoryUrl));
        }
        if (dsTlsCertificateProfileId != null) {
            fields.add("\"ds_tls_certificate_profile_id\":\"%s\"".formatted(dsTlsCertificateProfileId));
        }
        return session.given()
                .contentType("application/json")
                .body("{" + String.join(",", fields) + "}")
                .patch("/ds-tls-certification-authorities/{id}", id)
                .then();
    }

    /**
     * Deletes a DS TLS certification authority by its id.
     */
    public ValidatableResponse deleteDsTlsCertificationAuthority(int id) {
        return session.given()
                .delete("/ds-tls-certification-authorities/{id}", id)
                .then();
    }

    /**
     * Gets the certificate details for a DS TLS certification authority.
     */
    public ValidatableResponse getDsTlsCertificationAuthorityCertificate(int id) {
        return session.given()
                .get("/ds-tls-certification-authorities/{id}/certificate", id)
                .then();
    }

    /**
     * Lists the intermediate CAs of a DS TLS certification authority.
     */
    public ValidatableResponse listIntermediateCas(int id) {
        return session.given()
                .get("/ds-tls-certification-authorities/{id}/intermediate-cas", id)
                .then();
    }

    /**
     * Adds an intermediate CA certificate to a DS TLS certification authority.
     */
    public ValidatableResponse addIntermediateCa(int id, byte[] certificateBytes) {
        return addIntermediateCa(id, "certificate.der", certificateBytes);
    }

    /**
     * Same as {@link #addIntermediateCa(int, byte[])} but with an explicit upload filename.
     */
    public ValidatableResponse addIntermediateCa(int id, String filename, byte[] certificateBytes) {
        return session.given()
                .multiPart("certificate", filename, certificateBytes)
                .post("/ds-tls-certification-authorities/{id}/intermediate-cas", id)
                .then();
    }
}
