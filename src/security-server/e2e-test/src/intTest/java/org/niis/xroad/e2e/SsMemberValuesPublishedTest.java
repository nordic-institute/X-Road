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
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Each Security Server sends the DID of every member it hosts to the Central Server, through the management
 * Security Server, and marks it published once globalconf carries it. The scenario follows that whole path through
 * the admin API's data space provisioning status, which reports a publication state per MEMBER context, on ss1 and
 * on ss0.
 *
 * <p>The expected members come from the environment's setup, not from the endpoint: ss0 hosts only the member of its
 * own owner, {@code DEV:COM:1234}, and ss1 hosts its owner {@code DEV:COM:4321} plus {@code DEV:COM:1234}, which it
 * reaches only through the {@code test-consumer} subsystem client. A member missing from the status therefore fails
 * the scenario like a member that is not yet published.
 *
 * <p>Read-only and safe to re-run on a warm cluster. It runs before the scenarios that add or remove members.
 */
@DisplayName("SS dataspace - member DIDs published")
@Order(62)
@SuppressWarnings("checkstyle:magicnumber")
class SsMemberValuesPublishedTest extends E2eTest {

    private static final String MEMBER_KIND = "MEMBER";
    private static final String PUBLISHED_STATE = "PUBLISHED";
    private static final String REJECTED_STATE = "REJECTED";

    private static final String SS0_OWNER_MEMBER = "DEV:COM:1234";
    private static final String SS1_OWNER_MEMBER = "DEV:COM:4321";

    private static final Map<String, List<String>> HOSTED_MEMBERS = Map.of(
            "ss0", List.of(SS0_OWNER_MEMBER),
            "ss1", List.of(SS1_OWNER_MEMBER, SS0_OWNER_MEMBER));

    private static final Duration PUBLICATION_POLL_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration PUBLICATION_POLL_INTERVAL = Duration.ofSeconds(5);

    @Test
    @DisplayName("ss1 and ss0 report every hosted member DID as PUBLISHED")
    void memberDidsReachPublishedOnEveryServer(E2eEnvironment env) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        for (var envName : List.of("ss1", "ss0")) {
            then("%s reports members %s as PUBLISHED".formatted(envName, HOSTED_MEMBERS.get(envName)), () ->
                    awaitMembersPublished(env, envName, HOSTED_MEMBERS.get(envName)));
        }
    }

    private void awaitMembersPublished(E2eEnvironment env, String envName, List<String> expectedMembers) {
        var baseUrl = adminBaseUrl(env, envName);
        var session = login(baseUrl);
        var lastSeen = new AtomicReference<>("no provisioning status read yet");
        var rejected = new AtomicReference<String>();

        try {
            Awaitility.await()
                    .pollInterval(PUBLICATION_POLL_INTERVAL)
                    .timeout(PUBLICATION_POLL_TIMEOUT)
                    .until(() -> {
                        try {
                            var response = authed(session).get(baseUrl + "/api/v1/dataspace/provisioning-status");
                            if (response.getStatusCode() != HTTP_OK) {
                                lastSeen.set("HTTP status " + response.getStatusCode());
                                return false;
                            }
                            List<Map<String, Object>> contexts = response.jsonPath()
                                    .getList("participant_contexts.findAll { it.kind == '" + MEMBER_KIND + "' }");
                            lastSeen.set(contexts.toString());
                            return allPublishedOrRejected(contexts, expectedMembers, rejected);
                        } catch (RuntimeException e) {
                            lastSeen.set("request failed: " + e);
                            return false;
                        }
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    ("Timed out waiting for %s's members %s to reach publication_state=%s; last observed MEMBER "
                            + "provisioning status entries: %s").formatted(envName, expectedMembers, PUBLISHED_STATE,
                            lastSeen.get()), e);
        }
        assertThat(rejected.get()).as("%s's member publication was rejected", envName).isNull();
    }

    /** True once every expected member is PUBLISHED, or as soon as one is REJECTED, which is recorded in {@code rejected}. */
    private static boolean allPublishedOrRejected(List<Map<String, Object>> contexts, List<String> expectedMembers,
                                                  AtomicReference<String> rejected) {
        var byParticipantId = contexts.stream()
                .collect(Collectors.toMap(context -> String.valueOf(context.get("participant_id")),
                        Function.identity(), (first, _) -> first));
        var allPublished = true;
        for (var member : expectedMembers) {
            var context = byParticipantId.get(member);
            var state = context == null ? null : context.get("publication_state");
            if (REJECTED_STATE.equals(state)) {
                rejected.set("%s: %s".formatted(member, context));
                return true;
            }
            allPublished &= PUBLISHED_STATE.equals(state);
        }
        return allPublished;
    }
}
