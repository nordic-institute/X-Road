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

import io.restassured.response.ValidatableResponse;
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
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.AdminApiSession;
import org.niis.xroad.ss.test.api.admin.DiagnosticsAdminClient;
import org.niis.xroad.ss.test.api.admin.SystemAdminClient;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Admin-service trust of the local op-monitor with the RPC TLS on: a stored op-monitor CA replaces the
 * Security Server CA as the trust anchor when the admin-service channel is created, and deleting it
 * restores trust after the next restart.
 */
@DisplayName("Op-monitor CA trust on the admin-service channel (destructive lane)")
@ResourceLock(value = "op-monitor-ca", mode = READ_WRITE)
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorCaTrustDestructiveTest extends SsSharedStackDestructiveTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(120);
    private static final String NETWORK_ERROR_CODE = "core.op_monitor.network_error";

    @Test
    @DisplayName("an unrelated stored CA breaks the intervals call after an admin service restart, and deleting it restores the call")
    void unrelatedCaBreaksTheIntervalsCallAfterRestartAndDeleteRestoresIt(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        given("the intervals call works with no op-monitor CA stored", () ->
                assertThat(intervalsStatus(uiBaseUrl)).isEqualTo(200));

        try {
            when("an unrelated CA is uploaded", () ->
                    new SystemAdminClient(new AdminApiSession(uiBaseUrl))
                            .uploadOpMonitorCa(selfSignedCaPem("CN=Unrelated CA " + System.nanoTime()))
                            .statusCode(200));

            then("the intervals call still works on the already created channel", () ->
                    assertThat(intervalsStatus(uiBaseUrl)).isEqualTo(200));

            when("the admin service is restarted", () -> restartAdminService(stack, uiBaseUrl));

            then("the intervals call fails because the Security Server CA is no longer trusted", () ->
                    intervals(uiBaseUrl)
                            .statusCode(500)
                            .body("error.code", equalTo(NETWORK_ERROR_CODE)));
        } finally {
            restoreTrust(stack, uiBaseUrl);
        }

        then("the intervals call works again after the CA is deleted and the admin service restarted", () ->
                assertThat(intervalsStatus(uiBaseUrl)).isEqualTo(200));
    }

    private static void restoreTrust(SsApiTestContainerSetup stack, String uiBaseUrl) {
        try {
            and("the op-monitor CA is deleted", () ->
                    new SystemAdminClient(new AdminApiSession(uiBaseUrl)).deleteOpMonitorCa().statusCode(204));
        } finally {
            and("the admin service is restarted", () -> restartAdminService(stack, uiBaseUrl));
        }
    }

    private static int intervalsStatus(String uiBaseUrl) {
        return intervals(uiBaseUrl).extract().statusCode();
    }

    private static ValidatableResponse intervals(String uiBaseUrl) {
        var now = Instant.now();
        return new DiagnosticsAdminClient(new AdminApiSession(uiBaseUrl))
                .getOperationalMonitoringIntervals(now.minus(Duration.ofMinutes(5)), now, 1);
    }

    private static void restartAdminService(SsApiTestContainerSetup stack, String uiBaseUrl) {
        stack.restartService(SsApiTestContainerSetup.UI);
        await()
                .pollDelay(Duration.ofSeconds(8))
                .pollInterval(POLL_INTERVAL)
                .atMost(POLL_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> AdminApiSession.silent(uiBaseUrl));
    }

    @SneakyThrows
    private static byte[] selfSignedCaPem(String subject) {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        var name = new X500Name(subject);
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)), Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                name, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(certificate);
        }
        return writer.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
