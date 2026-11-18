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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.container.E2eEnvSetup;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.testcontainers.containers.Container;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_SERVICE_PATH;
import static org.niis.xroad.e2e.Mock1Fixture.callService;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * The op-monitor client-name allow-list on ss0's op-monitor RPC server, with RPC TLS on. {@code
 * compose.ss-opmonitor.e2e.yaml} seeds {@code xroad.op-monitor.rpc.allowed-client-names} with the proxy and admin
 * service names, and nothing sets {@code xroad.common-rpc.use-tls}, so TLS keeps its shipped default.
 *
 * <p>With TLS off the allow-list is not enforced and no certificate is validated, so the scenario first requires
 * op-monitor to present a certificate issued by the stack's OpenBao issuing CA. It then connects to op-monitor
 * with {@code openssl s_client} from the OpenBao container, using certificates issued by the same CA: the
 * identities {@code proxy} and {@code ui} are accepted and {@code signer}, which is not listed, is rejected. With
 * TLS 1.3 the server's rejection arrives as a fatal alert after the client has finished its handshake, so the
 * connection is kept open for a few seconds to read it.
 *
 * <p>Only the multi-container Compose stack runs ss0 with op-monitor as a separate service; every other
 * environment skips the test.
 */
@DisplayName("SS op-monitor RPC client-name allow-list")
@Order(395)
class SsOpMonitorAllowListTest extends E2eTest {

    private static final String PROVIDER_ENV = "ss0";
    private static final String CONSUMER_ENV = "ss1";
    private static final String CONSUMER_X_ROAD_CLIENT = "DEV/COM/4321/TestClient";
    private static final String SS0_STACK_PROPERTY = "test-framework.ss0-stack";
    private static final String SIDECAR_STACK = "sidecar";
    private static final String OP_MONITOR_RPC_ADDRESS = "op-monitor:2081";
    private static final String DB_OPMONITOR = "db-opmonitor";
    private static final int HTTP_OK = 200;
    private static final Duration PROBE_HOLD = Duration.ofSeconds(3);
    private static final Duration STORED_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STORED_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final String NO_CLIENT_CERTIFICATE = "";
    private static final String PROXY_IDENTITY = "proxy";
    private static final String UI_IDENTITY = "ui";
    private static final String SIGNER_IDENTITY = "signer";

    private static final String SERVER_CERTIFICATE = "Server certificate";
    private static final String TLS_VERIFIED = "Verify return code: 0 (ok)";
    private static final String NO_PEER_CERTIFICATE = "no peer certificate available";
    private static final String NO_CIPHER = "Cipher is (NONE)";
    private static final String FATAL_ALERT = "SSL alert number";
    private static final Pattern NEGOTIATED_CIPHER = Pattern.compile("(?m)^New, \\S+, Cipher is (?!\\(NONE\\))\\S+$");

    /**
     * The only {@code s_client} lines kept in the probe output, so that session material such as resumption
     * secrets never reaches an assertion message.
     */
    private static final Pattern REPORTABLE_LINE = Pattern.compile(
            "^(CONNECTED|Server certificate$|subject=|issuer=|Verification|New, |ALPN protocol|No ALPN|"
                    + "Verify return code|SSL alert number|no peer certificate available).*|.*:error:.*");

    /**
     * Run in the OpenBao container: {@code $1} is the common name of the client certificate to issue, empty for
     * none. The server certificate is verified against the issuing CA, and the connection stays open for
     * {@code $2} seconds so that a post-handshake rejection is read.
     */
    private static final String PROBE_SCRIPT = """
            set -eu
            dir=$(mktemp -d)
            bao read -field=certificate xrd-pki/cert/ca > "$dir/ca.pem"
            client_args=""
            if [ -n "$1" ]; then
              bao write -format=json xrd-pki/issue/xrd-internal common_name="$1" ttl=5m > "$dir/issued.json"
              jq -r .data.certificate "$dir/issued.json" > "$dir/client.pem"
              jq -r .data.private_key "$dir/issued.json" > "$dir/client.key"
              client_args="-cert $dir/client.pem -key $dir/client.key"
            fi
            (sleep "$2") | openssl s_client -connect "$3" -alpn h2 -CAfile "$dir/ca.pem" -verify_return_error \\
              $client_args 2>&1 || true
            rm -rf "$dir"
            """;

