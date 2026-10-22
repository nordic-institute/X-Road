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
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.e2e.container.E2eEnvSetup;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.getAgreementTokenKeyStatus;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.Mock1Fixture.MOCK1_SERVICE_PATH;
import static org.niis.xroad.e2e.Mock1Fixture.callService;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Pruning of old agreement-token signing keys on the provider Security Server (ss0) against the stack's real
 * OpenBao, whose policy is written fresh at startup and so grants the proxy the delete permission. The ss0
 * proxy runs with short key settings from {@code compose.ss-agreement-token-key-pruning.e2e.yaml}: a new key
 * after one minute, a retention of five minutes, a token lifetime of 30 seconds and a key refresh interval of
 * five seconds. Automatic rotation therefore creates a key every minute or so while the suite runs. A key is
 * deleted five minutes after it stops signing, which is when its successor starts signing, so the first deletion
 * comes about six minutes after the proxy starts.
 *
 * <p>Key ids are consecutive numbers and the highest id is never deleted, so the proxy retaining fewer keys than
 * the highest id shows that at least one old key was deleted. A dataspace request from ss1 then shows that
 * token verification still works.
 *
 * <p>Runs after the other scenarios, so that most of the retention period has already passed when it starts.
 * The signing keys live in the secret store shared by the whole stack, so the test holds its lock for the whole
 * run. Only the multi-container Compose stack carries the short key settings; every other environment skips it.
 */
@DisplayName("SS agreement-token signing key pruning")
@Order(450)
@ResourceLock("secret-store")
class SsAgreementTokenKeyPruningTest extends E2eTest {

    private static final String PROVIDER_ENV = "ss0";
    private static final String CONSUMER_ENV = "ss1";
    private static final String CONSUMER_X_ROAD_CLIENT = "DEV/COM/4321/TestClient";
    private static final String SS0_STACK_PROPERTY = "test-framework.ss0-stack";
    private static final String SIDECAR_STACK = "sidecar";
    private static final int HTTP_OK = 200;
    private static final Duration PRUNE_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration PRUNE_TIMEOUT = Duration.ofMinutes(8);

    @Test
    @DisplayName("an old key is deleted and token verification keeps working")
    void oldKeyIsDeletedAndTokenVerificationKeepsWorking(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof E2eEnvSetup
                        && !SIDECAR_STACK.equalsIgnoreCase(System.getProperty(SS0_STACK_PROPERTY)),
                "only the multi-container Compose stack runs ss0 with the short agreement-token key settings");
        var baseUrl = adminBaseUrl(env, PROVIDER_ENV);
        var session = login(baseUrl);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        and("the proxy retains fewer keys than it has ever created, so an old key was deleted", () ->
                Awaitility.await()
                        .alias("an old agreement-token signing key is deleted")
                        .pollInterval(PRUNE_POLL_INTERVAL)
                        .atMost(PRUNE_TIMEOUT)
                        .untilAsserted(() -> {
                            var status = getAgreementTokenKeyStatus(baseUrl, session).jsonPath();
                            var highestKeyId = highestKeyId(status.getString("pending_key_id"),
                                    status.getString("active_key_id"));
                            assertThat(status.getInt("retained_key_count")).isLessThan(highestKeyId);
                        }));

        then("the proxy still has a signing key", () -> {
            var status = getAgreementTokenKeyStatus(baseUrl, session).jsonPath();
            assertThat(status.getBoolean("available")).isTrue();
            assertThat(status.getString("active_key_id")).isNotBlank();
        });

        var response = and("a dataspace request from ss1 reaches the service on ss0", () ->
                callService(env, CONSUMER_ENV, CONSUMER_X_ROAD_CLIENT, MOCK1_SERVICE_PATH));

        then("the request is served, so the provider's token verification still works", () ->
                response.statusCode(HTTP_OK).body("message", equalTo("Hello, world from POST service!")));
    }

    private static int highestKeyId(String pendingKeyId, String activeKeyId) {
        return Integer.parseInt(pendingKeyId != null ? pendingKeyId : activeKeyId);
    }
}
