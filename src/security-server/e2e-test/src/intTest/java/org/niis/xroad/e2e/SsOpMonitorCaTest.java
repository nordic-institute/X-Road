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

import lombok.SneakyThrows;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.deleteOpMonitorCa;
import static org.niis.xroad.e2e.AdminApi.downloadExternalRpcCa;
import static org.niis.xroad.e2e.AdminApi.getExternalRpcCa;
import static org.niis.xroad.e2e.AdminApi.getOpMonitorCa;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.uploadOpMonitorCa;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * CA certificate exchange through the Security Server admin API against the stack's real OpenBao: view
 * and download of the external RPC CA, upload and view of the op-monitor CA, and its deletion. Request
 * validation and role checks are covered by the Security Server api-test module.
 *
 * <p>Every test leaves no op-monitor CA stored, so the tests are independent of order and of the other
 * scenarios in this suite.
 */
@DisplayName("SS CA exchange - external RPC CA and op-monitor CA")
@Order(65)
@SuppressWarnings("checkstyle:magicnumber")
class SsOpMonitorCaTest extends E2eTest {

    private static final String ENV_NAME = "ss1";
    private static final int HTTP_OK = 200;
    private static final int HTTP_NO_CONTENT = 204;

    @Test
    @DisplayName("the external RPC CA can be viewed and downloaded as one PEM certificate")
    void externalRpcCaCanBeViewedAndDownloaded(E2eEnvironment env) {
        var baseUrl = adminBaseUrl(env, ENV_NAME);
        var session = login(baseUrl);

        var details = given("the external RPC CA details are requested", () -> {
            var response = getExternalRpcCa(baseUrl, session);
            assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
            return response;
        });
        then("the details carry a subject and a fingerprint", () -> {
            assertThat(details.jsonPath().getString("subject_distinguished_name")).isNotBlank();
            assertThat(details.jsonPath().getString("hash")).isNotBlank();
        });

        var download = when("the external RPC CA is downloaded", () -> {
            var response = downloadExternalRpcCa(baseUrl, session);
            assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
            return response.asString();
        });
        then("the download is one PEM certificate without key material", () -> {
            assertThat(download).startsWith("-----BEGIN CERTIFICATE-----");
            assertThat(download.split("-----BEGIN ", -1)).hasSize(2);
            assertThat(download).doesNotContain("PRIVATE KEY");
        });
        and("the downloaded certificate is the one described by the details", () ->
                assertThat(sha256Hex(parseCertificate(download)))
                        .isEqualToIgnoringCase(details.jsonPath().getString("hash")));
    }

    @Test
    @DisplayName("an uploaded op-monitor CA is returned by the details")
    void uploadedOpMonitorCaCanBeViewed(E2eEnvironment env) {
        var baseUrl = adminBaseUrl(env, ENV_NAME);
        var session = login(baseUrl);
        var ca = selfSignedCa("CN=E2E Op-monitor CA " + UUID.randomUUID());

        try {
            given("an op-monitor CA certificate is uploaded", () ->
                    assertThat(uploadOpMonitorCa(baseUrl, session, pem(ca)).getStatusCode()).isEqualTo(HTTP_OK));

            var details = when("the op-monitor CA details are requested", () -> getOpMonitorCa(baseUrl, session));

            then("the details describe the uploaded certificate", () -> {
                assertThat(details.getStatusCode()).isEqualTo(HTTP_OK);
                assertThat(details.jsonPath().getString("hash")).isEqualToIgnoringCase(sha256Hex(ca));
                assertThat(details.jsonPath().getString("subject_distinguished_name")).isNotBlank();
            });
        } finally {
            deleteOpMonitorCa(baseUrl, session);
        }
    }

    @Test
    @DisplayName("a deleted op-monitor CA reads as absent")
    void deletedOpMonitorCaReadsAsAbsent(E2eEnvironment env) {
        var baseUrl = adminBaseUrl(env, ENV_NAME);
        var session = login(baseUrl);
        var ca = selfSignedCa("CN=E2E Op-monitor CA " + UUID.randomUUID());

        try {
            given("an op-monitor CA is stored", () -> {
                assertThat(uploadOpMonitorCa(baseUrl, session, pem(ca)).getStatusCode()).isEqualTo(HTTP_OK);
                assertThat(getOpMonitorCa(baseUrl, session).getStatusCode()).isEqualTo(HTTP_OK);
            });

            when("the op-monitor CA is deleted", () ->
                    assertThat(deleteOpMonitorCa(baseUrl, session).getStatusCode()).isEqualTo(HTTP_NO_CONTENT));

            then("the details report no op-monitor CA", () ->
                    assertThat(getOpMonitorCa(baseUrl, session).getStatusCode()).isEqualTo(HTTP_NO_CONTENT));
        } finally {
            deleteOpMonitorCa(baseUrl, session);
        }
    }

    @SneakyThrows
    private static X509Certificate selfSignedCa(String name) {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var subject = new X500Name(name);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(30, ChronoUnit.DAYS)),
                subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    @SneakyThrows
    private static byte[] pem(X509Certificate certificate) {
        var body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(certificate.getEncoded());
        return ("-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
    }

    @SneakyThrows
    private static X509Certificate parseCertificate(String pem) {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    @SneakyThrows
    private static String sha256Hex(X509Certificate certificate) {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }
}
