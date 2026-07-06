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
package org.niis.xroad.ss.test.api.platform;

import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that after the Central Server rotates its global conf signing keys (simulated by swapping
 * the nginx-served globalconf files), the Security Server re-fetches the new configuration and the
 * expiration date stored on the configuration-client advances.
 */
@DisplayName("Global conf sign key rotation")
@ResourceLock(Resources.GLOBAL)
@SuppressWarnings("checkstyle:magicnumber")
class GlobalConfRotationTest extends SsApiTest {

    private static final String ROTATED_KEYS_CLASSPATH = "files/global_conf_signed_with_rotated_keys";
    private static final String BASELINE_GLOBALCONF_CLASSPATH = "nginx-container-files/var/lib/xroad/public";
    private static final String NGINX_PUBLIC_PATH = "/var/lib/xroad/public";
    private static final String SHARED_PARAMS_METADATA_PATH =
            "/etc/xroad/globalconf/DEV/shared-params.xml.metadata";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);

    private static final String INITIAL_EXPIRY = "2035-11-11T03:07:40Z";
    private static final String ROTATED_EXPIRY = "2035-11-11T03:08:40Z";

    @Test
    @DisplayName("SS re-fetches globalconf and expiration date advances after CS rotates sign keys")
    void globalConfExpirationAdvancesAfterKeyRotation(SsApiTestContainerSetup stack) {
        given("the SS globalconf expiration date is the initial value", () -> {
            var expiry = readGlobalConfExpiry(stack);
            assertThat(expiry).isEqualTo(INITIAL_EXPIRY);
        });

        try {
            when("the nginx-served globalconf is replaced with a version signed by rotated keys", () ->
                    stack.copyFilesToContainer(
                            SsApiTestContainerSetup.NGINX,
                            ROTATED_KEYS_CLASSPATH,
                            NGINX_PUBLIC_PATH));

            then("the SS re-fetches the globalconf and the expiration date advances to the rotated value", () ->
                    await()
                            .pollDelay(Duration.ZERO)
                            .pollInterval(POLL_INTERVAL)
                            .atMost(POLL_TIMEOUT)
                            .untilAsserted(() -> {
                                var expiry = readGlobalConfExpiry(stack);
                                assertThat(expiry).isEqualTo(ROTATED_EXPIRY);
                            }));
        } finally {
            stack.copyFilesToContainer(
                    SsApiTestContainerSetup.NGINX,
                    BASELINE_GLOBALCONF_CLASSPATH,
                    NGINX_PUBLIC_PATH);
            await()
                    .pollDelay(Duration.ZERO)
                    .pollInterval(POLL_INTERVAL)
                    .atMost(POLL_TIMEOUT)
                    .untilAsserted(() -> {
                        var expiry = readGlobalConfExpiry(stack);
                        assertThat(expiry).isEqualTo(INITIAL_EXPIRY);
                    });
        }
    }

    @SneakyThrows
    private String readGlobalConfExpiry(SsApiTestContainerSetup stack) {
        var result = stack.execInContainer(
                SsApiTestContainerSetup.CONFIGURATION_CLIENT,
                "cat", SHARED_PARAMS_METADATA_PATH);
        return JsonPath.from(result.getStdout()).getString("expirationDate");
    }
}
