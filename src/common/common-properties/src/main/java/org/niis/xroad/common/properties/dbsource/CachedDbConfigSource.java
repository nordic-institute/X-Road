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

package org.niis.xroad.common.properties.dbsource;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CachedDbConfigSource {

    private final Cache<String, String> cache;
    private final DbSourceRepository repository;

    public CachedDbConfigSource(DataSource dataSource, DbSourceConfig config) {
        this.repository = new DbSourceRepository(dataSource, config);
        // todo use ConcurrentHashMap for cache?
        this.cache = Caffeine.newBuilder().build();

        reloadCache();

        int ttl = config.getCacheTtl();
        if (ttl > 0) {
            log.info("Scheduling cache refresh every {} seconds", ttl);
            Executors.newSingleThreadScheduledExecutor()
                    .scheduleAtFixedRate(this::reloadCache, ttl, ttl, TimeUnit.SECONDS);
        } else {
            log.info("Cache refresh disabled");
        }
    }

    public Set<String> getPropertyNames() {
        log.trace("getPropertyNames()");
        return cache.asMap().keySet();
    }

    public String getValue(String propertyName) {
        log.trace("getValue() for property {}", propertyName);
        return cache.getIfPresent(propertyName);
    }

    public Map<String, String> getProperties() {
        return cache.asMap();
    }

    private synchronized void reloadCache() {
        log.debug("reloadCache()");
        Map<String, String> dbProps = repository.getProperties();
        cache.invalidateAll();
        dbProps.forEach(cache::put);
    }

}
