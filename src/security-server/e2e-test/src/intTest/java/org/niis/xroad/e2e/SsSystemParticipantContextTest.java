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

import io.restassured.specification.RequestSpecification;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * SYSTEM participant context (XRDADR-41): a per-Security-Server dataspace identity, provisioned
 * unconditionally by the data space provisioning worker regardless of which subsystems a server hosts —
 * the asymmetry the {@code -mgmt} suffix machinery had before it fell back to running everywhere. This
 * scenario is the explicit, repeatable proof that the asymmetry does not resurface: it queries the admin
 * API's data space provisioning status on both ss1 (an ordinary member server) and ss0 (the management
 * Security Server), asserting the same SYSTEM outcome on each.
 *
 * <p>Read-only: it only queries provisioning status via the admin API, so it is safe to re-run against a
 * warm cluster and to run in any order relative to the traffic-generating scenarios in this suite — it
 * produces no proxy traffic, so it cannot disturb {@link SsMessagelogArchiveTest}'s exact message counts.
 *
 * <p><b>SYSTEM-published catalog contents are not asserted here.</b> The three provider-side catalog
 * stores are live {@code ServerConfProvider} reads, materialized per request — no {@code ds-control-plane}
 * table backs them, and the control plane exposes no listing endpoint for catalog contents; its only
 * externally reachable surface for catalog data is the dataspace-protocol port, which requires a fully
 * DCP-authenticated catalog request that nothing in this environment can construct today (the consumer-side
 * processor does not target SYSTEM as a counterparty until the next story). Catalog <i>content</i>
 * (built-ins present, {@code authCertReg} absent under SYSTEM, management-service entries gated on the
 * provider-hosted condition) is proven where it already is: {@code AssetIndexServerConfStoreTest},
 * {@code ContractDefinitionServerConfStoreTest} and {@code PolicyDefinitionServerConfStoreTest}. This
 * scenario proves the narrower, still end-to-end claim those unit tests cannot: that a real running server
 * has actually provisioned the SYSTEM identity those stores publish under, on every server, not only the
 * one hosting the management provider.
 *
 * <p>Only k8s and LXD run the dataspace protocol stack the provisioning worker depends on; the Compose
 * facade does not, so this scenario self-skips there via {@link Assumptions}, exactly like
 * {@link SsClientRegistrationTest}.
 */
@DisplayName("SS dataspace - SYSTEM participant context provisioning")
@Order(60)
@SuppressWarnings("checkstyle:magicnumber")
class SsSystemParticipantContextTest extends E2eTest {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";

    private static final String SYSTEM_KIND = "SYSTEM";
    private static final String ISSUED_CREDENTIAL_STATUS = "ISSUED";

    private static final Duration PROVISIONING_POLL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(5);

    @Test
    @DisplayName("ss0 and ss1 each provision their own SYSTEM context with an issued membership credential")
    void systemContextIsProvisionedOnEveryServer(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; SYSTEM context provisioning is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        for (var envName : List.of("ss1", "ss0")) {
            then("%s's SYSTEM participant context is created with an issued membership credential".formatted(envName), () ->
                    awaitSystemContextIssued(env, envName));
        }
    }

    private void awaitSystemContextIssued(E2eEnvironment env, String envName) {
        var baseUrl = adminBaseUrl(env, envName);
        var session = login(baseUrl);
        var lastSeen = new AtomicReference<Map<String, ?>>(null);

        try {
            Awaitility.await()
                    .pollInterval(PROVISIONING_POLL_INTERVAL)
                    .timeout(PROVISIONING_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var response = authed(session).get(baseUrl + "/api/v1/dataspace/provisioning-status");
                        if (response.getStatusCode() != 200) {
                            return false;
                        }
                        Map<String, ?> systemContext = response.jsonPath().getMap(
                                "participant_contexts.find { it.kind == '" + SYSTEM_KIND + "' }");
                        lastSeen.set(systemContext);
                        return systemContext != null
                                && Boolean.TRUE.equals(systemContext.get("context_created"))
                                && ISSUED_CREDENTIAL_STATUS.equals(systemContext.get("credential_status"));
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    ("Timed out waiting for %s's SYSTEM participant context to report context_created=true and "
                            + "credential_status=%s; last observed SYSTEM context entry (participant_contexts.find "
                            + "{ it.kind == '%s' }): %s")
                            .formatted(envName, ISSUED_CREDENTIAL_STATUS, SYSTEM_KIND, lastSeen.get()), e);
        }
    }

    private String adminBaseUrl(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.UI, SsStackSetup.Port.UI);
        return "https://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private AdminSession login(String baseUrl) {
        var response = RestAssuredFactory.given()
                .formParam("username", ADMIN_USERNAME)
                .formParam("password", ADMIN_PASSWORD)
                .post(baseUrl + "/login");
        assertThat(response.getStatusCode()).as("login to %s", baseUrl).isEqualTo(200);
        return new AdminSession(response.getCookies(), response.getCookie("XSRF-TOKEN"));
    }

    private RequestSpecification authed(AdminSession session) {
        return RestAssuredFactory.given()
                .cookies(session.cookies())
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    private record AdminSession(Map<String, String> cookies, String xsrfToken) {
    }
}
