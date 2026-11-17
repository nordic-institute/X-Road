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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.dataspace.IssuerSchemaTestSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies the real {@code centerui-changelog.xml} and {@code ds-issuer-service-changelog.xml} against a
 * database of their own to prove the {@code 202611161430-ds-issuer-service-keypair-grant} changeset: a
 * no-op, without error, while the Issuer Service's {@code keypair_resource} table does not exist yet (the
 * order a fresh install or an upgrade from an X-Road 7 database may apply the two changelogs in); schema
 * usage plus read access on only that table once the table exists; nothing else granted in that schema; a
 * re-run changes nothing; and the grant returns after something revokes it, as a restored dump does (it
 * carries the schema and the Liquibase tracking tables, but not privileges).
 */
class IssuerServiceKeypairGrantMigrationPostgresTest {

    private static final String CENTERUI_CHANGELOG = "liquibase/centerui-changelog.xml";
    private static final String FEATURE_CHANGESET = "202611161430-ds-issuer-service-keypair-grant";
    private static final String DATABASE = "issuer_keypair_grant_migration";
    private static final String CENTERUI_SCHEMA = "centerui";
    private static final String ISSUER_SCHEMA = "ds-issuer-service";
    private static final String ROLE = "centerui";

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static String url;

    @BeforeAll
    static void createDatabaseAndRoleThenMigrateCenteruiThroughTheGrantChangeset() throws Exception {
        SharedPostgres.start();
        url = createDatabase();
        var container = SharedPostgres.CONTAINER;
        dataSource = new SingleConnectionDataSource(url, container.getUsername(), container.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"" + CENTERUI_SCHEMA + "\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"" + ISSUER_SCHEMA + "\"");
        createRole();

        updateCenterui(changeSetCountThrough(FEATURE_CHANGESET));
    }

    @AfterAll
    static void dropRoleAndCloseConnection() {
        if (dataSource != null) {
            jdbc.execute("DROP OWNED BY \"" + ROLE + "\"");
            jdbc.execute("DROP ROLE \"" + ROLE + "\"");
            dataSource.destroy();
        }
    }

    /**
     * One sequential scenario, not several independent tests: each step's assertion depends on the
     * database state the previous step left, so splitting it across methods would make the outcome
     * depend on JUnit's (random) method execution order.
     */
    @Test
    void grantAppearsAfterTheInstallScriptGrantsItAndSurvivesACenteruiReRunAndARestore() throws Exception {
        assertThat(appliedCenteruiChangeSets()).contains(FEATURE_CHANGESET);
        assertThat(hasReaderGrant()).isFalse();

        IssuerSchemaTestSupport.applyIssuerServiceChangelog(issuerSchemaUrl(), SharedPostgres.CONTAINER.getUsername(),
                SharedPostgres.CONTAINER.getPassword(), ISSUER_SCHEMA);
        grantReaderAccessTheWayTheInstallScriptDoes();
        assertThat(hasReaderGrant()).isTrue();
        assertOnlyKeypairResourceIsReadable();

        updateCenterui(null);
        assertThat(hasReaderGrant()).isTrue();

        revokeReaderGrant();
        assertThat(hasReaderGrant()).isFalse();

        updateCenterui(null);
        assertThat(hasReaderGrant()).isTrue();
    }

