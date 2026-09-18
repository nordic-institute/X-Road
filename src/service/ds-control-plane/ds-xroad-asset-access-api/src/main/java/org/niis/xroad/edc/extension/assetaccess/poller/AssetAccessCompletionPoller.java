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

package org.niis.xroad.edc.extension.assetaccess.poller;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_NEGOTIATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;

/**
 * Completes asset access acquisition futures by polling the shared EDC contract negotiation and
 * transfer process stores for the terminal state of registered ids, in place of an in-process
 * listener. A terminal state is visible in the store before any listener anywhere would fire, so
 * the same mechanism serves a single instance and several instances sharing one database.
 *
 * <p>Each poll pass reads and classifies every registered waiter inside one transaction; the
 * transaction covers reads only. Completion - removing the waiter and finishing its future, resolving
 * a started transfer's data address - happens after the transaction has returned, on a dedicated
 * completion executor. The poll thread never completes a future or runs a caller continuation
 * directly, and never holds a transaction while resolving a data address.
 */
public class AssetAccessCompletionPoller {

    private static final String POLL_THREAD_NAME = "AssetAccessCompletionPoller-poll";
    private static final String COMPLETION_THREAD_NAME = "AssetAccessCompletionPoller-completion";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final String DEFAULT_TERMINATION_DETAIL = "provider terminated";

    private final ContractNegotiationStore negotiationStore;
    private final TransferProcessStore transferProcessStore;
    private final DataAddressStore dataAddressStore;
    private final TransactionContext transactionContext;
    private final Clock clock;
    private final Monitor monitor;
    private final Duration pollInterval;
    private final ScheduledExecutorService executor;
    private final ExecutorService completionExecutor;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private final Map<String, Waiter<ContractAgreement>> negotiationWaiters = new ConcurrentHashMap<>();
    private final Map<String, Waiter<DataAddress>> transferWaiters = new ConcurrentHashMap<>();

    public AssetAccessCompletionPoller(ContractNegotiationStore negotiationStore,
                                        TransferProcessStore transferProcessStore,
                                        DataAddressStore dataAddressStore,
                                        TransactionContext transactionContext,
                                        ExecutorInstrumentation executorInstrumentation,
                                        Clock clock,
                                        Monitor monitor,
                                        Duration pollInterval) {
        this.negotiationStore = negotiationStore;
        this.transferProcessStore = transferProcessStore;
        this.dataAddressStore = dataAddressStore;
        this.transactionContext = transactionContext;
        this.clock = clock;
        this.monitor = monitor;
        this.pollInterval = pollInterval;
        this.executor = executorInstrumentation.instrument(
                Executors.newSingleThreadScheduledExecutor(namedDaemonThreadFactory(POLL_THREAD_NAME)), POLL_THREAD_NAME);
        this.completionExecutor = executorInstrumentation.instrument(
                Executors.newCachedThreadPool(namedDaemonThreadFactory(COMPLETION_THREAD_NAME)), COMPLETION_THREAD_NAME);
    }

