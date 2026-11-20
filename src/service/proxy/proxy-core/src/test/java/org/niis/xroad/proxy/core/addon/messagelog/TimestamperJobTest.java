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
package org.niis.xroad.proxy.core.addon.messagelog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimestamperJobTest {

    private static final int RETRY_DELAY_SECONDS = 90;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private TaskQueue taskQueue;

    @Mock
    private ScheduledExecutorService taskScheduler;

    @Mock
    private ScheduledFuture<?> scheduledTask;

    private LogManager.TimestamperJob job;

    @BeforeEach
    void setUp() {
        doReturn(scheduledTask).when(taskScheduler).schedule(any(Runnable.class), anyLong(), eq(SECONDS));
        job = newJob(RETRY_DELAY_SECONDS);
    }

    @Test
    void schedulesFirstRunAfterInitialDelay() {
        verify(taskScheduler).schedule(any(Runnable.class), eq(3600L), eq(SECONDS));
    }

    @Test
    void successOutsideRetryModeDoesNotStartBatch() {
        job.onSuccess();

        verify(taskQueue, never()).handleStartTimestamping();
    }

    @Test
    void repeatedFailureThenSuccessStartsOneBatch() {
        job.onFailure();
        job.onFailure();
        job.onSuccess();

        verify(taskQueue, times(1)).handleStartTimestamping();
    }

    @Test
    void repeatedSuccessAfterFailureStartsOneBatch() {
        job.onFailure();
        job.onSuccess();
        job.onSuccess();

        verify(taskQueue, times(1)).handleStartTimestamping();
    }

    @Test
    void successOfRecoveryBatchDoesNotStartAnotherBatch() {
        doAnswer(invocation -> {
            job.onSuccess();
            return null;
        }).when(taskQueue).handleStartTimestamping();

        job.onFailure();
        job.onSuccess();

        verify(taskQueue, times(1)).handleStartTimestamping();
    }

    @Test
    void failureOfRecoveryBatchReturnsToRetryMode() {
        doAnswer(invocation -> {
            job.onFailure();
            return null;
        }).doNothing().when(taskQueue).handleStartTimestamping();

        job.onFailure();
        job.onSuccess();
        job.onSuccess();

        verify(taskQueue, times(2)).handleStartTimestamping();
    }

    @Test
    void scheduledRunStartsBatchAndSchedulesNextAfterGlobalInterval() {
        when(globalConfProvider.getTimestampingIntervalSeconds()).thenReturn(300);

        runLastScheduled();

        verify(taskQueue).handleStartTimestamping();
        assertEquals(300L, lastScheduledDelay());
    }

    @Test
    void scheduledRunInRetryModeStartsRetryBatchAfterRetryDelay() {
        when(globalConfProvider.getTimestampingIntervalSeconds()).thenReturn(300);
        job.onFailure();

        runLastScheduled();
        assertEquals(RETRY_DELAY_SECONDS, lastScheduledDelay());

        runLastScheduled();
        verify(taskQueue).handleStartTimestampingRetryMode();
        assertEquals(RETRY_DELAY_SECONDS, lastScheduledDelay());
    }

    @Test
    void retryModeUsesGlobalIntervalWhenRetryDelayIsNotShorter() {
        when(globalConfProvider.getTimestampingIntervalSeconds()).thenReturn(80);
        job.onFailure();

        runLastScheduled();

        assertEquals(80L, lastScheduledDelay());
    }

    @Test
    void retryModeUsesGlobalIntervalWhenRetryDelayIsZero() {
        job.shutdown();
        job = newJob(0);
        when(globalConfProvider.getTimestampingIntervalSeconds()).thenReturn(300);
        job.onFailure();

        runLastScheduled();

        assertEquals(300L, lastScheduledDelay());
    }

    @Test
    void intervalIsKeptWithinLimits() {
        when(globalConfProvider.getTimestampingIntervalSeconds()).thenReturn(10, 100_000);

        runLastScheduled();
        assertEquals(60L, lastScheduledDelay());

        runLastScheduled();
        assertEquals(86_400L, lastScheduledDelay());
    }

    @Test
    void failedIntervalLookupUsesMinimumInterval() {
        when(globalConfProvider.getTimestampingIntervalSeconds()).thenThrow(new RuntimeException("no global conf"));

        runLastScheduled();

        assertEquals(60L, lastScheduledDelay());
    }

    @Test
    void failedBatchStillSchedulesNextRun() {
        doThrow(new RuntimeException("batch failed")).when(taskQueue).handleStartTimestamping();

        assertThrows(RuntimeException.class, this::runLastScheduled);

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), anyLong(), eq(SECONDS));
    }

    @Test
    void schedulingNextRunCancelsThePendingOne() {
        runLastScheduled();

        verify(scheduledTask).cancel(false);
    }

    @Test
    void shutdownCancelsPendingRunAndStopsScheduler() {
        job.shutdown();

        verify(scheduledTask).cancel(false);
        verify(taskScheduler).shutdown();
    }

    private LogManager.TimestamperJob newJob(int retryDelaySeconds) {
        return new LogManager.TimestamperJob(globalConfProvider, Duration.ofHours(1), taskQueue, retryDelaySeconds, taskScheduler);
    }

    private void runLastScheduled() {
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler, atLeastOnce()).schedule(runnable.capture(), anyLong(), eq(SECONDS));
        runnable.getValue().run();
    }

    private long lastScheduledDelay() {
        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        verify(taskScheduler, atLeastOnce()).schedule(any(Runnable.class), delay.capture(), eq(SECONDS));
        return delay.getValue();
    }
}
