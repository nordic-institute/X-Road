/*
 * The MIT License
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
package org.niis.xroad.e2e;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.authed;

/**
 * Provisions a CA-signed sign certificate for a member on a Security Server's own token through the admin
 * API and the environment's test CA: SIGNING CSR, test CA signing, import and, when the import left the
 * certificate inactive, explicit activation. The admin API rejects a SIGNING CSR for a member id that is
 * not yet a local client, so the member is added before calling {@link #provisionSignCertificate}.
 */
@Slf4j
@UtilityClass
class SignCertificateOps {

    /** The Security Server's own token, addressed as {@code setup.hurl}'s sign-key block does. */
    private static final String SIGN_KEY_TOKEN_ID = "0";
    /** Same DN convention {@code setup.hurl} uses for every key generated on the token, regardless of member. */
    private static final String SIGN_KEY_SERIAL_NUMBER = "DEV/SS0/COM";

    private record GeneratedCsr(String keyId, String csrId) {
    }

    private record SignCertificate(String hash, boolean active) {
    }

    /**
     * Provisions the sign certificate of the member {@code memberCtxId} ({@code INSTANCE:CLASS:CODE}).
     * The explicit activation covers an import whose synchronous OCSP check left the certificate inactive,
     * which an inactive sign certificate would turn into a membership credential that is never issued.
     * A 409 on import means the certificate is already on the token; the existing one is used.
     */
    static void provisionSignCertificate(String baseUrl, AdminSession session, String caBaseUrl,
                                         String securityServerAddress, String memberCtxId, String memberCode) {
        var csr = generateSignCsr(baseUrl, session, securityServerAddress, memberCtxId, memberCode);
        var csrBytes = fetchCsrBytes(baseUrl, session, csr, memberCtxId);
        var certBytes = signWithTestCa(caBaseUrl, csrBytes, memberCtxId);
        var certificate = importSignCertificate(baseUrl, session, certBytes, memberCtxId);
        if (!certificate.active()) {
            activateSignCertificate(baseUrl, session, certificate.hash(), memberCtxId);
        }
    }

    private static GeneratedCsr generateSignCsr(String baseUrl, AdminSession session, String securityServerAddress,
                                                String memberCtxId, String memberCode) {
        var caResponse = authed(session).get(baseUrl + "/api/v1/certificate-authorities");
        assertThat(caResponse.getStatusCode()).as("look up the approved certificate authorities").isEqualTo(HTTP_OK);
        var body = """
                {
                  "key_label": "Sign key %s",
                  "csr_generate_request": {
                    "key_usage_type": "SIGNING",
                    "ca_name": "%s",
                    "csr_format": "DER",
                    "member_id": "%s",
                    "subject_field_values": {
                      "CN": "%s",
                      "C": "FI",
                      "O": "Test client",
                      "subjectAltName": "%s",
                      "serialNumber": "%s"
                    }
                  }
                }
                """.formatted(memberCode, caResponse.jsonPath().getString("[0].name"), memberCtxId, memberCode,
                securityServerAddress, SIGN_KEY_SERIAL_NUMBER);
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(body)
                .post(baseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID + "/keys-with-csrs");
        assertThat(response.getStatusCode()).as("generate a SIGNING CSR for %s", memberCtxId).isEqualTo(HTTP_OK);
        return new GeneratedCsr(response.jsonPath().getString("key.id"), response.jsonPath().getString("csr_id"));
    }

    private static byte[] fetchCsrBytes(String baseUrl, AdminSession session, GeneratedCsr csr, String memberCtxId) {
        var response = authed(session)
                .get(baseUrl + "/api/v1/keys/" + csr.keyId() + "/csrs/" + csr.csrId() + "?csr_format=PEM");
        assertThat(response.getStatusCode()).as("fetch the CSR PEM of %s", memberCtxId).isEqualTo(HTTP_OK);
        return response.getBody().asByteArray();
    }

    private static byte[] signWithTestCa(String caBaseUrl, byte[] csrBytes, String memberCtxId) {
        var response = RestAssuredFactory.given()
                .multiPart("certreq", "sign.csr.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "sign")
                .post(caBaseUrl + "/testca/sign");
        assertThat(response.getStatusCode()).as("sign the CSR of %s with the test CA", memberCtxId).isEqualTo(HTTP_OK);
        return response.getBody().asByteArray();
    }

    private static SignCertificate importSignCertificate(String baseUrl, AdminSession session, byte[] certBytes,
                                                         String memberCtxId) {
        var response = authed(session)
                .multiPart("certificate", "sign_key_cert.pem", certBytes, "application/octet-stream")
                .post(baseUrl + "/api/v1/token-certificates");
        if (response.getStatusCode() == HTTP_CONFLICT) {
            log.info("Sign certificate for {} is already imported; using the one on the token", memberCtxId);
            var existing = findSignCertificate(baseUrl, session, memberCtxId);
            assertThat(existing).as("an existing sign certificate for %s after a 409 on import", memberCtxId).isNotNull();
            return existing;
        }
        assertThat(response.getStatusCode()).as("import the sign certificate of %s", memberCtxId).isEqualTo(HTTP_CREATED);
        return new SignCertificate(response.jsonPath().getString("certificate_details.hash"),
                response.jsonPath().getBoolean("active"));
    }

    private static SignCertificate findSignCertificate(String baseUrl, AdminSession session, String memberCtxId) {
        var response = authed(session).get(baseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID);
        assertThat(response.getStatusCode()).as("look up the token %s", SIGN_KEY_TOKEN_ID).isEqualTo(HTTP_OK);
        var json = response.jsonPath();
        var certificate = "keys.certificates.flatten().find { it?.owner_id == '%s' }".formatted(memberCtxId);
        if (json.getMap(certificate) == null) {
            return null;
        }
        return new SignCertificate(json.getString(certificate + ".certificate_details.hash"),
                json.getBoolean(certificate + ".active"));
    }

    private static void activateSignCertificate(String baseUrl, AdminSession session, String certHash,
                                                String memberCtxId) {
        var response = authed(session).put(baseUrl + "/api/v1/token-certificates/" + certHash + "/activate");
        assertThat(response.getStatusCode())
                .as("activate the sign certificate %s of %s", certHash, memberCtxId)
                .isIn(HTTP_NO_CONTENT, HTTP_CONFLICT);
    }
}