    @Test
    @DisplayName("op-monitor uses TLS and admits the proxy and admin-service names only")
    void opMonitorAdmitsOnlyListedClientNames(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof E2eEnvSetup
                        && !SIDECAR_STACK.equalsIgnoreCase(System.getProperty(SS0_STACK_PROPERTY)),
                "only the multi-container Compose stack runs ss0 with a separate op-monitor service");
        var stack = (E2eEnvSetup) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var probes = when("op-monitor's RPC port is probed in parallel without a client certificate and with the "
                + "identities proxy, ui and signer", () ->
                probeInParallel(stack, List.of(NO_CLIENT_CERTIFICATE, PROXY_IDENTITY, UI_IDENTITY, SIGNER_IDENTITY)));
        then("op-monitor completes a TLS handshake with a certificate issued by the OpenBao issuing CA, so RPC TLS is on",
                () -> assertTlsHandshake(NO_CLIENT_CERTIFICATE, probes.get(NO_CLIENT_CERTIFICATE)));
        then("op-monitor accepts a certificate with the identity proxy",
                () -> assertAccepted(PROXY_IDENTITY, probes.get(PROXY_IDENTITY)));
        then("op-monitor accepts a certificate with the identity ui",
                () -> assertAccepted(UI_IDENTITY, probes.get(UI_IDENTITY)));
        then("op-monitor rejects a certificate with the unlisted identity signer",
                () -> assertRejected(SIGNER_IDENTITY, probes.get(SIGNER_IDENTITY)));

        var before = and("the number of stored operational data records is read", () -> storedRecordCount(stack));
        var response = when("a dataspace request from ss1 reaches the service on ss0", () ->
                callService(env, CONSUMER_ENV, CONSUMER_X_ROAD_CLIENT, MOCK1_SERVICE_PATH));
        and("the request is served", () ->
                response.statusCode(HTTP_OK).body("message", equalTo("Hello, world from POST service!")));
        then("the proxy's records reach op-monitor through the allow-list", () ->
                Awaitility.await()
                        .alias("operational data records stored by op-monitor")
                        .pollDelay(Duration.ZERO)
                        .pollInterval(STORED_POLL_INTERVAL)
                        .atMost(STORED_TIMEOUT)
                        .untilAsserted(() -> assertThat(storedRecordCount(stack)).isGreaterThan(before)));
    }

    private static void assertTlsHandshake(String identity, String probeOutput) {
        assertThat(probeOutput)
                .as("identity %s: op-monitor RPC server must complete a TLS handshake with a certificate from the "
                        + "issuing CA", describe(identity))
                .contains(SERVER_CERTIFICATE, TLS_VERIFIED)
                .doesNotContain(NO_PEER_CERTIFICATE, NO_CIPHER)
                .containsPattern(NEGOTIATED_CIPHER);
    }

    private static void assertAccepted(String identity, String probeOutput) {
        assertTlsHandshake(identity, probeOutput);
        assertThat(probeOutput)
                .as("identity %s: op-monitor must accept a listed client name", describe(identity))
                .doesNotContain(FATAL_ALERT);
    }

    private static void assertRejected(String identity, String probeOutput) {
        assertTlsHandshake(identity, probeOutput);
        assertThat(probeOutput)
                .as("identity %s: op-monitor must reject an unlisted client name", describe(identity))
                .contains(FATAL_ALERT);
    }

    private static String describe(String identity) {
        return identity.isEmpty() ? "(no client certificate)" : identity;
    }

    private static Map<String, String> probeInParallel(E2eEnvSetup stack, List<String> commonNames) {
        Map<String, Container.ExecResult> results;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var probes = commonNames.stream().collect(Collectors.toMap(Function.identity(),
                    commonName -> CompletableFuture.supplyAsync(() -> probe(stack, commonName), executor)));
            results = probes.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().join()));
        }
        return results.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> reportableOutput(entry.getKey(), entry.getValue())));
    }

    private static Container.ExecResult probe(E2eEnvSetup stack, String commonName) {
        return stack.execInEnvContainer(PROVIDER_ENV, SsStackSetup.OPENBAO, "bash", "-c", PROBE_SCRIPT, "probe",
                commonName, String.valueOf(PROBE_HOLD.toSeconds()), OP_MONITOR_RPC_ADDRESS);
    }

    private static String reportableOutput(String identity, Container.ExecResult result) {
        assertThat(result.getExitCode())
                .as("identity %s: probe script: %s", describe(identity), result.getStderr())
                .isZero();
        return result.getStdout().lines()
                .filter(REPORTABLE_LINE.asPredicate())
                .collect(Collectors.joining("\n"));
    }

    private static long storedRecordCount(E2eEnvSetup stack) {
        var result = stack.execInEnvContainer(PROVIDER_ENV, DB_OPMONITOR,
                "psql", "-U", "opmonitor", "-d", "op-monitor", "-tAX", "-c", "SELECT count(*) FROM operational_data");
        assertThat(result.getExitCode()).as("psql on db-opmonitor: %s", result.getStderr()).isZero();
        return Long.parseLong(result.getStdout().trim());
    }
}
