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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * ID-keyed bookkeeping for callers awaiting an asynchronous terminal event. Holds the active-waiter
 * registry plus two dead-letter caches (success payload, termination reason) that close the race
 * window where an event arrives before {@link #register(String, CompletableFuture)} is called.
 *
 * <p>Thread safety: {@link ConcurrentHashMap} guarantees atomic put/remove; dead-letter caches are
 * Guava's thread-safe {@link Cache}.
 *
 * @param <T> the success payload type delivered to waiting futures
 */
final class FutureRegistry<T> {

    private static final Duration PENDING_TTL = Duration.ofMinutes(5);
    private static final long PENDING_MAX_SIZE = 10_000;

    private final ConcurrentHashMap<String, CompletableFuture<T>> registry = new ConcurrentHashMap<>();

    private final Cache<String, T> pendingSuccess = CacheBuilder.newBuilder()
            .expireAfterWrite(PENDING_TTL)
            .maximumSize(PENDING_MAX_SIZE)
            .build();

    private final Cache<String, String> pendingTerminations = CacheBuilder.newBuilder()
            .expireAfterWrite(PENDING_TTL)
            .maximumSize(PENDING_MAX_SIZE)
            .build();

    private final BiFunction<String, String, ? extends Throwable> terminationExceptionFactory;

    FutureRegistry(BiFunction<String, String, ? extends Throwable> terminationExceptionFactory) {
        this.terminationExceptionFactory = terminationExceptionFactory;
    }

    /**
     * Registers a future to be completed when the terminal event for {@code id} arrives.
     * Must be called after the upstream operation returns its ID; dead-letter caches are consulted
     * for events that arrived before this call.
     */
    void register(String id, CompletableFuture<T> future) {
        registry.put(id, future);

        var pending = pendingSuccess.getIfPresent(id);
        if (pending != null) {
            pendingSuccess.invalidate(id);
            registry.remove(id);
            future.complete(pending);
            return;
        }

        var reason = pendingTerminations.getIfPresent(id);
        if (reason != null) {
            pendingTerminations.invalidate(id);
            registry.remove(id);
            future.completeExceptionally(terminationExceptionFactory.apply(id, reason));
        }
    }

    /**
     * Removes the ID from registry and dead-letter caches.
     * Safe to call if the entry was already removed by an event delivery.
     */
    void deregister(String id) {
        registry.remove(id);
        pendingSuccess.invalidate(id);
        pendingTerminations.invalidate(id);
    }

    /**
     * Returns the number of IDs currently registered.
     */
    int activeWaiters() {
        return registry.size();
    }

    /**
     * Delivers a success event for {@code id}. If a future is registered, completes it; otherwise
     * stashes the payload in the dead-letter cache for a later {@link #register} call.
     */
    void dispatchSuccess(String id, T payload) {
        var future = registry.remove(id);
        if (future != null) {
            future.complete(payload);
        } else {
            pendingSuccess.put(id, payload);
        }
    }

    /**
     * Delivers a termination event for {@code id}. If a future is registered, fails it via the
     * exception factory; otherwise stashes the reason in the dead-letter cache.
     */
    void dispatchTermination(String id, String reason) {
        var future = registry.remove(id);
        if (future != null) {
            future.completeExceptionally(terminationExceptionFactory.apply(id, reason));
        } else {
            pendingTerminations.put(id, reason);
        }
    }
}
