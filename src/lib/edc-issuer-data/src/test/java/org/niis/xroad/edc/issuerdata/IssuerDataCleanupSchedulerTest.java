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

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IssuerDataCleanupSchedulerTest {

    private static final Duration SHORT_INTERVAL = Duration.ofMillis(10);
    private static final Duration LONG_INTERVAL = Duration.ofHours(1);
    private static final Duration CLOSE_LIMIT = Duration.ofSeconds(2);

    private final ExecutorInstrumentation executorInstrumentation = mock(ExecutorInstrumentation.class, CALLS_REAL_METHODS);
    private final Runnable cleanup = mock();
    private final RecordingMonitor monitor = new RecordingMonitor();
    private IssuerDataCleanupScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    @Test
    void shouldRunRepeatedly() {
        scheduler = IssuerDataCleanupScheduler.start(cleanup, SHORT_INTERVAL, executorInstrumentation, monitor);

        verify(cleanup, timeout(5000).atLeast(2)).run();
    }

    @Test
    void shouldKeepRunningAfterFailedDelete() {
        var transactionContext = mock(TransactionContext.class);
        var calls = new AtomicInteger();
        when(transactionContext.execute(any(TransactionContext.ResultTransactionBlock.class))).thenAnswer(_ -> {
            if (calls.incrementAndGet() == 1) {
                throw new EdcException("database is down");
            }
            return StoreResult.success(0);
        });
        var store = new SqlIssuerDataStore(mock(DataSourceRegistry.class), DataSourceRegistry.DEFAULT_DATASOURCE,
                transactionContext, mock(QueryExecutor.class));
        var cleaner = new IssuerDataCleaner(store, new MutableClock(Instant.parse("2026-11-18T10:00:00Z")), mock(Monitor.class));

        scheduler = IssuerDataCleanupScheduler.start(cleaner::run, SHORT_INTERVAL, executorInstrumentation, monitor);

        verify(transactionContext, timeout(5000).atLeast(2)).execute(any(TransactionContext.ResultTransactionBlock.class));
    }

    @Test
    void shouldWarnAndRunAgainAfterUncheckedExceptionFromCleanup() {
        doThrow(new IllegalStateException("unexpected")).doNothing().when(cleanup).run();

        scheduler = IssuerDataCleanupScheduler.start(cleanup, SHORT_INTERVAL, executorInstrumentation, monitor);

        verify(cleanup, timeout(5000).atLeast(2)).run();
        assertThat(monitor.warnings()).hasSize(1);
    }

    @Test
    void shouldCloseWithoutWaitingForPendingRun() {
        scheduler = IssuerDataCleanupScheduler.start(cleanup, LONG_INTERVAL, executorInstrumentation, monitor);

        var begin = System.nanoTime();
        scheduler.close();
        var elapsed = Duration.ofNanos(System.nanoTime() - begin);

        assertThat(elapsed).isLessThan(CLOSE_LIMIT);
        verify(cleanup, never()).run();
    }
}
