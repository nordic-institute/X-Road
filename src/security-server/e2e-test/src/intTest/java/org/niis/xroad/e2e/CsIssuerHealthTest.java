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

import io.restassured.path.json.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.e2e.container.AuxStackSetup;
import org.niis.xroad.e2e.container.E2eEnvSetup;
import org.testcontainers.containers.Container;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * The Issuer Service health endpoint on the Central Server container {@code cs}, which runs the native package
 * settings (profiles {@code native,cs}).
 *
 * <p>The listener case lists the TCP listeners inside {@code cs} (port 6189 is not published to the host): the
 * endpoint listens on 127.0.0.1 only and nothing listens on the port on any other address.
 *
 * <p>The OpenBao case stops OpenBao inside {@code cs} and expects the node to stay ready and to keep serving the
 * DID document and the credential status list. The DID document is requested with the advertised issuer host
 * ({@code is_host} of {@code compose.aux.yaml}, the {@code cs} service name) pinned to the loopback address with
 * {@code curl --resolve}. The status list URL is the one the issuer stored for its published list. OpenBao stays
 * stopped until the issuer has logged a failed reload of the connector TLS certificate, which it attempts every
 * {@code xroad.edc.web.https.keystore.reload-interval-seconds} (default 60, not overridden in the {@code cs} image),
 * and readiness and serving are checked again after that. OpenBao is started again afterwards and unsealed with
 * the secret-store init script, also when an assertion fails. The class runs after every other class, so a failed
 * restore cannot affect them. PostgreSQL is not stopped here: it is OpenBao's storage and the Central Server's
 * database.
 *
 * <p>Only the multi-container Compose environment can exec into {@code cs}; every other environment skips the
 * class.
 */
