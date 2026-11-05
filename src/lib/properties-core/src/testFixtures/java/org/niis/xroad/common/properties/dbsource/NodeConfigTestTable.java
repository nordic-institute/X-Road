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

import lombok.experimental.UtilityClass;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.SQLException;
import java.util.Map;

/**
 * An in-memory configuration table holding a shared row and a {@code node_1} row for one key, together with
 * the environment variables that point {@link DbSourceConfig} at it.
 */
@UtilityClass
public class NodeConfigTestTable {

    public static final String NODE_NAME = "node_1";

    private static final String TABLE_NAME = "node_config_test";

    /**
     * Creates the table in a new in-memory database.
     * @param databaseName name of the in-memory database, unique per test class
     * @param key the property key stored twice
     * @param sharedValue the value of the shared row
     * @param nodeValue the value of the {@code node_1} row
     * @return the environment variables that make {@link DbSourceConfig} read the table
     * @throws SQLException when the table cannot be created
     */
    public static Map<String, String> create(String databaseName, String key, String sharedValue, String nodeValue)
            throws SQLException {
        var url = "jdbc:h2:mem:%s;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1".formatted(databaseName);
        var dataSource = new JdbcDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + TABLE_NAME + " ( "
                    + " id bigint PRIMARY KEY, "
                    + " property_key VARCHAR(512) NOT NULL, "
                    + " property_value VARCHAR(512) NOT NULL, "
                    + " ha_node_name VARCHAR(255))");
            statement.execute("INSERT INTO %s VALUES (1, '%s', '%s', null)".formatted(TABLE_NAME, key, sharedValue));
            statement.execute("INSERT INTO %s VALUES (2, '%s', '%s', '%s')"
                    .formatted(TABLE_NAME, key, nodeValue, NODE_NAME));
        }

        return Map.of("DB_CONFIG_SOURCE_URL", url,
                "DB_CONFIG_SOURCE_USERNAME", "sa",
                "DB_CONFIG_SOURCE_TABLE_NAME", TABLE_NAME);
    }
}
