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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_NEGOTIATION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;

/**
 * Completes asset access acquisition futures by polling the shared EDC contract negotiation and
 * transfer process stores for the terminal state of registered ids, in place of an in-process
 * listener. A terminal state is visible in the store before any listener anywhere would fire, so
 * the same mechanism serves a single instance and several instances sharing one database.
 */
public class AssetAccessCompletionPoller {

    private static final String THREAD_NAME = "AssetAccessCompletionPoller";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ContractNegotiationStore negotiationStore;
    private final TransferProcessStore transferProcessStore;
    private final TransactionContext transactionContext;
    private final Clock clock;
    private final Monitor monitor;
    private final Duration pollInterval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private final Map<String, Waiter<ContractAgreement>> negotiationWaiters = new ConcurrentHashMap<>();
    private final Map<String, Waiter<DataAddress>> transferWaiters = new ConcurrentHashMap<>();

    public AssetAccessCompletionPoller(ContractNegotiationStore negotiationStore,
                                        TransferProcessStore transferProcessStore,
                                        TransactionContext transactionContext,
                                        ExecutorInstrumentation executorInstrumentation,
                                        Clock clock,
                                        Monitor monitor,
                                        Duration pollInterval) {
        this.negotiationStore = negotiationStore;
        this.transferProcessStore = transferProcessStore;
        this.transactionContext = transactionContext;
        this.clock = clock;
        this.monitor = monitor;
        this.pollInterval = pollInterval;
        this.executor = executorInstrumentation.instrument(
                Executors.newSingleThreadScheduledExecutor(AssetAccessCompletionPoller::newPollerThread), THREAD_NAME);
    }

    private static Thread newPollerThread(Runnable runnable) {
        var thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName(THREAD_NAME);
        thread.setDaemon(true);
        return thread;
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
     * Registers a transfer process id to await; the returned future completes with the content data
     * address once the transfer reaches {@link TransferProcessStates#STARTED}, fails with the stored
     * error detail once it reaches {@link TransferProcessStates#TERMINATED}, or fails with a timeout
     * once {@code timeout} passes without a terminal state.
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
     * Stops the poll loop and fails every pending waiter with an acquisition failure.
     */
    public void stop() {
        active.set(false);
        executor.shutdown();
        failAllPending(negotiationWaiters);
        failAllPending(transferWaiters);
        awaitExecutorTermination();
    }

    private void runTick() {
        try {
            poll();
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
     * Runs one poll pass over every registered waiter. Called internally by the scheduled loop
     * between {@link #start()} and {@link #stop()}; also usable directly to drive a deterministic
     * pass in a test.
     */
    public void poll() {
        if (!active.get()) {
            return;
        }
        transactionContext.execute(() -> {
            pollNegotiations();
            pollTransfers();
        });
    }

    private void pollNegotiations() {
        negotiationWaiters.forEach((id, waiter) -> {
            var negotiation = negotiationStore.findById(id);
            if (negotiation == null || !handleNegotiation(id, waiter, negotiation)) {
                expireIfPastDeadline(negotiationWaiters, id, waiter);
            }
        });
    }

    private boolean handleNegotiation(String id, Waiter<ContractAgreement> waiter, ContractNegotiation negotiation) {
        var state = ContractNegotiationStates.from(negotiation.getState());
        if (state == ContractNegotiationStates.FINALIZED) {
            monitor.debug("Negotiation %s reached FINALIZED".formatted(id));
            complete(negotiationWaiters, id, waiter, negotiation.getContractAgreement());
            return true;
        }
        if (state == ContractNegotiationStates.TERMINATED) {
            monitor.debug("Negotiation %s reached TERMINATED".formatted(id));
            fail(negotiationWaiters, id, waiter, negotiationFailure(id, negotiation.getErrorDetail()));
            return true;
        }
        return false;
    }

    private void pollTransfers() {
        transferWaiters.forEach((id, waiter) -> {
            var transferProcess = transferProcessStore.findById(id);
            if (transferProcess == null || !handleTransfer(id, waiter, transferProcess)) {
                expireIfPastDeadline(transferWaiters, id, waiter);
            }
        });
    }

    private boolean handleTransfer(String id, Waiter<DataAddress> waiter, TransferProcess transferProcess) {
        var state = TransferProcessStates.from(transferProcess.getState());
        if (state == TransferProcessStates.STARTED) {
            monitor.debug("Transfer process %s reached STARTED".formatted(id));
            complete(transferWaiters, id, waiter, transferProcess.getContentDataAddress());
            return true;
        }
        if (state == TransferProcessStates.TERMINATED) {
            monitor.debug("Transfer process %s reached TERMINATED".formatted(id));
            var errorDetail = transferProcess.getErrorDetail() != null ? transferProcess.getErrorDetail() : "provider terminated";
            fail(transferWaiters, id, waiter, transferFailure(id, errorDetail));
            return true;
        }
        return false;
    }

    private <T> void expireIfPastDeadline(Map<String, Waiter<T>> waiters, String id, Waiter<T> waiter) {
        if (!clock.instant().isBefore(waiter.deadline())) {
            fail(waiters, id, waiter, new TimeoutException());
        }
    }

    private <T> void complete(Map<String, Waiter<T>> waiters, String id, Waiter<T> waiter, T value) {
        if (waiters.remove(id, waiter)) {
            waiter.future().complete(value);
        }
    }

    private <T> void fail(Map<String, Waiter<T>> waiters, String id, Waiter<T> waiter, Throwable throwable) {
        if (waiters.remove(id, waiter)) {
            waiter.future().completeExceptionally(throwable);
        }
    }

    private <T> void failAllPending(Map<String, Waiter<T>> waiters) {
        waiters.forEach((id, waiter) -> fail(waiters, id, waiter, acquisitionStoppedFailure(id)));
    }

    private void awaitExecutorTermination() {
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
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
