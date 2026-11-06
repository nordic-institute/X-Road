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

    private final String nodeName;

    private String selectAllStatement;

    public DbSourceRepository(DataSource dataSource, DbSourceConfig config) {
        this.dataSource = dataSource;
        this.nodeName = config.getNodeName();
        prepareQueries(config);
    }

    /**
     * Loads the shared overrides and, when a node name is configured, the overrides stored for that node;
     * a node value wins over the shared value of the same key. Fails fast on any SQL error: the DSL layer is
     * the only path stored configuration reaches a service, so silently starting on packaged defaults must
     * never happen — a connection failure already aborts startup, and a post-connect query failure aborts it
     * the same way.
     */
    public Map<String, String> getProperties() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(selectAllStatement)) {
            if (nodeName != null) {
                preparedStatement.setString(1, nodeName);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return merge(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("db-config-source: could not load configuration overrides", e);
        }
    }

    private static Map<String, String> merge(ResultSet resultSet) throws SQLException {
        Map<String, String> shared = new HashMap<>();
        Map<String, String> node = new HashMap<>();
        while (resultSet.next()) {
            var target = resultSet.getString("ha_node_name") == null ? shared : node;
            target.put(resultSet.getString("property_key"), resultSet.getString("property_value"));
        }
        shared.putAll(node);
        return shared;
    }

    private void prepareQueries(DbSourceConfig config) {
        var filter = nodeName == null ? "c.ha_node_name IS NULL" : "c.ha_node_name IS NULL OR c.ha_node_name = ?";
        selectAllStatement = "SELECT c.property_key, c.property_value, c.ha_node_name FROM %s c WHERE %s"
                .formatted(config.getTableName(), filter);
    }
}
