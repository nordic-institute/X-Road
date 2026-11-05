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
package org.niis.xroad.securityserver.restapi.scheduling;

import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.scheduling.PublicationBackoff.Key;
import org.niis.xroad.securityserver.restapi.scheduling.PublicationBackoff.RequestKind;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationBackoffTest {

    private static final Key SYSTEM = Key.ofKind(RequestKind.SYSTEM);
    private static final Key MEMBER = Key.ofKind(RequestKind.MEMBER);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
    private double jitter = 1.0;
    private final DoubleSupplier jitterSource = () -> jitter;
    private final PublicationBackoff backoff = new PublicationBackoff(clock, jitterSource);

    private Duration failAndMeasurePause(Key key) {
        backoff.recordFailure(key);
        var pause = Duration.ZERO;
        while (!backoff.mayAttempt(key)) {
            clock.advance(Duration.ofSeconds(1));
            pause = pause.plusSeconds(1);
        }
        return pause;
    }

    @Test
    void aKeyWithoutFailuresMayAttempt() {
        assertThat(backoff.mayAttempt(SYSTEM)).isTrue();
    }

    @Test
    void thePauseGrowsFromThirtySecondsAndStopsAtFiveMinutes() {
        var pauses = Stream.generate(() -> failAndMeasurePause(SYSTEM)).limit(8).toList();

        assertThat(pauses).containsExactly(
                Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120), Duration.ofSeconds(240),
                Duration.ofSeconds(300), Duration.ofSeconds(300), Duration.ofSeconds(300), Duration.ofSeconds(300));
    }

    @Test
    void theJitterOnlyShortensThePauseWithinItsBounds() {
        jitter = 0.0;
        var shortest = failAndMeasurePause(SYSTEM);
        backoff.recordSuccess(SYSTEM);
        jitter = 0.999999;
        var longest = failAndMeasurePause(SYSTEM);

        assertThat(shortest).isEqualTo(Duration.ofSeconds(23));
        assertThat(longest).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void theCeilingHoldsAfterManyFailuresWithTheLongestJitter() {
        for (int i = 0; i < 200; i++) {
            backoff.recordFailure(SYSTEM);
        }
        clock.advance(PublicationBackoff.MAX_DELAY);

        assertThat(backoff.mayAttempt(SYSTEM)).isTrue();
    }

    @Test
    void aSuccessEndsThePauseAndResetsTheGrowth() {
        failAndMeasurePause(SYSTEM);
        failAndMeasurePause(SYSTEM);
        backoff.recordFailure(SYSTEM);
        assertThat(backoff.mayAttempt(SYSTEM)).isFalse();

        backoff.recordSuccess(SYSTEM);

        assertThat(backoff.mayAttempt(SYSTEM)).isTrue();
        assertThat(failAndMeasurePause(SYSTEM)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void thePauseIsNotOverBeforeItsLastSecond() {
        backoff.recordFailure(SYSTEM);

        clock.advance(Duration.ofSeconds(29));

        assertThat(backoff.mayAttempt(SYSTEM)).isFalse();
    }

    @Test
    void requestKindsAreIndependent() {
        backoff.recordFailure(SYSTEM);

        assertThat(backoff.mayAttempt(SYSTEM)).isFalse();
        assertThat(backoff.mayAttempt(MEMBER)).isTrue();
    }

    @Test
    void valuesAreIndependentOfEachOtherAndOfTheirRequestKind() {
        var first = Key.ofValue(RequestKind.MEMBER, "did:web:a");
        var second = Key.ofValue(RequestKind.MEMBER, "did:web:b");

        backoff.recordFailure(first);

        assertThat(backoff.mayAttempt(first)).isFalse();
        assertThat(backoff.mayAttempt(second)).isTrue();
        assertThat(backoff.mayAttempt(MEMBER)).isTrue();

        backoff.recordFailure(MEMBER);
        backoff.recordSuccess(first);

        assertThat(backoff.mayAttempt(first)).isTrue();
        assertThat(backoff.mayAttempt(MEMBER)).isFalse();
    }

    @Test
    void aMaximumFailureStartsTheLongestPauseAtOnce() {
        backoff.recordMaximumFailure(SYSTEM);

        clock.advance(PublicationBackoff.MAX_DELAY.minusSeconds(1));
        assertThat(backoff.mayAttempt(SYSTEM)).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(backoff.mayAttempt(SYSTEM)).isTrue();
    }

    @Test
    void aProbeLimitsTheBatchSizeToOneUntilItIsClearedAndIsKeptPerRequestKind() {
        backoff.rememberAcceptedSize(RequestKind.MEMBER, 25);
        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(25);

        backoff.requireProbe(RequestKind.MEMBER);

        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(1);
        assertThat(backoff.batchSize(RequestKind.SYSTEM, 100)).isEqualTo(100);

        backoff.clearProbe(RequestKind.MEMBER);

        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(25);
    }

    @Test
    void theBatchSizeIsLimitedByTheRememberedAcceptedSizeOfItsRequestKindOnly() {
        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(100);

        backoff.rememberAcceptedSize(RequestKind.MEMBER, 25);

        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(25);
        assertThat(backoff.batchSize(RequestKind.MEMBER, 10)).isEqualTo(10);
        assertThat(backoff.batchSize(RequestKind.SYSTEM, 100)).isEqualTo(100);
    }

    @Test
    void theRememberedAcceptedSizeNeverGrowsBack() {
        backoff.rememberAcceptedSize(RequestKind.MEMBER, 25);
        backoff.rememberAcceptedSize(RequestKind.MEMBER, 50);

        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(25);
    }

    @Test
    void theRememberedAcceptedSizeNeverDropsBelowOne() {
        backoff.rememberAcceptedSize(RequestKind.MEMBER, 0);

        assertThat(backoff.batchSize(RequestKind.MEMBER, 100)).isEqualTo(1);
    }
}
