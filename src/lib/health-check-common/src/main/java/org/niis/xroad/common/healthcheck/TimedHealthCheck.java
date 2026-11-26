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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link HealthCheck} wrapper that enforces a per-call timeout on a delegate check.
 *
 * <p>On timeout, returns a {@link HealthCheckResponse} with status {@code DOWN} and data
 * {@code {"error":"timeout","timeout_ms":<configured>}} — it does NOT propagate
 * {@link TimeoutException}. This shape is designed so an outer {@code CachingHealthCheck}
 * treats the timeout as an error symmetrically with any other DOWN response.
 *
 * <p>Execution model: a per-instance {@link ExecutorService} backed by virtual threads is
 * used to submit the delegate call. On timeout the wrapper cancels the future WITHOUT
 * interrupting the running task — signer RPC and database calls have their own deadlines
 * (see {@code AbstractRpcClient}, {@code RpcChannelFactory}), so interrupting adds complexity
 * without improving correctness. The abandoned virtual thread is lightweight and terminates
 * when its work completes.
 *
 * <p>This class has no Quarkus / CDI coupling — it is a pure MicroProfile Health + JDK
 * primitive reusable from any Jakarta-compatible service.
 */
public final class TimedHealthCheck implements HealthCheck {

    private static final String NAME_FALLBACK = "UNKNOWN";
    private static final String INTERRUPTED_MESSAGE = "INTERRUPTED";

    private final HealthCheck delegate;
    private final long timeoutMillis;
    private final String name;
    private final ExecutorService executor;

    /**
     * Creates a new timeout wrapper.
     *
     * @param delegate the underlying health check whose call is bounded
     * @param timeout  maximum duration to wait for the delegate before returning DOWN
     * @param name     name written to the response on timeout (MicroProfile Health SPI has
     *                 no {@code getName()} so the name cannot be recovered after a timeout;
     *                 must be supplied at construction)
     */
    public TimedHealthCheck(HealthCheck delegate, Duration timeout, String name) {
        this.delegate = delegate;
        this.timeoutMillis = timeout.toMillis();
        this.name = name;
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    }

    @Override
    public HealthCheckResponse call() {
        Future<HealthCheckResponse> future = executor.submit(delegate::call);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // abandon-and-let-run — do NOT interrupt the wrapped thread.
            future.cancel(false);
            return HealthCheckResponse.named(nameOrFallback())
                    .down()
                    .withData(HealthCheckConstants.ERROR, HealthCheckConstants.TIMEOUT)
                    .withData("timeout_ms", timeoutMillis)
                    .build();
        } catch (ExecutionException e) {
            // let an outer CachingHealthCheck
            // observe the underlying exception. Unwrap the ExecutionException and
            // rethrow the original cause; if the cause is checked, surface as DOWN
            // rather than throwing a vanilla RuntimeException (ArchUnit NoVanillaExceptions).
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            return HealthCheckResponse.named(nameOrFallback())
                    .down()
                    .withData(HealthCheckConstants.ERROR, cause.getClass().getSimpleName())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthCheckResponse.named(nameOrFallback())
                    .down()
                    .withData(HealthCheckConstants.ERROR, INTERRUPTED_MESSAGE)
                    .build();
        }
    }

    private String nameOrFallback() {
        return name != null ? name : NAME_FALLBACK;
    }
}
