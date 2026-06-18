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
package org.niis.xroad.ss.test.api.keys;

import io.restassured.RestAssured;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateOcspStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormatDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.TokensAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for key creation, CSR generation and deletion on the soft token.
 */
@DisplayName("Keys and CSR — signer API persistence")
@ResourceLock("softToken-0")
@SuppressWarnings("checkstyle:magicnumber")
class KeysAndCsrTest extends SsApiTest {

    private static final String SOFT_TOKEN = "0";
    private static final String TEST_CA = "Test CA";
    private static final String MEMBER_ID = "DEV:COM:1234";

    // MIGRATED-FROM: 0100-ss-initialization.feature :: "Default token is initialized"
    @Test
    @DisplayName("Default soft token reports initialized and logged-in via the signer API")
    void defaultTokenIsInitialized(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);

        var token = given("softToken-0 is logged in as part of the baseline", () ->
                tokens.getToken(SOFT_TOKEN));

        then("the token is reported as logged in and initialized", () -> {
            assertThat(token.getLoggedIn()).isTrue();
            assertThat(token.getStatus()).isEqualTo(
                    org.niis.xroad.securityserver.restapi.openapi.model.TokenStatusDto.OK);
        });
    }

    // MIGRATED-FROM: 0100-ss-initialization.feature :: "Default token is initialized"
    // Specifically the logout → logged-out → re-login → logged-in lifecycle dropped from the original migration.
    @Test
    @DisplayName("Default soft token can be logged out and logged back in with the correct PIN")
    void defaultTokenLogoutAndRelogin(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);

        given("softToken-0 is logged in", () ->
                assertThat(tokens.getToken(SOFT_TOKEN).getLoggedIn()).isTrue());

        when("the token is logged out", () ->
                tokens.logoutToken(SOFT_TOKEN)
                        .statusCode(200));

        then("the token reports logged-out state", () ->
                assertThat(tokens.getToken(SOFT_TOKEN).getLoggedIn()).isFalse());

        and("logging back in with the correct PIN succeeds", () ->
                tokens.loginToken(SOFT_TOKEN, SsBaselineSeeder.SS_TOKEN_PIN)
                        .statusCode(200));

        and("the token is logged in again", () ->
                assertThat(tokens.getToken(SOFT_TOKEN).getLoggedIn()).isTrue());
    }

    // MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "New key with with empty label is created"
    @Test
    @DisplayName("Adding keys increments key counts on softToken-0")
    void newKeyWithEmptyLabelIncrementsCounts(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);
        var ns = Long.toHexString(System.nanoTime());

        var tokenBefore = when("the initial key count is recorded", () ->
                tokens.getToken(SOFT_TOKEN));

        and("a key with namespaced label is added to softToken-0", () ->
                tokens.addKey(SOFT_TOKEN, "key-count-k1-" + ns));

        and("a second key with namespaced label is added to softToken-0", () ->
                tokens.addKey(SOFT_TOKEN, "key-count-k2-" + ns));

        then("the token has more keys than before the additions", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            assertThat(token.getKeys().size()).isGreaterThan(tokenBefore.getKeys().size());
        });
    }

    // MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "CSR can be deleted"
    @Test
    @DisplayName("A generated CSR can be deleted and the key no longer lists it")
    void csrCanBeDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);

        var signView = given("a SIGN key + CSR is created for namespace 'csrdel-sign'", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "csrdel-sign", KeyUsageTypeDto.SIGNING,
                        TEST_CA, CsrFormatDto.PEM, MEMBER_ID));

        var authView = given("an AUTH key + CSR is created for namespace 'csrdel-auth'", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "csrdel-auth", KeyUsageTypeDto.AUTHENTICATION,
                        TEST_CA, CsrFormatDto.PEM, null));

        when("the AUTH CSR is deleted", () ->
                tokens.deleteCsr(authView.keyId(), authView.csrId())
                        .statusCode(204));

        then("the AUTH key has no CSRs", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var authKey = token.getKeys().stream()
                    .filter(k -> authView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(authKey.getCertificateSigningRequests()).isEmpty();
        });

        when("the SIGN CSR is deleted", () ->
                tokens.deleteCsr(signView.keyId(), signView.csrId())
                        .statusCode(204));

        then("the SIGN key has no CSRs", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var signKey = token.getKeys().stream()
                    .filter(k -> signView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(signKey.getCertificateSigningRequests()).isEmpty();
        });
    }

    // MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "Generating multiple CSR for key"
    @Test
    @DisplayName("Multiple CSRs can be generated for a single key and all are listed")
    void generatingMultipleCsrForKey(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);

        var keyView = given("a key 'multi-csr-key' is created with an initial CSR", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "multi-csr-key", KeyUsageTypeDto.AUTHENTICATION,
                        TEST_CA, CsrFormatDto.PEM, null));

        when("3 additional CSRs are generated for the same key with distinct subject CNs", () -> {
            tokens.generateCsr(keyView.keyId(), KeyUsageTypeDto.AUTHENTICATION, TEST_CA, CsrFormatDto.DER, null, "multi-csr-host-2");
            tokens.generateCsr(keyView.keyId(), KeyUsageTypeDto.AUTHENTICATION, TEST_CA, CsrFormatDto.DER, null, "multi-csr-host-3");
            tokens.generateCsr(keyView.keyId(), KeyUsageTypeDto.AUTHENTICATION, TEST_CA, CsrFormatDto.DER, null, "multi-csr-host-4");
        });

        then("the key has 4 CSRs in total", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var key = token.getKeys().stream()
                    .filter(k -> keyView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(key.getCertificateSigningRequests()).hasSize(4);
        });
    }

    // MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "<$label> key is added and imported"
    @Test
    @DisplayName("Signing CSR and importing cert: SIGN cert gets REGISTERED/OCSP_RESPONSE_GOOD, AUTH cert gets SAVED/DISABLED")
    @SneakyThrows
    void keyAddedAndImportedStatusAssertions(SsBaselineSeeder seeder, SsApiTestContainerSetup stack) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);
        var testCaMapping = stack.getContainerMapping(SsApiTestContainerSetup.TESTCA, Port.TEST_CA);
        var testCaBaseUrl = "http://%s:%d/testca".formatted(testCaMapping.host(), testCaMapping.port());

        var signView = given("a SIGN key 'test signing key' is created with a CSR for DEV:COM:1234", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "test signing key", KeyUsageTypeDto.SIGNING,
                        TEST_CA, CsrFormatDto.PEM, MEMBER_ID));

        var authView = given("an AUTH key 'test auth key' is created with a CSR", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "test auth key", KeyUsageTypeDto.AUTHENTICATION,
                        TEST_CA, CsrFormatDto.PEM, null));

        var signCert = when("the SIGN CSR is downloaded and submitted to the Test CA for signing", () -> {
            var csrBytes = tokens.downloadCsr(signView.keyId(), signView.csrId(), CsrFormatDto.PEM);
            return signCsrAtTestCa(testCaBaseUrl, csrBytes, "sign");
        });

        var authCert = and("the AUTH CSR is downloaded and submitted to the Test CA for signing", () -> {
            var csrBytes = tokens.downloadCsr(authView.keyId(), authView.csrId(), CsrFormatDto.PEM);
            return signCsrAtTestCa(testCaBaseUrl, csrBytes, "auth");
        });

        when("the SIGN certificate is imported", () ->
                tokens.importCertificate(signCert).statusCode(201));

        and("the AUTH certificate is imported", () ->
                tokens.importCertificate(authCert).statusCode(201));

        then("the SIGN key has a certificate with status REGISTERED and ocsp status OCSP_RESPONSE_GOOD", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var signKey = token.getKeys().stream()
                    .filter(k -> signView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(signKey.getCertificates()).isNotEmpty();
            var cert = signKey.getCertificates().getFirst();
            assertThat(cert.getStatus()).isEqualTo(CertificateStatusDto.REGISTERED);
            assertThat(cert.getOcspStatus()).isEqualTo(CertificateOcspStatusDto.OCSP_RESPONSE_GOOD);
            assertThat(cert.getNextAutomaticRenewalTime()).isNotNull();
        });

        and("the AUTH key has a certificate with status SAVED and ocsp status DISABLED", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var authKey = token.getKeys().stream()
                    .filter(k -> authView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(authKey.getCertificates()).isNotEmpty();
            var cert = authKey.getCertificates().getFirst();
            assertThat(cert.getStatus()).isEqualTo(CertificateStatusDto.SAVED);
            assertThat(cert.getOcspStatus()).isEqualTo(CertificateOcspStatusDto.DISABLED);
        });
    }

    // MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "New key with with empty label is created"
    // Specifically the auth/sign key count split assertion.
    @Test
    @DisplayName("After adding a SIGN key with CSR and an AUTH key with CSR each type count increases independently")
    void authSignKeySplitAfterAddingKeyWithCsr(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);
        var ns = Long.toHexString(System.nanoTime());

        var tokenBefore = given("the current auth and sign key counts are recorded", () ->
                tokens.getToken(SOFT_TOKEN));

        var authCountBefore = tokenBefore.getKeys().stream()
                .filter(k -> k.getUsage() == KeyUsageTypeDto.AUTHENTICATION)
                .count();
        var signCountBefore = tokenBefore.getKeys().stream()
                .filter(k -> k.getUsage() == KeyUsageTypeDto.SIGNING)
                .count();

        when("a SIGN key with CSR and an AUTH key with CSR are created", () -> {
            tokens.addKeyWithCsr(SOFT_TOKEN, "split-sign-" + ns, KeyUsageTypeDto.SIGNING,
                    TEST_CA, CsrFormatDto.PEM, MEMBER_ID);
            tokens.addKeyWithCsr(SOFT_TOKEN, "split-auth-" + ns, KeyUsageTypeDto.AUTHENTICATION,
                    TEST_CA, CsrFormatDto.PEM, null);
        });

        then("both auth and sign key counts each increased by exactly 1", () -> {
            var tokenAfter = tokens.getToken(SOFT_TOKEN);
            var authCountAfter = tokenAfter.getKeys().stream()
                    .filter(k -> k.getUsage() == KeyUsageTypeDto.AUTHENTICATION)
                    .count();
            var signCountAfter = tokenAfter.getKeys().stream()
                    .filter(k -> k.getUsage() == KeyUsageTypeDto.SIGNING)
                    .count();
            assertThat(authCountAfter).isEqualTo(authCountBefore + 1);
            assertThat(signCountAfter).isEqualTo(signCountBefore + 1);
        });
    }

    // MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "<$label> key is added and imported"
    // Specifically the "generate CSR button is disabled" postcondition — asserted via possible_actions.
    @Test
    @DisplayName("After importing a cert on a SIGN key, GENERATE_SIGN_CSR is absent from possible_actions")
    @SneakyThrows
    void generateCsrDisabledAfterCertImport(SsBaselineSeeder seeder, SsApiTestContainerSetup stack) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);
        var testCaMapping = stack.getContainerMapping(SsApiTestContainerSetup.TESTCA, Port.TEST_CA);
        var testCaBaseUrl = "http://%s:%d/testca".formatted(testCaMapping.host(), testCaMapping.port());

        var signView = given("a SIGN key with a CSR is created", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "csr-disabled-sign", KeyUsageTypeDto.SIGNING,
                        TEST_CA, CsrFormatDto.PEM, MEMBER_ID));

        var signCert = when("the SIGN CSR is signed by the test CA", () -> {
            var csrBytes = tokens.downloadCsr(signView.keyId(), signView.csrId(), CsrFormatDto.PEM);
            return signCsrAtTestCa(testCaBaseUrl, csrBytes, "sign");
        });

        and("the SIGN certificate is imported", () ->
                tokens.importCertificate(signCert).statusCode(201));

        then("GENERATE_SIGN_CSR is not present in the key's possible_actions", () -> {
            List<String> actions = session.given()
                    .get("/keys/{id}/possible-actions", signView.keyId())
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList("$");
            assertThat(actions).doesNotContain("GENERATE_SIGN_CSR");
        });
    }

    @SneakyThrows
    private byte[] signCsrAtTestCa(String testCaBaseUrl, byte[] csrBytes, String type) {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .multiPart("certreq", type + ".pem", csrBytes, "application/octet-stream")
                .multiPart("type", type)
                .post(testCaBaseUrl + "/sign")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }
}
