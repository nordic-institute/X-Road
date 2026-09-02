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

import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.DsTlsCertificateAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * API tests for the shared {@code /ds-tls-certificate} admin resource (common-admin-api, XRDDEV-3289 CSR-only
 * rework), limited to read-only operations that are safe on the warm substrate. The baseline seeder
 * provisions a real key and test-CA-signed certificate into the {@code tls/ds-https} vault slot through
 * the admin API before any test runs (ds-* components read their serving certificate from that same slot
 * and fail closed otherwise, restarting until it lands), so — unlike the server-wide internal TLS
 * certificate, which starts with packaging defaults — the DS TLS slot is never empty when tests observe it. The empty-slot
 * negative paths (no key generated, no certificate acquired yet) are exercised instead by the Central
 * Server api-test suite (which stands OpenBao in with MockServer and controls the slot's content
 * precisely) and by common-admin-api's unit tests, so that coverage is relocated, not lost. Mutating
 * operations (generating a new key, uploading a certificate) live in
 * {@code DsTlsCertificateLifecycleDestructiveTest} on the disposable destructive stack, including the
 * key/certificate-mismatch negative case.
 */
@DisplayName("DS TLS certificate — read-only, on the pre-seeded stack")
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsCertificateTest extends SsApiTest {

    @Test
    @DisplayName("status reports the seeded key and certificate")
    void statusReportsTheSeededCertificate(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        client.getStatus()
                .statusCode(200)
                .body("key_generated", equalTo(true))
                .body("certificate", notNullValue())
                .body("certificate.subject_distinguished_name", notNullValue());
    }

    @Test
    @DisplayName("a CSR generated for the seeded key is a well-formed PKCS#10 request for the requested subject")
    @SneakyThrows
    void csrGenerationSucceedsForTheSeededKey(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        var csrBytes = given("a CSR is generated for CN=ds.example.org", () ->
                client.generateCsr("CN=ds.example.org"));

        then("the response is a PEM certificate request", () -> {
            var pem = new String(csrBytes, StandardCharsets.US_ASCII).trim();
            assertThat(pem).startsWith("-----BEGIN CERTIFICATE REQUEST-----");
        });

        and("it parses as a PKCS#10 request", () -> parsePkcs10(csrBytes));
    }

    @Test
    @DisplayName("the seeded certificate downloads as a gzip archive containing a parseable X.509 entry")
    @SneakyThrows
    void certificateDownloadSucceedsForTheSeededCertificate(SsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        var downloaded = given("the DS TLS certificate is downloaded", () ->
                client.downloadCertificate()
                        .statusCode(200)
                        .extract().asByteArray());

        var entries = and("the tar.gz archive is decompressed and its entries are extracted", () ->
                extractTarEntries(downloaded));

        then("the archive contains a parseable X.509 certificate entry", () -> {
            assertThat(entries).isNotEmpty();
            entries.values().forEach(DsTlsCertificateTest::parseX509Certificate);
        });
    }

    @SneakyThrows
    private void parsePkcs10(byte[] pemCsrBytes) {
        var pem = new String(pemCsrBytes, StandardCharsets.US_ASCII).trim();
        var body = pem
                .replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("-----END CERTIFICATE REQUEST-----", "")
                .replaceAll("\\s+", "");
        var der = Base64.getDecoder().decode(body);
        var csr = new PKCS10CertificationRequest(der);
        assertThat(csr.getSubject()).isNotNull();
    }

    private static void parseX509Certificate(byte[] certBytes) {
        try {
            var factory = CertificateFactory.getInstance("X.509");
            factory.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse downloaded DS TLS certificate entry as X.509", e);
        }
    }

    @SneakyThrows
    private Map<String, byte[]> extractTarEntries(byte[] tarGzBytes) {
        var result = new HashMap<String, byte[]>();
        try (var gzipStream = new GZIPInputStream(new ByteArrayInputStream(tarGzBytes));
                var tarStream = new TarArchiveInputStream(gzipStream)) {
            var entry = tarStream.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    result.put(entry.getName(), tarStream.readAllBytes());
                }
                entry = tarStream.getNextEntry();
            }
        }
        return result;
    }
}
