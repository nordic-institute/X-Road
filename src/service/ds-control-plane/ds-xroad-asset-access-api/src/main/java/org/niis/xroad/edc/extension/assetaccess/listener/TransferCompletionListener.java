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

package org.niis.xroad.edc.extension.assetaccess.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessListener;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton listener registered once at extension startup.
 * Dispatches TransferProcess terminal events to waiting futures
 * via an ID-keyed registry. Replaces per-request anonymous listener instances.
 *
 * <p>Thread safety: ConcurrentHashMap guarantees atomic put/remove.
 * Dead-letter maps handle the sub-microsecond window where an event arrives
 * before {@link #register(String, CompletableFuture)} is called.
 */
public class TransferCompletionListener implements TransferProcessListener {

    private static final Duration PENDING_TTL = Duration.ofMinutes(5);
    private static final long PENDING_MAX_SIZE = 10_000;

    private final ConcurrentHashMap<String, CompletableFuture<DataAddress>> registry =
            new ConcurrentHashMap<>();

    private final Cache<String, DataAddress> pendingStarted = CacheBuilder.newBuilder()
            .expireAfterWrite(PENDING_TTL)
            .maximumSize(PENDING_MAX_SIZE)
            .build();

    private final Cache<String, Termination> pendingTerminations = CacheBuilder.newBuilder()
            .expireAfterWrite(PENDING_TTL)
            .maximumSize(PENDING_MAX_SIZE)
            .build();

    record Termination(String reason) {
    }

    /**
     * Registers a future to be completed when the transfer process with the given ID reaches a terminal state.
     * Must be called after {@code initiateTransfer()} returns the transfer process ID.
     * Checks dead-letter maps for events that arrived before this call.
     *
     * @param transferProcessId the transfer process ID returned by initiateTransfer
     * @param future            the future to complete on terminal state
     */
    public void register(String transferProcessId, CompletableFuture<DataAddress> future) {
        registry.put(transferProcessId, future);

        var pending = pendingStarted.getIfPresent(transferProcessId);
        if (pending != null) {
            pendingStarted.invalidate(transferProcessId);
            registry.remove(transferProcessId);
            future.complete(pending);
            return;
        }

        var termination = pendingTerminations.getIfPresent(transferProcessId);
        if (termination != null) {
            pendingTerminations.invalidate(transferProcessId);
            registry.remove(transferProcessId);
            future.completeExceptionally(new EdcException(termination.reason()));
        }
    }

    /**
     * Removes the transfer process ID from all maps. Called on timeout or explicit cleanup.
     * Safe to call if the entry was already removed by an event delivery.
     *
     * @param transferProcessId the transfer process ID to remove
     */
    public void deregister(String transferProcessId) {
        registry.remove(transferProcessId);
        pendingStarted.invalidate(transferProcessId);
        pendingTerminations.invalidate(transferProcessId);
    }

    /**
     * Returns the number of active waiters (transfer process IDs currently registered).
     * Useful for monitoring and debugging.
     *
     * @return the number of registered futures
     */
    public int activeWaiters() {
        return registry.size();
    }

    @Override
    public void started(TransferProcess process, TransferProcessStartedData additionalData) {
        var future = registry.remove(process.getId());
        if (future != null) {
            future.complete(additionalData.getDataAddress());
        } else {
            pendingStarted.put(process.getId(), additionalData.getDataAddress());
        }
    }

    @Override
    public void terminated(TransferProcess process) {
        var future = registry.remove(process.getId());
        String errorDetail = process.getErrorDetail() != null
                ? process.getErrorDetail() : "provider terminated";
        if (future != null) {
            future.completeExceptionally(
                    new EdcException("Transfer process terminated: " + errorDetail));
        } else {
            pendingTerminations.put(process.getId(),
                    new Termination("Transfer process terminated: " + errorDetail));
        }
    }
}
