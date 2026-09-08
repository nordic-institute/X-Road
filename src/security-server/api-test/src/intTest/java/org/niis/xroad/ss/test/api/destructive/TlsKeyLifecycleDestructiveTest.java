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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.SystemAdminClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Destructive-lane TLS key lifecycle tests. Generating or importing the server-wide internal TLS
 * key/certificate replaces the admin endpoint certificate, breaking any live admin session
 * (observed as {@code 401} on subsequent calls). These run in the destructive phase after all
 * non-destructive tests have finished; each test creates its own fresh {@link AdminApiSession}.
 */
@DisplayName("TLS key lifecycle — generate and import (destructive)")
@SuppressWarnings("checkstyle:magicnumber")
class TlsKeyLifecycleDestructiveTest extends SsSharedStackDestructiveTest {

    private static final int TEST_CA_PORT = 8888;

    @Test
    @DisplayName("Generating a new TLS key and certificate succeeds and the cert info is retrievable")
    void generateNewTlsKeyAndCertificate(SsApiTestContainerSetup stack) {
        var system = new SystemAdminClient(adminSession(stack));

        when("a new TLS key and certificate is generated", () ->
                system.generateTlsKeyAndCertificate()
                        .statusCode(201));

        then("the system certificate info is retrievable and contains subject/issuer info", () -> {
            var cert = system.getSystemCertificate();
            assertThat(cert.getSubjectDistinguishedName()).isNotBlank();
            assertThat(cert.getIssuerDistinguishedName()).isNotBlank();
            assertThat(cert.getHash()).isNotBlank();
        });
    }

    @Test
    @DisplayName("TLS certificate imported after CSR->test-CA signing is accepted and cert info is updated")
    @SneakyThrows
    void importNewTlsCertificateViaCsrAndTestCa(SsApiTestContainerSetup stack) {
        var system = new SystemAdminClient(adminSession(stack));
        var testCaMapping = stack.getContainerMapping(SsApiTestContainerSetup.TESTCA, TEST_CA_PORT);
        var testCaBaseUrl = "http://%s:%d/testca".formatted(testCaMapping.host(), testCaMapping.port());

        var csrBytes = given("a TLS CSR is generated for CN=localhost", () ->
                system.generateCsr("CN=localhost"));

        then("the CSR bytes are non-empty", () ->
                assertThat(csrBytes).isNotEmpty());

        var signedCert = when("the CSR is submitted to the test CA for signing", () ->
                signCsrAtTestCa(testCaBaseUrl, csrBytes));

        then("the signed certificate bytes are non-empty", () ->
                assertThat(signedCert).isNotEmpty());

        var certDetails = when("the signed certificate is imported as the new TLS certificate", () ->
                system.importCertificate(signedCert)
                        .statusCode(200)
                        .extract()
                        .as(CertificateDetailsDto.class));

        then("the returned certificate details contain the expected subject CN=localhost", () -> {
            assertThat(certDetails.getSubjectDistinguishedName()).contains("CN=localhost");
            assertThat(certDetails.getHash()).isNotBlank();
        });
    }

    private AdminApiSession adminSession(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        return new AdminApiSession("https://%s:%d".formatted(uiMapping.host(), uiMapping.port()));
    }

    @SneakyThrows
    private byte[] signCsrAtTestCa(String testCaBaseUrl, byte[] csrBytes) {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .multiPart("certreq", "tls.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "auth")
                .post(testCaBaseUrl + "/sign")
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
    }
}
