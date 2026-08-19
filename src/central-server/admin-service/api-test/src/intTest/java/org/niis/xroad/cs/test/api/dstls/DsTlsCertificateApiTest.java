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
package org.niis.xroad.cs.test.api.dstls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.DsTlsCertificateAdminClient;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for the shared {@code /ds-tls-certificate} admin resource (common-admin-api, XRDDEV-3289 CSR-only
 * rework), exercised here through the Central Server admin API. The private key never crosses the admin API:
 * the resource is backed by the OpenBao {@code tls/ds-https} KV slot, stood in for by the stack's MockServer
 * instance (see {@code ManagementServicesApiTest} for the same pattern against {@code tls/management-service}).
 *
 * <p>All tests but {@link #statusReportsNothingProvisionedWhenVaultSlotIsEmpty} share one canonical
 * "key generated" vault fixture (same RSA keypair, generated once per JVM) so that concurrently running tests
 * registering/clearing the identical MockServer expectation never observe a behavioural difference from the
 * race - only the "nothing provisioned" test needs a genuinely different (404) response, so it alone takes the
 * {@code ds-tls-vault-slot} write lock while every other test holds a read lock.
 */
@SuppressWarnings("checkstyle:magicnumber")
class DsTlsCertificateApiTest extends CsApiTest {

    private static final String VAULT_SECRET_PATH = "/v1/xrd-secret/tls/ds-https";
    private static final String VAULT_RESOURCE_LOCK = "ds-tls-vault-slot";

    private static final KeyPair KEY_PAIR = generateRsaKeyPair();
    private static final String KEY_PAIR_PEM = toPem(KEY_PAIR);
    private static final X509Certificate MATCHING_CERTIFICATE = selfSignedCertificate(KEY_PAIR, "CN=ds.example.org");
    private static final byte[] MATCHING_CERTIFICATE_BYTES = toPemBytes(MATCHING_CERTIFICATE);

    private static final String VAULT_SECRET_NOT_FOUND_MOCK = """
            {
              "httpRequest": {"method": "GET", "path": "%s"},
              "httpResponse": {"statusCode": 404}
            }
            """.formatted(VAULT_SECRET_PATH);

    private static final String VAULT_SECRET_POST_MOCK = """
            {
              "httpRequest": {"method": "POST", "path": "%s"},
              "httpResponse": {"statusCode": 200, "headers": {"Content-Type": ["application/json"]}}
            }
            """.formatted(VAULT_SECRET_PATH);

    private static final String VAULT_SECRET_KEY_ONLY_MOCK = vaultGetMock(KEY_PAIR_PEM, "");
    private static final String VAULT_SECRET_KEY_AND_CERT_MOCK =
            vaultGetMock(KEY_PAIR_PEM, new String(MATCHING_CERTIFICATE_BYTES, StandardCharsets.UTF_8));

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ_WRITE)
    void statusReportsNothingProvisionedWhenVaultSlotIsEmpty(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered to return 404 (empty slot)", () ->
                seeder.mockExpectation(VAULT_SECRET_NOT_FOUND_MOCK));
        try {
            then("status reports no key generated and no certificate", () ->
                    client.getStatus()
                            .statusCode(200)
                            .body("key_generated", equalTo(false))
                            .body("certificate", nullValue()));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    void keyIsGeneratedForPrivilegedUser(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault POST mock registered for key storage", () -> seeder.mockExpectation(VAULT_SECRET_POST_MOCK));
        try {
            then("generating a DS TLS key returns 201", () ->
                    client.generateKey().statusCode(201));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    void keyGenerationForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newManagementServiceOnlySession());

        when("management service role attempts to generate a DS TLS key", () ->
                client.generateKey().statusCode(403));
    }

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ)
    void statusReportsKeyGeneratedPendingCertificate(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered with a key but no certificate", () ->
                seeder.mockExpectation(VAULT_SECRET_KEY_ONLY_MOCK));
        try {
            then("status reports key generated with no certificate yet", () ->
                    client.getStatus()
                            .statusCode(200)
                            .body("key_generated", equalTo(true))
                            .body("certificate", nullValue()));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ)
    void csrIsGeneratedFromTheStoredKey(CsBaselineSeeder seeder) throws Exception {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered with the stored key", () ->
                seeder.mockExpectation(VAULT_SECRET_KEY_ONLY_MOCK));
        try {
            var csrBytes = then("generating a CSR returns 200 with a PKCS#10 body", () ->
                    client.generateCsr("CN=ds.example.org")
                            .statusCode(200)
                            .extract().asByteArray());

            and("the returned bytes parse as a PKCS#10 request for the stored key's subject", () -> {
                var csr = parseCsr(csrBytes);
                var publicKeyFromCsr = new JcaPEMKeyConverter().getPublicKey(csr.getSubjectPublicKeyInfo());
                assertEquals(KEY_PAIR.getPublic(), publicKeyFromCsr);
                assertEquals(new X500Name("CN=ds.example.org"), csr.getSubject());
                return null;
            });
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    void csrGenerationFailsWhenNoKeyGenerated(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered to return 404 (empty slot)", () ->
                seeder.mockExpectation(VAULT_SECRET_NOT_FOUND_MOCK));
        try {
            then("generating a CSR without a stored key returns 404 ds_tls_key_not_generated", () ->
                    client.generateCsr("CN=ds.example.org")
                            .statusCode(404)
                            .body("error.code", equalTo("ds_tls_key_not_generated")));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ)
    void certificateChainMatchingTheStoredKeyIsUploaded(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered with the stored key and POST mock for the write-back", () -> {
            seeder.mockExpectation(VAULT_SECRET_KEY_ONLY_MOCK);
            seeder.mockExpectation(VAULT_SECRET_POST_MOCK);
        });
        try {
            then("uploading a certificate chain for that key returns the stored certificate's details", () ->
                    client.uploadCertificate(MATCHING_CERTIFICATE_BYTES)
                            .statusCode(200)
                            .body("hash", notNullValue())
                            .body("subject_distinguished_name", equalTo("CN=ds.example.org"))
                            .body("public_key_algorithm", equalTo("RSA")));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ)
    void certificateUploadRejectedWhenKeyMismatch(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());
        var mismatchedCertBytes = loadFile("test-data/management-service-mismatched.crt");

        given("vault GET mock registered with the stored key (unrelated to the uploaded certificate)", () ->
                seeder.mockExpectation(VAULT_SECRET_KEY_ONLY_MOCK));
        try {
            then("uploading a certificate for a different key returns 400 ds_tls_key_certificate_mismatch", () ->
                    client.uploadCertificate(mismatchedCertBytes)
                            .statusCode(400)
                            .body("error.code", equalTo("ds_tls_key_certificate_mismatch")));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    void certificateUploadFailsWhenNoKeyGenerated(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered to return 404 (empty slot)", () ->
                seeder.mockExpectation(VAULT_SECRET_NOT_FOUND_MOCK));
        try {
            then("uploading a certificate without a stored key returns 404 ds_tls_key_not_generated", () ->
                    client.uploadCertificate(MATCHING_CERTIFICATE_BYTES)
                            .statusCode(404)
                            .body("error.code", equalTo("ds_tls_key_not_generated")));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ)
    void statusReportsAcquiredCertificateDetails(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered with key and certificate", () ->
                seeder.mockExpectation(VAULT_SECRET_KEY_AND_CERT_MOCK));
        try {
            then("status reports the certificate's details", () ->
                    client.getStatus()
                            .statusCode(200)
                            .body("key_generated", equalTo(true))
                            .body("certificate.subject_distinguished_name", equalTo("CN=ds.example.org"))
                            .body("certificate.hash", notNullValue()));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    @ResourceLock(value = VAULT_RESOURCE_LOCK, mode = ResourceAccessMode.READ)
    void certificateIsDownloadedAsCertOnlyTar(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered with key and certificate", () ->
                seeder.mockExpectation(VAULT_SECRET_KEY_AND_CERT_MOCK));
        try {
            then("downloading the certificate returns a non-empty gzip archive", () -> {
                var body = client.downloadCertificate()
                        .statusCode(200)
                        .extract().asByteArray();
                assertTrue(body.length > 0, "downloaded archive must not be empty");
            });
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    @Test
    void certificateDownloadFailsWhenNoCertificateAcquired(CsBaselineSeeder seeder) {
        var client = new DsTlsCertificateAdminClient(seeder.newSession());

        given("vault GET mock registered to return 404 (empty slot)", () ->
                seeder.mockExpectation(VAULT_SECRET_NOT_FOUND_MOCK));
        try {
            then("downloading without a certificate returns 404 ds_tls_certificate_not_configured", () ->
                    client.downloadCertificate()
                            .statusCode(404)
                            .body("error.code", equalTo("ds_tls_certificate_not_configured")));
        } finally {
            seeder.clearMockExpectations(VAULT_SECRET_PATH);
        }
    }

    private static String vaultGetMock(String privateKeyPem, String certificatePem) {
        return """
                {
                  "httpRequest": {"method": "GET", "path": "%s"},
                  "httpResponse": {
                    "statusCode": 200,
                    "headers": {"Content-Type": ["application/json"]},
                    "body": {
                      "type": "JSON",
                      "json": {
                        "renewable": false,
                        "lease_duration": 0,
                        "data": {
                          "certificate": "%s",
                          "privateKey": "%s"
                        }
                      }
                    }
                  }
                }
                """.formatted(VAULT_SECRET_PATH, escapePem(certificatePem), escapePem(privateKeyPem));
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair for DS TLS API tests", e);
        }
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair, String subjectDn) {
        try {
            var subject = new X500Name(subjectDn);
            var certBuilder = new JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(System.nanoTime()),
                    Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                    Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                    subject,
                    keyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build self-signed DS TLS test certificate", e);
        }
    }

    private static String toPem(KeyPair keyPair) {
        try {
            var stringWriter = new StringWriter();
            try (var pemWriter = new PemWriter(stringWriter)) {
                pemWriter.writeObject(new PemObject("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            }
            return stringWriter.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to PEM-encode DS TLS test private key", e);
        }
    }

    private static byte[] toPemBytes(X509Certificate certificate) {
        try {
            var stringWriter = new StringWriter();
            try (var pemWriter = new PemWriter(stringWriter)) {
                pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
            }
            return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to PEM-encode DS TLS test certificate", e);
        }
    }

    private static String escapePem(String pem) {
        return pem.replace("\r\n", "\\n").replace("\n", "\\n");
    }

    private static PKCS10CertificationRequest parseCsr(byte[] csrBytes) throws Exception {
        try (var pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(csrBytes), StandardCharsets.UTF_8))) {
            return (PKCS10CertificationRequest) pemParser.readObject();
        }
    }

    private static byte[] loadFile(String resourcePath) {
        try (var stream = ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load resource: " + resourcePath, e);
        }
    }
}
