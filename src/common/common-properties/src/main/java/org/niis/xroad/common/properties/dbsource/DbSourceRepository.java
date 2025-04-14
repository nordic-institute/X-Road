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

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DbSourceRepository {
    private final DataSource dataSource;

    private String selectAllStatement;
    private String selectValueStatement;
    private String selectKeysStatement;

    public DbSourceRepository(DataSource dataSource, DbSourceConfig config) {
        this.dataSource = dataSource;
        prepareQueries(config);
    }

    public Set<String> getPropertyNames() {
        try (PreparedStatement preparedStatement = dataSource.getConnection().prepareStatement(selectKeysStatement)) {
            Set<String> keys = new HashSet<>();
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    keys.add(resultSet.getString(1));
                }
            }
            return keys;
        } catch (SQLException e) {
            log.error("db-config-source: could not get property names", e);
            return Collections.emptySet();
        }
    }

    public String getValue(String propertyName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectValueStatement)) {
            preparedStatement.setString(1, propertyName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString(2);
                }
            }
        } catch (SQLException e) {
            log.error("db-config-source: could not get property names", e);
        }
        return null;
    }

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
            log.error("db-config-source: could not get properties", e);
        }
        return Map.of();
    }


    private void prepareQueries(DbSourceConfig config) {
        selectAllStatement = """
                WITH ranked_props AS (
                    SELECT c.key, c.value,
                           row_number() OVER (PARTITION BY c.key ORDER BY c.scope NULLS LAST) AS rn
                    FROM %s c
                    WHERE c.scope IS NULL OR c.scope = '%s'
                )
                SELECT conf.key, conf.value
                FROM ranked_props conf WHERE conf.rn = 1
                """.formatted(config.getTableName(), config.getAppName());

        selectValueStatement = selectAllStatement + " AND conf.key = ?";

        selectKeysStatement = "SELECT DISTINCT conf.key FROM %s conf WHERE conf.scope IS NULL or conf.scope = '%s'"
                .formatted(config.getTableName(), config.getAppName());
    }
}
