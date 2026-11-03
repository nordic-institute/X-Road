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
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;


public class TaskPollExecutor {

    private static final long POLL_DELAY_MILLIS = 100L;

    private final TaskPollConfig taskPollConfig;
    private final ContractNegotiationTaskExecutor contractNegotiationTaskExecutor;
    private final TransferProcessTaskExecutor transferProcessTaskExecutor;
    private final TaskStore taskStore;
    private final TransactionContext transactionContext;
    private final Monitor monitor;
    private final Clock clock;
    private final ProcessLock processLock;
    private final AtomicBoolean active = new AtomicBoolean();
    private final List<Worker> workers;

    public TaskPollExecutor(TaskPollConfig taskPollConfig, int workerCount, ExecutorInstrumentation instrumentation,
                            ContractNegotiationTaskExecutor contractNegotiationTaskExecutor,
                            TransferProcessTaskExecutor transferProcessTaskExecutor, TaskStore taskStore,
                            TransactionContext transactionContext,
                            Monitor monitor, Clock clock, ProcessLock processLock) {
        this.taskPollConfig = taskPollConfig;
        this.contractNegotiationTaskExecutor = contractNegotiationTaskExecutor;
        this.transferProcessTaskExecutor = transferProcessTaskExecutor;
        this.taskStore = taskStore;
        this.transactionContext = transactionContext;
        this.monitor = monitor;
        this.clock = clock;
        this.processLock = processLock;

        this.workers = IntStream.rangeClosed(1, workerCount)
                .mapToObj(workerNumber -> newWorker(instrumentation, workerNumber))
                .toList();
    }

    private static Worker newWorker(ExecutorInstrumentation instrumentation, int workerNumber) {
        var name = "TaskPollExecutor-" + workerNumber;
        var executor = instrumentation.instrument(
                Executors.newSingleThreadScheduledExecutor(r -> {
                    var thread = Executors.defaultThreadFactory().newThread(r);
                    thread.setName(name);
                    return thread;
                }), name);
        return new Worker(name, executor);
    }

    /**
     * Start every worker's loop; each will run processors until it's stopped
     *
     * @return a future that will complete when the first worker's loop starts
     */
    public Future<?> start() {
        active.set(true);
        Future<?> firstFuture = null;
        for (var worker : workers) {
            var future = scheduleNextIterationIn(worker, 0L);
            if (firstFuture == null) {
                firstFuture = future;
            }
        }
        return firstFuture;
    }

    @NotNull
    private Future<?> scheduleNextIterationIn(Worker worker, long delayMillis) {
        return worker.executor().schedule(() -> run(worker), delayMillis, MILLISECONDS);
    }

    /**
     * Stop every worker's loop gracefully as suggested in the {@link java.util.concurrent.ExecutorService} documentation
     */
    public void stop() {
        active.set(false);
        workers.forEach(worker -> worker.executor().shutdown());
        workers.forEach(this::awaitTermination);
    }

