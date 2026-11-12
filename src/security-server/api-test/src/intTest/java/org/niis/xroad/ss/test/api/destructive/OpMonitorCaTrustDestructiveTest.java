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
import jakarta.annotation.Nullable;
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
import org.niis.xroad.ss.test.api.admin.SystemParametersAdminClient;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that an imported op-monitor CA replaces the Security Server RPC CA for the
 * admin-service channel, and that deleting it restores the default trust after restart.
 * The test temporarily shortens the channel deadline to bound how long the expected failure
 * takes: the client waits for the channel to become ready, so the failed call ends at the deadline.
 * The admin service log shows the switch to the imported CA. The previous deadline is restored
 * before the final restart.
 */
@DisplayName("Op-monitor CA trust on the admin-service channel (destructive lane)")
@ResourceLock(value = "op-monitor-ca", mode = READ_WRITE)
@ResourceLock(value = "configurable-property", mode = READ_WRITE)
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorCaTrustDestructiveTest extends SsSharedStackDestructiveTest {

    private static final Duration RESTART_POLL_DELAY = Duration.ofSeconds(1);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(120);
    private static final String NETWORK_ERROR_CODE = "core.op_monitor.network_error";
    private static final String OP_MONITOR_DEADLINE = "xroad.common-rpc.channel.op-monitor.deadline-after";
    private static final String SHORT_OP_MONITOR_DEADLINE_MILLIS = "5000";
    private static final String IMPORTED_CA_TRUST_LOG = "Trusting only the imported op-monitor CA on the op-monitor channel";
    private static final Duration LOG_POLL_TIMEOUT = Duration.ofSeconds(10);

    @Test
    @DisplayName("an unrelated stored CA breaks the intervals call after an admin service restart, and deleting it restores the call")
    void unrelatedCaBreaksTheIntervalsCallAfterRestartAndDeleteRestoresIt(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var uiBaseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        given("the intervals call works with no op-monitor CA stored", () ->
                assertThat(intervalsStatus(uiBaseUrl)).isEqualTo(200));

        var previousDeadline = and("the effective op-monitor deadline is captured for restore", () ->
                effectiveOpMonitorDeadline(uiBaseUrl));

        try {
            and("the op-monitor deadline is shortened to %s ms, bounding the expected failure of the channel created at the next restart"
                    .formatted(SHORT_OP_MONITOR_DEADLINE_MILLIS), () ->
                    systemParameters(uiBaseUrl).updateConfigurableProperty(OP_MONITOR_DEADLINE, SHORT_OP_MONITOR_DEADLINE_MILLIS)
                            .statusCode(204));

            when("an unrelated CA is uploaded", () ->
                    new SystemAdminClient(new AdminApiSession(uiBaseUrl))
                            .uploadOpMonitorCa(selfSignedCaPem("CN=Unrelated CA " + System.nanoTime()))
                            .statusCode(200));

            then("the intervals call still works on the already created channel", () ->
                    assertThat(intervalsStatus(uiBaseUrl)).isEqualTo(200));

            var trustLogLinesBeforeRestart = and("the admin service trust log lines are counted before the restart", () ->
                    countImportedCaTrustLogLines(stack));

            when("the admin service is restarted", () -> restartAdminService(stack, uiBaseUrl));

            then("the intervals call fails with a network error at the %s ms deadline: the Security Server CA is no longer trusted"
                    .formatted(SHORT_OP_MONITOR_DEADLINE_MILLIS), () ->
                    intervals(uiBaseUrl)
                            .statusCode(500)
                            .body("error.code", equalTo(NETWORK_ERROR_CODE)));

            and("the admin service logged after the restart that it trusts only the imported op-monitor CA", () ->
                    await()
                            .pollInterval(POLL_INTERVAL)
                            .atMost(LOG_POLL_TIMEOUT)
                            .untilAsserted(() -> assertThat(countImportedCaTrustLogLines(stack))
                                    .isGreaterThan(trustLogLinesBeforeRestart)));
        } catch (Throwable failure) {
            restoreTrust(stack, uiBaseUrl, previousDeadline, failure);
            throw failure;
        }
        restoreTrust(stack, uiBaseUrl, previousDeadline, null);

        then("the intervals call works again after the CA is deleted and the admin service restarted", () ->
                assertThat(intervalsStatus(uiBaseUrl)).isEqualTo(200));

        and("the op-monitor deadline is back at its previous effective value", () ->
                assertThat(effectiveOpMonitorDeadline(uiBaseUrl)).isEqualTo(previousDeadline));
    }

    /**
     * Attempts every cleanup step even when an earlier one fails. An admin service that does not answer is restarted
     * before the CA delete and the deadline restore, and a final restart applies the restored configuration.
     * Cleanup failures are suppressed into {@code testFailure} when there is one; otherwise the first cleanup failure
     * is thrown with the rest suppressed.
     */
    @SneakyThrows
    private static void restoreTrust(SsApiTestContainerSetup stack, String uiBaseUrl, String previousDeadline,
                                     @Nullable Throwable testFailure) {
        var cleanupFailures = new ArrayList<Throwable>();
        if (!attempt(cleanupFailures, () -> and("the admin service answers before the cleanup", () ->
                awaitAdminService(uiBaseUrl)))) {
            attempt(cleanupFailures, () -> and("the unresponsive admin service is restarted before the cleanup", () ->
                    restartAdminService(stack, uiBaseUrl)));
        }
        attempt(cleanupFailures, () -> and("the op-monitor CA is deleted", () ->
                new SystemAdminClient(new AdminApiSession(uiBaseUrl)).deleteOpMonitorCa().statusCode(204)));
        attempt(cleanupFailures, () -> and("the op-monitor deadline is restored to %s ms".formatted(previousDeadline), () ->
                systemParameters(uiBaseUrl).updateConfigurableProperty(OP_MONITOR_DEADLINE, previousDeadline)
                        .statusCode(204)));
        attempt(cleanupFailures, () -> and("the admin service is restarted to apply the restored configuration", () ->
                restartAdminService(stack, uiBaseUrl)));

        if (testFailure != null) {
            cleanupFailures.forEach(testFailure::addSuppressed);
        } else if (!cleanupFailures.isEmpty()) {
            var failure = cleanupFailures.removeFirst();
            cleanupFailures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static boolean attempt(List<Throwable> failures, Runnable step) {
        try {
            step.run();
            return true;
        } catch (Throwable failure) {
            failures.add(failure);
            return false;
        }
    }

    private static String effectiveOpMonitorDeadline(String uiBaseUrl) {
        var value = systemParameters(uiBaseUrl).getConfigurablePropertyEffectiveValue(OP_MONITOR_DEADLINE);
        assertThat(value).as("effective value of %s", OP_MONITOR_DEADLINE).isNotBlank();
        return value;
    }

    private static SystemParametersAdminClient systemParameters(String uiBaseUrl) {
        return new SystemParametersAdminClient(new AdminApiSession(uiBaseUrl));
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
        awaitAdminService(uiBaseUrl);
    }

    private static long countImportedCaTrustLogLines(SsApiTestContainerSetup stack) {
        return stack.containerLogs(SsApiTestContainerSetup.UI).lines()
                .filter(line -> line.contains(IMPORTED_CA_TRUST_LOG))
                .count();
    }

    private static void awaitAdminService(String uiBaseUrl) {
        await()
                .pollDelay(RESTART_POLL_DELAY)
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
