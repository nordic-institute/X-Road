/*
 * The MIT License
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
package org.niis.xroad.restapi.config;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.util.CaffeineCacheBuilder;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Api caching configuration. Dynamically loads all caches built with {@link CaffeineCacheBuilder}.
 */
@Slf4j
@EnableCaching
@Configuration
public class ApiCachingConfiguration {
    public static final String LIST_ALL_KEYS_CACHE = "all-apikeys";

    @Bean
    public CacheManager cacheManager(List<CaffeineCacheBuilder.ConfiguredCache> caches) {
        var cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(caches.stream()
                .peek(configuredCache -> log.info("Initializing [{}] cache. Provider: {}, TTL: {} seconds.",
                        configuredCache.getCache().getName(),
                        configuredCache.getCache().getClass().getSimpleName(),
                        configuredCache.getTimeToLiveSeconds()))
                .map(CaffeineCacheBuilder.ConfiguredCache::getCache)
                .collect(Collectors.toList()));

        return cacheManager;
    }

    @Bean
    public CaffeineCacheBuilder.ConfiguredCache cacheApiKeyListAll(Config cachingProperties) {
        return CaffeineCacheBuilder.newExpireAfterWriteCache(LIST_ALL_KEYS_CACHE, cachingProperties.getCacheApiKeyTtl());
    }

    public interface Config {
        int getCacheDefaultTtl();

        int getCacheApiKeyTtl();
    }
}
