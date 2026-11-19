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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.e2e.parity.acl.Verdict;
import org.niis.xroad.e2e.parity.acl.VerdictPoller;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.e2e.DisabledServiceFault.awaitCallSucceeds;
import static org.niis.xroad.e2e.DisabledServiceFault.observe;
import static org.niis.xroad.e2e.Mock1Fixture.callMock1;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Proves that calls survive an Issuer Service outage across a restart of ss0's control plane and Identity Hub, and
 * that the maximum age for issuer data from the global configuration turns the stale copies off. ss0 calls its own
 * {@code mock1} service, so its control plane verifies the credential its own Identity Hub presents. The timing of
 * the deadlines is covered by unit tests; the maximum age keeps its default during the outage and is set to 0 only
 * to end it.
 *
 * <p>Every failure of the transfer reaches the client as the same HTTP 500 from the consumer's proxy, whatever
 * status the provider answered. The scenario therefore expects a failure that is neither a success nor
 * {@code access_denied} nor a rejected agreement token.
 *
 * <p>The scenario changes the Central Server's global configuration and stops a service, so it is {@link Isolated}
 * (the suite runs serially in {@link Order} order) and restores the Issuer Service and the default maximum age
 * when it ends, also on failure. It needs {@link IssuerOutageOps}, which only the compose adapter implements,
 * and self-skips elsewhere via {@link Assumptions}.
 */
@DisplayName("SS dataspace - verification survives a short Issuer Service outage")
@Slf4j
@Order(420)
@Isolated
class SsIssuerOutageTest extends E2eTest {

    private static final String SS0_ENV = "ss0";

    /** {@code xroad.proxy.agreement-token.token-ttl} of ss0 in {@code compose.ss-agreement-token-key-pruning.e2e.yaml}. */
    private static final Duration AGREEMENT_TOKEN_TTL = Duration.ofSeconds(30);
    /** How long the consumer's proxy reuses an access it has won, so that a call needs no new verification. */
    private static final Duration ASSET_ACCESS_LIFETIME =
            AGREEMENT_TOKEN_TTL.minus(ProxyConfigKeys.DSP_CACHE_MARGIN.convertedDefaultValue());
    private static final Duration ACCESS_EXPIRY_SLACK = Duration.ofSeconds(5);

    /**
     * The latest moment, counted from the start of the outage, at which a call that must succeed may begin. It is far
     * below the 600 s default maximum age, so a slow environment fails with its own message instead of a product
     * assertion.
     */
    private static final Duration PRECONDITION_LIMIT = Duration.ofSeconds(300);

    private static final Duration RESTART_CALL_TIMEOUT = Duration.ofSeconds(30);
    /** Time for the global configuration with the new maximum age to reach ss0's configuration client. */
    private static final Duration MAXIMUM_AGE_DELIVERY_TIMEOUT = Duration.ofSeconds(90);
    /** Time for ss0's control plane and Identity Hub to load the downloaded global configuration. */
    private static final Duration PROXY_REFRESH_GRACE = Duration.ofSeconds(15);
    private static final Duration FAILURE_SLACK = Duration.ofSeconds(10);
    private static final Duration CALL_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration FAILURE_POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(5);

    @Test
    @DisplayName("Calls survive the outage and a restart, fail once the maximum age is 0 and recover")
    void callsSurviveTheOutageAcrossARestart(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof IssuerOutageOps candidate && candidate.ss0RunsSeparateDataspaceServices(),
                () -> "%s cannot stop the Issuer Service and restart ss0's dataspace services"
                        .formatted(env.getClass().getSimpleName()));

