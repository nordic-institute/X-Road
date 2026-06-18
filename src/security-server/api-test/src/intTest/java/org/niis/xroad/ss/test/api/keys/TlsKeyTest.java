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
import org.niis.xroad.ss.test.api.admin.SystemAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * API tests for the server-wide TLS certificate, limited to read-only operations that are safe on
 * the warm substrate. The destructive generate/import mutations (which replace the certificate the
 * admin endpoint is served over) live in {@code TlsKeyLifecycleDestructiveTest} on the disposable
 * destructive stack.
 */
@DisplayName("TLS certificate — read-only export")
@SuppressWarnings("checkstyle:magicnumber")
class TlsKeyTest extends SsApiTest {

    // MIGRATED-FROM: 0360-ss-key-and-certificates-tls-key.feature :: "User can export TLS key certificate"
    @Test
    @DisplayName("TLS certificate export returns a non-empty gzip archive")
    void tlsCertificateExportedAsGzipArchive(SsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        var exported = given("the TLS certificate is exported", system::exportCertificate);

        then("the response is a non-empty gzip archive (magic bytes 1f 8b)", () -> {
            assertThat(exported).isNotEmpty();
            assertThat(exported[0] & 0xFF).isEqualTo(0x1f);
            assertThat(exported[1] & 0xFF).isEqualTo(0x8b);
        });
    }

    // MIGRATED-FROM: 0360-ss-key-and-certificates-tls-key.feature :: "User can export TLS key certificate"
    @Test
    @DisplayName("TLS certificate export tar contains cert.cer and cert.pem entries, each parseable as X.509")
    @SneakyThrows
    void tlsCertificateExportTarContainsBothEntries(SsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        var exported = given("the TLS certificate is exported", system::exportCertificate);

        var entries = and("the tar.gz archive is decompressed and its entries are extracted", () ->
                extractTarEntries(exported));

        then("the archive contains './cert.cer' and './cert.pem'", () -> {
            assertThat(entries).containsKeys("./cert.cer", "./cert.pem");
        });

        and("./cert.cer parses as a valid X.509 certificate", () ->
                parseX509Certificate(entries.get("./cert.cer")));

        and("./cert.pem parses as a valid X.509 certificate", () ->
                parseX509Certificate(entries.get("./cert.pem")));
    }

    // MIGRATED-FROM: 0360-ss-key-and-certificates-tls-key.feature :: "User can import new TLS certificate"
    @Test
    @DisplayName("TLS CSR export is a PEM-armored PKCS10 request whose decoded DER begins with ASN.1 SEQUENCE")
    @SneakyThrows
    void tlsCsrExportHasPkcs10Structure(SsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        var csrBytes = given("a TLS CSR is generated for CN=localhost", () ->
                system.generateCsr("CN=localhost"));

        then("the response starts with the PEM header for a certificate request", () -> {
            var pem = new String(csrBytes, StandardCharsets.US_ASCII).trim();
            assertThat(pem).startsWith("-----BEGIN CERTIFICATE REQUEST-----");
        });

        and("the base64-decoded body begins with the DER SEQUENCE tag (0x30), confirming PKCS10/ASN.1 structure", () -> {
            var pem = new String(csrBytes, StandardCharsets.US_ASCII).trim();
            var body = pem
                    .replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                    .replace("-----END CERTIFICATE REQUEST-----", "")
                    .replaceAll("\\s+", "");
            var der = Base64.getDecoder().decode(body);
            assertThat(der[0] & 0xFF).isEqualTo(0x30);
        });

        and("the response parses as a valid PKCS10 CertificationRequest", () -> {
            var pem = new String(csrBytes, StandardCharsets.US_ASCII).trim();
            var body = pem
                    .replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                    .replace("-----END CERTIFICATE REQUEST-----", "")
                    .replaceAll("\\s+", "");
            var der = Base64.getDecoder().decode(body);
            parsePkcs10(der);
        });
    }

    @SneakyThrows
    private void parsePkcs10(byte[] der) {
        var csr = new PKCS10CertificationRequest(der);
        assertThat(csr.getSubject()).isNotNull();
    }

    @SneakyThrows
    private void parseX509Certificate(byte[] certBytes) {
        var factory = CertificateFactory.getInstance("X.509");
        factory.generateCertificate(new ByteArrayInputStream(certBytes));
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
