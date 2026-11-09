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
package org.niis.xroad.cs.admin.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs the real {@code centerui-changelog.xml} against the shared Postgres container and checks the
 * {@code configuration_properties} unique key {@code (property_key, ha_node_name) NULLS NOT DISTINCT}:
 * the shared-row upsert used by every writer, rejection of a duplicate shared row, node rows next to
 * a shared row, and a second changelog run.
 */
class ConfigurationPropertiesNodeKeyPostgresTest extends AbstractCentralServerPostgresTest {

    private static final String SHARED_UPSERT = """
            INSERT INTO configuration_properties (property_key, property_value, ha_node_name)
            VALUES (?, ?, NULL)
            ON CONFLICT (property_key, ha_node_name) DO UPDATE SET property_value = EXCLUDED.property_value
            """;
    private static final String PLAIN_SHARED_INSERT = """
            INSERT INTO configuration_properties (property_key, property_value) VALUES (?, ?)
            """;
    private static final String PLAIN_NODE_INSERT = """
            INSERT INTO configuration_properties (property_key, property_value, ha_node_name) VALUES (?, ?, ?)
            """;
    private static final String UNIQUE_VIOLATION = "23505";

    @Test
    void sharedUpsertUpdatesTheExistingSharedRow() {
        var key = newKey();

        jdbc.update(SHARED_UPSERT, key, "first");
        jdbc.update(SHARED_UPSERT, key, "second");

        assertEquals(1, countRows(key));
        assertEquals("second", sharedValue(key));
    }

    @Test
    void secondPlainInsertOfASharedRowViolatesTheUniqueKey() {
        var key = newKey();
        jdbc.update(PLAIN_SHARED_INSERT, key, "first");

        var failure = assertThrows(DataAccessException.class, () -> jdbc.update(PLAIN_SHARED_INSERT, key, "second"));

        assertEquals(UNIQUE_VIOLATION, sqlState(failure));
        assertEquals(1, countRows(key));
    }

    @Test
    void nodeRowCoexistsWithTheSharedRowAndLeavesItUntouched() {
        var key = newKey();
        jdbc.update(SHARED_UPSERT, key, "shared");

        jdbc.update(PLAIN_NODE_INSERT, key, "for-node_1", "node_1");
        jdbc.update(PLAIN_NODE_INSERT, key, "for-node_2", "node_2");
        jdbc.update(SHARED_UPSERT, key, "shared-updated");

        assertEquals(3, countRows(key));
        assertEquals("shared-updated", sharedValue(key));
        assertEquals("for-node_1", nodeValue(key, "node_1"));
        assertEquals("for-node_2", nodeValue(key, "node_2"));
    }

    @Test
    void secondRowForTheSameKeyAndNodeViolatesTheUniqueKey() {
        var key = newKey();
        jdbc.update(PLAIN_NODE_INSERT, key, "first", "node_1");

        var failure = assertThrows(DataAccessException.class,
                () -> jdbc.update(PLAIN_NODE_INSERT, key, "second", "node_1"));

        assertEquals(UNIQUE_VIOLATION, sqlState(failure));
    }

    @Test
    void runningTheChangelogAgainChangesNothing() throws Exception {
        var key = newKey();
        jdbc.update(SHARED_UPSERT, key, "kept");
        var changesBefore = countChangeSets();

        applyCentralServerChangelog();

        assertEquals(changesBefore, countChangeSets());
        assertEquals(1, countRows(key));
        assertEquals("kept", sharedValue(key));
    }

    private static String newKey() {
        return "xroad.test.node-key-" + UUID.randomUUID();
    }

    private static String sqlState(DataAccessException failure) {
        return ((SQLException) failure.getMostSpecificCause()).getSQLState();
    }

    private long countRows(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM configuration_properties WHERE property_key = ?",
                Long.class, key);
    }

    private long countChangeSets() {
        return jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Long.class);
    }

    private String sharedValue(String key) {
        return jdbc.queryForObject("SELECT property_value FROM configuration_properties"
                + " WHERE property_key = ? AND ha_node_name IS NULL", String.class, key);
    }

    private String nodeValue(String key, String node) {
        return jdbc.queryForObject("SELECT property_value FROM configuration_properties"
                + " WHERE property_key = ? AND ha_node_name = ?", String.class, key, node);
    }
}
