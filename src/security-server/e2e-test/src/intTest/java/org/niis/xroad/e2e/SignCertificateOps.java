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
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;

/**
 * Provisions a CA-signed sign certificate for a member on ss0's own token through the admin API and the
 * environment's test CA. The member must already be a local client of ss0, and a rerun reuses the
 * certificate or pending CSR the member already has on the token.
 */
@Slf4j
@UtilityClass
@SuppressWarnings("unchecked")
class SignCertificateOps {

    private static final String SS0_ENV = "ss0";
    private static final String CA_ENV = "ca";
    private static final String SIGN_KEY_TOKEN_ID = "0";
    private static final String SIGN_KEY_SERIAL_NUMBER = "DEV/SS0/COM";

    private record GeneratedCsr(String keyId, String csrId) {
    }

    private record SignCertificate(String hash, boolean active) {
    }

    private record TokenSignMaterial(SignCertificate certificate, GeneratedCsr pendingCsr) {
        static TokenSignMaterial none() {
            return new TokenSignMaterial(null, null);
        }
    }

    /**
     * Provisions the sign certificate of the member {@code memberCtxId} ({@code INSTANCE:CLASS:CODE}).
     * An import that left the certificate inactive is followed by an explicit activation, because an inactive
     * sign certificate keeps the membership credential from being issued.
     */
    static void provisionSignCertificate(E2eEnvironment env, AdminSession ss0, String memberCtxId) {
        var baseUrl = adminBaseUrl(env, SS0_ENV);
        var existing = findSignMaterial(baseUrl, ss0, memberCtxId);
        var certificate = existing.certificate();
        if (certificate == null) {
            var csr = existing.pendingCsr() != null
                    ? existing.pendingCsr()
                    : generateSignCsr(baseUrl, ss0, env.securityServerAddress(SS0_ENV), memberCtxId);
            var csrBytes = fetchCsrBytes(baseUrl, ss0, csr, memberCtxId);
            var certBytes = signWithTestCa(caBaseUrl(env), csrBytes, memberCtxId);
            certificate = importSignCertificate(baseUrl, ss0, certBytes, memberCtxId);
        }
        if (!certificate.active()) {
            activateSignCertificate(baseUrl, ss0, certificate.hash(), memberCtxId);
        }
    }

    private static String caBaseUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CA_ENV, SsStackSetup.CA, SsStackSetup.Port.CA_API);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private static TokenSignMaterial findSignMaterial(String baseUrl, AdminSession session, String memberCtxId) {
        var response = authed(session).get(baseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID);
        assertThat(response.getStatusCode()).as("look up the token %s", SIGN_KEY_TOKEN_ID).isEqualTo(HTTP_OK);
        for (Map<String, Object> key : response.jsonPath().getList("keys", Map.class)) {
            for (Map<String, Object> cert : (List<Map<String, Object>>) key.get("certificates")) {
                if (memberCtxId.equals(cert.get("owner_id"))) {
                    var details = (Map<String, Object>) cert.get("certificate_details");
                    return new TokenSignMaterial(
                            new SignCertificate((String) details.get("hash"), Boolean.TRUE.equals(cert.get("active"))),
                            null);
                }
            }
            for (Map<String, Object> csr : (List<Map<String, Object>>) key.get("certificate_signing_requests")) {
                if (memberCtxId.equals(csr.get("owner_id"))) {
                    return new TokenSignMaterial(null, new GeneratedCsr((String) key.get("id"), (String) csr.get("id")));
                }
            }
        }
        return TokenSignMaterial.none();
    }

    private static GeneratedCsr generateSignCsr(String baseUrl, AdminSession session, String securityServerAddress,
                                                String memberCtxId) {
        var memberCode = memberCtxId.substring(memberCtxId.lastIndexOf(':') + 1);
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
            var existing = findSignMaterial(baseUrl, session, memberCtxId).certificate();
            assertThat(existing).as("an existing sign certificate for %s after a 409 on import", memberCtxId).isNotNull();
            return existing;
        }
        assertThat(response.getStatusCode()).as("import the sign certificate of %s", memberCtxId).isEqualTo(HTTP_CREATED);
        return new SignCertificate(response.jsonPath().getString("certificate_details.hash"),
                response.jsonPath().getBoolean("active"));
    }

    private static void activateSignCertificate(String baseUrl, AdminSession session, String certHash,
                                                String memberCtxId) {
        var response = authed(session).put(baseUrl + "/api/v1/token-certificates/" + certHash + "/activate");
        assertThat(response.getStatusCode())
                .as("activate the sign certificate %s of %s", certHash, memberCtxId)
                .isIn(HTTP_NO_CONTENT, HTTP_CONFLICT);
    }
}
