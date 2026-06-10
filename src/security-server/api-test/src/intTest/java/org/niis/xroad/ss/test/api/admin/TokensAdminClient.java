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
import org.niis.xroad.securityserver.restapi.openapi.model.AcmeEabCredentialsStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AcmeOrderDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormatDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelWithCsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenPasswordDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenPinUpdateDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the tokens, keys, CSR, and ACME admin API resources.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class TokensAdminClient {

    private final AdminApiSession session;

    public TokensAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Lists all tokens on the Security Server.
     */
    public List<TokenDto> listTokens() {
        return Arrays.asList(session.given()
                .get("/tokens")
                .then()
                .statusCode(200)
                .extract()
                .as(TokenDto[].class));
    }

    /**
     * Gets a single token by ID.
     */
    public TokenDto getToken(String tokenId) {
        return session.given()
                .get("/tokens/{id}", tokenId)
                .then()
                .statusCode(200)
                .extract()
                .as(TokenDto.class);
    }

    /**
     * Logs in to the given token with the provided PIN.
     */
    public ValidatableResponse loginToken(String tokenId, String pin) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new TokenPasswordDto().password(pin))
                .put("/tokens/{id}/login", tokenId)
                .then();
    }

    /**
     * Logs out from the given token.
     */
    public ValidatableResponse logoutToken(String tokenId) {
        return session.given()
                .put("/tokens/{id}/logout", tokenId)
                .then();
    }

    /**
     * Updates the PIN for the given token.
     */
    public ValidatableResponse updateTokenPin(String tokenId, String oldPin, String newPin) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new TokenPinUpdateDto(oldPin, newPin))
                .put("/tokens/{id}/pin", tokenId)
                .then();
    }

    /**
     * Deletes the given token (must be inactive/unavailable).
     */
    public ValidatableResponse deleteToken(String tokenId) {
        return session.given()
                .delete("/tokens/{id}", tokenId)
                .then();
    }

    /**
     * Adds a new key with the given label to the token.
     * Returns the new key ID.
     */
    public String addKey(String tokenId, String label) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new KeyLabelDto().label(label))
                .post("/tokens/{id}/keys", tokenId)
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("id");
    }

    /**
     * Adds a new key and generates a CSR for it in a single operation.
     * Returns a view containing the new key ID and the generated CSR ID.
     */
    public KeyWithCsrView addKeyWithCsr(String tokenId, String label, KeyUsageTypeDto usage,
                                        String caName, CsrFormatDto format, String memberId) {
        var csrRequest = new CsrGenerateDto()
                .keyUsageType(usage)
                .caName(caName)
                .csrFormat(format)
                .memberId(memberId)
                .subjectFieldValues(buildSubjectFields(usage));

        var request = new KeyLabelWithCsrGenerateDto()
                .keyLabel(label)
                .csrGenerateRequest(csrRequest);

        var response = session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/tokens/{id}/keys-with-csrs", tokenId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        var keyId = response.getString("key.id");
        var csrId = response.getString("csr_id");
        return new KeyWithCsrView(keyId, csrId);
    }

    /**
     * Generates an additional CSR for an existing key using default subject fields.
     */
    public void generateCsr(String keyId, KeyUsageTypeDto usage, String caName,
                             CsrFormatDto format, String memberId) {
        generateCsr(keyId, usage, caName, format, memberId, null);
    }

    /**
     * Generates an additional CSR for an existing key with an optional CN override (used to produce distinct subjects).
     */
    public void generateCsr(String keyId, KeyUsageTypeDto usage, String caName,
                             CsrFormatDto format, String memberId, String cnOverride) {
        var subjectFields = buildSubjectFields(usage);
        if (cnOverride != null) {
            var mutable = new java.util.HashMap<>(subjectFields);
            mutable.put("CN", cnOverride);
            mutable.put("subjectAltName", cnOverride);
            subjectFields = mutable;
        }
        var request = new CsrGenerateDto()
                .keyUsageType(usage)
                .caName(caName)
                .csrFormat(format)
                .memberId(memberId)
                .subjectFieldValues(subjectFields);

        session.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/keys/{id}/csrs", keyId)
                .then()
                .statusCode(200);
    }

    /**
     * Downloads the CSR binary for the given key and CSR.
     */
    public byte[] downloadCsr(String keyId, String csrId, CsrFormatDto format) {
        return session.given()
                .queryParam("csr_format", format.getValue())
                .get("/keys/{keyId}/csrs/{csrId}", keyId, csrId)
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    /**
     * Deletes the CSR with the given ID from the given key.
     */
    public ValidatableResponse deleteCsr(String keyId, String csrId) {
        return session.given()
                .delete("/keys/{keyId}/csrs/{csrId}", keyId, csrId)
                .then();
    }

    /**
     * Imports a certificate (PEM/DER bytes) into the signer.
     */
    public ValidatableResponse importCertificate(byte[] certBytes) {
        return session.given()
                .multiPart("certificate", "certificate.pem", certBytes, "application/octet-stream")
                .post("/token-certificates")
                .then();
    }

    /**
     * Queries whether EAB credentials exist for the given CA and member.
     */
    public AcmeEabCredentialsStatusDto getAcmeEabCredentialsStatus(String caName, KeyUsageTypeDto usage, String memberId) {
        return session.given()
                .queryParam("key_usage_type", usage.getValue())
                .queryParam("member_id", memberId)
                .get("/certificate-authorities/{caName}/has-acme-eab-credentials", caName)
                .then()
                .statusCode(200)
                .extract()
                .as(AcmeEabCredentialsStatusDto.class);
    }

    /**
     * Orders an ACME certificate for the given CSR from the given CA.
     * Returns the response for further assertion or status extraction.
     */
    public ValidatableResponse orderAcmeCertificate(String caName, String csrId, KeyUsageTypeDto usage) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new AcmeOrderDto().csrId(csrId).keyUsageType(usage))
                .post("/certificate-authorities/{caName}/acme-order", caName)
                .then();
    }

    private Map<String, String> buildSubjectFields(KeyUsageTypeDto usage) {
        if (usage == KeyUsageTypeDto.SIGNING) {
            return Map.of(
                    "O", "ui-test",
                    "subjectAltName", "ui"
            );
        }
        return Map.of(
                "O", "ui-test",
                "CN", "ui",
                "subjectAltName", "ui"
        );
    }

    /**
     * Lightweight view of a key + CSR pair returned by the add-key-with-csr operation.
     *
     * @param keyId the new key ID
     * @param csrId the generated CSR ID
     */
    public record KeyWithCsrView(String keyId, String csrId) {
    }
}