    private static String createDatabase() throws Exception {
        var container = SharedPostgres.CONTAINER;
        try (var connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/" + DATABASE
                + "?currentSchema=" + CENTERUI_SCHEMA + "," + ISSUER_SCHEMA + ",public";
    }

    private static int changeSetCountThrough(String changeSetId) throws Exception {
        var accessor = new ClassLoaderResourceAccessor();
        var changeLog = ChangeLogParserFactory.getInstance().getParser(CENTERUI_CHANGELOG, accessor)
                .parse(CENTERUI_CHANGELOG, new ChangeLogParameters(), accessor);
        var ids = changeLog.getChangeSets().stream().map(ChangeSet::getId).toList();
        assertThat(ids).contains(changeSetId);
        return ids.indexOf(changeSetId) + 1;
    }

    private static void updateCenterui(Integer count) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope(count == null ? "update" : "updateCount");
            var changeLogParameters = new ChangeLogParameters();
            changeLogParameters.set("db_schema", CENTERUI_SCHEMA);
            changeLogParameters.set("db_user", ROLE);
            update.addArgumentValue("changelogFile", CENTERUI_CHANGELOG);
            update.addArgumentValue("url", url);
            update.addArgumentValue("username", SharedPostgres.CONTAINER.getUsername());
            update.addArgumentValue("password", SharedPostgres.CONTAINER.getPassword());
            update.addArgumentValue("defaultSchemaName", CENTERUI_SCHEMA);
            update.addArgumentValue("contexts", "!admin");
            update.addArgumentValue("changelogParameters", changeLogParameters);
            if (count != null) {
                update.addArgumentValue("count", count);
            }
            update.execute();
        });
    }

    /**
     * Builds the URL the Issuer Service changelog is applied through: its own {@code currentSchema},
     * matching {@code setup_ds_issuerservice_db.sh}'s own JDBC URL, rather than the shared {@link #url}
     * whose {@code currentSchema} list starts with {@value #CENTERUI_SCHEMA}. That changelog creates the
     * Issuer Service schema's tables, {@code keypair_resource} included, the way
     * {@code setup_ds_issuerservice_db.sh} does on a fresh install, or the way an X-Road 7 upgrade creates
     * them the first time the Issuer Service is set up on it: independently of, and in no fixed order
     * relative to, the centerui migration run above.
     */
    private static String issuerSchemaUrl() {
        var container = SharedPostgres.CONTAINER;
        var baseUrl = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/" + DATABASE;
        return IssuerSchemaTestSupport.issuerSchemaUrl(baseUrl, ISSUER_SCHEMA);
    }

    private static List<String> appliedCenteruiChangeSets() {
        return jdbc.queryForList("select id from \"" + CENTERUI_SCHEMA + "\".databasechangelog", String.class);
    }

    private static void createRole() {
        jdbc.execute("CREATE ROLE \"" + ROLE + "\" LOGIN PASSWORD 'test'");
    }

    private static boolean hasReaderGrant() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select has_schema_privilege('" + ROLE + "', '" + ISSUER_SCHEMA + "', 'USAGE') "
                        + "and has_table_privilege('" + ROLE + "', '" + ISSUER_SCHEMA + ".keypair_resource', 'SELECT')",
                Boolean.class));
    }

    /**
     * The same two statements {@code setup_ds_issuerservice_db.sh} issues right after applying the Issuer
     * Service changelog, on both a fresh install and an X-Road 7 upgrade: that script, not this centerui
     * changeset, is what actually grants the role on those two paths.
     */
    private static void grantReaderAccessTheWayTheInstallScriptDoes() {
        jdbc.execute("GRANT USAGE ON SCHEMA \"" + ISSUER_SCHEMA + "\" TO \"" + ROLE + "\"");
        jdbc.execute("GRANT SELECT ON \"" + ISSUER_SCHEMA + "\".keypair_resource TO \"" + ROLE + "\"");
    }

    /**
     * Connects as the centerui role itself and proves the grant is restricted to {@code keypair_resource}:
     * a {@code SELECT} against every other table {@code pg_tables} lists in the Issuer Service schema must
     * be refused with {@code permission denied} (SQLSTATE {@code 42501}), while a {@code SELECT} against
     * {@code keypair_resource} succeeds. Reads the schema's actual table list instead of a hard-coded one,
     * so an unqualified {@code grant ... on all tables} regression in the changeset would be caught here.
     */
    private static void assertOnlyKeypairResourceIsReadable() throws Exception {
        var otherTables = jdbc.queryForList(
                "select tablename from pg_tables where schemaname = '" + ISSUER_SCHEMA + "' and tablename <> 'keypair_resource'",
                String.class);
        try (var connection = DriverManager.getConnection(url, ROLE, "test")) {
            for (var table : otherTables) {
                try (var statement = connection.createStatement()) {
                    assertThatThrownBy(() -> statement.executeQuery(selectAllFrom(table)))
                            .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("42501"));
                }
            }
            try (var statement = connection.createStatement()) {
                assertThatCode(() -> statement.executeQuery(selectAllFrom("keypair_resource"))).doesNotThrowAnyException();
            }
        }
    }

    private static String selectAllFrom(String table) {
        return "select * from \"" + ISSUER_SCHEMA + "\".\"" + table + "\"";
    }

    private static void revokeReaderGrant() {
        jdbc.execute("revoke select on \"" + ISSUER_SCHEMA + "\".keypair_resource from \"" + ROLE + "\"");
        jdbc.execute("revoke usage on schema \"" + ISSUER_SCHEMA + "\" from \"" + ROLE + "\"");
    }
}