        var ops = (IssuerOutageOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        try {
            var lastCallBeforeRestart = given("a call to ss0's own service succeeds", () -> {
                awaitCallSucceeds(() -> callMock1(env, SS0_ENV), RECOVERY_TIMEOUT, CALL_POLL_INTERVAL);
                log.info("Issuer outage timeline: baseline call succeeded");
                return Instant.now();
            });

            var outageStart = when("the Issuer Service is stopped", () -> {
                ops.stopIssuerService();
                return Instant.now();
            });
            timeline(outageStart, "Issuer Service stop returned");

            when("ss0's control plane and Identity Hub are restarted", ops::restartSs0DataspaceServices);
            timeline(outageStart, "ss0 restart ended");

            and("the access the proxy won before the restart has expired", () -> pause(remaining(
                    lastCallBeforeRestart.plus(ASSET_ACCESS_LIFETIME).plus(ACCESS_EXPIRY_SLACK))));
            requireWithinLimit(outageStart, "ss0 restart and access expiry pause");
            then("a call succeeds on a new verification from the copy read back from the database",
                    () -> awaitVerdict(env, outageStart, "call after the restart", Verdict.ALLOWED,
                            RESTART_CALL_TIMEOUT, CALL_POLL_INTERVAL));

            when("the maximum age is set to 0", () -> ops.setIssuerDataFreshnessSeconds(0));
            var delivered = and("the maximum age reaches ss0", () -> {
                awaitMaximumAgeDelivered(ops);
                return Instant.now();
            });
            timeline(outageStart, "maximum age 0 delivered to ss0");

            then("calls fail on the consumer side, not with access_denied or a rejected token", () -> {
                var failureTimeout = remaining(
                        delivered.plus(PROXY_REFRESH_GRACE).plus(ASSET_ACCESS_LIFETIME).plus(FAILURE_SLACK));
                awaitVerdict(env, outageStart, "call after the maximum age 0", Verdict.CONSUMER_FAILED,
                        failureTimeout, FAILURE_POLL_INTERVAL);
            });
            timeline(outageStart, "first consumer failure seen");

            when("the Issuer Service is started again", ops::startIssuerService);
            timeline(outageStart, "Issuer Service started");
            then("calls succeed again", () -> awaitVerdict(env, outageStart, "call after the Issuer Service restart",
                    Verdict.ALLOWED, RECOVERY_TIMEOUT, CALL_POLL_INTERVAL));
            timeline(outageStart, "calls recovered");
        } finally {
            try {
                ops.startIssuerService();
            } finally {
                ops.resetIssuerDataFreshnessSeconds();
            }
            awaitCallsSucceedAgainQuietly(env);
        }
    }

    private static void awaitMaximumAgeDelivered(IssuerOutageOps ops) {
        await().alias("global configuration carrying a maximum age of 0 reached ss0")
                .pollDelay(Duration.ZERO).atMost(MAXIMUM_AGE_DELIVERY_TIMEOUT).pollInterval(CALL_POLL_INTERVAL)
                .ignoreExceptions()
                .until(() -> ops.ss0HasDownloadedIssuerDataFreshnessSeconds(0));
    }

    private static void awaitCallsSucceedAgainQuietly(E2eEnvironment env) {
        try {
            awaitCallSucceeds(() -> callMock1(env, SS0_ENV), RECOVERY_TIMEOUT, CALL_POLL_INTERVAL);
        } catch (RuntimeException | AssertionError e) {
            log.warn("Calls to ss0 did not succeed again after the scenario restored the environment", e);
        }
    }

    private static void pause(Duration duration) {
        await().pollDelay(duration).atMost(duration.plusMinutes(1)).until(() -> true);
    }

    private static void awaitVerdict(E2eEnvironment env, Instant outageStart, String call, Verdict expected,
                                     Duration timeout, Duration pollInterval) {
        var result = new VerdictPoller().await(() -> timedCall(env, outageStart), expected, timeout, pollInterval);
        assertThat(result.matched()).as(() -> result.failureMessage(call, expected)).isTrue();
    }

    private static VerdictPoller.Observation timedCall(E2eEnvironment env, Instant outageStart) {
        var observation = observe(callMock1(env, SS0_ENV).extract().response());
        timeline(outageStart, "call " + observation.verdict());
        return observation;
    }

    private static void requireWithinLimit(Instant outageStart, String phase) {
        var elapsed = Duration.between(outageStart, Instant.now());
        if (elapsed.compareTo(PRECONDITION_LIMIT) > 0) {
            fail("environment too slow: %s ended at +%d s, limit +%d s"
                    .formatted(phase, elapsed.toSeconds(), PRECONDITION_LIMIT.toSeconds()));
        }
    }

    private static void timeline(Instant outageStart, String event) {
        log.info("Issuer outage timeline +{} s: {}", Duration.between(outageStart, Instant.now()).toSeconds(), event);
    }

    private static Duration remaining(Instant until) {
        var left = Duration.between(Instant.now(), until);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
