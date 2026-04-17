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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CachingHealthCheckTest {

    private static final String CHECK_NAME = "TEST_CACHING";
    private static final Duration SUCCESS_TTL = Duration.ofMillis(50);
    private static final Duration ERROR_TTL = Duration.ofMillis(100);
    private static final Duration MAX_ERROR_TTL = Duration.ofMillis(800);
    private static final int BACKOFF_MULTIPLIER = 2;

    @Test
    void returnsCachedResponseWithinSuccessTtl() {
        AtomicInteger invocations = new AtomicInteger();
        HealthCheck delegate = () -> {
            invocations.incrementAndGet();
            return HealthCheckResponse.up(CHECK_NAME);
        };
        CachingHealthCheck caching = new CachingHealthCheck(delegate,
                Duration.ofSeconds(5), ERROR_TTL, MAX_ERROR_TTL, BACKOFF_MULTIPLIER);

        HealthCheckResponse first = caching.call();
        HealthCheckResponse second = caching.call();

        assertThat(first.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(second).isSameAs(first);
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void refreshesAfterSuccessTtlExpiry() throws InterruptedException {
        AtomicInteger invocations = new AtomicInteger();
        HealthCheck delegate = () -> {
            invocations.incrementAndGet();
            return HealthCheckResponse.up(CHECK_NAME);
        };
        CachingHealthCheck caching = new CachingHealthCheck(delegate,
                SUCCESS_TTL, ERROR_TTL, MAX_ERROR_TTL, BACKOFF_MULTIPLIER);

        caching.call();
        Thread.sleep(SUCCESS_TTL.toMillis() + 30);
        caching.call();

        assertThat(invocations.get()).isEqualTo(2);
    }

    @Test
    void errorTtlDoublesUntilCap() throws InterruptedException {
        // Warm JIT + class-loading so first-iteration timing isn't distorted by
        // ~50ms startup cost that can otherwise erode the 50ms sleep margin inside
        // the 100ms step-0 TTL window.
        CachingHealthCheck warmup = new CachingHealthCheck(
                () -> HealthCheckResponse.down(CHECK_NAME),
                Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(10), 2);
        warmup.call();
        warmup.call();

        AtomicInteger invocations = new AtomicInteger();
        HealthCheck delegate = () -> {
            invocations.incrementAndGet();
            return HealthCheckResponse.down(CHECK_NAME);
        };
        CachingHealthCheck caching = new CachingHealthCheck(delegate,
                SUCCESS_TTL, ERROR_TTL, MAX_ERROR_TTL, BACKOFF_MULTIPLIER);

        // Expected progression of stored error-TTL (ms): 100, 200, 400, 800 (cap), 800
        long[] expectedTtlMs = {100, 200, 400, 800, 800};

        for (int i = 0; i < expectedTtlMs.length; i++) {
            long before = invocations.get();
            HealthCheckResponse resp = caching.call();
            assertThat(resp.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
            assertThat(invocations.get()).isEqualTo(before + 1);

            long currentTtl = expectedTtlMs[i];
            // Sleep well within current TTL and verify no new invocation (cache still valid).
            // Use currentTtl/4 (not currentTtl/2) so first-iteration JIT/class-load jitter
            // doesn't accidentally exhaust the cache window on the tightest step (100ms).
            Thread.sleep(currentTtl / 4);
            long midInvocations = invocations.get();
            caching.call();
            assertThat(invocations.get())
                    .as("within error-TTL step %d (ttl=%dms) no new invocation", i, currentTtl)
                    .isEqualTo(midInvocations);

            // Sleep past the TTL window so the next outer-loop call refreshes
            Thread.sleep(currentTtl * 3 / 4 + 50);
        }

        // After cap is reached, next refresh should STILL use cap (not grow further)
        long before = invocations.get();
        caching.call();
        assertThat(invocations.get()).isEqualTo(before + 1);
        // Within cap window, still cached
        Thread.sleep(MAX_ERROR_TTL.toMillis() / 2);
        caching.call();
        assertThat(invocations.get()).isEqualTo(before + 1);
    }

    @Test
    void errorTtlResetsOnFirstOkAfterErrors() throws InterruptedException {
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<HealthCheckResponse.Status> nextStatus =
                new AtomicReference<>(HealthCheckResponse.Status.DOWN);
        HealthCheck delegate = () -> {
            invocations.incrementAndGet();
            return nextStatus.get() == HealthCheckResponse.Status.UP
                    ? HealthCheckResponse.up(CHECK_NAME)
                    : HealthCheckResponse.down(CHECK_NAME);
        };
        CachingHealthCheck caching = new CachingHealthCheck(delegate,
                SUCCESS_TTL, ERROR_TTL, MAX_ERROR_TTL, BACKOFF_MULTIPLIER);

        // Walk backoff a few steps: 100ms, 200ms, 400ms
        long[] climbTtlMs = {100, 200, 400};
        for (long ttl : climbTtlMs) {
            caching.call();
            Thread.sleep(ttl + 30);
        }

        // Flip to UP and let the next refresh observe it
        nextStatus.set(HealthCheckResponse.Status.UP);
        HealthCheckResponse resp = caching.call();
        assertThat(resp.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        Thread.sleep(SUCCESS_TTL.toMillis() + 30);

        // Now flip back to DOWN. The very first DOWN after the OK must store error-ttl (100ms), NOT the cap.
        nextStatus.set(HealthCheckResponse.Status.DOWN);
        long beforeFirstError = invocations.get();
        caching.call();
        assertThat(invocations.get()).isEqualTo(beforeFirstError + 1);

        // Within 100ms window (initial error-TTL), cached.
        Thread.sleep(50);
        long midInvocations = invocations.get();
        caching.call();
        assertThat(invocations.get())
                .as("first error after OK uses initial error-TTL, not cap")
                .isEqualTo(midInvocations);

        // After 100ms (+margin), cache must expire — confirms TTL was 100ms (reset) not 800ms (cap).
        Thread.sleep(90);
        caching.call();
        assertThat(invocations.get())
                .as("cache expired at initial error-TTL (reset verified)")
                .isEqualTo(midInvocations + 1);
    }

    @Test
    void exceptionFromDelegateCountsAsError() {
        AtomicInteger invocations = new AtomicInteger();
        HealthCheck delegate = () -> {
            invocations.incrementAndGet();
            throw new IllegalStateException("boom");
        };
        CachingHealthCheck caching = new CachingHealthCheck(delegate,
                SUCCESS_TTL, ERROR_TTL, MAX_ERROR_TTL, BACKOFF_MULTIPLIER);

        HealthCheckResponse first = caching.call();
        assertThat(first.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(first.getData().orElseThrow().get("error")).isEqualTo("IllegalStateException");
        assertThat(invocations.get()).isEqualTo(1);

        // Immediately recall — must be cached (backoff advanced), no new invocation
        HealthCheckResponse second = caching.call();
        assertThat(second.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void upstreamInvocationsBoundedUnderConcurrentProbes() throws Exception {
        final int threads = 32;
        final int itersPerThread = 1000;
        final Duration okTtl = Duration.ofMillis(100);

        AtomicInteger upstreamCalls = new AtomicInteger();
        HealthCheck delegate = () -> {
            upstreamCalls.incrementAndGet();
            return HealthCheckResponse.up(CHECK_NAME);
        };
        CachingHealthCheck caching = new CachingHealthCheck(delegate, okTtl,
                Duration.ofSeconds(5), Duration.ofSeconds(30), 2);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        long startNanos = System.nanoTime();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < itersPerThread; j++) {
                        caching.call();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS))
                .as("32-thread stress completed within 30s")
                .isTrue();
        long elapsedNanos = System.nanoTime() - startNanos;
        pool.shutdownNow();

        long expectedMax = (elapsedNanos / okTtl.toNanos()) + threads + 1;
        assertThat(upstreamCalls.get())
                .as("upstream invocations bounded by elapsed/okTtl + ε")
                .isLessThanOrEqualTo((int) expectedMax);
    }
}
