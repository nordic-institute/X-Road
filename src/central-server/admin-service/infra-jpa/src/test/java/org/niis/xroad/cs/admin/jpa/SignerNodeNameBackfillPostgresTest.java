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

import liquibase.Scope;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.command.CommandScope;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.liquibase.LiquibaseExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import picocli.CommandLine;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real changelogs through the real {@link LiquibaseExecutor}, the way the package scripts do. Each test
 * starts from a database migrated up to just before the signer node name backfill and seeded with one row per
 * signer table under a marker name, so a row that still carries the marker was not touched by the backfill.
 */
class SignerNodeNameBackfillPostgresTest {

    private static final String BACKFILL_CHANGESET = "signer-backfill-ha-node-name";
    private static final String COLUMN_CHANGESET = "signer-add-ha-node-name-columns";
    private static final String SEED_NODE = "seeded";
    private static final List<String> SIGNER_TABLES = List.of(
            "signer_tokens", "signer_keys", "signer_certificates", "signer_certificate_requests");
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private String url;

    @BeforeEach
    void createDatabase() throws Exception {
        SharedPostgres.start();
        var container = SharedPostgres.CONTAINER;
        var database = "signer_node_name_backfill_" + DATABASE_COUNTER.incrementAndGet();
        try (var connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database);
            statement.execute("CREATE DATABASE " + database);
        }
        url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/" + database;
        dataSource = new SingleConnectionDataSource(url, container.getUsername(), container.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");
    }

