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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable.Row;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeAwareDbSourceTest {

    private static Map<String, String> databaseEnvironment;

    @BeforeAll
    static void createTable() throws SQLException {
        databaseEnvironment = NodeConfigTestTable.create("nodeaware", List.of(
                new Row("both.key", "shared value", null),
                new Row("both.key", "node_1 value", "node_1"),
                new Row("both.key", "node_2 value", "node_2"),
                new Row("shared.only.key", "shared only value", null),
                new Row("node_2.only.key", "node_2 only value", "node_2"),
                new Row("node_1.only.key", "node_1 only value", "node_1"),
                new Row("quoted.key", "shared quoted value", null),
                new Row("quoted.key", "quoted node value", "node'x")));
    }

    @Test
    void nodeRowBeatsSharedRowForTheSameKey() {
        Map<String, String> properties = load("node_1");

        assertThat(properties).containsEntry("both.key", "node_1 value");
    }

    @Test
    void rowsOfAnotherNodeAreIgnored() {
        Map<String, String> properties = load("node_1");

        assertThat(properties).doesNotContainKey("node_2.only.key").doesNotContainValue("node_2 value");
    }

    @Test
    void keyWithOnlyASharedRowResolvesToIt() {
        assertThat(load("node_1")).containsEntry("shared.only.key", "shared only value");
    }

    @Test
    void nodeOnlyKeyOfTheOwnNodeResolves() {
        assertThat(load("node_1")).containsEntry("node_1.only.key", "node_1 only value");
    }

    @Test
    void sharedProviderLoadsSharedRowsOnlyEvenWhenNodeRowsExist() {
        Map<String, String> properties = load(null);

        assertThat(properties).containsOnly(
                Map.entry("both.key", "shared value"),
                Map.entry("shared.only.key", "shared only value"),
                Map.entry("quoted.key", "shared quoted value"));
    }

    @Test
    void nodeNameIsBoundAsParameterNotInterpolated() {
        Map<String, String> properties = load("node'x");

        assertThat(properties).containsEntry("quoted.key", "quoted node value");
        assertThat(properties).doesNotContainValue("node_1 value").doesNotContainValue("node_2 value");
    }

    @Test
    void loadValuesTakesTheNodeNameFromTheProvider() {
        assertThat(DbSourceConfig.loadValues("test-app", () -> Optional.of("node_1")).getNodeName())
                .isEqualTo("node_1");
        assertThat(DbSourceConfig.loadValues("test-app").getNodeName()).isNull();
        assertThat(DbSourceConfig.loadValues("test-app", NodeNameProvider.shared()).getNodeName()).isNull();
    }

    private static Map<String, String> load(String nodeName) {
        DbSourceConfig config = mock(DbSourceConfig.class);
        when(config.getUrl()).thenReturn(databaseEnvironment.get("DB_CONFIG_SOURCE_URL"));
        when(config.getUsername()).thenReturn(databaseEnvironment.get("DB_CONFIG_SOURCE_USERNAME"));
        when(config.getAppName()).thenReturn("test-app");
        when(config.getTableName()).thenReturn(databaseEnvironment.get("DB_CONFIG_SOURCE_TABLE_NAME"));
        when(config.getNodeName()).thenReturn(nodeName);

        return new CachedDbConfigSource(config).getProperties();
    }
}
