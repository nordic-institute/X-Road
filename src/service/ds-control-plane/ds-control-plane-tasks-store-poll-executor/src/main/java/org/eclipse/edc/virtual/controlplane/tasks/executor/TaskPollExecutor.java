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
import org.eclipse.edc.controlplane.tasks.Task;
import org.eclipse.edc.controlplane.tasks.store.TaskStore;
import org.eclipse.edc.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.controlplane.transfer.spi.tasks.TransferProcessTaskPayload;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;


public class TaskPollExecutor {

    private final TaskPollConfig taskPollConfig;
    private final ContractNegotiationTaskExecutor contractNegotiationTaskExecutor;
    private final TransferProcessTaskExecutor transferProcessTaskExecutor;
    private final TaskStore taskStore;
    private final TransactionContext transactionContext;
    private final Monitor monitor;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final AtomicBoolean active = new AtomicBoolean();

    private final QuerySpec query = QuerySpec.Builder.newInstance()
            .sortField("at")
            .sortOrder(SortOrder.ASC)
            .limit(1)
            .build();


    public TaskPollExecutor(TaskPollConfig taskPollConfig, ExecutorInstrumentation instrumentation,
                            ContractNegotiationTaskExecutor contractNegotiationTaskExecutor,
                            TransferProcessTaskExecutor transferProcessTaskExecutor, TaskStore taskStore,
                            TransactionContext transactionContext,
                            Monitor monitor, Clock clock) {
        this.taskPollConfig = taskPollConfig;
        this.contractNegotiationTaskExecutor = contractNegotiationTaskExecutor;
        this.transferProcessTaskExecutor = transferProcessTaskExecutor;
        this.taskStore = taskStore;
        this.transactionContext = transactionContext;
        this.monitor = monitor;

        executor = instrumentation.instrument(
                Executors.newSingleThreadScheduledExecutor(r -> {
                    var thread = Executors.defaultThreadFactory().newThread(r);
                    thread.setName("TaskPollExecutor");
                    return thread;
                }), "TaskPollExecutor");
        this.clock = clock;
    }

    /**
     * Start the loop that will run processors until it's stopped
     *
     * @return a future that will complete when the loop starts
     */
    public Future<?> start() {
        active.set(true);
        return scheduleNextIterationIn(0L);
    }

    @NotNull
    private Future<?> scheduleNextIterationIn(long delayMillis) {
        return executor.schedule(this::run, delayMillis, MILLISECONDS);
    }

    /**
     * Stop the loop gracefully as suggested in the {@link ExecutorService} documentation
     */
    public void stop() {
        active.set(false);
        executor.shutdown();

        try {
            if (!executor.awaitTermination(taskPollConfig.shutdownTimeout(), SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(taskPollConfig.shutdownTimeout(), SECONDS)) {
                    monitor.severe("StateMachineManager [%s] await termination timeout");
                }
            }
        } catch (InterruptedException e) {
            monitor.severe("TaskPollExecutor  await termination failed", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }

    private void run() {
        if (active.get()) {
            transactionContext.execute(() -> {
                try {
                    var tasks = taskStore.fetchForUpdate(query);
                    for (var task : tasks) {
                        executeTask(task);
                    }
                } catch (Exception e) {
                    monitor.severe("TaskPollExecutor failed to process tasks", e);
                } finally {
                    scheduleNextIterationIn(100L);
                }
            });

        }
    }

    private void executeTask(Task task) {
        var result = handleTask(task);
        if (result.succeeded()) {
            taskStore.delete(task.getId());
        } else {
            if (result.fatalError()) {
                monitor.severe("Fatal error processing task " + task.getId() + ": " + result.getFailureDetail());
                taskStore.delete(task.getId());
            } else {
                if (task.getRetryCount() >= taskPollConfig.maxRetries()) {
                    monitor.severe("Task " + task.getId() + " reached max retry count of " + taskPollConfig.maxRetries()
                            + ". Dropping task. Last error: " + result.getFailureDetail());
                    taskStore.delete(task.getId());
                    return;
                }
                monitor.warning("Transient error processing task " + task.getId() + ": "
                        + result.getFailureDetail() + ". Will retry later.");
                taskStore.update(task.toBuilder().at(clock.millis()).retryCount(task.getRetryCount() + 1).build());
            }
        }
    }

    private StatusResult<Void> handleTask(Task task) {
        if (task.getPayload() instanceof ContractNegotiationTaskPayload cnPayload) {
            return contractNegotiationTaskExecutor.handle(cnPayload);
        } else if (task.getPayload() instanceof TransferProcessTaskPayload tpPayload) {
            return transferProcessTaskExecutor.handle(tpPayload);
        } else {
            return StatusResult.success();
        }
    }
}