    @AfterEach
    void closeConnection() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void backfillWritesNodeNameIntoExistingRowsOfAllFourTables() throws Exception {
        seedBeforeBackfill("centerui");

        assertThat(execute("centerui", "node_a")).isZero();

        assertThat(backfillApplied()).isTrue();
        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly("node_a"));
    }

    @Test
    void laterRunWithAnotherNameKeepsTheFirstName() throws Exception {
        seedBeforeBackfill("centerui");
        assertThat(execute("centerui", "node_a")).isZero();
        var checksum = backfillChecksum();
        insertToken("later_token", "node_c");

        assertThat(execute("centerui", "node_b")).isZero();

        assertThat(nodeNames("signer_tokens")).containsExactlyInAnyOrder("node_a", "node_c");
        assertThat(nodeNames("signer_keys")).containsExactly("node_a");
        assertThat(nodeNames("signer_certificates")).containsExactly("node_a");
        assertThat(nodeNames("signer_certificate_requests")).containsExactly("node_a");
        assertThat(backfillChecksum()).isEqualTo(checksum);
    }

    @Test
    void laterRunWithBlankOrNoNameKeepsTheFirstName() throws Exception {
        seedBeforeBackfill("centerui");
        assertThat(execute("centerui", "node_a")).isZero();

        assertThat(execute("centerui", "")).isZero();
        assertThat(execute("centerui", null)).isZero();

        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly("node_a"));
    }

    @Test
    void sameNameRunAgainChangesNothing() throws Exception {
        seedBeforeBackfill("centerui");
        assertThat(execute("centerui", "node_a")).isZero();
        var checksum = backfillChecksum();
        insertToken("later_token", "node_c");

        assertThat(execute("centerui", "node_a")).isZero();

        assertThat(nodeNames("signer_tokens")).containsExactlyInAnyOrder("node_a", "node_c");
        assertThat(backfillChecksum()).isEqualTo(checksum);
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog where id = ?", Integer.class,
                BACKFILL_CHANGESET)).isEqualTo(1);
    }

    @Test
    void runWithoutNameWritesTheDefaultName() throws Exception {
        seedBeforeBackfill("centerui");

        assertThat(execute("centerui", null)).isZero();

        assertThat(backfillApplied()).isTrue();
        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly("node_0"));
    }

    @Test
    void blankNameHaltsAndWritesNothing() throws Exception {
        seedBeforeBackfill("centerui");

        assertThat(execute("centerui", "")).isNotZero();

        assertThat(backfillApplied()).isFalse();
        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly(SEED_NODE));
    }

    @Test
    void rowsWithoutColumnGetTheNodeNameFromTheBackfill() throws Exception {
        migrateBefore("centerui", COLUMN_CHANGESET);
        seedRowsWithoutColumn();

        assertThat(execute("centerui", "node_a")).isZero();

        assertThat(backfillApplied()).isTrue();
        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly("node_a"));
    }

    @Test
    void rowsWithoutColumnGetTheDefaultNameWhenNoNameIsGiven() throws Exception {
        migrateBefore("centerui", COLUMN_CHANGESET);
        seedRowsWithoutColumn();

        assertThat(execute("centerui", null)).isZero();

        assertThat(backfillApplied()).isTrue();
        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly("node_0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"serverconf", "confproxy"})
    void changelogsWithoutNameLeaveRowsAtDefaultName(String changelog) throws Exception {
        seedBeforeBackfill(changelog);

        assertThat(execute(changelog, null)).isZero();

        assertThat(backfillApplied()).isTrue();
        SIGNER_TABLES.forEach(table -> assertThat(nodeNames(table)).as(table).containsExactly("node_0"));
    }

    private void seedBeforeBackfill(String changelog) throws Exception {
        migrateBefore(changelog, BACKFILL_CHANGESET);
        seedRows();
    }

    private void migrateBefore(String changelog, String changeSetId) throws Exception {
        var changelogFile = "liquibase/" + changelog + "-changelog.xml";
        var accessor = new ClassLoaderResourceAccessor();
        var parsed = ChangeLogParserFactory.getInstance().getParser(changelogFile, accessor)
                .parse(changelogFile, new ChangeLogParameters(), accessor);
        var ids = parsed.getChangeSets().stream().map(ChangeSet::getId).toList();
        assertThat(ids).contains(changeSetId);

        Scope.child(Scope.Attr.resourceAccessor, accessor, () -> {
            var update = new CommandScope("updateCount");
            update.addArgumentValue("changelogFile", changelogFile);
            update.addArgumentValue("url", url);
            update.addArgumentValue("username", SharedPostgres.CONTAINER.getUsername());
            update.addArgumentValue("password", SharedPostgres.CONTAINER.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.addArgumentValue("count", ids.indexOf(changeSetId));
            update.execute();
        });
        assertThat(backfillApplied()).isFalse();
    }

    private int execute(String changelog, String nodeName) throws Exception {
        var args = new ArrayList<>(List.of(
                "--changelog=" + changelog,
                "--url=" + url,
                "--username=" + SharedPostgres.CONTAINER.getUsername(),
                "--contexts=!admin"));
        if (nodeName != null) {
            args.add("--prop-ha-node-name=" + nodeName);
        }
        args.add("update");
        return Scope.child(Map.of("liquibase.command.password", SharedPostgres.CONTAINER.getPassword()),
                () -> new CommandLine(new LiquibaseExecutor()).execute(args.toArray(String[]::new)));
    }

    private void seedRows() {
        insertToken("seed_token", SEED_NODE);
        jdbc.update("insert into signer_keys (external_id, token_id, type, sign_mechanism_name, created_at, "
                + "updated_at, ha_node_name) select 'seed_key', id, 'SIGN', 'CKM_RSA_PKCS', now(), now(), ? "
                + "from signer_tokens where external_id = 'seed_token'", SEED_NODE);
        jdbc.update("insert into signer_certificates (external_id, key_id, data, created_at, updated_at, "
                + "ha_node_name) select 'seed_cert', id, '\\x00', now(), now(), ? "
                + "from signer_keys where external_id = 'seed_key'", SEED_NODE);
        jdbc.update("insert into signer_certificate_requests (external_id, key_id, subject_name, created_at, "
                + "updated_at, ha_node_name) select 'seed_request', id, 'CN=seed', now(), now(), ? "
                + "from signer_keys where external_id = 'seed_key'", SEED_NODE);
    }

    private void seedRowsWithoutColumn() {
        jdbc.update("insert into signer_tokens (external_id, type, created_at, updated_at) "
                + "values ('seed_token', 'softToken', now(), now())");
        jdbc.update("insert into signer_keys (external_id, token_id, type, sign_mechanism_name, created_at, "
                + "updated_at) select 'seed_key', id, 'SIGN', 'CKM_RSA_PKCS', now(), now() "
                + "from signer_tokens where external_id = 'seed_token'");
        jdbc.update("insert into signer_certificates (external_id, key_id, data, created_at, updated_at) "
                + "select 'seed_cert', id, '\\x00', now(), now() from signer_keys where external_id = 'seed_key'");
        jdbc.update("insert into signer_certificate_requests (external_id, key_id, subject_name, created_at, "
                + "updated_at) select 'seed_request', id, 'CN=seed', now(), now() "
                + "from signer_keys where external_id = 'seed_key'");
    }

    private void insertToken(String externalId, String nodeName) {
        jdbc.update("insert into signer_tokens (external_id, type, created_at, updated_at, ha_node_name) "
                + "values (?, 'softToken', now(), now(), ?)", externalId, nodeName);
    }

    private List<String> nodeNames(String table) {
        return jdbc.queryForList("select ha_node_name from " + table, String.class);
    }

    private boolean backfillApplied() {
        return jdbc.queryForObject("select count(*) from databasechangelog where id = ?", Integer.class,
                BACKFILL_CHANGESET) > 0;
    }

    private String backfillChecksum() {
        return jdbc.queryForObject("select md5sum from databasechangelog where id = ?", String.class,
                BACKFILL_CHANGESET);
    }
}
