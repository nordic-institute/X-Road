package org.niis.xroad.restapi.config;

import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
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

    public static final int TTL_SECONDS = 60;

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
                        .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
                        .build()
                        .asMap();
                return new ConcurrentMapCache(name, store, isAllowNullValues());
            }
        };
    }

}
