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

import io.restassured.response.Response;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.e2e.OpMonHostOps.HostCommandResult;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.deleteOpMonitorCa;
import static org.niis.xroad.e2e.AdminApi.listConfigurableProperties;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.opMonitorCaHash;
import static org.niis.xroad.e2e.AdminApi.operationalMonitoringTrafficTotal;
import static org.niis.xroad.e2e.AdminApi.patchConfigurableProperty;
import static org.niis.xroad.e2e.AdminApi.uploadOpMonitorCa;
import static org.niis.xroad.e2e.SsOpMonitorCaTest.parseCertificate;
import static org.niis.xroad.e2e.SsOpMonitorCaTest.pem;
import static org.niis.xroad.e2e.SsOpMonitorCaTest.selfSignedCa;
import static org.niis.xroad.e2e.SsOpMonitorCaTest.sha256Hex;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * ss1 stores operational data in the external op-monitor on {@code xrd-opmon} over mutual TLS: an untrusted
 * CA and a channel host absent from the server certificate's SAN list are rejected, and with the right CA
 * and host the records reach the external database and the Diagnostics Traffic view. All three tests use
 * the same short deadline, so the trusted flow shows that the deadline alone does not cause a rejection.
 * Runs only where a bare op-monitor host exists (LXD).
 *
 * <p>Runs after every other op-monitor class: it restarts the proxy and changes the channel rows and the
 * stored op-monitor CA.
 *
 * <p>Each test saves the op-monitor channel rows and the stored op-monitor CA before changing ss1, and
 * restores and verifies them in a {@code finally} block. The rows are seeded by provisioning; the tests
 * only update their values.
 */
@DisplayName("SS external op-monitor - mutual TLS channel to the bare op-monitor host")
@Order(600)
@ResourceLock(value = "op-monitor-ca", mode = READ_WRITE)
@ResourceLock(value = "configurable-property", mode = READ_WRITE)
@SuppressWarnings("checkstyle:magicnumber")
class SsExternalOpMonitorTest extends E2eTest {

    private static final String SS = "ss1";
    private static final String OPMON = "opmon";
    private static final String HOST_ROW = "xroad.common-rpc.channel.op-monitor.host";
    private static final String PORT_ROW = "xroad.common-rpc.channel.op-monitor.port";
    private static final String DEADLINE_ROW = "xroad.common-rpc.channel.op-monitor.deadline-after";
    private static final String ADDON_FLAG = "xroad.proxy.addon.op-monitor.enabled";
    private static final List<String> CHANNEL_ROWS = List.of(HOST_ROW, PORT_ROW, DEADLINE_ROW);
    private static final String SHORT_DEADLINE = "3000";
    private static final String OPMON_CA_PATH = "/etc/xroad-opmonitor/ssl/ca.crt";
    private static final String OPMON_CERT_PATH = "/etc/xroad-opmonitor/ssl/server.crt";
    private static final String PROXY_LOG = "/var/log/xroad/proxy.log";
    private static final String SEND_FAILED = "Sending operational monitoring data failed";
    private static final String COUNT_SQL = "SELECT count(*) FROM opmonitor.operational_data";
    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration RESTART_POLL_DELAY = Duration.ofSeconds(3);

    @Test
    @DisplayName("an untrusted CA is rejected and nothing reaches the external database")
    void untrustedCaIsRejected(E2eEnvironment env) {
        var fx = prepare(env);
        var untrusted = selfSignedCa("CN=E2E Untrusted Op-monitor CA " + UUID.randomUUID());

        runWithRestore(fx, () -> {
            given("ss1 points at xrd-opmon with a short deadline and trusts a CA that did not sign its identity", () ->
                    configure(fx, fx.opmonHost(), SHORT_DEADLINE, pem(untrusted)));
            then("the stored CA is the self-signed one, not the xrd-opmon CA", () -> {
                var stored = opMonitorCaHash(fx.baseUrl(), session(fx)).orElseThrow();
                assertThat(stored).isEqualTo(sha256Hex(untrusted)).isNotEqualTo(fx.opmonCaHash());
            });

            var count = rowCount(fx);
            var logLines = logLineCount(fx);
            when("a request is sent through the ss1 proxy", () -> sendRequestUntilAnswered(env));
            then("ss1 logs a failed send and the external row count does not increase", () -> {
                awaitLogLine(fx, logLines, line -> line.contains(SEND_FAILED));
                assertThat(rowCount(fx)).isEqualTo(count);
            });
        });
    }

