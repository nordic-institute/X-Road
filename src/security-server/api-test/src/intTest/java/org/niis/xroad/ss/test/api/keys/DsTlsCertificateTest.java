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
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.DsTlsCertificateAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * API tests for the shared {@code /ds-tls-certificate} admin resource (common-admin-api, XRDDEV-3289 CSR-only
 * rework), limited to what is safe to assert before the DS TLS vault slot has ever been touched. Unlike the
 * server-wide internal TLS certificate (see {@code TlsKeyTest}), the DS TLS slot starts genuinely empty on a
 * fresh stack and stays that way until {@code DsTlsCertificateLifecycleDestructiveTest} generates a key in the
 * destructive phase - so the "nothing provisioned yet" assertions here only hold before that phase runs.
 * The phased suite ({@code SsApiPhasedSuite}) guarantees that ordering: all non-destructive tests (this class
 * included) complete before any destructive test starts.
 */
@DisplayName("DS TLS certificate — before any key has been generated")
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsCertificateTest extends SsApiTest {

    @Test
    @DisplayName("status reports nothing provisioned on a stack where the DS TLS slot was never touched")
    void statusReportsNothingProvisioned(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        client.getStatus()
                .statusCode(200)
                .body("key_generated", equalTo(false))
                .body("certificate", nullValue());
    }

    @Test
    @DisplayName("generating a CSR without a stored key fails with an actionable error")
    void csrGenerationFailsWhenNoKeyGenerated(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        client.generateCsrRaw("CN=ds.example.org")
                .statusCode(404)
                .body("error.code", equalTo("ds_tls_key_not_generated"));
    }

    @Test
    @DisplayName("uploading a certificate without a stored key fails with an actionable error")
    void certificateUploadFailsWhenNoKeyGenerated(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());
        var certBytes = "-----BEGIN CERTIFICATE-----\nirrelevant-no-key-stored-yet\n-----END CERTIFICATE-----\n"
                .getBytes(StandardCharsets.UTF_8);

        client.uploadCertificate(certBytes)
                .statusCode(404)
                .body("error.code", equalTo("ds_tls_key_not_generated"));
    }

    @Test
    @DisplayName("downloading the certificate without one acquired fails with an actionable error")
    void certificateDownloadFailsWhenNoCertificateAcquired(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        client.downloadCertificate()
                .statusCode(404)
                .body("error.code", equalTo("ds_tls_certificate_not_configured"));
    }
}
