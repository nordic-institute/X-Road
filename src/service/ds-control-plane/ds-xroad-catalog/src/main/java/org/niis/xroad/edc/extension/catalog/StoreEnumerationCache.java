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
package org.niis.xroad.edc.extension.catalog;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * TTL-based Guava cache wrapping enumeration (find-all) and point-lookup (find-by-id) paths.
 */
@Slf4j
class StoreEnumerationCache<T> {

    @Nullable
    private final Cache<Boolean, List<T>> enumerationCache;
    @Nullable
    private final Cache<String, T> findByIdCache;
    @Nullable
    private final Cache<String, DataAddress> dataAddressCache;
    private final String storeName;

    StoreEnumerationCache(boolean enabled, long ttlSeconds, int findByIdMaxSize, String storeName) {
        this(enabled, ttlSeconds, findByIdMaxSize, storeName, Ticker.systemTicker());
    }

    StoreEnumerationCache(boolean enabled, long ttlSeconds, int findByIdMaxSize, String storeName, Ticker ticker) {
        this.storeName = storeName;
        if (enabled) {
            enumerationCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                    .ticker(ticker)
                    .recordStats()
                    .build();
            findByIdCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                    .maximumSize(findByIdMaxSize)
                    .ticker(ticker)
                    .recordStats()
                    .build();
            dataAddressCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                    .maximumSize(findByIdMaxSize)
                    .ticker(ticker)
                    .recordStats()
                    .build();
        } else {
            enumerationCache = null;
            findByIdCache = null;
            dataAddressCache = null;
        }
    }

    List<T> getEnumeration(Supplier<List<T>> loader) {
        if (enumerationCache == null) {
            return loader.get();
        }
        try {
            return enumerationCache.get(Boolean.TRUE, () -> {
                var result = loader.get();
                logStatistics();
                return result;
            });
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Cache load failed", e.getCause());
        }
    }

    @Nullable
    T findById(String id, Supplier<T> loader) {
        return getOrLoad(findByIdCache, id, loader);
    }

    @Nullable
    private static <V> V getOrLoad(@Nullable Cache<String, V> cache, String key, Supplier<V> loader) {
        if (cache == null) {
            return loader.get();
        }
        var cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        var result = loader.get();
        if (result != null) {
            cache.put(key, result);
        }
        return result;
    }

    void cacheDataAddress(String assetId, @Nullable DataAddress value) {
        if (dataAddressCache != null && value != null) {
            dataAddressCache.put(assetId, value);
        }
    }

    @Nullable
    DataAddress getDataAddress(String assetId) {
        if (dataAddressCache == null) {
            return null;
        }
        return dataAddressCache.getIfPresent(assetId);
    }

    void logStatistics() {
        if (!log.isTraceEnabled()) {
            return;
        }
        if (enumerationCache != null) {
            log.trace("{} enumeration cache stats: {}", storeName, enumerationCache.stats());
        }
        if (findByIdCache != null) {
            log.trace("{} findById cache stats: {}", storeName, findByIdCache.stats());
        }
        if (dataAddressCache != null) {
            log.trace("{} dataAddress cache stats: {}", storeName, dataAddressCache.stats());
        }
    }

    void invalidate() {
        if (enumerationCache != null) {
            enumerationCache.invalidateAll();
        }
        if (findByIdCache != null) {
            findByIdCache.invalidateAll();
        }
        if (dataAddressCache != null) {
            dataAddressCache.invalidateAll();
        }
    }
}
