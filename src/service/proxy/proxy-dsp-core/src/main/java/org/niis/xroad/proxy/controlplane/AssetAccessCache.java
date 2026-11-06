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
package org.niis.xroad.proxy.controlplane;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.annotation.Nullable;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Caffeine-backed cache for acquired asset access responses.
 * Handles cache construction and per-entry TTL derived from the response expiry field.
 *
 * <p>Backed by a Caffeine {@link AsyncCache}, so a cache miss is loaded on its own thread
 * rather than under the cache's internal per-key lock, and one key's load never blocks another's.
 * Concurrent callers of the same key share the one future Caffeine tracks for that key; a failed
 * load is removed before {@link #get} rethrows, so the next caller loads again.
 */
class AssetAccessCache {

    /**
     * Cache key for acquired asset access responses. A cache must key on every field that
     * affects the response; {@code participantContextId} varies per request with the sender.
     *
     * <p>Planned: {@code counterPartyAddress} will be resolved from GlobalConf; multiple
     * {@code protocol} versions are foreseeable.
     */
    record CacheKey(String participantContextId, String assetId, String counterPartyId,
                    String counterPartyAddress, String protocol, @Nullable String consumerClientId) { }

    record CachedEntry(AssetAccessResponse response, long expiresAtEpochSeconds) { }

    private final AsyncCache<CacheKey, CachedEntry> cache;
    private final Executor loadExecutor;

    AssetAccessCache(AssetAccessClientProperties.Cache cacheProps) {
        this(cacheProps, Ticker.systemTicker(), defaultLoadExecutor());
    }

    /**
     * @param ticker       the cache's time source; overridable so a test can fast-forward past an entry's
     *                     expiry without waiting on the wall clock. Production callers get the system ticker.
     * @param loadExecutor where {@link #get} runs a cache miss's load. Production callers get an executor sized
     *                     for blocking calls; a test using {@code ticker} to fast-forward time needs a
     *                     same-thread executor so the load, and the expiry Caffeine derives from it,
     *                     complete before {@link #get}'s caller observes the result.
     */
    AssetAccessCache(AssetAccessClientProperties.Cache cacheProps, Ticker ticker, Executor loadExecutor) {
        this.loadExecutor = loadExecutor;
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheProps.maximumSize())
                .expireAfter(new EntryExpiry(cacheProps.defaultTtl().toNanos(), cacheProps.margin().toNanos()))
                .ticker(ticker)
                .buildAsync();
    }

    private static Executor defaultLoadExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("asset-access-cache-load-", 0L).factory());
    }

    private static final class EntryExpiry implements Expiry<CacheKey, CachedEntry> {
        private static final long MINIMUM_LIFETIME_NANOS = TimeUnit.SECONDS.toNanos(1);

        private final long defaultTtlNanos;
        private final long marginNanos;

        EntryExpiry(long defaultTtlNanos, long marginNanos) {
            this.defaultTtlNanos = defaultTtlNanos;
            this.marginNanos = marginNanos;
        }

        /**
         * An entry carrying a real expiry lives until {@code margin} before it, floored at
         * {@link #MINIMUM_LIFETIME_NANOS} so a margin close to (or beyond) the remaining lifetime never
         * makes a still-valid entry expire on arrival and defeat the cache. An entry whose expiry has
         * already passed is not retained at all: the caller that loaded it still receives it, and the
         * next lookup acquires afresh.
         */
        @Override
        public long expireAfterCreate(CacheKey key, CachedEntry value, long currentTime) {
            if (value.expiresAtEpochSeconds() <= 0) {
                return defaultTtlNanos;
            }
            long ttlSeconds = value.expiresAtEpochSeconds() - Instant.now().getEpochSecond();
            if (ttlSeconds <= 0) {
                return 0;
            }
            long ttlNanos = TimeUnit.SECONDS.toNanos(ttlSeconds) - marginNanos;
            return Math.max(ttlNanos, MINIMUM_LIFETIME_NANOS);
        }

        @Override
        public long expireAfterUpdate(CacheKey key, CachedEntry value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(CacheKey key, CachedEntry value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }

    /**
     * Returns the cached entry for the given key, loading it on a cache miss via {@code loader}.
     * The load runs on its own thread, off the caller's and off any other key's; concurrent callers of
     * the same key share the single in-flight future Caffeine associates with that key and get the same
     * result. The calling thread still blocks for the result, bounded only by whatever deadline
     * {@code loader} itself enforces. A failed load is removed from the cache before this method
     * returns, so the next call loads again. A {@link RuntimeException} from {@code loader} reaches the
     * caller unwrapped; any other failure (for example an {@link Error}) reaches the caller wrapped in
     * the {@link CompletionException} thrown by {@link CompletableFuture#join}.
     */
    CachedEntry get(CacheKey key, Function<CacheKey, CachedEntry> loader) {
        var future = cache.get(key, (k, _) -> CompletableFuture.supplyAsync(() -> loader.apply(k), loadExecutor));
        try {
            return future.join();
        } catch (CompletionException e) {
            // Caffeine also drops a failed future from the map, but only in a whenComplete callback that can
            // run after join() has already woken this thread; removing it here closes that window so a
            // same-key call right behind this one never observes the stale failure.
            cache.asMap().remove(key, future);
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    /**
     * Drops the entries acquired for the given participant context, asset and calling client, including one
     * still loading: those of one provider candidate, or of every candidate when {@code counterPartyId} is
     * {@code null}.
     */
    void invalidate(String participantContextId, String assetId, @Nullable String counterPartyId,
                    @Nullable String consumerClientId) {
        cache.asMap().keySet().removeIf(key -> key.participantContextId().equals(participantContextId)
                && key.assetId().equals(assetId)
                && (counterPartyId == null || key.counterPartyId().equals(counterPartyId))
                && Objects.equals(key.consumerClientId(), consumerClientId));
    }
}
