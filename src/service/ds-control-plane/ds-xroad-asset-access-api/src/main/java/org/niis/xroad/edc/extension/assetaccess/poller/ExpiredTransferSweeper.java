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

import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.edc.extension.assetaccess.token.AgreementTokenExpiry;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically completes every {@code STARTED} consumer transfer process whose agreement token expired
 * more than a grace period ago. A consumer re-acquires access under a reused agreement roughly every
 * minute, which starts a new transfer process each time; this sweep is what retires the previous one
 * once its token can no longer serve a request, including for a relationship that goes idle and never
 * re-acquires.
 *
 * <p>Each pass reads candidate transfers and resolves their token expiry inside one transaction; the
 * transaction covers reads only; completing a transfer - a separate call to
 * {@link TransferProcessService#complete(String)} - happens afterwards, outside the transaction, so a
 * slow completion call never holds a read transaction open.
 */
public class ExpiredTransferSweeper {

    private static final String SWEEP_THREAD_NAME = "ExpiredTransferSweeper-sweep";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final int READ_BATCH_SIZE = 500;

    private final TransferProcessStore transferProcessStore;
    private final DataAddressStore dataAddressStore;
    private final TransferProcessService transferProcessService;
    private final TransactionContext transactionContext;
    private final Clock clock;
    private final Monitor monitor;
    private final Duration interval;
    private final Duration grace;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean(false);

    public ExpiredTransferSweeper(TransferProcessStore transferProcessStore,
                                   DataAddressStore dataAddressStore,
                                   TransferProcessService transferProcessService,
                                   TransactionContext transactionContext,
                                   Clock clock,
                                   Monitor monitor,
                                   Duration interval,
                                   Duration grace) {
        this.transferProcessStore = transferProcessStore;
        this.dataAddressStore = dataAddressStore;
        this.transferProcessService = transferProcessService;
        this.transactionContext = transactionContext;
        this.clock = clock;
        this.monitor = monitor;
        this.interval = interval;
        this.grace = grace;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name(SWEEP_THREAD_NAME + "-", 1).daemon().factory());
    }

    /**
     * Starts the scheduled sweep loop.
     */
    public void start() {
        active.set(true);
        executor.schedule(this::runTick, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the sweep loop and shuts the executor down, waiting (bounded) for a pass already in
     * progress to finish before returning.
     */
    public void stop() {
        active.set(false);
        executor.shutdown();
        awaitTermination();
    }

    private void runTick() {
        try {
            sweep();
        } catch (Exception e) {
            monitor.severe("Expired transfer sweep pass failed", e);
        } finally {
            scheduleNextTick();
        }
    }

    private void scheduleNextTick() {
        if (active.get()) {
            executor.schedule(this::runTick, interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Runs one sweep pass. Called internally by the scheduled loop between {@link #start()} and
     * {@link #stop()}; also usable directly to drive a deterministic pass in a test.
     */
    public void sweep() {
        List<String> expiredIds = new ArrayList<>();
        transactionContext.execute(() -> {
            expiredIds.addAll(findExpiredTransferIds());
        });
        if (!expiredIds.isEmpty()) {
            completeAll(expiredIds);
        }
    }

    private List<String> findExpiredTransferIds() {
        List<String> expired = new ArrayList<>();
        int offset = 0;
        int pageSize;
        do {
            var page = readPage(offset);
            pageSize = page.size();
            page.stream()
                    .filter(this::expiredBeyondGrace)
                    .map(TransferProcess::getId)
                    .forEach(expired::add);
            offset += pageSize;
        } while (pageSize == READ_BATCH_SIZE);
        return expired;
    }

    private List<TransferProcess> readPage(int offset) {
        var query = QuerySpec.Builder.newInstance()
                .filter(List.of(
                        Criterion.criterion("type", "=", TransferProcess.Type.CONSUMER.name()),
                        Criterion.criterion("state", "=", TransferProcessStates.STARTED.code())))
                .offset(offset)
                .limit(READ_BATCH_SIZE)
                .build();
        try (var stream = transferProcessStore.findAll(query)) {
            return stream.toList();
        }
    }

    private boolean expiredBeyondGrace(TransferProcess transferProcess) {
        var resolved = dataAddressStore.resolve(transferProcess);
        if (resolved.failed()) {
            return false;
        }
        return AgreementTokenExpiry.expiryOf(resolved.getContent())
                .map(expiry -> expiry.plus(grace).isBefore(clock.instant()))
                .orElse(false);
    }

    private void completeAll(List<String> transferProcessIds) {
        var completedCount = 0;
        for (var transferProcessId : transferProcessIds) {
            if (complete(transferProcessId)) {
                completedCount++;
            }
        }
        if (completedCount > 0) {
            monitor.info("Expired transfer sweep completed %d transfer process(es)".formatted(completedCount));
        }
    }

    private boolean complete(String transferProcessId) {
        try {
            var result = transferProcessService.complete(transferProcessId);
            if (result.failed()) {
                monitor.warning("Completing expired transfer failed: transferProcessId=%s reason=%s"
                        .formatted(transferProcessId, result.getFailureDetail()));
                return false;
            }
            return true;
        } catch (Exception e) {
            monitor.warning("Completing expired transfer failed: transferProcessId=%s".formatted(transferProcessId), e);
            return false;
        }
    }

    private void awaitTermination() {
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
