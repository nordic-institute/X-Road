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

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DbSourceRepository {
    private final DataSource dataSource;

    private String selectAllStatement;

    public DbSourceRepository(DataSource dataSource, DbSourceConfig config) {
        this.dataSource = dataSource;
        prepareQueries(config);
    }

    /**
     * Loads every stored override. Fails fast on any SQL error: the DSL layer is the only path stored
     * configuration reaches a service, so silently starting on packaged defaults must never happen —
     * a connection failure already aborts startup, and a post-connect query failure aborts it the same way.
     */
    public Map<String, String> getProperties() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectAllStatement);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            Map<String, String> properties = new HashMap<>();
            while (resultSet.next()) {
                properties.put(resultSet.getString(1), resultSet.getString(2));
            }
            return properties;
        } catch (SQLException e) {
            throw new IllegalStateException("db-config-source: could not load configuration overrides", e);
        }
    }

    private void prepareQueries(DbSourceConfig config) {
        selectAllStatement = "SELECT c.property_key, c.property_value FROM %s c".formatted(config.getTableName());
    }
}
