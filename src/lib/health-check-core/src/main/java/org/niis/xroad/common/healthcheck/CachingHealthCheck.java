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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link HealthCheck} wrapper that memoizes a delegate's {@link HealthCheckResponse} using
 * a tiered dual-TTL cache with exponential error backoff.
 *
 * <p>OK responses are cached for {@code successTtl}. Error responses (status DOWN or an
 * exception escaping {@code delegate.call()}) are cached for an adaptive window that starts
 * at {@code errorTtl} and doubles on each consecutive error up to {@code maxErrorTtl}. The
 * first OK after any error sequence resets the error-TTL back to {@code errorTtl}.
 *
 * <p>Thread-safety: all state lives inside a single {@link AtomicReference} holding a
 * {@code CachedResponse} record; the hot path is lock-free and safe under concurrent probes.
 *
 * <p>This class has no Quarkus / CDI coupling — it is a pure MicroProfile Health + JDK
 * primitive reusable from any Jakarta-compatible service.
 */
public final class CachingHealthCheck implements HealthCheck {

    private final HealthCheck delegate;
    private final long successTtlNanos;
    private final long errorTtlNanos;
    private final long maxErrorTtlNanos;
    private final int backoffMultiplier;

    private final AtomicReference<CachedResponse> ref = new AtomicReference<>();

    record CachedResponse(
            HealthCheckResponse response,
            long expiresAtNanos,
            long currentErrorTtlNanos,
            boolean isError) { }

    /**
     * Creates a new caching wrapper.
     *
     * @param delegate           the underlying health check whose response is cached
     * @param successTtl         cache duration for an OK response
     * @param errorTtl           initial cache duration for an error response
     * @param maxErrorTtl        ceiling for the exponential backoff
     * @param backoffMultiplier  multiplier applied to the current error-TTL on each consecutive error (must be &gt;= 1)
     */
    public CachingHealthCheck(HealthCheck delegate,
                              Duration successTtl,
                              Duration errorTtl,
                              Duration maxErrorTtl,
                              int backoffMultiplier) {
        this.delegate = delegate;
        this.successTtlNanos = successTtl.toNanos();
        this.errorTtlNanos = errorTtl.toNanos();
        this.maxErrorTtlNanos = maxErrorTtl.toNanos();
        this.backoffMultiplier = backoffMultiplier;
    }

    @Override
    public HealthCheckResponse call() {
        var now = System.nanoTime();
        var cached = ref.get();
        if (cached != null && now - cached.expiresAtNanos() < 0) {
            return cached.response();
        }

        HealthCheckResponse fresh;
        boolean isError;
        try {
            fresh = delegate.call();
            isError = fresh.getStatus() == HealthCheckResponse.Status.DOWN;
        } catch (RuntimeException e) {
            // exceptions are symmetric with DOWN — synthesize a DOWN response
            // rather than propagating, so callers (including an outer CachingHealthCheck)
            // observe a consistent HealthCheckResponse contract.
            fresh = HealthCheckResponse.named(nameOrUnknown(cached))
                    .down()
                    .withData(HealthCheckConstants.ERROR, e.getClass().getSimpleName())
                    .build();
            isError = true;
        }

        long nextErrorTtlNanos = computeNextErrorTtl(cached, isError);
        long newExpiryNanos = now + (isError ? nextErrorTtlNanos : successTtlNanos);
        var updated = new CachedResponse(fresh, newExpiryNanos, nextErrorTtlNanos, isError);
        ref.compareAndSet(cached, updated);
        return fresh;
    }

    private long computeNextErrorTtl(CachedResponse prev, boolean isError) {
        if (!isError) {
            return errorTtlNanos;                        // reset on first OK
        }
        if (prev == null || !prev.isError()) {
            return errorTtlNanos;                        // first error starts at errorTtl
        }
        long doubled = prev.currentErrorTtlNanos() * (long) backoffMultiplier;
        return Math.min(doubled, maxErrorTtlNanos);      // cap at maxErrorTtl
    }

    private static String nameOrUnknown(CachedResponse cached) {
        if (cached == null || cached.response() == null) {
            return "UNKNOWN";
        }
        return cached.response().getName();
    }

}
