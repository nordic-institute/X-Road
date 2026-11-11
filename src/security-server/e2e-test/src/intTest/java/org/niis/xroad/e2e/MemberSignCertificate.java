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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.authed;

/**
 * Provisions a CA-signed SIGNING certificate on ss0's token for a member registered on ss0 at test run
 * time, shared by the scenarios that onboard a member after the stack is already running
 * ({@link SsProxyDspRuntimeMemberTest}, {@link SsProxyDspEncodedMemberTest}). The member is identified by its
 * {@code INSTANCE:CLASS:CODE} id as the admin API reports it.
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings({"checkstyle:magicnumber", "unchecked"})
final class MemberSignCertificate {

    private static final String CA_ENV = "ca";

    /**
     * ss0's own token, addressed the same way {@code setup.hurl}'s ss0 sign-key block does
     * ({@code /tokens/0/...}), not ss1's captured hardware-token id.
     */
    private static final String SIGN_KEY_TOKEN_ID = "0";
    /** Same DN convention {@code setup.hurl} uses for every key generated on ss0's token, regardless of member. */
    private static final String SIGN_KEY_SERIAL_NUMBER = "DEV/SS0/COM";

    private final String ss0Env;
    private final String memberId;
    private final String memberCode;
    private final String keyLabel;

    private record GeneratedCsr(String keyId, String csrId) {
    }

    private record ImportedCertificate(String hash, boolean active) {
    }

    /**
     * The outcome of scanning ss0's token for sign material already belonging to the member:
     * either a certificate ({@code certHash} set), a CSR generated but never signed/imported on a
     * previous, interrupted run ({@code pendingCsrKeyId}/{@code pendingCsrId} set), or neither.
     */
    private record TokenScanResult(String certHash, boolean active, String pendingCsrKeyId, String pendingCsrId) {
        static TokenScanResult none() {
            return new TokenScanResult(null, false, null, null);
        }
    }

    /**
     * The test CA's cert-issuance endpoint is plain HTTP, unlike the admin APIs, mirroring
     * {@code setup.hurl}'s own {@code http://{{ca_host}}:8888/testca/sign} calls.
     */
    static String caBaseUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CA_ENV, SsStackSetup.CA, SsStackSetup.Port.CA_API);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    /**
     * Provisions a CA-signed sign certificate for the member on ss0's token, mirroring
     * {@code setup.hurl}'s own ss0 sign-key block (keys-with-csrs -&gt; fetch CSR PEM -&gt; test CA
     * {@code /testca/sign} -&gt; import) for {@code memberId} instead of ss0's own owner
     * member. The admin API rejects a SIGNING CSR for a member id that is not yet a local client
     * (see {@code TokenCertificateService.generateCertRequest}), which is why this runs after
     * {@link AdminApi#addLocalClient}, not at environment bring-up.
     *
     * <p>Tolerant of a warm rerun at every stage: an existing certificate for the member is reused
     * outright; an existing CSR with no certificate yet (a previous run that crashed mid-flow) is
     * signed and imported without generating a new key. A certificate the signer could not activate
     * inline — the import call verifies the OCSP response synchronously and only activates on
     * success, so a transient OCSP hiccup would otherwise leave it inactive — is activated explicitly
     * through the same endpoint an administrator would use, rather than waiting on the signer's
     * periodic OCSP refresh (whose default interval does not fit inside the callers'
     * provisioning-status timeout). An inactive sign certificate cannot issue the member's dataspace
     * membership credential.
     */
    void provision(E2eEnvironment env, String ss0BaseUrl, AdminSession ss0, String caBaseUrl) {
        var existing = scanTokenForMemberSignMaterial(ss0BaseUrl, ss0);
        String certHash = existing.certHash();
        boolean active = existing.active();

        if (certHash == null) {
            var pending = existing.pendingCsrKeyId() != null
                    ? new GeneratedCsr(existing.pendingCsrKeyId(), existing.pendingCsrId())
                    : generateSignCsr(ss0BaseUrl, ss0, env.securityServerAddress(ss0Env));
            var csrBytes = fetchCsrBytes(ss0BaseUrl, ss0, pending.keyId(), pending.csrId());
            var certBytes = signWithTestCa(caBaseUrl, csrBytes);
            var imported = importSignCertificate(ss0BaseUrl, ss0, certBytes);
            certHash = imported.hash();
            active = imported.active();
        }

        if (!active) {
            activateSignCertificate(ss0BaseUrl, ss0, certHash);
        }
    }

    /**
     * Looks for sign material already belonging to {@code memberId} on ss0's token: a
     * {@code Key}'s {@code certificates}/{@code certificate_signing_requests} both carry an
     * {@code owner_id} field identifying the client they were issued for, so a warm rerun is
     * detected without needing a deterministic key label (key generation has none — see
     * {@code KeyService.addKey} — so re-running the generate step unconditionally would accumulate a
     * new key on every rerun instead of reusing the existing one).
     */
    private TokenScanResult scanTokenForMemberSignMaterial(String ss0BaseUrl, AdminSession ss0) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID);
        assertThat(response.getStatusCode()).as("look up ss0's token %s", SIGN_KEY_TOKEN_ID).isEqualTo(200);

        for (Map<String, Object> key : response.jsonPath().getList("keys", Map.class)) {
            for (Map<String, Object> cert : (List<Map<String, Object>>) key.get("certificates")) {
                if (memberId.equals(cert.get("owner_id"))) {
                    var certificateDetails = (Map<String, Object>) cert.get("certificate_details");
                    return new TokenScanResult(
                            (String) certificateDetails.get("hash"), Boolean.TRUE.equals(cert.get("active")), null, null);
                }
            }
            for (Map<String, Object> csr : (List<Map<String, Object>>) key.get("certificate_signing_requests")) {
                if (memberId.equals(csr.get("owner_id"))) {
                    return new TokenScanResult(null, false, (String) key.get("id"), (String) csr.get("id"));
                }
            }
        }
        return TokenScanResult.none();
    }

    private String fetchCaName(String ss0BaseUrl, AdminSession ss0) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/certificate-authorities");
        assertThat(response.getStatusCode()).as("look up ss0's approved certificate authorities").isEqualTo(200);
        return response.jsonPath().getString("[0].name");
    }

    /**
     * Generates the member's SIGNING key/CSR on ss0's token, same DN field conventions as
     * {@code setup.hurl}'s ss0 sign-key block for {@code DEV:COM:1234} (CN/C/O/subjectAltName/
     * serialNumber), just for {@code memberId}'s identity instead.
     */
    private GeneratedCsr generateSignCsr(String ss0BaseUrl, AdminSession ss0, String ss0SecurityServerAddress) {
        var caName = fetchCaName(ss0BaseUrl, ss0);
        var body = """
                {
                  "key_label": "%s",
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
                """.formatted(keyLabel, caName, memberId, memberCode,
                ss0SecurityServerAddress, SIGN_KEY_SERIAL_NUMBER);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID + "/keys-with-csrs");
        // Same quirk as setup.hurl's ss0 sign-key block: the API returns 200, not the 201 its
        // own definition promises.
        assertThat(response.getStatusCode())
                .as("generate a SIGNING CSR for %s on ss0's token", memberId)
                .isEqualTo(200);
        return new GeneratedCsr(response.jsonPath().getString("key.id"), response.jsonPath().getString("csr_id"));
    }

    private byte[] fetchCsrBytes(String ss0BaseUrl, AdminSession ss0, String keyId, String csrId) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/keys/" + keyId + "/csrs/" + csrId + "?csr_format=PEM");
        assertThat(response.getStatusCode()).as("fetch CSR %s PEM for key %s", csrId, keyId).isEqualTo(200);
        return response.getBody().asByteArray();
    }

    /**
     * Same test CA the environment's own bring-up uses (the CA needs a filename on the CSR part,
     * which {@code RestAssuredFactory}'s multipart support handles directly, unlike
     * {@code setup.hurl}, which has to hand-roll the multipart body for this same reason).
     */
    private byte[] signWithTestCa(String caBaseUrl, byte[] csrBytes) {
        var response = RestAssuredFactory.given()
                .multiPart("certreq", "sign.csr.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "sign")
                .post(caBaseUrl + "/testca/sign");
        assertThat(response.getStatusCode()).as("sign the member's sign CSR with the test CA").isEqualTo(200);
        return response.getBody().asByteArray();
    }

    /**
     * Imports the CA-signed certificate, tolerating a warm rerun where it was already imported by an
     * earlier attempt at this same run's key/CSR: {@code TokenCertificateService.importCertificate}
     * rejects re-importing an identical, already-saved certificate with
     * {@code CertificateAlreadyExistsException} (an {@code XrdRuntimeException} tagged
     * {@code CERT_EXISTS}), a {@code ConflictException} mapped to 409. The 409 body carries no
     * identifiers, so the existing certificate is recovered the same way {@link #provision}
     * detects one from a previous run entirely.
     */
    private ImportedCertificate importSignCertificate(String ss0BaseUrl, AdminSession ss0, byte[] certBytes) {
        var response = authed(ss0)
                .multiPart("certificate", "sign_key_cert.pem", certBytes, "application/octet-stream")
                .post(ss0BaseUrl + "/api/v1/token-certificates");
        if (response.getStatusCode() == 409) {
            log.info("Sign certificate for {} already imported on a warm rerun; looking it up instead of importing again",
                    memberId);
            var existing = scanTokenForMemberSignMaterial(ss0BaseUrl, ss0);
            assertThat(existing.certHash())
                    .as("an existing sign certificate for %s after a 409 on import", memberId)
                    .isNotBlank();
            return new ImportedCertificate(existing.certHash(), existing.active());
        }
        assertThat(response.getStatusCode()).as("import the member's sign certificate").isEqualTo(201);
        return new ImportedCertificate(response.jsonPath().getString("certificate_details.hash"), response.jsonPath().getBoolean("active"));
    }

    /**
     * Explicit activation trigger: {@code ActivateCertReqHandler} re-runs the same synchronous OCSP
     * verification the import call performs, so this recovers a certificate the import left inactive
     * without waiting on the signer's periodic OCSP refresh job. Tolerates 409: {@code PossibleActionEnum
     * .ACTIVATE} is only offered for a currently-inactive certificate, so a certificate the import call
     * already activated makes this a no-op conflict rather than an error.
     */
    private void activateSignCertificate(String ss0BaseUrl, AdminSession ss0, String certHash) {
        var response = authed(ss0).put(ss0BaseUrl + "/api/v1/token-certificates/" + certHash + "/activate");
        assertThat(response.getStatusCode())
                .as("activate the member's sign certificate %s (204 first attempt, 409 if already active)", certHash)
                .isIn(204, 409);
    }
}
