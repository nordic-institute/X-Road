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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.ofNullable;

@Slf4j
public class CachedDbConfigSource {

    private final Map<String, String> cache;

    public CachedDbConfigSource(DbSourceConfig config) {
        DbSourceRepository repository = initRepository(config);
        this.cache = new ConcurrentHashMap<>(repository.getProperties());
    }

    private DbSourceRepository initRepository(DbSourceConfig config) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setJdbcUrl(config.getUrl());

        ofNullable(config.getUsername()).ifPresent(hikariConfig::setUsername);
        ofNullable(config.getPassword()).ifPresent(p -> hikariConfig.setPassword(new String(p)));
        ofNullable(config.getSchema()).ifPresent(hikariConfig::setSchema);
        ofNullable(config.getAppName()).ifPresent(app -> hikariConfig.addDataSourceProperty("ApplicationName", app));

        return new DbSourceRepository(new HikariDataSource(hikariConfig), config);
    }

    public Set<String> getPropertyNames() {
        log.trace("getPropertyNames()");
        return cache.keySet();
    }

    public String getValue(String propertyName) {
        log.trace("getValue() for property {}", propertyName);
        return cache.get(propertyName);
    }

    public Map<String, String> getProperties() {
        return new HashMap<>(cache);
    }

}
