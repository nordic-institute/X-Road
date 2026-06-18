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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateOcspStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrFormatDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.TokensAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for ACME certificate ordering via the admin API.
 */
@DisplayName("ACME certificate ordering")
@ResourceLock("softToken-0")
@SuppressWarnings("checkstyle:magicnumber")
class AcmeOrderTest extends SsApiTest {

    private static final String SOFT_TOKEN = "0";
    private static final String TEST_CA = "Test CA";

    // MIGRATED-FROM: 0505-ss-keys-and-certificates-acme.feature :: "Certificate ordering is disabled when external account binding credentials are required but missing"
    @Test
    @DisplayName("EAB credentials status endpoint reports EAB required and credentials absent for a member without configured credentials")
    void acmeOrderFailsWhenEabCredentialsMissing(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);
        var memberWithoutEabCreds = "DEV:COM:9999";

        var status = given("the EAB credentials status is queried for a member not in the EAB config", () ->
                tokens.getAcmeEabCredentialsStatus(TEST_CA, KeyUsageTypeDto.SIGNING, memberWithoutEabCreds));

        then("the CA reports EAB is required", () ->
                assertThat(status.getAcmeEabRequired()).isTrue());

        and("the response confirms no credentials exist for this member", () ->
                assertThat(status.getHasAcmeExternalAccountCredentials()).isFalse());
    }

    // MIGRATED-FROM: 0505-ss-keys-and-certificates-acme.feature :: "New key is added certificate ordered and imported" (SIGNING row)
    @Test
    @DisplayName("ACME order on a SIGN key results in REGISTERED status and OCSP_RESPONSE_GOOD")
    void acmeOrderOnSignKeyResultsInRegisteredAndOcspGood(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);

        var keyView = given("a SIGN key with a CSR is created for the ACME order", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "acme-order-sign-key", KeyUsageTypeDto.SIGNING,
                        TEST_CA, CsrFormatDto.DER, "DEV:COM:1234"));

        when("an ACME order is placed for the SIGN CSR", () ->
                tokens.orderAcmeCertificate(TEST_CA, keyView.csrId(), KeyUsageTypeDto.SIGNING)
                        .statusCode(204));

        then("the SIGN key has a certificate with status REGISTERED and OCSP status OCSP_RESPONSE_GOOD", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var key = token.getKeys().stream()
                    .filter(k -> keyView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(key.getCertificates()).isNotEmpty();
            var cert = key.getCertificates().getFirst();
            assertThat(cert.getStatus()).isEqualTo(CertificateStatusDto.REGISTERED);
            assertThat(cert.getOcspStatus()).isEqualTo(CertificateOcspStatusDto.OCSP_RESPONSE_GOOD);
            assertThat(cert.getNextAutomaticRenewalTime()).isNotNull();
        });
    }

    // MIGRATED-FROM: 0505-ss-keys-and-certificates-acme.feature :: "Certificate is ordered on existing CSR"
    @Test
    @DisplayName("Certificate ordered via ACME on an existing CSR results in SAVED status and DISABLED OCSP")
    void certificateOrderedOnExistingCsr(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var tokens = new TokensAdminClient(session);

        var keyView = given("an AUTH key with a CSR is created for the ACME order", () ->
                tokens.addKeyWithCsr(SOFT_TOKEN, "acme-order-auth-key", KeyUsageTypeDto.AUTHENTICATION,
                        TEST_CA, CsrFormatDto.DER, null));

        when("an ACME order is placed for the existing AUTH CSR", () ->
                tokens.orderAcmeCertificate(TEST_CA, keyView.csrId(), KeyUsageTypeDto.AUTHENTICATION)
                        .statusCode(204));

        then("the key has a certificate with status SAVED and OCSP status DISABLED", () -> {
            var token = tokens.getToken(SOFT_TOKEN);
            var key = token.getKeys().stream()
                    .filter(k -> keyView.keyId().equals(k.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(key.getCertificates()).isNotEmpty();
            var cert = key.getCertificates().getFirst();
            assertThat(cert.getStatus()).isEqualTo(CertificateStatusDto.SAVED);
            assertThat(cert.getOcspStatus()).isEqualTo(CertificateOcspStatusDto.DISABLED);
            assertThat(cert.getNextAutomaticRenewalTime()).isNotNull();
        });
    }
}
