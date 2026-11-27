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
package org.niis.xroad.migration.signer;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.migration.utils.DbCredentials;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Calls the token PIN write of {@link SignerRepository} directly against a real PostgreSQL database that holds the
 * schema of the real {@code serverconf-changelog.xml}.
 */
class SignerRepositoryPostgresTest {

    private static final String TEMPLATE_DATABASE = "signer_repository_template";
    private static final String TOKEN_PIN_HEX = "SELECT encode(pin, 'hex') FROM signer_tokens "
            + "WHERE ha_node_name = '%s' AND external_id = '0'";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    private String database;

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
        database = "signer_repository_" + DATABASE_COUNTER.incrementAndGet();
        execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + database + " TEMPLATE " + TEMPLATE_DATABASE);
    }

    @Test
    void shouldSetThePinWhereItIsNull() throws Exception {
        insertToken("node_b", "NULL");

        boolean changed = repositoryFor("node_b").setTokenPinIfEmpty(tokenId("node_b"), new byte[] {1, 2, 3});

        assertThat(changed).isTrue();
        assertThat(queryStrings(TOKEN_PIN_HEX.formatted("node_b"))).containsExactly("010203");
    }

    @Test
    void shouldNotOverwriteAnExistingPinHash() throws Exception {
        insertToken("node_b", "decode('abcdef', 'hex')");

        boolean changed = repositoryFor("node_b").setTokenPinIfEmpty(tokenId("node_b"), new byte[] {1, 2, 3});

        assertThat(changed).isFalse();
        assertThat(queryStrings(TOKEN_PIN_HEX.formatted("node_b"))).containsExactly("abcdef");
    }

    @Test
    void shouldNotTouchAnotherNodesRowWhenGivenItsTokenId() throws Exception {
        insertToken("node_a", "NULL");
        insertToken("node_b", "NULL");
        List<String> nodeARows = queryStrings("SELECT to_jsonb(t)::text FROM signer_tokens t "
                + "WHERE ha_node_name = 'node_a'");

        boolean changed = repositoryFor("node_b").setTokenPinIfEmpty(tokenId("node_a"), new byte[] {1, 2, 3});

        assertThat(changed).isFalse();
        assertThat(queryStrings("SELECT to_jsonb(t)::text FROM signer_tokens t WHERE ha_node_name = 'node_a'"))
                .isEqualTo(nodeARows);
    }

    private SignerRepository repositoryFor(String node) {
        var credentials = new DbCredentials(url(database), POSTGRES.getUsername(),
                POSTGRES.getPassword().toCharArray(), null);
        return new SignerRepository(credentials, NodeIdentity.resolve(node));
    }

    private void insertToken(String node, String pinSql) throws SQLException {
        execute(database, "INSERT INTO signer_tokens(ha_node_name, external_id, type, pin) "
                + "VALUES ('" + node + "', '0', 'softToken', " + pinSql + ")");
    }

    private long tokenId(String node) {
        return Long.parseLong(queryStrings("SELECT id FROM signer_tokens WHERE ha_node_name = '" + node
                + "' AND external_id = '0'").get(0));
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
