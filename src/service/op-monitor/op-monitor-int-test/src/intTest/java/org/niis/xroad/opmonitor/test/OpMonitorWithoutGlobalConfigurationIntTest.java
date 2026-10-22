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
package org.niis.xroad.opmonitor.test;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

@ExtendWith(ApiStackExtension.class)
@DisplayName("op-monitor runs without global configuration")
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorWithoutGlobalConfigurationIntTest {

    private static final String DATABASE_CHECK = "OP_MONITOR_DATABASE_READINESS_CHECK";
    private static final String CONFIGURATION_CLIENT_CHECK = "CONFCLIENT_CHANNEL_READINESS_CHECK";
    private static final String GLOBAL_CONFIGURATION_DIRECTORY = "/etc/xroad/globalconf";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);

    @Test
    @DisplayName("No global configuration is present in the op-monitor container")
    void noGlobalConfigurationIsPresent(OpMonitorContainerSetup stack) {
        var files = when("the global configuration directory is listed inside the op-monitor container",
                () -> stack.filesInOpMonitor(GLOBAL_CONFIGURATION_DIRECTORY));
        then("it holds no files", () -> assertThat(files).isEmpty());
    }

    @Test
    @DisplayName("Readiness is UP and the configuration-client readiness check is not registered")
    void readinessIsUpWithoutConfigurationClientCheck(OpMonitorContainerSetup stack) {
        var readiness = when("the op-monitor readiness endpoint is polled until UP",
                () -> await()
                        .pollDelay(Duration.ZERO)
                        .pollInterval(POLL_INTERVAL)
                        .atMost(POLL_TIMEOUT)
                        .ignoreExceptions()
                        .until(() -> JsonPath.from(stack.readinessResponse()),
                                json -> "UP".equals(json.getString("status"))));
        then("the overall status is UP and the database check is UP", () -> {
            assertThat(readiness.getString("status")).isEqualTo("UP");
            assertThat(readiness.getString("checks.find { it.name == '%s' }.status".formatted(DATABASE_CHECK)))
                    .isEqualTo("UP");
        });
        then("no configuration-client readiness check is registered", () ->
                assertThat(readiness.getList("checks.name", String.class)).doesNotContain(CONFIGURATION_CLIENT_CHECK));
    }
}
