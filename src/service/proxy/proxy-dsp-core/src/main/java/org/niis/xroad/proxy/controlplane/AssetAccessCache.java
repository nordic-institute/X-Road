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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Caffeine-backed cache for acquired asset access responses.
 * Handles cache construction and per-entry TTL derived from the response expiry field.
 */
class AssetAccessCache {

    /**
     * Cache key for acquired asset access responses. A cache must key on every field that
     * affects the response.
     *
     * <p>Planned: {@code participantContextId} will become per-request-configurable;
     * {@code counterPartyAddress} will be resolved from GlobalConf; multiple {@code protocol}
     * versions are foreseeable.
     */
    record CacheKey(String participantContextId, String assetId, String counterPartyId,
                    String counterPartyAddress, String protocol) { }

    record CachedEntry(AssetAccessResponse response, long expiresAtEpochSeconds) { }

    private final Cache<CacheKey, CachedEntry> cache;

    AssetAccessCache(AssetAccessClientProperties.Cache cacheProps) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheProps.maximumSize())
                .expireAfter(new EntryExpiry(cacheProps.defaultTtl().toNanos()))
                .build();
    }

    private static final class EntryExpiry implements Expiry<CacheKey, CachedEntry> {
        private final long defaultTtlNanos;

        EntryExpiry(long defaultTtlNanos) {
            this.defaultTtlNanos = defaultTtlNanos;
        }

        @Override
        public long expireAfterCreate(CacheKey key, CachedEntry value, long currentTime) {
            if (value.expiresAtEpochSeconds() <= 0) {
                return defaultTtlNanos;
            }
            long ttlSeconds = value.expiresAtEpochSeconds() - Instant.now().getEpochSecond();
            return TimeUnit.SECONDS.toNanos(Math.max(ttlSeconds, 0));
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
     * Concurrent loads on the same key are collapsed into a single call.
     */
    CachedEntry get(CacheKey key, Function<CacheKey, CachedEntry> loader) {
        return cache.get(key, loader);
    }
}
