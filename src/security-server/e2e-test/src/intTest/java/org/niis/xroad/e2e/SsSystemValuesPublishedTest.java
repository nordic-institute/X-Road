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
package org.niis.xroad.e2e;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Each Security Server sends its SYSTEM DID and DSP base URL to the Central Server on its own, and marks them
 * published once globalconf carries them. The scenario follows that whole path through the admin API's data space
 * provisioning status, which reports the server-level publication state, on ss1 and on ss0, the management
 * Security Server.
 *
 * <p>Read-only: it only queries provisioning status, so it is safe to re-run against a warm cluster. Unlike the
 * participant context scenarios it does not need the control plane database, so it runs on compose and k8s alike.
 * Publication waits for the data space to be ready, a send and then several globalconf distribution cycles, hence
 * the long bound.
 */
@DisplayName("SS dataspace - SYSTEM values published")
@Order(61)
@SuppressWarnings("checkstyle:magicnumber")
class SsSystemValuesPublishedTest extends E2eTest {

    private static final String PUBLISHED_STATE = "PUBLISHED";

    private static final Duration PUBLICATION_POLL_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration PUBLICATION_POLL_INTERVAL = Duration.ofSeconds(5);

    @Test
    @DisplayName("ss1 and ss0 each report their SYSTEM values as PUBLISHED")
    void systemValuesReachPublishedOnEveryServer(E2eEnvironment env) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        for (var envName : List.of("ss1", "ss0")) {
            then("%s reports its SYSTEM values as PUBLISHED".formatted(envName), () ->
                    awaitSystemValuesPublished(env, envName));
        }
    }

    private void awaitSystemValuesPublished(E2eEnvironment env, String envName) {
        var baseUrl = adminBaseUrl(env, envName);
        var session = login(baseUrl);
        var lastSeen = new AtomicReference<Map<String, ?>>(null);

        try {
            Awaitility.await()
                    .pollInterval(PUBLICATION_POLL_INTERVAL)
                    .timeout(PUBLICATION_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var response = authed(session).get(baseUrl + "/api/v1/dataspace/provisioning-status");
                        if (response.getStatusCode() != HTTP_OK) {
                            return false;
                        }
                        Map<String, ?> server = response.jsonPath().getMap("server");
                        lastSeen.set(server);
                        return server != null && PUBLISHED_STATE.equals(server.get("publication_state"));
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    ("Timed out waiting for %s's SYSTEM values to reach publication_state=%s; last observed "
                            + "provisioning status server entry: %s").formatted(envName, PUBLISHED_STATE, lastSeen.get()), e);
        }
    }
}