@Slf4j
@DisplayName("CS Issuer Service health endpoint")
@Order(600)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CsIssuerHealthTest extends E2eTest {

    private static final String CS_ENV = "aux";
    private static final String LOOPBACK = "127.0.0.1";
    private static final int HEALTH_PORT = 6189;
    private static final String HEALTH_URL = "http://" + LOOPBACK + ":" + HEALTH_PORT + "/q/health/";
    private static final int DID_PORT = 6183;
    private static final String DID_DOCUMENT_URL = "https://" + AuxStackSetup.CS + ":" + DID_PORT + "/issuer/did.json";
    private static final String OPENBAO_SEAL_STATUS_URL = "https://" + LOOPBACK + ":8200/v1/sys/seal-status";
    private static final String OPENBAO_PROGRAM = "openbao";
    private static final String SECRET_STORE_INIT = "/usr/share/xroad/scripts/secret-store-init.sh";
    private static final String ISSUER_LOG = "/var/log/xroad/ds-issuer-service.log";
    private static final String FAILED_RELOAD_LOG_MARKER = "ds-https-keystore: exhausted";
    private static final String CS_DATABASE = "centerui_production";
    private static final String PUBLISHED_STATUS_LIST_URL_SQL = """
            SELECT metadata->>'publicUrl' FROM "ds-issuer-service".credential_resource
            WHERE participant_context_id = 'issuer' AND usage = 'StatusList' AND metadata->>'published' = 'true'
            ORDER BY create_timestamp LIMIT 1""";

    private static final int NETSTAT_LOCAL_ADDRESS_COLUMN = 3;
    private static final String HTTP_OK = "200";
    private static final String STATUS_UP = "UP";
    private static final Duration RELOAD_INTERVAL = Duration.ofSeconds(60);
    private static final Duration RELOAD_CYCLE_MARGIN = Duration.ofSeconds(30);
    private static final Duration RELOAD_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration UNSEAL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration UNSEAL_POLL_INTERVAL = Duration.ofSeconds(2);

    @Test
    @Order(1)
    @DisplayName("health endpoint listens on the loopback address only")
    void healthEndpointListensOnLoopbackOnly(E2eEnvironment env) {
        var stack = composeStack(env);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var listeners = when("the TCP listeners inside cs are listed", () -> exec(stack, "netstat", "-tln").getStdout());
        then("the health port listens on 127.0.0.1 and on no other address, such as 0.0.0.0 or [::]", () ->
                assertThat(listenAddresses(listeners, HEALTH_PORT))
                        .as("addresses listening on port %d in:%n%s", HEALTH_PORT, listeners)
                        .containsExactly(LOOPBACK + ":" + HEALTH_PORT));
        then("liveness answers 200 on 127.0.0.1", () ->
                assertThat(httpStatus(stack, HEALTH_URL + "live")).isEqualTo(HTTP_OK));
    }

    @Test
    @Order(2)
    @DisplayName("node stays ready and serves the DID document and status list while OpenBao is down")
    void nodeServesWhileOpenBaoIsDown(E2eEnvironment env) {
        var stack = composeStack(env);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());
        var statusListUrl = and("the published status list URL is read from the issuer database", () ->
                publishedStatusListUrl(stack));
        then("the node is ready and serves the DID document and the status list with OpenBao up", () ->
                assertReadyAndServing(stack, statusListUrl));
        var failedReloadsBefore = and("the failed reloads logged so far are counted", () -> failedReloadCount(stack));
        try {
            when("OpenBao is stopped inside cs", () -> {
                var stopped = exec(stack, "supervisorctl", "stop", OPENBAO_PROGRAM);
                assertThat(stopped.getExitCode()).as("supervisorctl stop: %s%s", stopped.getStdout(), stopped.getStderr())
                        .isZero();
            });
            then("OpenBao no longer answers", () ->
                    assertThat(exec(stack, "curl", "-sk", "--connect-timeout", "3", "-o", "/dev/null",
                            OPENBAO_SEAL_STATUS_URL).getExitCode()).isNotZero());
            then("the node is still ready and serves the DID document and the status list", () ->
                    assertReadyAndServing(stack, statusListUrl));
            and("a reload of the connector TLS certificate fails while OpenBao is down", () ->
                    awaitFailedReload(stack, failedReloadsBefore));
            then("the node is still ready and serves the DID document and the status list after the failed reload", () ->
                    assertReadyAndServing(stack, statusListUrl));
        } finally {
            restoreOpenBao(stack);
        }

        then("OpenBao answers unsealed again", () -> assertThat(isUnsealed(stack)).isTrue());
        and("the node is ready and serves the DID document and the status list", () ->
                assertReadyAndServing(stack, statusListUrl));
    }

    private static E2eEnvSetup composeStack(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof E2eEnvSetup, "only the Compose environment can exec into cs");
        return (E2eEnvSetup) env;
    }

    private static void assertReadyAndServing(E2eEnvSetup stack, String statusListUrl) {
        var ready = exec(stack, "curl", "-s", HEALTH_URL + "ready").getStdout();
        assertThat(JsonPath.from(ready.isBlank() ? "{}" : ready).getString("status"))
                .as("readiness body: %s", ready)
                .isEqualTo(STATUS_UP);
        assertThat(httpStatus(stack, "--resolve", AuxStackSetup.CS + ":" + DID_PORT + ":" + LOOPBACK, DID_DOCUMENT_URL))
                .as("DID document status")
                .isEqualTo(HTTP_OK);
        var statusList = URI.create(statusListUrl);
        assertThat(httpStatus(stack, "--resolve", statusList.getHost() + ":" + statusList.getPort() + ":" + LOOPBACK,
                statusListUrl))
                .as("status list status")
                .isEqualTo(HTTP_OK);
    }

    private static int failedReloadCount(E2eEnvSetup stack) {
        var result = exec(stack, "grep", "-c", "-F", "-e", FAILED_RELOAD_LOG_MARKER, ISSUER_LOG);
        assertThat(result.getExitCode()).as("grep in %s: %s", ISSUER_LOG, result.getStderr()).isIn(0, 1);
        return Integer.parseInt(result.getStdout().trim());
    }
    private static void awaitFailedReload(E2eEnvSetup stack, int failedReloadsBefore) {
        Awaitility.await()
                .alias("failed reload of the connector TLS certificate")
                .atMost(RELOAD_INTERVAL.plus(RELOAD_CYCLE_MARGIN))
                .pollInterval(RELOAD_POLL_INTERVAL)
                .until(() -> failedReloadCount(stack) > failedReloadsBefore);
    }
    private static String publishedStatusListUrl(E2eEnvSetup stack) {
        var result = exec(stack, "runuser", "-u", "postgres", "--", "psql", "-d", CS_DATABASE, "-tAX", "-c",
                PUBLISHED_STATUS_LIST_URL_SQL);
        assertThat(result.getExitCode()).as("psql: %s", result.getStderr()).isZero();
        var url = result.getStdout().trim();
        assertThat(url).as("published status list URL").startsWith("https://");
        return url;
    }

    private static void restoreOpenBao(E2eEnvSetup stack) {
        var started = exec(stack, "supervisorctl", "start", OPENBAO_PROGRAM);
        log.info("supervisorctl start {}: {}{}", OPENBAO_PROGRAM, started.getStdout(), started.getStderr());
        var unsealed = exec(stack, SECRET_STORE_INIT);
        assertThat(unsealed.getExitCode()).as("secret-store-init.sh: %s%s", unsealed.getStdout(), unsealed.getStderr())
                .isZero();
        Awaitility.await()
                .alias("OpenBao unsealed")
                .atMost(UNSEAL_TIMEOUT)
                .pollInterval(UNSEAL_POLL_INTERVAL)
                .ignoreExceptions()
                .until(() -> isUnsealed(stack));
    }

    private static boolean isUnsealed(E2eEnvSetup stack) {
        var body = exec(stack, "curl", "-sk", OPENBAO_SEAL_STATUS_URL).getStdout();
        return !JsonPath.from(body).getBoolean("sealed");
    }

    private static List<String> listenAddresses(String netstatOutput, int port) {
        return netstatOutput.lines()
                .filter(line -> line.startsWith("tcp"))
                .map(line -> line.trim().split("\\s+"))
                .filter(columns -> columns.length > NETSTAT_LOCAL_ADDRESS_COLUMN
                        && columns[NETSTAT_LOCAL_ADDRESS_COLUMN].endsWith(":" + port))
                .map(columns -> columns[NETSTAT_LOCAL_ADDRESS_COLUMN])
                .toList();
    }

    private static String httpStatus(E2eEnvSetup stack, String... curlArguments) {
        var command = new ArrayList<>(List.of("curl", "-sk", "-o", "/dev/null", "-w", "%{http_code}"));
        command.addAll(List.of(curlArguments));
        return exec(stack, command.toArray(String[]::new)).getStdout().trim();
    }

    private static Container.ExecResult exec(E2eEnvSetup stack, String... command) {
        return stack.execInEnvContainer(CS_ENV, AuxStackSetup.CS, command);
    }
}
