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
package org.niis.xroad.common.vault;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.niis.xroad.common.core.exception.ErrorCode.SECRET_STORE_UNAVAILABLE;

/**
 * Runs one secret store call and retries failures the stack classifies as retryable until a total elapsed-time
 * budget passes. A failure that is not retryable is rethrown unchanged after one attempt. When the budget runs out
 * on a retryable failure, the call fails with {@code SECRET_STORE_UNAVAILABLE}.
 * <p>
 * The budget cannot cut short an attempt already in flight, so a call can last the budget plus one client timeout.
 */
@Slf4j
public final class ResilientVaultCall {

    public static final Duration DEFAULT_BUDGET = Duration.ofSeconds(30);

    private static final IntervalFunction DEFAULT_BACKOFF =
            IntervalFunction.ofExponentialBackoff(Duration.ofMillis(500), 2.0, Duration.ofSeconds(5));

    private final Predicate<Throwable> retryable;
    private final Duration budget;
    private final Clock clock;
    private final IntervalFunction backoff;
    private final RetryConfig template;

    public ResilientVaultCall(Predicate<Throwable> retryable, Duration budget) {
        this(retryable, budget, Clock.systemUTC(), DEFAULT_BACKOFF);
    }

    ResilientVaultCall(Predicate<Throwable> retryable, Duration budget, Clock clock, IntervalFunction backoff) {
        this.retryable = retryable;
        this.budget = budget;
        this.clock = clock;
        this.backoff = backoff;
        this.template = RetryConfig.custom()
                .maxAttempts(Integer.MAX_VALUE)
                .intervalFunction(backoff)
                .build();
    }

    public ResilientVaultCall withBudget(Duration newBudget) {
        return new ResilientVaultCall(retryable, newBudget, clock, backoff);
    }

    public <T> T execute(String operation, String path, Supplier<T> call) {
        var deadline = clock.instant().plus(budget);
        var attempts = new AtomicInteger();
        var retry = Retry.of(operation, RetryConfig.from(template)
                .retryOnException(e -> retryable.test(e) && clock.instant().isBefore(deadline))
                .build());

        try {
            return retry.executeSupplier(() -> attempt(operation, path, call, attempts));
        } catch (RuntimeException e) {
            if (retryable.test(e)) {
                throw unavailable(operation, path, attempts.get(), e);
            }
            throw e;
        }
    }

    private <T> T attempt(String operation, String path, Supplier<T> call, AtomicInteger attempts) {
        var attemptNumber = attempts.incrementAndGet();
        try {
            return call.get();
        } catch (RuntimeException e) {
            if (retryable.test(e)) {
                log.warn("Secret store {} at path {} failed on attempt {} (retry budget {}): {}",
                        operation, path, attemptNumber, budget, e.toString());
            }
            throw e;
        }
    }

    private XrdRuntimeException unavailable(String operation, String path, int attempts, RuntimeException cause) {
        return XrdRuntimeException.systemException(SECRET_STORE_UNAVAILABLE, cause,
                "Secret store %s at path %s failed after %d attempt(s), retry budget %s",
                operation, path, attempts, budget);
    }
}
