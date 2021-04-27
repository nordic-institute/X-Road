/**
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
package org.niis.xroad.securityserver.restapi.config;

import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Configuration which makes ConcurrentMapCache use Guava cache and a hard coded TTL (after write) for cache items.
 * By default this cache manager, and TTL setting, applies for all caches. It is intended to be used with simple
 * ConcurrentMapCache autoconfiguration.
 *
 * storeByValue = true is not supported. If that is needed, either change this configuration or use
 * e.g. Caffeine (perhaps better option)
 */
@Configuration
@Slf4j
public class TtlConcurrentMapCacheConfiguration {

    @Value("${cache.simple.ttl}")
    private int timeToLiveSeconds;

    @Bean
    public CacheManager cacheManager() {

        return new ConcurrentMapCacheManager() {
            @Override
            protected Cache createConcurrentMapCache(String name) {
                // storeByValue is false by default (for ConcurrentMapCaches) and wont be true unless we set it
                // explicitly. I think it is enough to just not support storeByValue, and keep things simple.
                // https://github.com/spring-projects/spring-framework/issues/18331
                if (isStoreByValue()) {
                    throw new IllegalStateException("storeByValue is not supported");
                }
                ConcurrentMap<Object, Object> store = CacheBuilder.newBuilder()
                        .expireAfterWrite(timeToLiveSeconds, TimeUnit.SECONDS)
                        .build()
                        .asMap();
                return new ConcurrentMapCache(name, store, isAllowNullValues());
            }
        };
    }

}