    @Test
    @DisplayName("with the xrd-opmon CA trusted the records reach the external database and the Traffic view")
    void trustedStoreQueryAndTraffic(E2eEnvironment env) {
        var fx = prepare(env);

        runWithRestore(fx, () -> {
            given("ss1 points at xrd-opmon with a short deadline and trusts its CA", () ->
                    configure(fx, fx.opmonHost(), SHORT_DEADLINE, fx.opmonCaPem()));

            var from = Instant.now().minus(Duration.ofMinutes(5));
            var count = rowCount(fx);
            var traffic = operationalMonitoringTrafficTotal(fx.baseUrl(), session(fx), from, Instant.now());
            when("a request is sent through the ss1 proxy", () -> sendRequestUntilAnswered(env));

            then("the external row count increases", () -> Awaitility.await()
                    .atMost(SETTLE_TIMEOUT).pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(rowCount(fx)).isGreaterThan(count)));
            and("the Diagnostics Traffic view shows the records", () -> Awaitility.await()
                    .atMost(SETTLE_TIMEOUT).pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(
                            operationalMonitoringTrafficTotal(fx.baseUrl(), session(fx), from, Instant.now()))
                            .isGreaterThan(traffic)));
        });
    }

    @Test
    @DisplayName("a channel host absent from the server certificate SAN list is rejected")
    void wrongIdentityIsRejected(E2eEnvironment env) {
        var fx = prepare(env);
        var address = fx.opmonAddress();
        assertThat(fx.sanList()).as("xrd-opmon certificate SAN list").doesNotContain(address);
        assertThat(tcpReachable(fx, address)).as("TCP from ss1 to %s:%s", address, fx.savedRows().get(PORT_ROW)).isTrue();

        runWithRestore(fx, () -> {
            given("ss1 trusts the xrd-opmon CA and its channel host is the address missing from the SAN list", () ->
                    configure(fx, address, SHORT_DEADLINE, fx.opmonCaPem()));

            var count = rowCount(fx);
            var logLines = logLineCount(fx);
            when("a request is sent through the ss1 proxy", () -> sendRequestUntilAnswered(env));
            then("ss1 logs a failed send and the external row count does not increase", () -> {
                awaitLogLine(fx, logLines, line -> line.contains(SEND_FAILED));
                assertThat(rowCount(fx)).isEqualTo(count);
            });
        });
    }

    private Fixture prepare(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof OpMonHostOps,
                () -> "%s has no bare op-monitor host; it is provisioned only in LXD"
                        .formatted(env.getClass().getSimpleName()));
        var host = (OpMonHostOps) env;
        var baseUrl = adminBaseUrl(env, SS);
        var opmonHost = env.securityServerAddress(OPMON);

        assertThat(host.exec(OPMON, "systemctl", "list-unit-files", "xroad-confclient.service").stdout())
                .as("xroad-confclient unit on xrd-opmon").doesNotContain("xroad-confclient.service");

        var rows = listConfigurableProperties(baseUrl, login(baseUrl));
        CHANNEL_ROWS.forEach(row -> assertThat(rows.get(row)).as("configuration property row %s", row).isNotNull());
        assertThat(rows.get(HOST_ROW)).as("leftover channel host %s", HOST_ROW).isNotEqualTo(opmonHost);
        assertThat(rows.get(DEADLINE_ROW)).as("leftover shortened deadline %s", DEADLINE_ROW).isNotEqualTo(SHORT_DEADLINE);
        assertThat(rows.get(ADDON_FLAG)).as("add-on flag %s", ADDON_FLAG).isEqualTo("true");

        var opmonCaPem = pullBytes(host, OPMON_CA_PATH);
        var opmonCaHash = sha256Hex(parseCertificate(new String(opmonCaPem, US_ASCII)));
        var savedCa = opMonitorCaHash(baseUrl, login(baseUrl));
        savedCa.ifPresent(hash -> assertThat(hash)
                .as("stored op-monitor CA is not restorable: its hash differs from the xrd-opmon CA").isEqualTo(opmonCaHash));

        var sanList = commandOutput(host.exec(OPMON, "openssl", "x509", "-in", OPMON_CERT_PATH, "-noout", "-ext", "subjectAltName"));
        assertThat(sanList).as("xrd-opmon certificate SAN list").contains("DNS:" + opmonHost);
        var address = commandOutput(host.exec(SS, "sh", "-c", "getent ahostsv4 " + opmonHost + " | awk 'NR==1{print $1}'"));
        var fixture = new Fixture(host, baseUrl, opmonHost, address, sanList, opmonCaPem, opmonCaHash, savedCa,
                Map.of(HOST_ROW, rows.get(HOST_ROW), PORT_ROW, rows.get(PORT_ROW), DEADLINE_ROW, rows.get(DEADLINE_ROW)));
        assertThat(tcpReachable(fixture, opmonHost)).as("TCP from ss1 to %s:%s", opmonHost, rows.get(PORT_ROW)).isTrue();
        return fixture;
    }

    private static boolean tcpReachable(Fixture fx, String target) {
        var port = fx.savedRows().get(PORT_ROW);
        return fx.host().exec(SS, "timeout", "5", "bash", "-c", "exec 3<>/dev/tcp/%s/%s".formatted(target, port)).exitCode() == 0;
    }

    private static void configure(Fixture fx, String channelHost, String deadline, byte[] caPem) {
        var session = session(fx);
        assertThat(patchConfigurableProperty(fx.baseUrl(), session, HOST_ROW, channelHost).getStatusCode()).isEqualTo(204);
        assertThat(patchConfigurableProperty(fx.baseUrl(), session, DEADLINE_ROW, deadline).getStatusCode()).isEqualTo(204);
        assertThat(uploadOpMonitorCa(fx.baseUrl(), session, caPem).getStatusCode()).isEqualTo(200);
        restartProxyAndAdminService(fx);
    }

    private static void restartProxyAndAdminService(Fixture fx) {
        var result = fx.host().systemctl(SS, "restart", "xroad-proxy", "xroad-proxy-ui-api");
        assertThat(result.exitCode()).as("restart xroad-proxy and xroad-proxy-ui-api: %s", result.stderr()).isZero();
        awaitAdminService(fx);
    }

    private static void awaitAdminService(Fixture fx) {
        Awaitility.await().pollDelay(RESTART_POLL_DELAY).pollInterval(POLL_INTERVAL).atMost(SETTLE_TIMEOUT)
                .ignoreExceptions().untilAsserted(() -> login(fx.baseUrl()));
    }

    private static AdminSession session(Fixture fx) {
        return login(fx.baseUrl());
    }

    private static void runWithRestore(Fixture fx, Runnable body) {
        Throwable failure = null;
        try {
            body.run();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            restore(fx, failure);
        }
    }

    @SneakyThrows
    private static void restore(Fixture fx, Throwable testFailure) {
        var failures = new ArrayList<Throwable>();
        attempt(failures, () -> awaitAdminService(fx));
        fx.savedRows().forEach((row, value) -> attempt(failures, () ->
                assertThat(patchConfigurableProperty(fx.baseUrl(), session(fx), row, value).getStatusCode()).isEqualTo(204)));
        attempt(failures, () -> {
            if (fx.savedCaHash().isPresent()) {
                assertThat(uploadOpMonitorCa(fx.baseUrl(), session(fx), fx.opmonCaPem()).getStatusCode()).isEqualTo(200);
            } else {
                assertThat(deleteOpMonitorCa(fx.baseUrl(), session(fx)).getStatusCode()).isEqualTo(204);
            }
        });
        attempt(failures, () -> restartProxyAndAdminService(fx));
        attempt(failures, () -> verifyRestored(fx));
        if (testFailure != null) {
            failures.forEach(testFailure::addSuppressed);
        } else if (!failures.isEmpty()) {
            var first = failures.removeFirst();
            failures.forEach(first::addSuppressed);
            throw first;
        }
    }

    private static void verifyRestored(Fixture fx) {
        var session = session(fx);
        var rows = listConfigurableProperties(fx.baseUrl(), session);
        fx.savedRows().forEach((row, value) -> assertThat(rows.get(row)).as("restored row %s", row).isEqualTo(value));
        assertThat(opMonitorCaHash(fx.baseUrl(), session)).as("restored op-monitor CA hash").isEqualTo(fx.savedCaHash());
    }

    private static void attempt(List<Throwable> failures, Runnable step) {
        try {
            step.run();
        } catch (Throwable failure) {
            failures.add(failure);
        }
    }

    private static long rowCount(Fixture fx) {
        var result = fx.host().exec(OPMON, "sudo", "-u", "postgres", "psql", "-d", "op-monitor", "-tAX", "-c", COUNT_SQL);
        assertThat(result.exitCode()).as("row count query: %s", result.stderr()).isZero();
        return Long.parseLong(result.stdout().trim());
    }

    private static long logLineCount(Fixture fx) {
        var result = fx.host().exec(SS, "sh", "-c", "wc -l < " + PROXY_LOG);
        assertThat(result.exitCode()).as("proxy log readable: %s", result.stderr()).isZero();
        return Long.parseLong(result.stdout().trim());
    }

    private static void awaitLogLine(Fixture fx, long afterLine, Predicate<String> matches) {
        Awaitility.await().atMost(SETTLE_TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            var result = fx.host().exec(SS, "tail", "-n", "+" + (afterLine + 1), PROXY_LOG);
            assertThat(result.stdout().lines()).as("new proxy log lines").anyMatch(matches);
        });
    }

    private static void sendRequestUntilAnswered(E2eEnvironment env) {
        var mapping = env.getContainerMapping(SS, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        Awaitility.await().atMost(SETTLE_TIMEOUT).pollInterval(POLL_INTERVAL).ignoreExceptions().untilAsserted(() -> {
            Response response = RestAssuredFactory.given()
                    .body("{\"data\": 1.0, \"service\": \"random\"}")
                    .header("Content-Type", "application/json")
                    .header("x-road-client", "DEV/COM/4321/TestClient")
                    .post("http://%s:%s/r1/DEV/COM/1234/TestService/mock1".formatted(mapping.host(), mapping.port()));
            assertThat(response.getStatusCode()).isEqualTo(200);
        });
    }

    private static String commandOutput(HostCommandResult result) {
        assertThat(result.exitCode()).as("host command: %s", result.stderr()).isZero();
        assertThat(result.stdout()).isNotBlank();
        return result.stdout().trim();
    }

    @SneakyThrows
    private static byte[] pullBytes(OpMonHostOps host, String remotePath) {
        var local = Files.createTempFile("opmon-ca-", ".crt");
        try {
            host.pullFile(OPMON, remotePath, local);
            return Files.readAllBytes(local);
        } finally {
            Files.deleteIfExists(local);
        }
    }

    private record Fixture(OpMonHostOps host, String baseUrl, String opmonHost, String opmonAddress, String sanList,
                           byte[] opmonCaPem, String opmonCaHash, Optional<String> savedCaHash,
                           Map<String, String> savedRows) {
    }
}
