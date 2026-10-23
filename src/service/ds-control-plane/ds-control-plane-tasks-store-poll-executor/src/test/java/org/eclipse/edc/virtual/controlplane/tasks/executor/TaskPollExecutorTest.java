/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.virtual.controlplane.tasks.executor;

import org.eclipse.edc.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.controlplane.tasks.Task;
import org.eclipse.edc.controlplane.tasks.store.TaskStore;
import org.eclipse.edc.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.controlplane.transfer.spi.tasks.PrepareTransfer;
import org.eclipse.edc.controlplane.transfer.spi.tasks.TransferProcessTaskPayload;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.spi.response.ResponseStatus.ERROR_RETRY;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskPollExecutorTest {

    private static final int SINGLE_WORKER = 1;

    private final ContractNegotiationTaskExecutor contractNegotiationTaskExecutor = mock();
    private final TransferProcessTaskExecutor transferProcessTaskExecutor = mock();
    private final TaskStore taskStore = mock();
    private final TransactionContext transactionContext = new NoopTransactionContext();
    private final Monitor monitor = mock();
    private final ExecutorInstrumentation instrumentation = mock();
    private final Clock clock = Clock.systemUTC();
    private TaskPollExecutor pollExecutor;

    @BeforeEach
    void setUp() {
        when(instrumentation.instrument(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        pollExecutor = newPollExecutor(SINGLE_WORKER);
    }

    private TaskPollExecutor newPollExecutor(int workerCount) {
        var cfg = new TaskPollConfig(10, 2);
        return new TaskPollExecutor(
                cfg, workerCount, instrumentation,
                contractNegotiationTaskExecutor,
                transferProcessTaskExecutor,
                taskStore,
                transactionContext,
                monitor,
                clock
        );
    }

    private static Task negotiationTask(String processId) {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId(processId)
                .processState(100)
                .processType("CONSUMER")
                .build();
        return Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();
    }

    @AfterEach
    void tearDown() {
        pollExecutor.stop();
    }

    @Test
    void startShouldActivatePollExecutor() {
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenReturn(List.of());

        var future = pollExecutor.start();

        assertThat(future).isNotNull();
        assertThat(future.isCancelled()).isFalse();
    }

    @Test
    void startShouldBeginPollingTasks() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore, atLeastOnce()).fetchForUpdate(any(QuerySpec.class))
        );
    }

    @Test
    void executeTaskShouldDeleteTaskOnSuccess() {
        var payload = PrepareTransfer.Builder.newInstance()
                .processId("transfer-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(transferProcessTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore).delete(task.getId())
        );
    }

    @Test
    void executeTaskShouldUpdateTaskOnFailure() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(contractNegotiationTaskExecutor.handle(any()))
                .thenReturn(StatusResult.failure(ERROR_RETRY, "Processing failed"));

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore).update(any(Task.class))
        );
    }

    @Test
    void handleTaskShouldDispatchContractNegotiationTaskPayload() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(contractNegotiationTaskExecutor).handle(any(ContractNegotiationTaskPayload.class))
        );
    }

    @Test
    void handleTaskShouldDispatchTransferProcessTaskPayload() {
        var payload = PrepareTransfer.Builder.newInstance()
                .processId("transfer-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());
        when(transferProcessTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(transferProcessTaskExecutor).handle(any(TransferProcessTaskPayload.class))
        );
    }

    @Test
    void handleTaskShouldHandleUnknownPayloadType() {
        var payload = new UnknownPayload("process-1", 100, "CONSUMER");
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore).delete(task.getId())
        );
    }

    @Test
    void runShouldContinuePollingAfterSuccessfulExecution() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore, atLeast(3)).fetchForUpdate(any(QuerySpec.class))
        );
    }

    @Test
    void runShouldContinuePollingAfterExecutionException() {
        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenThrow(new RuntimeException("Store error"))
                .thenReturn(List.of());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(monitor).severe(any(String.class), any(Exception.class))
        );
    }

    @Test
    void runShouldDeleteTaskAfterLimitReached() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-123")
                .processState(100)
                .processType("CONSUMER")
                .build();

        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of(task.toBuilder().retryCount(task.getRetryCount() + 1).build()))
                .thenReturn(List.of(task.toBuilder().retryCount(task.getRetryCount() + 2).build()))
                .thenReturn(List.of());

        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.failure(ERROR_RETRY));

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    verify(taskStore, atLeast(3)).fetchForUpdate(any(QuerySpec.class));
                    verify(taskStore).delete(task.getId());
                    verify(taskStore, atLeast(2)).update(any());
                }
        );
    }

    @Test
    void stopShouldStopPolling() {
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenReturn(List.of());

        pollExecutor.start();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore, atLeastOnce()).fetchForUpdate(any(QuerySpec.class))
        );

        pollExecutor.stop();

        var callCountAfterStop = mockingDetails(taskStore).getInvocations().size();

        await().during(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(mockingDetails(taskStore).getInvocations()).hasSize(callCountAfterStop)
        );
    }

    @Test
    void executeTaskShouldLogErrorOnProcessingFailure() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-123")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());

        when(contractNegotiationTaskExecutor.handle(any()))
                .thenReturn(StatusResult.failure(FATAL_ERROR, "Task execution failed"));

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                verify(monitor).severe(any(String.class))
        );
    }

    @Test
    void executeTaskShouldProcessMultipleTasks() {
        var payload1 = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-1")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task1 = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload1)
                .build();

        var payload2 = PrepareTransfer.Builder.newInstance()
                .processId("transfer-1")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task2 = Task.Builder.newInstance()
                .at(System.currentTimeMillis() + 1)
                .payload(payload2)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task1, task2))
                .thenReturn(List.of());
        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.success());
        when(transferProcessTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(contractNegotiationTaskExecutor).handle(any());
            verify(transferProcessTaskExecutor).handle(any());
            verify(taskStore, times(2)).delete(any());
        });
    }

    @Test
    void nWorkersRunBlockingTasksForDifferentProcessesConcurrently() throws InterruptedException {
        var workerCount = 3;
        pollExecutor = newPollExecutor(workerCount);

        var tasks = IntStream.range(0, workerCount)
                .mapToObj(i -> negotiationTask("negotiation-" + i))
                .toList();
        var nextTask = new AtomicInteger();
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenAnswer(invocation -> {
            var index = nextTask.getAndIncrement();
            return index < tasks.size() ? List.of(tasks.get(index)) : List.of();
        });

        var runningCount = new AtomicInteger();
        var maxConcurrent = new AtomicInteger();
        var release = new CountDownLatch(1);
        when(contractNegotiationTaskExecutor.handle(any())).thenAnswer(invocation -> {
            var current = runningCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            release.await(5, TimeUnit.SECONDS);
            runningCount.decrementAndGet();
            return StatusResult.success();
        });

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(runningCount.get()).isEqualTo(workerCount)
        );
        release.countDown();

        assertThat(maxConcurrent.get()).isEqualTo(workerCount);
    }

    @Test
    void workersRunOnDistinctlyNamedThreads() {
        var workerCount = 3;
        pollExecutor = newPollExecutor(workerCount);

        Set<String> threadNames = new CopyOnWriteArraySet<>();
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenAnswer(invocation -> {
            threadNames.add(Thread.currentThread().getName());
            return List.of();
        });

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(threadNames).hasSize(workerCount));
        assertThat(threadNames).allMatch(name -> name.matches("TaskPollExecutor-\\d+"));
    }

    @Test
    void shutdownStopsEveryWorkerWithinTheTimeout() {
        var workerCount = 3;
        pollExecutor = newPollExecutor(workerCount);
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenReturn(List.of());

        pollExecutor.start();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore, atLeast(workerCount)).fetchForUpdate(any(QuerySpec.class))
        );

        pollExecutor.stop();

        var callCountAfterStop = mockingDetails(taskStore).getInvocations().size();

        await().during(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(mockingDetails(taskStore).getInvocations()).hasSize(callCountAfterStop)
        );
    }

    @Test
    void rePollHappensImmediatelyAfterWorkAndIsDelayedAfterAnEmptyFetch() {
        var task = negotiationTask("negotiation-timing");
        List<Long> fetchTimestamps = new CopyOnWriteArrayList<>();
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenAnswer(invocation -> {
            fetchTimestamps.add(System.nanoTime());
            return fetchTimestamps.size() == 1 ? List.of(task) : List.of();
        });
        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.success());

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).until(() -> fetchTimestamps.size() >= 3);

        var gapAfterWork = Duration.ofNanos(fetchTimestamps.get(1) - fetchTimestamps.get(0)).toMillis();
        var gapAfterEmptyFetch = Duration.ofNanos(fetchTimestamps.get(2) - fetchTimestamps.get(1)).toMillis();

        assertThat(gapAfterWork).isLessThan(50);
        assertThat(gapAfterEmptyFetch).isGreaterThanOrEqualTo(90);
    }

    @Test
    void rePollIsDelayedAfterHandlerThrows() {
        var task = negotiationTask("negotiation-handler-throw");
        List<Long> fetchTimestamps = new CopyOnWriteArrayList<>();
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenAnswer(invocation -> {
            fetchTimestamps.add(System.nanoTime());
            return fetchTimestamps.size() == 1 ? List.of(task) : List.of();
        });
        when(contractNegotiationTaskExecutor.handle(any())).thenThrow(new RuntimeException("handler failure"));

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).until(() -> fetchTimestamps.size() >= 2);

        var gapAfterThrow = Duration.ofNanos(fetchTimestamps.get(1) - fetchTimestamps.get(0)).toMillis();
        assertThat(gapAfterThrow).isGreaterThanOrEqualTo(90);
    }

    @Test
    void rePollIsDelayedAfterRetryRequeueAndRetryCapStillEndsTheTask() {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId("negotiation-retry-timing")
                .processState(100)
                .processType("CONSUMER")
                .build();
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        List<Long> fetchTimestamps = new CopyOnWriteArrayList<>();
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenAnswer(invocation -> {
            fetchTimestamps.add(System.nanoTime());
            return switch (fetchTimestamps.size()) {
                case 1 -> List.of(task);
                case 2 -> List.of(task.toBuilder().retryCount(task.getRetryCount() + 1).build());
                case 3 -> List.of(task.toBuilder().retryCount(task.getRetryCount() + 2).build());
                default -> List.of();
            };
        });
        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.failure(ERROR_RETRY));

        pollExecutor.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(taskStore, atLeast(3)).fetchForUpdate(any(QuerySpec.class));
            verify(taskStore).delete(task.getId());
        });

        var gapAfterFirstRetry = Duration.ofNanos(fetchTimestamps.get(1) - fetchTimestamps.get(0)).toMillis();
        var gapAfterSecondRetry = Duration.ofNanos(fetchTimestamps.get(2) - fetchTimestamps.get(1)).toMillis();
        assertThat(gapAfterFirstRetry).isGreaterThanOrEqualTo(90);
        assertThat(gapAfterSecondRetry).isGreaterThanOrEqualTo(90);
    }

    /**
     * Unknown task payload for testing handler logic
     */
    public static class UnknownPayload extends ProcessTaskPayload {

        UnknownPayload(String processId, Integer processState, String processType) {
            this.processId = processId;
            this.processState = processState;
            this.processType = processType;
        }

        @Override
        public String name() {
            return "unknown.payload";
        }

        @Override
        public String group() {
            return "unknown";
        }
    }
}
