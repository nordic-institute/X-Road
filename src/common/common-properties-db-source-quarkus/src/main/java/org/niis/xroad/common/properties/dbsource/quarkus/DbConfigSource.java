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

package org.niis.xroad.common.properties.dbsource.quarkus;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.niis.xroad.common.properties.dbsource.CachedDbConfigSource;
import org.niis.xroad.common.properties.dbsource.DbSourceConfig;

import javax.sql.DataSource;

import java.util.Map;
import java.util.Set;

@Slf4j
public class DbConfigSource implements ConfigSource {
    private static final String NAME = "db-source";
    private static final int ORDINAL = 299; // lower than system properties (400), env variables from system (300)

    private final CachedDbConfigSource dbConfigSource;

    public DbConfigSource(DataSource dataSource, DbSourceConfig config) {
        this.dbConfigSource = new CachedDbConfigSource(dataSource, config);
    }

    @Override
    public Set<String> getPropertyNames() {
        return dbConfigSource.getPropertyNames();
    }

    @Override
    public String getValue(String propertyName) {
        return dbConfigSource.getValue(propertyName);
    }

    @Override
    public Map<String, String> getProperties() {
        return dbConfigSource.getProperties();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

}
