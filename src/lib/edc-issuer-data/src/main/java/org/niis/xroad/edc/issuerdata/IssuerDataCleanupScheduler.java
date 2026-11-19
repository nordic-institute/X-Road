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

package org.niis.xroad.edc.issuerdata;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs a cleanup repeatedly on its own single-thread executor. Closing it drops the pending run, so it does not wait for
 * the next interval.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class IssuerDataCleanupScheduler implements AutoCloseable {

    private static final String THREAD_NAME = "xroad-issuer-data-cleanup";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ScheduledExecutorService executor;
    private final Runnable cleanup;
    private final Duration interval;
    private final Monitor monitor;
    private volatile boolean shuttingDown;

    static IssuerDataCleanupScheduler start(Runnable cleanup, Duration interval, ExecutorInstrumentation instrumentation,
                                            Monitor monitor) {
        var pool = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name(THREAD_NAME).daemon().factory());
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        var executor = instrumentation.instrument(pool, THREAD_NAME);
        var scheduler = new IssuerDataCleanupScheduler(executor, cleanup, interval, monitor);
        scheduler.scheduleNextRun();
        return scheduler;
    }

    @Override
    public void close() {
        shuttingDown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        try {
            cleanup.run();
        } catch (RuntimeException e) {
            monitor.warning("Issuer data cleanup failed", e);
        } finally {
            scheduleNextRun();
        }
    }

    private void scheduleNextRun() {
        if (shuttingDown) {
            return;
        }
        try {
            executor.schedule(this::run, interval.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException _) {
            monitor.debug("Issuer data cleanup stopped");
        }
    }
}
