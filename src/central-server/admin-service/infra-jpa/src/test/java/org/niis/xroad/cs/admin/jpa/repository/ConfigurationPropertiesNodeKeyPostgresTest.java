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
package org.niis.xroad.cs.admin.jpa.repository;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code centerui-changelog.xml} against a Postgres testcontainer and checks the
 * {@code configuration_properties} unique key {@code (property_key, ha_node_name) NULLS NOT DISTINCT}:
 * the shared-row upsert used by every writer, rejection of a duplicate shared row, node rows next to
 * a shared row, and a second changelog run.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * so a Docker-less run skips instead of failing.
 */
class ConfigurationPropertiesNodeKeyPostgresTest {

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

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @BeforeAll
    static void migrate() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping the Postgres-backed integration test");
        POSTGRES.start();
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS hstore");
        }
        applyCenteruiChangelog();
    }

    @AfterAll
    static void stop() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Test
    void sharedUpsertUpdatesTheExistingSharedRow() throws Exception {
        var key = newKey();

        sharedUpsert(key, "first");
        sharedUpsert(key, "second");

        assertEquals(1, countRows(key));
        assertEquals("second", sharedValue(key));
    }

    @Test
    void secondPlainInsertOfASharedRowViolatesTheUniqueKey() throws Exception {
        var key = newKey();
        execute(PLAIN_SHARED_INSERT, key, "first");

        var failure = assertThrows(SQLException.class, () -> execute(PLAIN_SHARED_INSERT, key, "second"));

        assertEquals(UNIQUE_VIOLATION, failure.getSQLState());
        assertEquals(1, countRows(key));
    }

    @Test
    void nodeRowCoexistsWithTheSharedRowAndLeavesItUntouched() throws Exception {
        var key = newKey();
        sharedUpsert(key, "shared");

        execute(PLAIN_NODE_INSERT, key, "for-node_1", "node_1");
        execute(PLAIN_NODE_INSERT, key, "for-node_2", "node_2");
        sharedUpsert(key, "shared-updated");

        assertEquals(3, countRows(key));
        assertEquals("shared-updated", sharedValue(key));
        assertEquals("for-node_1", nodeValue(key, "node_1"));
        assertEquals("for-node_2", nodeValue(key, "node_2"));
    }

    @Test
    void secondRowForTheSameKeyAndNodeViolatesTheUniqueKey() throws Exception {
        var key = newKey();
        execute(PLAIN_NODE_INSERT, key, "first", "node_1");

        var failure = assertThrows(SQLException.class, () -> execute(PLAIN_NODE_INSERT, key, "second", "node_1"));

        assertEquals(UNIQUE_VIOLATION, failure.getSQLState());
    }

    @Test
    void runningTheChangelogAgainChangesNothing() throws Exception {
        var key = newKey();
        sharedUpsert(key, "kept");
        var changesBefore = countChangeSets();

        applyCenteruiChangelog();

        assertEquals(changesBefore, countChangeSets());
        assertEquals(1, countRows(key));
        assertEquals("kept", sharedValue(key));
    }

    private static String newKey() {
        return "xroad.test.node-key-" + UUID.randomUUID();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void sharedUpsert(String key, String value) throws SQLException {
        execute(SHARED_UPSERT, key, value);
    }

    private static void execute(String sql, String... parameters) throws SQLException {
        try (var connection = connect(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    private static long countRows(String key) throws SQLException {
        return queryLong("SELECT count(*) FROM configuration_properties WHERE property_key = ?", key);
    }

    private static long countChangeSets() throws SQLException {
        return queryLong("SELECT count(*) FROM databasechangelog");
    }

    private static long queryLong(String sql, String... parameters) throws SQLException {
        try (var connection = connect(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String sharedValue(String key) throws SQLException {
        return queryString("SELECT property_value FROM configuration_properties"
                + " WHERE property_key = ? AND ha_node_name IS NULL", key);
    }

    private static String nodeValue(String key, String node) throws SQLException {
        return queryString("SELECT property_value FROM configuration_properties"
                + " WHERE property_key = ? AND ha_node_name = ?", key, node);
    }

    private static String queryString(String sql, String... parameters) throws SQLException {
        try (var connection = connect(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    /**
     * Applies the same {@code centerui-changelog.xml} production deployments run, excluding only the
     * last changeset ({@code separate-admin-user}): it grants and revokes permissions between two
     * Postgres roles, a split this single-role testcontainer has no use for.
     */
    private static void applyCenteruiChangelog() throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/centerui-changelog.xml");
            update.addArgumentValue("url", POSTGRES.getJdbcUrl());
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
