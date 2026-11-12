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

package org.niis.xroad.edc.extension.assetaccess.service;

import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Holds the per-instance in-flight request map that collapses concurrent asset access acquisitions for
 * the same key into a single call. Agreement state lives in the shared EDC agreement store instead; see
 * {@link org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup}.
 *
 * <p>Every caller gets a waiter of its own on the shared work. Cancelling a waiter ends only that caller's wait;
 * the work stops, through its {@link AcquisitionScope}, once its last waiter has been cancelled.
 */
public class AssetAccessStateStore {

    private final ConcurrentHashMap<String, InFlight> inFlightRequests;

    public AssetAccessStateStore() {
        this(new ConcurrentHashMap<>());
    }

    AssetAccessStateStore(ConcurrentHashMap<String, InFlight> inFlightRequests) {
        this.inFlightRequests = inFlightRequests;
    }

    /**
     * Returns a new waiter on the in-flight work for {@code key} if there is any; otherwise claims the slot,
     * then calls {@code supplier} outside the map to produce the work and returns the first waiter on it. The
     * work's outcome completes every waiter still waiting, and the slot is freed once the work completes.
     *
     * <p>The slot is claimed before {@code supplier} runs, so the supplier may block; a supplier that throws
     * synchronously fails the work and leaves no entry behind.
     */
    public CompletableFuture<AssetAccessResult> loadOrStartInFlight(
            String key,
            Function<AcquisitionScope, CompletableFuture<AssetAccessResult>> supplier) {
        while (true) {
            var claimed = new InFlight(key);
            var firstWaiter = claimed.addWaiter();
            var existing = inFlightRequests.putIfAbsent(key, claimed);
            if (existing == null) {
                return start(claimed, firstWaiter, supplier);
            }
            var waiter = existing.addWaiter();
            if (waiter != null) {
                return waiter;
            }
            inFlightRequests.remove(key, existing);
        }
    }

    private CompletableFuture<AssetAccessResult> start(
            InFlight claimed, CompletableFuture<AssetAccessResult> waiter,
            Function<AcquisitionScope, CompletableFuture<AssetAccessResult>> supplier) {
        claimed.outcome.whenComplete((result, throwable) -> inFlightRequests.remove(claimed.key, claimed));
        try {
            supplier.apply(claimed.scope).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    claimed.outcome.completeExceptionally(throwable);
                } else {
                    claimed.outcome.complete(result);
                }
            });
        } catch (Exception e) {
            claimed.outcome.completeExceptionally(e);
        }
        return waiter;
    }

    final class InFlight {
        private final String key;
        private final CompletableFuture<AssetAccessResult> outcome = new CompletableFuture<>();
        private final AcquisitionScope scope = new AcquisitionScope();
        private int waiters;
        private boolean abandoned;

        private InFlight(String key) {
            this.key = key;
        }

        /** @return a new waiter, or {@code null} when the work was abandoned by all its waiters */
        private synchronized CompletableFuture<AssetAccessResult> addWaiter() {
            if (abandoned) {
                return null;
            }
            waiters++;
            var waiter = new CompletableFuture<AssetAccessResult>();
            waiter.whenComplete((result, throwable) -> {
                if (waiter.isCancelled()) {
                    removeWaiter();
                }
            });
            outcome.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    waiter.completeExceptionally(throwable);
                } else {
                    waiter.complete(result);
                }
            });
            return waiter;
        }

        private void removeWaiter() {
            synchronized (this) {
                if (outcome.isDone()) {
                    return;
                }
                waiters--;
                if (waiters > 0) {
                    return;
                }
                abandoned = true;
            }
            inFlightRequests.remove(key, this);
            scope.stop();
            outcome.cancel(true);
        }
    }
}
