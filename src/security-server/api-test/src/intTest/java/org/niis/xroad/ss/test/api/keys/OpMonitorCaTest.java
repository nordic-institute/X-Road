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
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.AdminUsersAdminClient;
import org.niis.xroad.ss.test.api.admin.SystemAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.notNullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

@DisplayName("Op-monitor CA — upload and details")
@ResourceLock("op-monitor-ca")
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorCaTest extends SsApiTest {

    @Test
    @DisplayName("a PEM root CA, a PEM intermediate CA and a DER CA are each stored and returned with their details")
    void validCaFilesAreStored(SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());
        var rootKeyPair = newKeyPair();
        var root = ca("CN=Root CA " + unique(), rootKeyPair, "CN=Root CA " + unique(), rootKeyPair, new BasicConstraints(true), -1, 365);
        var intermediate = ca("CN=Root CA", rootKeyPair, "CN=Intermediate CA " + unique(), newKeyPair(), new BasicConstraints(0), -1, 365);
        var der = ca("CN=Der CA " + unique(), rootKeyPair, "CN=Der CA " + unique(), rootKeyPair, new BasicConstraints(true), -1, 365);

        for (var upload : List.of(new Upload("PEM root CA", root, pem(root)),
                new Upload("PEM intermediate CA", intermediate, pem(intermediate)),
                new Upload("DER CA", der, derBytes(der)))) {
            given("the " + upload.name() + " is uploaded", () ->
                    client.uploadOpMonitorCa(upload.bytes())
                            .statusCode(200)
                            .body("hash", equalToIgnoringCase(sha256Hex(upload.certificate())))
                            .body("subject_distinguished_name", notNullValue()));

            then("the details report it as the stored CA", () ->
                    client.getOpMonitorCa()
                            .statusCode(200)
                            .body("hash", equalToIgnoringCase(sha256Hex(upload.certificate())))
                            .body("issuer_distinguished_name", notNullValue())
                            .body("not_before", notNullValue())
                            .body("not_after", notNullValue()));
        }
    }

    @Test
    @DisplayName("each invalid input is rejected with 400 and its own error code, and the stored CA is unchanged")
    void invalidInputsAreRejectedAndTheStoredCaIsUnchanged(SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());
        var keyPair = newKeyPair();
        var stored = ca("CN=Stored CA " + unique(), keyPair, "CN=Stored CA " + unique(), keyPair, new BasicConstraints(true), -1, 365);
        var other = ca("CN=Other CA " + unique(), keyPair, "CN=Other CA " + unique(), keyPair, new BasicConstraints(true), -1, 365);
        var leaf = ca("CN=Stored CA", keyPair, "CN=Op-monitor " + unique(), newKeyPair(), new BasicConstraints(false), -1, 365);
        var expired = ca("CN=Old CA", keyPair, "CN=Old CA " + unique(), keyPair, new BasicConstraints(true), -30, -1);
        var notYetValid = ca("CN=Future CA", keyPair, "CN=Future CA " + unique(), keyPair, new BasicConstraints(true), 1, 30);
        var privateKey = pemObject(keyPair.getPrivate());
        var random = new byte[128];
        new Random(7).nextBytes(random);
        random[0] = 1;

        given("a CA is stored", () -> client.uploadOpMonitorCa(pem(stored)).statusCode(200));

        for (var rejection : List.of(
                new Rejection("empty", new byte[0], "ca_cert_empty"),
                new Rejection("random bytes", random, "ca_cert_not_a_certificate"),
                new Rejection("two certificates", concat(pem(stored), pem(other)), "ca_cert_multiple_certificates"),
                new Rejection("certificate plus key", concat(pem(other), privateKey), "ca_cert_key_material"),
                new Rejection("key only", privateKey, "ca_cert_key_material"),
                new Rejection("leaf certificate", pem(leaf), "ca_cert_not_a_ca"),
                new Rejection("expired certificate", pem(expired), "ca_cert_expired"),
                new Rejection("not yet valid certificate", pem(notYetValid), "ca_cert_not_yet_valid"))) {
            var body = given("a " + rejection.name() + " is uploaded", () ->
                    client.uploadOpMonitorCa(rejection.bytes())
                            .statusCode(400)
                            .body("error.code", equalTo(rejection.errorCode()))
                            .extract().body().asString());

            then("the error body does not echo the upload", () -> {
                assertThat(body).doesNotContain("PRIVATE KEY").doesNotContain("BEGIN CERTIFICATE");
                assertThat(body).doesNotContain(Base64Fragment.of(rejection.bytes()));
            });

            and("the stored CA is unchanged", () ->
                    client.getOpMonitorCa()
                            .statusCode(200)
                            .body("hash", equalToIgnoringCase(sha256Hex(stored))));
        }
    }

    @Test
    @DisplayName("uploading the same certificate again returns 200 and keeps the stored CA")
    void reUploadingTheSameCertificateIsIdempotent(SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());
        var keyPair = newKeyPair();
        var ca = ca("CN=Idempotent CA " + unique(), keyPair, "CN=Idempotent CA " + unique(), keyPair, new BasicConstraints(true), -1, 365);

        given("a CA is uploaded", () -> client.uploadOpMonitorCa(pem(ca)).statusCode(200));
        var again = and("the same CA is uploaded again", () ->
                client.uploadOpMonitorCa(pem(ca)).statusCode(200).extract().body().asString());

        then("the stored CA is still that CA", () -> {
            assertThat(again).containsIgnoringCase(sha256Hex(ca));
            client.getOpMonitorCa().statusCode(200).body("hash", equalToIgnoringCase(sha256Hex(ca)));
        });
    }

    @Test
    @DisplayName("uploading a different certificate replaces the stored one")
    void aDifferentCertificateReplacesTheStoredOne(SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());
        var first = selfSignedCa("CN=First CA " + unique());
        var second = selfSignedCa("CN=Second CA " + unique());

        given("a first CA is stored", () -> client.uploadOpMonitorCa(pem(first)).statusCode(200));
        and("the details show the first CA", () ->
                client.getOpMonitorCa().statusCode(200).body("hash", equalToIgnoringCase(sha256Hex(first))));
        and("a second CA is uploaded", () -> client.uploadOpMonitorCa(pem(second)).statusCode(200));

        then("the details show the second CA", () ->
                client.getOpMonitorCa().statusCode(200).body("hash", equalToIgnoringCase(sha256Hex(second))));
    }

    @Test
    @DisplayName("view roles may read the op-monitor CA, only the security officer and system administrator may upload")
    void roleMapping(SsApiTestContainerSetup stack, SsBaselineSeeder seeder) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());
        var adminUsers = new AdminUsersAdminClient(seeder.newSession());
        var keyPair = newKeyPair();
        var ca = ca("CN=Role CA " + unique(), keyPair, "CN=Role CA " + unique(), keyPair, new BasicConstraints(true), -1, 365);
        var password = "T0pSecret!789";
        given("a CA is stored", () -> new SystemAdminClient(seeder.newSession()).uploadOpMonitorCa(pem(ca)).statusCode(200));

        for (var role : List.of("XROAD_SECURITYSERVER_OBSERVER", "XROAD_SERVICE_ADMINISTRATOR", "XROAD_REGISTRATION_OFFICER")) {
            var username = "op-monitor-ca-" + unique();
            given("a user with only the " + role + " role is created", () ->
                    adminUsers.createUser(username, password, List.of(role)).statusCode(201));
            var user = new SystemAdminClient(new AdminApiSession(uiBaseUrl, username, password));

            then("the user can view the op-monitor CA", () -> user.getOpMonitorCa().statusCode(200));
            and("the user cannot upload it", () -> user.uploadOpMonitorCa(pem(ca)).statusCode(403));

            adminUsers.deleteUser(username).statusCode(200);
        }

        for (var role : List.of("XROAD_SECURITY_OFFICER", "XROAD_SYSTEM_ADMINISTRATOR")) {
            var username = "op-monitor-ca-" + unique();
            given("a user with only the " + role + " role is created", () ->
                    adminUsers.createUser(username, password, List.of(role)).statusCode(201));
            var user = new SystemAdminClient(new AdminApiSession(uiBaseUrl, username, password));

            then("the user can upload the op-monitor CA", () -> user.uploadOpMonitorCa(pem(ca)).statusCode(200));

            adminUsers.deleteUser(username).statusCode(200);
        }
    }

    private record Upload(String name, X509Certificate certificate, byte[] bytes) {
    }

    private record Rejection(String name, byte[] bytes, String errorCode) {
    }

    private static final class Base64Fragment {
        private Base64Fragment() {
        }

        static String of(byte[] upload) {
            var text = new String(upload, StandardCharsets.ISO_8859_1);
            var lines = text.lines().filter(line -> !line.startsWith("-----") && line.length() > 20).toList();
            return lines.isEmpty() ? "\u0000no-fragment\u0000" : lines.getFirst();
        }
    }

    private static String unique() {
        return Long.toString(System.nanoTime(), 36);
    }

    @SneakyThrows
    private static KeyPair newKeyPair() {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCa(String name) {
        var keyPair = newKeyPair();
        return ca(name, keyPair, name, keyPair, new BasicConstraints(true), -1, 365);
    }

    @SneakyThrows
    private static X509Certificate ca(String issuer, KeyPair issuerKeyPair, String subject, KeyPair subjectKeyPair,
                                      BasicConstraints basicConstraints, int notBeforeDaysFromNow, int notAfterDaysFromNow) {
        var builder = new JcaX509v3CertificateBuilder(new X500Name(issuer), BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().plus(notBeforeDaysFromNow, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(notAfterDaysFromNow, ChronoUnit.DAYS)),
                new X500Name(subject), subjectKeyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, basicConstraints);
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    @SneakyThrows
    private static byte[] pem(X509Certificate certificate) {
        return pemObject(certificate);
    }

    @SneakyThrows
    private static byte[] pemObject(Object object) {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(object);
        }
        return writer.toString().getBytes(StandardCharsets.US_ASCII);
    }

    @SneakyThrows
    private static byte[] derBytes(X509Certificate certificate) {
        return certificate.getEncoded();
    }

    @SneakyThrows
    private static String sha256Hex(X509Certificate certificate) {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static byte[] concat(byte[] first, byte[] second) {
        var result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
