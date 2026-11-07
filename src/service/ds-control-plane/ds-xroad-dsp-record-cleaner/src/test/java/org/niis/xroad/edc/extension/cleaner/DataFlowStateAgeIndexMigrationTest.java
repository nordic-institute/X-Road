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
package org.niis.xroad.edc.extension.cleaner;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code serverconf-changelog.xml} on a fresh Postgres database, and again on a database that
 * already held data-flow rows before the age index changeset ran.
 */
class DataFlowStateAgeIndexMigrationTest {

    private static final String INDEX = "idx_dataflow_state_state_updated_at";
    private static final String INDEX_CHANGESET = "014-dataflow-state-age-index";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Test
    void indexExistsAfterMigratingAFreshDatabase() throws Exception {
        createDatabase("fresh");

        migrate("fresh");

        assertThat(indexColumns("fresh")).containsExactly("state", "updated_at");
    }

    @Test
    void indexExistsAfterUpgradingADatabaseThatAlreadyHoldsFlowRows() throws Exception {
        createDatabase("upgraded");
        migrate("upgraded");
        execute("upgraded", "INSERT INTO dataflow_state (flow_id, state) VALUES ('existing-flow', 'STARTED')");
        execute("upgraded", "DROP INDEX " + INDEX);
        execute("upgraded", "DELETE FROM databasechangelog WHERE id = '" + INDEX_CHANGESET + "'");
        assertThat(indexColumns("upgraded")).isEmpty();

        migrate("upgraded");

        assertThat(indexColumns("upgraded")).containsExactly("state", "updated_at");
        assertThat(query("upgraded", "SELECT flow_id FROM dataflow_state")).containsExactly("existing-flow");
    }

    private static List<String> indexColumns(String database) throws SQLException {
        return query(database, """
                SELECT a.attname FROM pg_index i
                JOIN pg_class c ON c.oid = i.indexrelid
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                WHERE c.relname = '%s'
                ORDER BY array_position(i.indkey::int2[], a.attnum)
                """.formatted(INDEX));
    }

    private static void createDatabase(String database) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
    }

    private static String url(String database) {
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
    }

    private static void execute(String database, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> query(String database, String sql) throws SQLException {
        var values = new ArrayList<String>();
        try (var connection = DriverManager.getConnection(url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private static void migrate(String database) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/serverconf-changelog.xml");
            update.addArgumentValue("url", url(database));
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
