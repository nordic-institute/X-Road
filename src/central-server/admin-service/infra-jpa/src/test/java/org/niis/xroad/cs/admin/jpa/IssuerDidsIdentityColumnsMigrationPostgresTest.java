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
 * Applies the real {@code centerui-changelog.xml} in two steps on a database of its own: everything up to and
 * including the original {@code issuer_dids} changeset (its old, pre-identity-columns shape), then a seeded
 * leftover row, then the rest. Proves the original changeset's checksum still matches and that the new
 * changeset clears the leftover row and leaves the identity columns NOT NULL.
 */
class IssuerDidsIdentityColumnsMigrationPostgresTest {

    private static final String CHANGELOG = "liquibase/centerui-changelog.xml";
    private static final String FEATURE_CHANGESET = "202611091200-issuer-dids-identity-columns";
    private static final String DATABASE = "issuer_dids_identity_columns_migration";

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabaseWithLeftoverRow() throws Exception {
        SharedPostgres.start();
        var url = createDatabase();
        var container = SharedPostgres.CONTAINER;
        dataSource = new SingleConnectionDataSource(url, container.getUsername(), container.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");

        update(url, changeSetsBefore(FEATURE_CHANGESET));
        assertThat(appliedChangeSets()).isNotEmpty().doesNotContain(FEATURE_CHANGESET);
        seedLeftoverRow();

        update(url, null);
        assertThat(appliedChangeSets()).contains(FEATURE_CHANGESET);
    }

    @AfterAll
    static void closeConnection() {
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void leftoverRowIsGone() {
        assertThat(jdbc.queryForObject("select count(*) from issuer_dids", Integer.class)).isZero();
    }

    @Test
    void identityColumnsAreNotNull() {
        var columns = jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_name = 'issuer_dids' and is_nullable = 'NO' "
                + "and column_name in ('host', 'did_port', 'issuance_url', 'statuslist_base_url')");

        assertThat(columns).extracting(row -> row.get("column_name"))
                .containsExactlyInAnyOrder("host", "did_port", "issuance_url", "statuslist_base_url");
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

    private static int changeSetsBefore(String changeSetId) throws Exception {
        var accessor = new ClassLoaderResourceAccessor();
        var changeLog = ChangeLogParserFactory.getInstance().getParser(CHANGELOG, accessor)
                .parse(CHANGELOG, new ChangeLogParameters(), accessor);
        var ids = changeLog.getChangeSets().stream().map(ChangeSet::getId).toList();
        assertThat(ids).contains(changeSetId);
        return ids.indexOf(changeSetId);
    }

    private static void update(String url, Integer count) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope(count == null ? "update" : "updateCount");
            update.addArgumentValue("changelogFile", CHANGELOG);
            update.addArgumentValue("url", url);
            update.addArgumentValue("username", SharedPostgres.CONTAINER.getUsername());
            update.addArgumentValue("password", SharedPostgres.CONTAINER.getPassword());
            update.addArgumentValue("contexts", "!admin");
            if (count != null) {
                update.addArgumentValue("count", count);
            }
            update.execute();
        });
    }

    private static List<String> appliedChangeSets() {
        return jdbc.queryForList("select id from databasechangelog", String.class);
    }

    private static void seedLeftoverRow() {
        jdbc.update("insert into issuer_dids (did, created_at, updated_at) "
                + "values ('did:web:cs.leftover%3A6183:issuer', now(), now())");
    }
}
