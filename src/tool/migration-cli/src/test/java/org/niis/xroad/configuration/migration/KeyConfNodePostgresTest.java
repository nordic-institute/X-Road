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
package org.niis.xroad.configuration.migration;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs {@code keyconf} through the CLI against a real PostgreSQL database that holds the schema of the real
 * {@code serverconf-changelog.xml}. The signer repository uses {@code RETURNING}, so an in-memory database
 * cannot stand in.
 */
@ExtendWith(SystemStubsExtension.class)
class KeyConfNodePostgresTest {

    private static final String TEMPLATE_DATABASE = "keyconf_template";
    private static final String SOFT_TOKEN_PIN_ENV = "XROAD_MIGRATION_SOFTTOKEN_PIN";
    private static final List<String> SIGNER_TABLES = List.of(
            "signer_tokens", "signer_keys", "signer_certificates", "signer_certificate_requests");

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    private String database;
    private String keyconfDir;
    private String dbProperties;

    @BeforeAll
    static void startDatabase() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        POSTGRES.start();
        execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + TEMPLATE_DATABASE);
        migrateSchema(TEMPLATE_DATABASE);
    }

    @AfterAll
    static void stopDatabase() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void createFreshDatabase() throws Exception {
        envVars.set(SOFT_TOKEN_PIN_ENV, "Secret1234");
        database = "keyconf_" + DATABASE_COUNTER.incrementAndGet();
        execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + database + " TEMPLATE " + TEMPLATE_DATABASE);

        keyconfDir = Paths.get(getClass().getClassLoader().getResource("signer/").toURI()).toString();
        Path propertiesFile = tempDir.resolve("db.properties");
        Files.writeString(propertiesFile, """
                xroad.db.serverconf.hibernate.connection.url=%s
                xroad.db.serverconf.hibernate.connection.username=%s
                xroad.db.serverconf.hibernate.connection.password=%s
                """.formatted(url(database), POSTGRES.getUsername(), POSTGRES.getPassword()));
        dbProperties = propertiesFile.toString();
    }

    @Test
    void shouldWriteAllRowsUnderTheNodeGiven() {
        runKeyconf("--node", "node_b");

        assertThat(rowCountsByNode("node_b")).containsExactly(2, 3, 3, 1);
        assertThat(rowCountsByNode("node_0")).containsExactly(0, 0, 0, 0);
    }

    @Test
    void shouldWriteUnderNode0WhenNoNodeIsGiven() {
        runKeyconf();

        assertThat(rowCountsByNode("node_0")).containsExactly(2, 3, 3, 1);
    }

    @Test
    void shouldChangeNothingWhenRunAgainForTheSameNode() {
        runKeyconf("--node", "node_b");
        List<String> afterFirstRun = snapshot();

        runKeyconf("--node", "node_b");

        assertThat(afterFirstRun).hasSize(9);
        assertThat(snapshot()).isEqualTo(afterFirstRun);
    }

    @Test
    void shouldKeepEachNodesRowsApartWhenTheTokenIdentifiersAreTheSame() {
        runKeyconf("--node", "node_a");
        List<String> nodeARows = snapshotOf("node_a");

        runKeyconf("--node", "node_b");

        assertThat(rowCountsByNode("node_a")).containsExactly(2, 3, 3, 1);
        assertThat(rowCountsByNode("node_b")).containsExactly(2, 3, 3, 1);
        assertThat(snapshotOf("node_a")).isEqualTo(nodeARows);
    }

    @Test
    void shouldWriteNothingWhenTheNodeNameIsInvalid() {
        assertThatThrownBy(() -> runKeyconf("--node", "_bad"))
                .isInstanceOf(MigrationException.class)
                .cause()
                .isInstanceOf(XrdRuntimeException.class);

        assertThat(totalRowCount()).isZero();
    }

    private void runKeyconf(String... nodeOption) {
        List<String> args = new ArrayList<>(List.of("keyconf"));
        args.addAll(List.of(nodeOption));
        args.addAll(List.of(keyconfDir, dbProperties));
        LegacyConfigMigrationCLI.main(args.toArray(new String[0]));
    }

    private List<Integer> rowCountsByNode(String node) {
        return SIGNER_TABLES.stream()
                .map(table -> queryInt("SELECT count(*) FROM " + table + " WHERE ha_node_name = '" + node + "'"))
                .toList();
    }

    private int totalRowCount() {
        return SIGNER_TABLES.stream().mapToInt(table -> queryInt("SELECT count(*) FROM " + table)).sum();
    }

    private List<String> snapshot() {
        List<String> rows = new ArrayList<>();
        SIGNER_TABLES.forEach(table -> rows.addAll(
                queryStrings("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY id")));
        return rows;
    }

    private List<String> snapshotOf(String node) {
        List<String> rows = new ArrayList<>();
        SIGNER_TABLES.forEach(table -> rows.addAll(queryStrings(
                "SELECT to_jsonb(t)::text FROM " + table + " t WHERE ha_node_name = '" + node + "' ORDER BY id")));
        return rows;
    }

    private int queryInt(String sql) {
        return Integer.parseInt(queryStrings(sql).get(0));
    }

    private List<String> queryStrings(String sql) {
        try (var connection = DriverManager.getConnection(
                url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void execute(String targetDatabase, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(
                url(targetDatabase), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String url(String targetDatabase) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + targetDatabase;
    }

    private static void migrateSchema(String targetDatabase) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/serverconf-changelog.xml");
            update.addArgumentValue("url", url(targetDatabase));
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
