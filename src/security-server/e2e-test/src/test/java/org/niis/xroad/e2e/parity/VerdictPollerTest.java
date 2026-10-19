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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictPollerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final Duration INTERVAL = Duration.ofSeconds(3);

    /** A clock that only advances when slept on, and fails if a poller sleeps far past any sane deadline. */
    private static final class FakeClock implements VerdictPoller.Clock {
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public void sleepMillis(long millis) {
            now += millis;
            if (now > TIMEOUT.toMillis() * 10) {
                throw new AssertionError("poller kept sleeping long after the deadline");
            }
        }
    }

    @Test
    void returnsAsSoonAsTheExpectedVerdictAppears() {
        var calls = new AtomicInteger();
        var poller = new VerdictPoller(new FakeClock());

        var result = poller.await(
                () -> calls.incrementAndGet() < 4 ? Verdict.ACCESS_DENIED : Verdict.ALLOWED,
                Verdict.ALLOWED, TIMEOUT, INTERVAL);

        assertThat(result.matched()).isTrue();
        assertThat(result.last()).isEqualTo(Verdict.ALLOWED);
        assertThat(result.attempts()).isEqualTo(4);
        assertThat(result.elapsed()).isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void stopsAtTheDeadlineWhenTheVerdictNeverFlips() {
        var poller = new VerdictPoller(new FakeClock());

        var result = poller.await(() -> Verdict.ACCESS_DENIED, Verdict.ALLOWED, TIMEOUT, INTERVAL);

        assertThat(result.matched()).isFalse();
        assertThat(result.last()).isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(result.attempts()).isEqualTo(31);
        assertThat(result.elapsed()).isEqualTo(TIMEOUT);
    }

    @Test
    void doesNotSeeAFlipThatComesAfterTheDeadline() {
        var calls = new AtomicInteger();
        var poller = new VerdictPoller(new FakeClock());

        var result = poller.await(
                () -> calls.incrementAndGet() <= 31 ? Verdict.ACCESS_DENIED : Verdict.ALLOWED,
                Verdict.ALLOWED, TIMEOUT, INTERVAL);

        assertThat(result.matched()).isFalse();
        assertThat(calls).hasValue(31);
    }

    @Test
    void aProbeThatThrowsCountsAsErrorAndPollingContinues() {
        var calls = new AtomicInteger();
        var poller = new VerdictPoller(new FakeClock());

        var result = poller.await(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("connection refused");
            }
            return Verdict.ALLOWED;
        }, Verdict.ALLOWED, TIMEOUT, INTERVAL);

        assertThat(result.matched()).isTrue();
        assertThat(result.attempts()).isEqualTo(3);
    }

    @Test
    void failureMessageNamesTheScenarioAndBothVerdicts() {
        var poller = new VerdictPoller(new FakeClock());

        var result = poller.await(() -> Verdict.ERROR, Verdict.ACCESS_DENIED, TIMEOUT, INTERVAL);

        assertThat(result.failureMessage("V02", Verdict.ACCESS_DENIED))
                .startsWith("V02: expected ACCESS_DENIED but got ERROR");
    }
}
