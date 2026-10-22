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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.getAgreementTokenKeyStatus;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.rotateAgreementTokenKey;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Agreement-token signing key rotation through the Security Server admin API against the stack's real
 * OpenBao. A rotated key is first reported as pending and becomes the signing key once the activation delay
 * has passed and the proxy's refresh job has reloaded its key snapshot, which takes up to two key refresh
 * intervals (30 seconds each by default).
 *
 * <p>The signing keys live in the secret store shared by the whole stack, so the test holds its lock for
 * the whole run.
 */
@DisplayName("SS agreement-token signing key rotation")
@Order(70)
@ResourceLock("secret-store")
class SsAgreementTokenKeyRotationTest extends E2eTest {

    private static final String ENV_NAME = "ss1";
    private static final int HTTP_OK = 200;
    private static final Duration ACTIVATION_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration ACTIVATION_TIMEOUT = Duration.ofSeconds(90);

    @Test
    @DisplayName("a rotated key is reported as pending, then as the signing key")
    void rotatedKeyIsPendingThenActive(E2eEnvironment env) {
        var baseUrl = adminBaseUrl(env, ENV_NAME);
        var session = login(baseUrl);

        var before = given("the proxy reports its signing key status", () -> {
            var response = getAgreementTokenKeyStatus(baseUrl, session);
            assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
            return response.jsonPath();
        });
        then("signing key material is available with an active key", () -> {
            assertThat(before.getBoolean("available")).isTrue();
            assertThat(before.getString("active_key_id")).isNotBlank();
        });

        var newKeyId = when("the signing key is rotated", () -> {
            var response = rotateAgreementTokenKey(baseUrl, session);
            assertThat(response.getStatusCode()).isEqualTo(HTTP_OK);
            return response.jsonPath().getString("key_id");
        });
        then("a new key id is returned", () ->
                assertThat(newKeyId).isNotBlank().isNotEqualTo(before.getString("active_key_id")));

        var pending = when("the status is read right after the rotation", () ->
                getAgreementTokenKeyStatus(baseUrl, session).jsonPath());
        then("the new key is pending while the previous key still signs", () -> {
            assertThat(pending.getString("pending_key_id")).isEqualTo(newKeyId);
            assertThat(pending.getString("active_key_id")).isEqualTo(before.getString("active_key_id"));
            assertThat(pending.getInt("retained_key_count")).isEqualTo(before.getInt("retained_key_count") + 1);
        });

        and("the new key becomes the signing key with nothing pending", () ->
                Awaitility.await()
                        .alias("agreement-token key %s becomes the signing key".formatted(newKeyId))
                        .pollInterval(ACTIVATION_POLL_INTERVAL)
                        .atMost(ACTIVATION_TIMEOUT)
                        .untilAsserted(() -> {
                            var status = getAgreementTokenKeyStatus(baseUrl, session).jsonPath();
                            assertThat(status.getString("active_key_id")).isEqualTo(newKeyId);
                            assertThat(status.getString("pending_key_id")).isNull();
                            assertThat(status.getString("active_key_created_at")).isNotBlank();
                        }));
    }
}
