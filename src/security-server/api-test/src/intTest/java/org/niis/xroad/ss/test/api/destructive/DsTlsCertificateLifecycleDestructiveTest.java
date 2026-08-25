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
package org.niis.xroad.ss.test.api.destructive;

import io.restassured.RestAssured;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.DsTlsCertificateAdminClient;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Destructive-lane tests for the shared {@code /ds-tls-certificate} admin resource (common-admin-api,
 * XRDDEV-3289 CSR-only rework). Unlike the server-wide internal TLS certificate ({@link TlsKeyLifecycleDestructiveTest}),
 * generating or replacing the DS TLS key/certificate does not affect the admin endpoint's own serving
 * certificate - but it does mutate the real, persistent OpenBao {@code tls/ds-https} KV slot (this stack runs
 * a real {@code openbao} container, unlike the Central Server api-test suite which stands Vault in with
 * MockServer), so these still run serially on the destructive stack rather than the parallel non-destructive
 * one. Every test starts by generating its own fresh key so it is self-contained regardless of method
 * execution order (the destructive suite randomises method order within the serial phase).
 */
@DisplayName("DS TLS certificate lifecycle — generate, CSR, upload (destructive)")
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsCertificateLifecycleDestructiveTest extends SsSharedStackDestructiveTest {

    @Test
    @DisplayName("Generating a DS TLS key succeeds and status reports it pending a certificate")
    void generatingKeySucceedsAndStatusReflectsKeyGeneratedPending(SsApiTestContainerSetup stack) {
        var client = new DsTlsCertificateAdminClient(adminSession(stack));

        when("a new DS TLS key is generated", () ->
                client.generateKey().statusCode(201));

        then("status reports a key generated and no certificate yet", () ->
                client.getStatus()
                        .statusCode(200)
                        .body("key_generated", equalTo(true))
                        .body("certificate", nullValue()));
    }

    @Test
    @DisplayName("A CSR generated from the stored key is a well-formed PKCS#10 request for the requested subject")
    @SneakyThrows
    void csrIsGeneratedForTheGeneratedKey(SsApiTestContainerSetup stack) {
        var client = new DsTlsCertificateAdminClient(adminSession(stack));

        given("a DS TLS key is generated", () ->
                client.generateKey().statusCode(201));

        var csrBytes = when("a CSR is generated for CN=ds.example.org", () ->
                client.generateCsr("CN=ds.example.org"));

        then("the response is a non-empty PEM certificate request", () -> {
            var pem = new String(csrBytes, StandardCharsets.US_ASCII).trim();
            assertThat(pem).startsWith("-----BEGIN CERTIFICATE REQUEST-----");
        });

        and("it parses as a PKCS#10 request whose subject matches CN=ds.example.org", () -> {
            var csr = parsePkcs10(csrBytes);
            assertThat(csr.getSubject()).isEqualTo(new X500Name("CN=ds.example.org"));
        });
    }

    @Test
    @DisplayName("A certificate signed by the test CA for that CSR is accepted and reflected in status and download")
    @SneakyThrows
    void certificateSignedByTestCaIsAcceptedAndReflectedInStatusAndDownload(SsApiTestContainerSetup stack) {
        var client = new DsTlsCertificateAdminClient(adminSession(stack));
        var testCaMapping = stack.getContainerMapping(SsApiTestContainerSetup.TESTCA, Port.TEST_CA);
        var testCaBaseUrl = "http://%s:%d/testca".formatted(testCaMapping.host(), testCaMapping.port());

        given("a DS TLS key is generated", () ->
                client.generateKey().statusCode(201));

        var csrBytes = given("a CSR is generated for CN=ds.example.org", () ->
                client.generateCsr("CN=ds.example.org"));

        var signedCert = when("the CSR is signed by the test CA", () ->
                signCsrAtTestCa(testCaBaseUrl, csrBytes));

        then("the signed certificate bytes are non-empty", () ->
                assertThat(signedCert).isNotEmpty());

        when("the signed certificate is uploaded", () ->
                client.uploadCertificate(signedCert)
                        .statusCode(200)
                        .body("subject_distinguished_name", equalTo("CN=ds.example.org"))
                        .body("hash", notNullValue()));

        then("status reports the acquired certificate's details", () ->
                client.getStatus()
                        .statusCode(200)
                        .body("key_generated", equalTo(true))
                        .body("certificate.subject_distinguished_name", equalTo("CN=ds.example.org")));

        var downloaded = and("the certificate is downloaded as a gzip archive", () ->
                client.downloadCertificate()
                        .statusCode(200)
                        .extract().asByteArray());

        var entries = and("the tar.gz archive is decompressed and its entries are extracted", () ->
                extractTarEntries(downloaded));

        then("the archive contains a parseable X.509 certificate entry", () -> {
            assertThat(entries).isNotEmpty();
            entries.values().forEach(DsTlsCertificateLifecycleDestructiveTest::parseX509Certificate);
        });
    }

    @Test
    @DisplayName("Uploading a certificate for a different key is rejected as a key/certificate mismatch")
    @SneakyThrows
    void uploadingCertificateForADifferentKeyIsRejected(SsApiTestContainerSetup stack) {
        var client = new DsTlsCertificateAdminClient(adminSession(stack));

        given("a DS TLS key is generated", () ->
                client.generateKey().statusCode(201));

        var unrelatedCert = and("an unrelated self-signed certificate is built for a different key pair", () ->
                selfSignedCertificateBytes("CN=unrelated-ds-tls-key"));

        then("uploading it is rejected as a key/certificate mismatch", () ->
                client.uploadCertificate(unrelatedCert)
                        .statusCode(400)
                        .body("error.code", equalTo("ds_tls_key_certificate_mismatch")));
    }

    private AdminApiSession adminSession(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return new AdminApiSession("https://%s:%d".formatted(uiMapping.host(), uiMapping.port()));
    }

    @SneakyThrows
    private byte[] signCsrAtTestCa(String testCaBaseUrl, byte[] csrBytes) {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .multiPart("certreq", "ds-tls.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "auth")
                .post(testCaBaseUrl + "/sign")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }

    @SneakyThrows
    private PKCS10CertificationRequest parsePkcs10(byte[] pemCsrBytes) {
        var pem = new String(pemCsrBytes, StandardCharsets.US_ASCII).trim();
        var body = pem
                .replace("-----BEGIN CERTIFICATE REQUEST-----", "")
                .replace("-----END CERTIFICATE REQUEST-----", "")
                .replaceAll("\\s+", "");
        var der = Base64.getDecoder().decode(body);
        return new PKCS10CertificationRequest(der);
    }

    @SneakyThrows
    private static byte[] selfSignedCertificateBytes(String subjectDn) {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();

        var subject = new X500Name(subjectDn);
        var certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));

        var pem = new StringBuilder();
        pem.append("-----BEGIN CERTIFICATE-----\n");
        var encoded = Base64.getEncoder().encodeToString(cert.getEncoded());
        for (int i = 0; i < encoded.length(); i += 64) {
            pem.append(encoded, i, Math.min(i + 64, encoded.length())).append('\n');
        }
        pem.append("-----END CERTIFICATE-----\n");
        return pem.toString().getBytes(StandardCharsets.UTF_8);
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
