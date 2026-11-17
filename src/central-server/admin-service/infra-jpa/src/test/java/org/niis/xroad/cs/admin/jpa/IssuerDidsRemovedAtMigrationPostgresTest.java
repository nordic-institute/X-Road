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
 * including the {@code issuer-dids-identity-columns} changeset, then a bound row seeded the way a pre-removal
 * deployment would have it, then the rest including the {@code removed_at} changeset. Proves a bound row from
 * before the changeset is left unchanged, with {@code removed_at} unset.
 */
class IssuerDidsRemovedAtMigrationPostgresTest {

    private static final String CHANGELOG = "liquibase/centerui-changelog.xml";
    private static final String PRIOR_CHANGESET = "202611091200-issuer-dids-identity-columns";
    private static final String FEATURE_CHANGESET = "202611171200-issuer-dids-removed-at";
    private static final String DATABASE = "issuer_dids_removed_at_migration";
    private static final String BOUND_DID = "did:web:cs.bound%3A6183:issuer";

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabaseWithABoundRow() throws Exception {
        SharedPostgres.start();
        var url = createDatabase();
        var container = SharedPostgres.CONTAINER;
        dataSource = new SingleConnectionDataSource(url, container.getUsername(), container.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS hstore");

        update(url, changeSetsBefore(FEATURE_CHANGESET));
        assertThat(appliedChangeSets()).contains(PRIOR_CHANGESET).doesNotContain(FEATURE_CHANGESET);
        seedBoundRow();

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
    void boundRowIsUnchangedWithRemovedAtUnset() {
        var rows = jdbc.queryForList("select did, host, removed_at from issuer_dids where did = ?", BOUND_DID);

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.get("did")).isEqualTo(BOUND_DID);
        assertThat(row.get("host")).isEqualTo("cs.bound");
        assertThat(row.get("removed_at")).isNull();
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

    private static void seedBoundRow() {
        jdbc.update("""
                insert into issuer_dids (did, host, did_port, issuance_url, statuslist_base_url, created_at, updated_at)
                values (?, ?, ?, ?, ?, now(), now())
                """,
                BOUND_DID, "cs.bound", 6183,
                "https://cs.bound:6185/api/issuance/v1/participants/issuer",
                "https://cs.bound:6187/statuslist");
    }
}
