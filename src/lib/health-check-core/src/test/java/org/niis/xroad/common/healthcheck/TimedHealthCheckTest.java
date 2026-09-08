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
package org.niis.xroad.common.healthcheck;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimedHealthCheckTest {

    private static final String CHECK_NAME = "TEST_TIMED";
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(100);
    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void returnsDownWithTimeoutDataWhenDelegateExceedsTimeout() {
        HealthCheck slow = () -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ignored) {
                // abandon-and-let-run
            }
            return HealthCheckResponse.up("WONT_GET_HERE");
        };
        TimedHealthCheck timed = new TimedHealthCheck(slow, SHORT_TIMEOUT, CHECK_NAME);

        long t0 = System.nanoTime();
        HealthCheckResponse resp = timed.call();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(resp.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(resp.getName()).isEqualTo(CHECK_NAME);
        assertThat(resp.getData()).isPresent();
        assertThat(resp.getData().get())
                .containsEntry("error", "timeout")
                .containsEntry("timeout_ms", 100L);
        assertThat(elapsedMs)
                .as("timeout fires promptly, not after the delegate's 2s sleep")
                .isBetween(100L, 1_500L);
    }

    @Test
    void passesThroughResponseWhenDelegateCompletesInTime() {
        HealthCheck fast = () -> HealthCheckResponse.up("FAST");
        TimedHealthCheck timed = new TimedHealthCheck(fast, LONG_TIMEOUT, "FAST");

        HealthCheckResponse resp = timed.call();
        assertThat(resp.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(resp.getName()).isEqualTo("FAST");
    }

    @Test
    void propagatesRuntimeExceptionFromDelegate() {
        HealthCheck broken = () -> {
            throw new IllegalStateException("boom");
        };
        TimedHealthCheck timed = new TimedHealthCheck(broken, LONG_TIMEOUT, CHECK_NAME);

        assertThatThrownBy(timed::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void doesNotInterruptWrappedThreadOnTimeout() throws Exception {
        AtomicBoolean observedInterrupted = new AtomicBoolean(false);
        CountDownLatch observedLatch = new CountDownLatch(1);

        HealthCheck slow = () -> {
            try {
                // Sleep past the wrapper's 100ms timeout. The wrapper MUST NOT interrupt.
                Thread.sleep(500);
                observedInterrupted.set(Thread.currentThread().isInterrupted());
            } catch (InterruptedException e) {
                observedInterrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                observedLatch.countDown();
            }
            return HealthCheckResponse.up("WONT_GET_HERE");
        };
        TimedHealthCheck timed = new TimedHealthCheck(slow, SHORT_TIMEOUT, CHECK_NAME);

        HealthCheckResponse resp = timed.call();
        assertThat(resp.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);

        // Wait for the delegate thread to finish its sleep and record its state.
        assertThat(observedLatch.await(2, TimeUnit.SECONDS))
                .as("delegate thread finished its sleep within 2s")
                .isTrue();
        assertThat(observedInterrupted.get())
                .as("TimedHealthCheck MUST NOT interrupt the wrapped thread on timeout")
                .isFalse();
    }
}
