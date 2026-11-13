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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the real {@code centerui-changelog.xml} against a database of its own to prove the
 * {@code ds-issuer-service} grant changeset: a no-op, without error, while that role does not exist;
 * schema usage plus read access on {@code issuer_dids}, {@code system_parameters} and
 * {@code configuration_properties} once it does; a re-run changes nothing; and the grant returns
 * after something revokes it, as a restored dump does (it carries the schema and the Liquibase
 * tracking tables, but not privileges).
 */
class IssuerServiceGrantsMigrationPostgresTest {

    private static final String CHANGELOG = "liquibase/centerui-changelog.xml";
    private static final String FEATURE_CHANGESET = "202611131200-ds-issuer-service-grants";
    private static final String DATABASE = "issuer_service_grants_migration";
    private static final String SCHEMA = "public";
    private static final String ROLE = "ds-issuer-service";

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static String url;

    @BeforeAll
    static void migrateUpToAndIncludingTheGrantChangeset() throws Exception {
        SharedPostgres.start();
        url = createDatabase();
        var container = SharedPostgres.CONTAINER;
        dataSource = new SingleConnectionDataSource(url, container.getUsername(), container.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");

        update(changeSetCountThrough(FEATURE_CHANGESET));
    }

    @AfterAll
    static void closeConnection() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    /**
     * One sequential scenario, not several independent tests: each step's assertion depends on the
     * database state the previous step left, so splitting it across methods would make the outcome
     * depend on JUnit's (random) method execution order.
     */
    @Test
    void grantsFollowTheRoleAcrossMigrationsAndARestore() throws Exception {
        assertThat(appliedChangeSets()).contains(FEATURE_CHANGESET);
        assertThat(roleExists()).isFalse();

        createRole();
        assertThat(hasReaderGrants()).isFalse();

        update(null);
        assertThat(hasReaderGrants()).isTrue();

        update(null);
        assertThat(hasReaderGrants()).isTrue();

        revokeReaderGrants();
        assertThat(hasReaderGrants()).isFalse();

        update(null);
        assertThat(hasReaderGrants()).isTrue();
    }

    private static String createDatabase() throws Exception {
        var container = SharedPostgres.CONTAINER;
        try (var connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
        return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/" + DATABASE;
    }

    private static int changeSetCountThrough(String changeSetId) throws Exception {
        var accessor = new ClassLoaderResourceAccessor();
        var changeLog = ChangeLogParserFactory.getInstance().getParser(CHANGELOG, accessor)
                .parse(CHANGELOG, new ChangeLogParameters(), accessor);
        var ids = changeLog.getChangeSets().stream().map(ChangeSet::getId).toList();
        assertThat(ids).contains(changeSetId);
        return ids.indexOf(changeSetId) + 1;
    }

    private static void update(Integer count) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope(count == null ? "update" : "updateCount");
            var changeLogParameters = new ChangeLogParameters();
            changeLogParameters.set("db_schema", SCHEMA);
            update.addArgumentValue("changelogFile", CHANGELOG);
            update.addArgumentValue("url", url);
            update.addArgumentValue("username", SharedPostgres.CONTAINER.getUsername());
            update.addArgumentValue("password", SharedPostgres.CONTAINER.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.addArgumentValue("changelogParameters", changeLogParameters);
            if (count != null) {
                update.addArgumentValue("count", count);
            }
            update.execute();
        });
    }

    private static List<String> appliedChangeSets() {
        return jdbc.queryForList("select id from databasechangelog", String.class);
    }

    private static void createRole() {
        jdbc.execute("CREATE ROLE \"" + ROLE + "\" LOGIN PASSWORD 'test'");
    }

    private static boolean roleExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pg_roles where rolname = '" + ROLE + "')", Boolean.class));
    }

    private static boolean hasReaderGrants() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select has_schema_privilege('" + ROLE + "', '" + SCHEMA + "', 'USAGE') "
                        + "and has_table_privilege('" + ROLE + "', '" + SCHEMA + ".issuer_dids', 'SELECT') "
                        + "and has_table_privilege('" + ROLE + "', '" + SCHEMA + ".system_parameters', 'SELECT') "
                        + "and has_table_privilege('" + ROLE + "', '" + SCHEMA + ".configuration_properties', 'SELECT')",
                Boolean.class));
    }

    private static void revokeReaderGrants() {
        jdbc.execute("revoke select on \"" + SCHEMA + "\".issuer_dids, \"" + SCHEMA + "\".system_parameters, "
                + "\"" + SCHEMA + "\".configuration_properties from \"" + ROLE + "\"");
        jdbc.execute("revoke usage on schema \"" + SCHEMA + "\" from \"" + ROLE + "\"");
    }
}
