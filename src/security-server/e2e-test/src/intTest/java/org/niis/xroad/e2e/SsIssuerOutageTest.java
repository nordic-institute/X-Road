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
import org.niis.xroad.e2e.parity.acl.Verdict;
import org.niis.xroad.e2e.parity.acl.VerdictPoller;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

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
 * {@code access_denied} nor a rejected agreement token, and reads the cause from ss0's control plane log. ss0 is its
 * own provider, so its control plane warns once for each 503 caused by unusable issuer data. The restart empties
 * every in-memory copy of issuer data, so a call after it counts as a new verification when the provider's control
 * plane logs an incoming transfer request during that call; the Issuer Service is stopped, so the stored copies
 * served it.
 *
 * <p>The scenario changes the Central Server's global configuration and stops a service, so it is {@link Isolated}
 * (the suite runs serially in {@link Order} order) and restores the Issuer Service and the default maximum age
 * when it ends, also on failure. It needs {@link IssuerOutageOps}, which only the compose adapter implements,
 * and self-skips elsewhere via {@link Assumptions}. It also skips in a legacy ACL run, which has no Issuer Service.
 */
@DisplayName("SS dataspace - verification survives a short Issuer Service outage")
@Slf4j
@Order(420)
@Isolated
class SsIssuerOutageTest extends E2eTest {

    private static final String SS0_ENV = "ss0";

    /** The provider's control plane logs this for each transfer request it receives, which it verifies. */
    private static final Pattern INCOMING_TRANSFER_REQUEST = Pattern.compile("DSP: Incoming TransferRequestMessage");

    /**
     * The warning of the provider's control plane for each 503 it answers because no usable copy of the issuer data
     * remains. The colon after "completed" keeps the debug echo of the same failure out.
     */
    private static final Pattern ISSUER_DATA_UNAVAILABLE = Pattern.compile(
            "Token verification could not be completed: .*Failed to download the "
                    + "(DID document of|statuslist credential from) ");

    /**
     * The latest moment, counted from the start of the outage, at which a call that must succeed may begin. It is far
     * below the 600 s default maximum age, so a slow environment fails with its own message instead of a product
     * assertion.
     */
    private static final Duration PRECONDITION_LIMIT = Duration.ofSeconds(300);

    /** Longer than the time the consumer's proxy reuses an access it has won, so a call then needs a verification. */
    private static final Duration NEW_VERIFICATION_TIMEOUT = Duration.ofSeconds(60);
    /** Time for the global configuration with the new maximum age to reach ss0's configuration client. */
    private static final Duration MAXIMUM_AGE_DELIVERY_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration FAILURE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration FAILURE_CAUSE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CALL_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration FAILURE_POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofMinutes(5);

    @Test
    @DisplayName("Calls survive the outage and a restart, fail once the maximum age is 0 and recover")
    void callsSurviveTheOutageAcrossARestart(E2eEnvironment env) {
        Assumptions.assumeFalse(AclParityProperties.isLegacyRun(),
                "a legacy run has no Issuer Service to stop, and every call is unnegotiated, so the provider verifies "
                        + "no credential");
        Assumptions.assumeTrue(env instanceof IssuerOutageOps candidate && candidate.ss0RunsSeparateDataspaceServices(),
                () -> "%s cannot stop the Issuer Service and restart ss0's dataspace services"
                        .formatted(env.getClass().getSimpleName()));

        var ops = (IssuerOutageOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        try {
            given("a call to ss0's own service succeeds", () -> {
                awaitCallSucceeds(() -> callMock1(env, SS0_ENV), RECOVERY_TIMEOUT, CALL_POLL_INTERVAL);
                log.info("Issuer outage timeline: baseline call succeeded");
            });

            var outageStart = when("the Issuer Service is stopped", () -> {
                ops.stopIssuerService();
                return Instant.now();
            });
            timeline(outageStart, "Issuer Service stop returned");
            var unavailableAnswersAtOutageStart = ops.countSs0ControlPlaneLogLines(ISSUER_DATA_UNAVAILABLE);

            when("ss0's control plane and Identity Hub are restarted", ops::restartSs0DataspaceServices);
            timeline(outageStart, "ss0 restart ended");

            then("a call succeeds on a new verification served by the stored copies of the issuer data",
                    () -> awaitAllowedWithNewVerification(env, ops, outageStart));
            requireWithinLimit(outageStart, "ss0 restart and first verification");
            then("the provider answered no 503 while the stored copies were usable",
                    () -> assertThat(ops.countSs0ControlPlaneLogLines(ISSUER_DATA_UNAVAILABLE))
                            .isEqualTo(unavailableAnswersAtOutageStart));

            when("the maximum age is set to 0", () -> ops.setIssuerDataFreshnessSeconds(0));
            and("the maximum age reaches ss0", () -> awaitMaximumAgeDelivered(ops));
            timeline(outageStart, "maximum age 0 delivered to ss0");

            var unavailableAnswersBefore = ops.countSs0ControlPlaneLogLines(ISSUER_DATA_UNAVAILABLE);
            then("calls fail on the consumer side, not with access_denied or a rejected token",
                    () -> awaitVerdict(env, outageStart, "call after the maximum age 0", Verdict.CONSUMER_FAILED,
                            FAILURE_TIMEOUT, FAILURE_POLL_INTERVAL));
            timeline(outageStart, "first consumer failure seen");
            and("the provider answered 503 because no usable copy of the issuer data remains",
                    () -> awaitIssuerDataUnavailableAnswer(ops, unavailableAnswersBefore));

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

    private static void awaitAllowedWithNewVerification(E2eEnvironment env, IssuerOutageOps ops, Instant outageStart) {
        await().alias("a call succeeded and the provider received a new transfer request during it")
                .pollDelay(Duration.ZERO).atMost(NEW_VERIFICATION_TIMEOUT).pollInterval(CALL_POLL_INTERVAL)
                .until(() -> allowedOnNewVerification(env, ops, outageStart));
    }

    private static boolean allowedOnNewVerification(E2eEnvironment env, IssuerOutageOps ops, Instant outageStart) {
        var requestsBefore = ops.countSs0ControlPlaneLogLines(INCOMING_TRANSFER_REQUEST);
        var allowed = timedCall(env, outageStart).verdict() == Verdict.ALLOWED;
        return allowed && ops.countSs0ControlPlaneLogLines(INCOMING_TRANSFER_REQUEST) > requestsBefore;
    }

    private static void awaitIssuerDataUnavailableAnswer(IssuerOutageOps ops, long answersBefore) {
        await().alias("the provider answered 503 because no usable copy of the issuer data remains")
                .pollDelay(Duration.ZERO).atMost(FAILURE_CAUSE_TIMEOUT).pollInterval(Duration.ofSeconds(1))
                .until(() -> ops.countSs0ControlPlaneLogLines(ISSUER_DATA_UNAVAILABLE) > answersBefore);
    }

    private static void awaitCallsSucceedAgainQuietly(E2eEnvironment env) {
        try {
            awaitCallSucceeds(() -> callMock1(env, SS0_ENV), RECOVERY_TIMEOUT, CALL_POLL_INTERVAL);
        } catch (RuntimeException | AssertionError e) {
            log.warn("Calls to ss0 did not succeed again after the scenario restored the environment", e);
        }
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
}