    private static ThreadFactory namedDaemonThreadFactory(String name) {
        var counter = new AtomicInteger();
        return runnable -> {
            var thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName(name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Registers a negotiation id to await; the returned future completes with the contract
     * agreement once the negotiation reaches {@link ContractNegotiationStates#FINALIZED}, fails with
     * the stored error detail once it reaches {@link ContractNegotiationStates#TERMINATED}, or fails
     * with a timeout once {@code timeout} passes without a terminal state.
     */
    public CompletableFuture<ContractAgreement> awaitNegotiation(String negotiationId, Duration timeout) {
        return register(negotiationWaiters, negotiationId, timeout);
    }

    /**
     * Registers a transfer process id to await; the returned future completes with the data address
     * resolved through {@link DataAddressStore} once the transfer reaches
     * {@link TransferProcessStates#STARTED}, fails with the stored error detail once it reaches
     * {@link TransferProcessStates#TERMINATED}, or fails with a timeout once {@code timeout} passes
     * without a terminal state.
     */
    public CompletableFuture<DataAddress> awaitTransfer(String transferProcessId, Duration timeout) {
        return register(transferWaiters, transferProcessId, timeout);
    }

    private <T> CompletableFuture<T> register(Map<String, Waiter<T>> waiters, String id, Duration timeout) {
        var future = new CompletableFuture<T>();
        waiters.put(id, new Waiter<>(future, clock.instant().plus(timeout)));
        return future;
    }

    /**
     * Starts the scheduled poll loop.
     */
    public void start() {
        active.set(true);
        executor.schedule(this::runTick, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the poll loop, fails every pending waiter through the completion executor and shuts both
     * executors down, waiting (bounded) for the completion executor so that no completion runs after
     * this method returns.
     */
    public void stop() {
        active.set(false);
        executor.shutdown();
        awaitTermination(executor);
        failAllPending(negotiationWaiters);
        failAllPending(transferWaiters);
        completionExecutor.shutdown();
        awaitTermination(completionExecutor);
    }

    private void runTick() {
        try {
            poll();
        } catch (Exception e) {
            monitor.severe("Asset access completion poll pass failed", e);
        } finally {
            scheduleNextTick();
        }
    }

    private void scheduleNextTick() {
        if (active.get()) {
            executor.schedule(this::runTick, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Runs one poll pass. The transaction covers reads and classification only; every completion -
     * removing the waiter and finishing its future - runs afterwards on the completion executor.
     * Called internally by the scheduled loop between {@link #start()} and {@link #stop()}; also
     * usable directly to drive a deterministic pass in a test.
     */
    public void poll() {
        if (!active.get()) {
            return;
        }
        List<Runnable> completions = new ArrayList<>();
        transactionContext.execute(() -> {
            negotiationWaiters.forEach((id, waiter) -> pollNegotiation(completions, id, waiter));
            transferWaiters.forEach((id, waiter) -> pollTransfer(completions, id, waiter));
        });
        completions.forEach(Runnable::run);
    }

    private void pollNegotiation(List<Runnable> completions, String id, Waiter<ContractAgreement> waiter) {
        ContractNegotiation negotiation;
        try {
            negotiation = negotiationStore.findById(id);
        } catch (Exception e) {
            monitor.warning("Reading negotiation %s failed, leaving it pending".formatted(id), e);
            return;
        }
        if (negotiation != null) {
            var state = ContractNegotiationStates.from(negotiation.getState());
            if (state == ContractNegotiationStates.FINALIZED) {
                monitor.debug("Negotiation %s reached FINALIZED".formatted(id));
                var agreement = negotiation.getContractAgreement();
                completions.add(() -> completeWaiter(negotiationWaiters, id, waiter, agreement));
                return;
            }
            if (state == ContractNegotiationStates.TERMINATED) {
                monitor.debug("Negotiation %s reached TERMINATED".formatted(id));
                var failure = negotiationFailure(id, errorDetailOrDefault(negotiation.getErrorDetail()));
                completions.add(() -> failWaiter(negotiationWaiters, id, waiter, failure));
                return;
            }
        }
        if (!clock.instant().isBefore(waiter.deadline())) {
            completions.add(() -> failWaiter(negotiationWaiters, id, waiter, new TimeoutException()));
        }
    }

    private void pollTransfer(List<Runnable> completions, String id, Waiter<DataAddress> waiter) {
        TransferProcess transferProcess;
        try {
            transferProcess = transferProcessStore.findById(id);
        } catch (Exception e) {
            monitor.warning("Reading transfer process %s failed, leaving it pending".formatted(id), e);
            return;
        }
        if (transferProcess != null) {
            var state = TransferProcessStates.from(transferProcess.getState());
            if (state == TransferProcessStates.STARTED) {
                monitor.debug("Transfer process %s reached STARTED".formatted(id));
                completions.add(() -> resolveAndCompleteTransfer(id, waiter, transferProcess));
                return;
            }
            if (state == TransferProcessStates.TERMINATED) {
                monitor.debug("Transfer process %s reached TERMINATED".formatted(id));
                var failure = transferFailure(id, errorDetailOrDefault(transferProcess.getErrorDetail()));
                completions.add(() -> failWaiter(transferWaiters, id, waiter, failure));
                return;
            }
        }
        if (!clock.instant().isBefore(waiter.deadline())) {
            completions.add(() -> failWaiter(transferWaiters, id, waiter, new TimeoutException()));
        }
    }

    private void resolveAndCompleteTransfer(String id, Waiter<DataAddress> waiter, TransferProcess transferProcess) {
        if (!transferWaiters.remove(id, waiter)) {
            return;
        }
        dispatch(() -> {
            try {
                var resolved = dataAddressStore.resolve(transferProcess);
                if (resolved.succeeded()) {
                    waiter.future().complete(resolved.getContent());
                } else {
                    waiter.future().completeExceptionally(transferFailure(id, resolved.getFailureDetail()));
                }
            } catch (Exception e) {
                waiter.future().completeExceptionally(transferFailure(id, e.getMessage()));
            }
        });
    }

    private static String errorDetailOrDefault(String errorDetail) {
        return errorDetail != null ? errorDetail : DEFAULT_TERMINATION_DETAIL;
    }

    private <T> void completeWaiter(Map<String, Waiter<T>> waiters, String id, Waiter<T> waiter, T value) {
        if (waiters.remove(id, waiter)) {
            dispatch(() -> waiter.future().complete(value));
        }
    }

    private <T> void failWaiter(Map<String, Waiter<T>> waiters, String id, Waiter<T> waiter, Throwable throwable) {
        if (waiters.remove(id, waiter)) {
            dispatch(() -> waiter.future().completeExceptionally(throwable));
        }
    }

    private <T> void failAllPending(Map<String, Waiter<T>> waiters) {
        waiters.forEach((id, waiter) -> failWaiter(waiters, id, waiter, acquisitionStoppedFailure(id)));
    }

    /**
     * Runs a completion on {@link #completionExecutor}. Falls back to running it on the calling thread if the
     * executor has already been shut down, so that a repeated {@link #stop()} call - which fails any waiter
     * registered after a previous {@link #stop()} already terminated the executor - never throws.
     */
    private void dispatch(Runnable completion) {
        try {
            completionExecutor.execute(completion);
        } catch (RejectedExecutionException e) {
            completion.run();
        }
    }

    private void awaitTermination(ExecutorService service) {
        try {
            if (!service.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static XrdRuntimeException negotiationFailure(String negotiationId, String reason) {
        return XrdRuntimeException.systemException(DSP_NEGOTIATION_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems(negotiationId)
                .details(reason)
                .build();
    }

    private static XrdRuntimeException transferFailure(String transferProcessId, String reason) {
        return XrdRuntimeException.systemException(DSP_TRANSFER_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems(transferProcessId)
                .details(reason)
                .build();
    }

    private static XrdRuntimeException acquisitionStoppedFailure(String id) {
        return XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems(id)
                .details("Asset access completion poller stopped")
                .build();
    }

    private record Waiter<T>(CompletableFuture<T> future, Instant deadline) {
    }
}
