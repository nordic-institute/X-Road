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

import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.AdminUsersAdminClient;
import org.niis.xroad.ss.test.api.admin.SystemAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

@DisplayName("Internal RPC CA — details and download")
@SuppressWarnings("checkstyle:magicnumber")
class InternalRpcCaTest extends SsApiTest {

    private static final Pattern PEM_CERTIFICATE_BLOCK = Pattern.compile(
            "\\A-----BEGIN CERTIFICATE-----\\R([A-Za-z0-9+/=]+\\R)+-----END CERTIFICATE-----\\R?\\z");

    @Test
    @DisplayName("details report the subject, issuer, SHA-256 fingerprint and validity of the xrd-pki issuing CA")
    void detailsDescribeTheIssuingCa(SsApiTestContainerSetup stack, SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());
        var expected = openBaoIssuingCa(stack);

        var body = given("the internal RPC CA details are requested", () ->
                client.getInternalRpcCa()
                        .statusCode(200)
                        .body("subject_distinguished_name", notNullValue())
                        .body("issuer_distinguished_name", notNullValue())
                        .body("not_before", notNullValue())
                        .body("not_after", notNullValue())
                        .body("hash", notNullValue())
                        .extract().body().asString());

        then("the subject, issuer and validity are those of the xrd-pki issuing CA", () -> {
            var details = JsonPath.from(body);
            assertThat(details.getString("subject_distinguished_name")).isEqualTo(expected.getSubjectX500Principal().toString());
            assertThat(details.getString("issuer_distinguished_name")).isEqualTo(expected.getIssuerX500Principal().toString());
            assertThat(OffsetDateTime.parse(details.getString("not_before")).toInstant()).isEqualTo(expected.getNotBefore().toInstant());
            assertThat(OffsetDateTime.parse(details.getString("not_after")).toInstant()).isEqualTo(expected.getNotAfter().toInstant());
        });

        and("the fingerprint is the SHA-256 of the xrd-pki issuing CA", () ->
                assertThat(sha256Hex(expected)).isEqualToIgnoringCase(JsonPath.from(body).getString("hash")));

        and("the response carries no private key material", () ->
                assertThat(body).doesNotContain("PRIVATE KEY"));
    }

    @Test
    @DisplayName("download returns exactly one PEM certificate block equal to the xrd-pki issuing CA")
    void downloadReturnsTheIssuingCaAsOnePemBlock(SsApiTestContainerSetup stack, SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());
        var expected = openBaoIssuingCa(stack);

        var bytes = given("the internal RPC CA is downloaded", () ->
                client.downloadInternalRpcCa()
                        .statusCode(200)
                        .header("Content-Type", startsWith("application/x-pem-file"))
                        .extract().asByteArray());

        then("the body is one PEM certificate block and nothing else", () -> {
            var pem = new String(bytes, StandardCharsets.UTF_8);
            assertThat(pem).matches(PEM_CERTIFICATE_BLOCK);
            assertThat(pem).doesNotContain("PRIVATE KEY");
        });

        and("the certificate equals the xrd-pki issuing CA", () ->
                assertThat(parseCertificate(bytes)).isEqualTo(expected));
    }

    @Test
    @DisplayName("downloading twice returns identical bytes")
    void downloadIsRepeatable(SsBaselineSeeder seeder) {
        var client = new SystemAdminClient(seeder.newSession());

        var first = given("the internal RPC CA is downloaded", () ->
                client.downloadInternalRpcCa().statusCode(200).extract().asByteArray());
        var second = and("it is downloaded again", () ->
                client.downloadInternalRpcCa().statusCode(200).extract().asByteArray());

        then("both downloads are identical", () -> assertThat(second).isEqualTo(first));
    }

    @Test
    @DisplayName("an observer can view the details but not download the internal RPC CA")
    void observerCannotDownload(SsApiTestContainerSetup stack, SsBaselineSeeder seeder) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());
        var adminUsers = new AdminUsersAdminClient(seeder.newSession());
        var username = "ca-observer-" + Long.toString(System.nanoTime(), 36);
        var password = "T0pSecret!789";

        given("an observer-only admin user is created", () ->
                adminUsers.createUser(username, password, List.of("XROAD_SECURITYSERVER_OBSERVER")).statusCode(201));

        var observer = new SystemAdminClient(new AdminApiSession(uiBaseUrl, username, password));

        then("the observer sees the details", () ->
                observer.getInternalRpcCa().statusCode(200).body("hash", notNullValue()));

        and("the observer is refused the download", () ->
                observer.downloadInternalRpcCa().statusCode(403));

        adminUsers.deleteUser(username).statusCode(200);
    }

    @SneakyThrows
    private static X509Certificate openBaoIssuingCa(SsApiTestContainerSetup stack) {
        var result = stack.execInContainer(SsApiTestContainerSetup.OPENBAO, "bao", "read", "-field=certificate", "xrd-pki/cert/ca");
        assertThat(result.getExitCode()).as("bao read xrd-pki/cert/ca").isZero();
        return parseCertificate(result.getStdout().getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private static X509Certificate parseCertificate(byte[] pem) {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pem));
    }

    @SneakyThrows
    private static String sha256Hex(X509Certificate certificate) {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }
}
