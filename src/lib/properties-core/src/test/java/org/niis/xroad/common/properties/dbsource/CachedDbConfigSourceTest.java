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

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CachedDbConfigSourceTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

    private static DataSource dataSource;

    @BeforeAll
    public static void beforeAll() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl(JDBC_URL);
        ds.setUser("sa");
        ds.setPassword("");

        dataSource = ds;

        try (var connection = ds.getConnection();
             var statement = connection.createStatement()) {

            // key-only schema: property_key is globally unique, no scope column
            statement.execute(
                    "CREATE TABLE config ( "
                            + " id bigint PRIMARY KEY, "
                            + " property_key VARCHAR(512) NOT NULL UNIQUE, "
                            + " property_value VARCHAR(512) NOT NULL, "
                            + " created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL, "
                            + " updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL) "
            );

            statement.execute("insert into config values (1, 'test.key', 'a value', now(), now())");
            statement.execute("insert into config values (2, 'test.signer.key', 'signer value', now(), now())");
            statement.execute("insert into config values (3, 'test.app.key', 'app value', now(), now())");
            statement.execute("insert into config values (4, 'global.key', 'global value', now(), now())");
        }

    }

    @Test
    void allPropertiesAreResolvedByKeyRegardlessOfAppName() {
        Map<String, String> properties = new CachedDbConfigSource(mockConfig("signer")).getProperties();

        assertEquals(4, properties.size());
        assertEquals("a value", properties.get("test.key"));
        assertEquals("signer value", properties.get("test.signer.key"));
        assertEquals("app value", properties.get("test.app.key"));
        assertEquals("global value", properties.get("global.key"));
    }

    @Test
    void appNameDoesNotFilterTheResolvedProperties() {
        Map<String, String> forSigner = new CachedDbConfigSource(mockConfig("signer")).getProperties();
        Map<String, String> forOtherApp = new CachedDbConfigSource(mockConfig("otherApp")).getProperties();

        assertEquals(forSigner, forOtherApp);
    }

    @Test
    void singleProperty() {
        CachedDbConfigSource configSource = new CachedDbConfigSource(mockConfig("test-app"));

        assertEquals("app value", configSource.getValue("test.app.key"));
        assertEquals("a value", configSource.getValue("test.key"));
        assertNull(configSource.getValue("missing.key"));
    }

    @Test
    void queryFailureAfterConnectFailsFastInsteadOfServingDefaults() {
        DbSourceConfig config = mockConfig("test-app");
        when(config.getTableName()).thenReturn("missing_table");

        assertThrows(IllegalStateException.class, () -> new CachedDbConfigSource(config));
    }

    private DbSourceConfig mockConfig(String appName) {
        DbSourceConfig config = mock(DbSourceConfig.class);

        when(config.getUrl()).thenReturn(JDBC_URL);
        when(config.getUsername()).thenReturn("sa");

        when(config.getAppName()).thenReturn(appName);
        when(config.getTableName()).thenReturn("config");

        return config;
    }

}