    private void awaitTermination(Worker worker) {
        var executor = worker.executor();
        try {
            if (!executor.awaitTermination(taskPollConfig.shutdownTimeout(), SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(taskPollConfig.shutdownTimeout(), SECONDS)) {
                    monitor.severe("%s await termination timeout".formatted(worker.name()));
                }
            }
        } catch (InterruptedException e) {
            monitor.severe("%s await termination failed".formatted(worker.name()), e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void run(Worker worker) {
        if (active.get()) {
            transactionContext.execute(() -> {
                var immediateRepoll = false;
                try {
                    immediateRepoll = pollAndProcessDueTasks();
                } catch (Exception e) {
                    monitor.severe("TaskPollExecutor failed to process tasks", e);
                } finally {
                    scheduleNextIterationIn(worker, immediateRepoll ? 0L : POLL_DELAY_MILLIS);
                }
            });

        }
    }

    /**
     * @return {@code true} when a non-empty fetch was returned and every task in it reached a final state
     */
    private boolean pollAndProcessDueTasks() {
        var tasks = taskStore.fetchForUpdate(dueTasksQuery());
        if (tasks.isEmpty()) {
            return false;
        }
        var allTasksCompletedImmediately = true;
        for (var task : tasks) {
            if (!executeTask(task)) {
                allTasksCompletedImmediately = false;
            }
        }
        return allTasksCompletedImmediately;
    }

    /**
     * Only a task whose {@code at} is not in the future is due: a re-queue that sets {@code at} to now plus the
     * poll delay must hold other workers off until that time passes, not just sort behind not-yet-due tasks.
     */
    private QuerySpec dueTasksQuery() {
        return QuerySpec.Builder.newInstance()
                .filter(new Criterion("at", CriterionOperatorRegistry.LESS_THAN_EQUAL, clock.millis()))
                .sortField("at")
                .sortOrder(SortOrder.ASC)
                .limit(1)
                .build();
    }

    /**
     * @return {@code true} when the task reached a final state (succeeded, fatal error, retry cap or stale process
     *         dropped), {@code false} when it was re-queued, either for a transient-failure retry or because the
     *         process lock was busy
     */
    private boolean executeTask(Task task) {
        var lockOutcome = acquireLock(task);
        if (lockOutcome == LockOutcome.BUSY) {
            requeueBusy(task);
            return false;
        }
        if (lockOutcome == LockOutcome.MISSING) {
            monitor.debug("Dropping task " + task.getId() + ": its process no longer exists");
            taskStore.delete(task.getId());
            return true;
        }

        var result = handleTask(task);
        if (result.succeeded()) {
            taskStore.delete(task.getId());
            return true;
        } else {
            if (result.fatalError()) {
                monitor.severe("Fatal error processing task " + task.getId() + ": " + result.getFailureDetail());
                taskStore.delete(task.getId());
                return true;
            } else {
                if (task.getRetryCount() >= taskPollConfig.maxRetries()) {
                    monitor.severe("Task " + task.getId() + " reached max retry count of " + taskPollConfig.maxRetries()
                            + ". Dropping task. Last error: " + result.getFailureDetail());
                    taskStore.delete(task.getId());
                    return true;
                }
                monitor.warning("Transient error processing task " + task.getId() + ": "
                        + result.getFailureDetail() + ". Will retry later.");
                taskStore.update(task.toBuilder()
                        .at(clock.millis() + POLL_DELAY_MILLIS)
                        .retryCount(task.getRetryCount() + 1)
                        .build());
                return false;
            }
        }
    }

    /**
     * @return {@link LockOutcome#PROCEED} when the task is not process-bound, or its process is locked for this
     *         transaction and still exists; {@link LockOutcome#BUSY} when another transaction already holds the
     *         process lock; {@link LockOutcome#MISSING} when the task's negotiation or transfer process no longer
     *         exists
     */
    private LockOutcome acquireLock(Task task) {
        if (task.getPayload() instanceof ContractNegotiationTaskPayload cnPayload) {
            var processId = cnPayload.getProcessId();
            return classify(processLock.tryLockContractNegotiation(processId), () -> processLock.contractNegotiationExists(processId));
        } else if (task.getPayload() instanceof TransferProcessTaskPayload tpPayload) {
            var processId = tpPayload.getProcessId();
            return classify(processLock.tryLockTransferProcess(processId), () -> processLock.transferProcessExists(processId));
        } else {
            return LockOutcome.PROCEED;
        }
    }

    private static LockOutcome classify(boolean locked, BooleanSupplier existsCheck) {
        if (!locked) {
            return LockOutcome.BUSY;
        }
        return existsCheck.getAsBoolean() ? LockOutcome.PROCEED : LockOutcome.MISSING;
    }

    private void requeueBusy(Task task) {
        taskStore.update(task.toBuilder()
                .at(clock.millis() + POLL_DELAY_MILLIS)
                .retryCount(task.getRetryCount())
                .build());
    }

    private enum LockOutcome {
        PROCEED, BUSY, MISSING
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

    private record Worker(String name, ScheduledExecutorService executor) {
    }
}
