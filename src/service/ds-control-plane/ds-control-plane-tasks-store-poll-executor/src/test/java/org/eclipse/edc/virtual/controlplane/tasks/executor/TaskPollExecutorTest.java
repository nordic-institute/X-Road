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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.spi.response.ResponseStatus.ERROR_RETRY;
import static org.eclipse.edc.spi.response.ResponseStatus.FATAL_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskPollExecutorTest {

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

        var cfg = new TaskPollConfig(10, 2);
        pollExecutor = new TaskPollExecutor(
                cfg, instrumentation,
                contractNegotiationTaskExecutor,
                transferProcessTaskExecutor,
                taskStore,
                transactionContext,
                monitor,
                clock
        );
    }

    @AfterEach
    void tearDown() {
        pollExecutor.stop();
    }

    @Test
    void start_shouldActivatePollExecutor() {
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenReturn(List.of());

        var future = pollExecutor.start();

        assertThat(future).isNotNull();
        assertThat(future.isCancelled()).isFalse();
    }

    @Test
    void start_shouldBeginPollingTasks() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore, atLeastOnce()).fetchForUpdate(any(QuerySpec.class))
        );
    }

    @Test
    void executeTask_shouldDeleteTaskOnSuccess() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore).delete(task.getId())
        );
    }

    @Test
    void executeTask_shouldUpdateTaskOnFailure() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore).update(any(Task.class))
        );
    }

    @Test
    void handleTask_shouldDispatchContractNegotiationTaskPayload() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(contractNegotiationTaskExecutor).handle(any(ContractNegotiationTaskPayload.class))
        );
    }

    @Test
    void handleTask_shouldDispatchTransferProcessTaskPayload() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(transferProcessTaskExecutor).handle(any(TransferProcessTaskPayload.class))
        );
    }

    @Test
    void handleTask_shouldHandleUnknownPayloadType() {
        var payload = new UnknownPayload("process-1", 100, "CONSUMER");
        var task = Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();

        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenReturn(List.of(task))
                .thenReturn(List.of());

        pollExecutor.start();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore).delete(task.getId())
        );
    }

    @Test
    void run_shouldContinuePollingAfterSuccessfulExecution() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(taskStore, times(3)).fetchForUpdate(any(QuerySpec.class))
        );
    }

    @Test
    void run_shouldContinuePollingAfterExecutionException() {
        when(taskStore.fetchForUpdate(any(QuerySpec.class)))
                .thenThrow(new RuntimeException("Store error"))
                .thenReturn(List.of());

        pollExecutor.start();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(monitor).severe(any(String.class), any(Exception.class))
        );
    }

    @Test
    void run_shouldDeleteTaskAfterLimitReached() {
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
                .thenReturn(List.of(task.toBuilder().retryCount(task.getRetryCount() + 2).build()));

        when(contractNegotiationTaskExecutor.handle(any())).thenReturn(StatusResult.failure(ERROR_RETRY));

        pollExecutor.start();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                    verify(taskStore, times(3)).fetchForUpdate(any(QuerySpec.class));
                    verify(taskStore).delete(task.getId());
                    verify(taskStore, times(2)).update(any());
                }
        );
    }

    @Test
    void stop_shouldStopPolling() throws InterruptedException {
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenReturn(List.of());

        pollExecutor.start();
        Thread.sleep(200); // Allow some polling cycles
        pollExecutor.stop();

        // Verify that no more tasks are fetched after stopping
        var callCountBefore = mockingDetails(taskStore).getInvocations().size();
        Thread.sleep(300);
        var callCountAfter = mockingDetails(taskStore).getInvocations().size();

        assertThat(callCountAfter).isEqualTo(callCountBefore);
    }

    @Test
    void executeTask_shouldLogErrorOnProcessingFailure() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                verify(monitor).severe(any(String.class))
        );
    }

    @Test
    void executeTask_shouldProcessMultipleTasks() {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(contractNegotiationTaskExecutor).handle(any());
            verify(transferProcessTaskExecutor).handle(any());
            verify(taskStore, times(2)).delete(any());
        });
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
