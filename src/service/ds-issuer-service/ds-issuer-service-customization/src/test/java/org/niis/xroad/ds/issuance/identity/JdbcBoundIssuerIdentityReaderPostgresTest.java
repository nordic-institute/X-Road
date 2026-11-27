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
package org.niis.xroad.ds.issuance.identity;

import liquibase.Scope;
import liquibase.changelog.ChangeLogParameters;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ds.identity.IssuerDidFormat;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code centerui-changelog.xml} against a Postgres testcontainer, connects the reader as a
 * {@code ds-issuer-service} role carrying exactly the read grants {@code setup_ds_issuerservice_db.sh}
 * gives that role on the Central Server schema, and proves the reader answers from that schema alone.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * instead of via {@code @Testcontainers}/{@code @Container}: that extension starts the container
 * before a {@code @BeforeAll} can check Docker availability, so a Docker-less run would fail instead
 * of skip. Prior art: {@code IssuerDidPostgresTest}.
 */
class JdbcBoundIssuerIdentityReaderPostgresTest {

    private static final String SCHEMA = "centerui";
    private static final String ROLE = "ds-issuer-service";
    private static final String ROLE_PASSWORD = "ds-issuer-service-test-password";

    private static final String HOST = "cs.example";
    private static final int LISTEN_DID_PORT = 6183;
    private static final int PUBLISHED_DID_PORT = 7183;
    private static final String ISSUANCE_URL = "https://cs.example:6185/api/issuance/v1/participants/issuer";
    private static final String STATUSLIST_BASE_URL = "https://cs.example:6187/statuslist";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private final RecordingMonitor monitor = new RecordingMonitor();

    @BeforeAll
    static void migrateAndGrant() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping the Postgres-backed integration test");
        POSTGRES.start();
        createHstoreExtension();
        createCenteruiSchema();
        createRole();
        applyCenteruiChangelog();
    }

    @AfterAll
    static void disconnect() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void clearRows() throws Exception {
        try (var connection = superuserConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM system_parameters WHERE key = 'issuerServiceHost'");
            statement.execute("DELETE FROM configuration_properties WHERE property_key = 'xroad.dataspace.issuer.did-port'");
            statement.execute("DELETE FROM issuer_dids");
        }
    }

    @Test
    void unboundWithNoIssuerDidsRow() throws Exception {
        setHost(HOST);
        var rebuiltDid = IssuerDidFormat.didFor(HOST, LISTEN_DID_PORT);

        try (var reader = reader()) {
            assertThat(reader.read()).isEmpty();
        }

        assertThat(monitor.warnings).hasSize(1);
        assertThat(monitor.warnings.getFirst()).contains(rebuiltDid);
    }

    @Test
    void warnsOnceWhenTheSameMismatchPersistsAcrossRepeatedReads() throws Exception {
        setHost(HOST);

        try (var reader = reader()) {
            assertThat(reader.read()).isEmpty();
            assertThat(reader.read()).isEmpty();
        }

        assertThat(monitor.warnings).hasSize(1);
    }

    @Test
    void returnsTheBoundIdentityOnceARowWithTheRebuiltDidExists() throws Exception {
        setHost(HOST);
        var did = IssuerDidFormat.didFor(HOST, LISTEN_DID_PORT);
        insertIssuerDid(did, HOST, LISTEN_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);

        try (var reader = reader()) {
            assertThat(reader.read()).contains(new BoundIssuerIdentity(did, HOST, LISTEN_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL));
        }
    }

    @Test
    void unboundWhenNoIssuerHostIsSet() throws Exception {
        var did = IssuerDidFormat.didFor(HOST, LISTEN_DID_PORT);
        insertIssuerDid(did, HOST, LISTEN_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);

        try (var reader = reader()) {
            assertThat(reader.read()).isEmpty();
        }

        assertThat(monitor.warnings).isEmpty();
    }

    @Test
    void rebuildsTheDidFromThePublishedDidPortNotTheListenPort() throws Exception {
        setHost(HOST);
        setDidPort(PUBLISHED_DID_PORT);
        var did = IssuerDidFormat.didFor(HOST, PUBLISHED_DID_PORT);
        insertIssuerDid(did, HOST, PUBLISHED_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);

        try (var reader = reader()) {
            assertThat(reader.read()).contains(new BoundIssuerIdentity(did, HOST, PUBLISHED_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL));
        }
    }

    @Test
    void returnsOnlyTheRowMatchingItsRebuiltDidWhenAnotherIssuerRowExists() throws Exception {
        setHost(HOST);
        setDidPort(PUBLISHED_DID_PORT);
        var otherDid = IssuerDidFormat.didFor("other.example", LISTEN_DID_PORT);
        insertIssuerDid(otherDid, "other.example", LISTEN_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
        var matchingDid = IssuerDidFormat.didFor(HOST, PUBLISHED_DID_PORT);
        insertIssuerDid(matchingDid, HOST, PUBLISHED_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);

        try (var reader = reader()) {
            assertThat(reader.read())
                    .contains(new BoundIssuerIdentity(matchingDid, HOST, PUBLISHED_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL));
        }
    }

    @Test
    void unboundWhenOnlyTheOtherIssuerRowExists() throws Exception {
        setHost(HOST);
        setDidPort(PUBLISHED_DID_PORT);
        var otherDid = IssuerDidFormat.didFor("other.example", LISTEN_DID_PORT);
        insertIssuerDid(otherDid, "other.example", LISTEN_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);

        try (var reader = reader()) {
            assertThat(reader.read()).isEmpty();
        }
    }

    @Test
    void unboundWhenTheOnlyMatchingRowIsRemoved() throws Exception {
        setHost(HOST);
        var did = IssuerDidFormat.didFor(HOST, LISTEN_DID_PORT);
        insertIssuerDid(did, HOST, LISTEN_DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
        markIssuerDidRemoved(did);

        try (var reader = reader()) {
            assertThat(reader.read()).isEmpty();
        }
    }

    private JdbcBoundIssuerIdentityReader reader() {
        return new JdbcBoundIssuerIdentityReader(POSTGRES.getJdbcUrl(), ROLE, ROLE_PASSWORD, SCHEMA, monitor);
    }

    private static final class RecordingMonitor implements Monitor {
        private final List<String> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void warning(Supplier<String> supplier, Throwable... errors) {
            warnings.add(supplier.get());
        }
    }

    private static void setHost(String host) throws SQLException {
        try (var connection = superuserConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO system_parameters (key, value, ha_node_name, created_at, updated_at) "
                                + "VALUES (?, ?, ?, now(), now())")) {
            statement.setString(1, "issuerServiceHost");
            statement.setString(2, host);
            statement.setString(3, "node_0");
            statement.executeUpdate();
        }
    }

    private static void setDidPort(int port) throws SQLException {
        try (var connection = superuserConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at) "
                                + "VALUES (?, ?, now(), now())")) {
            statement.setString(1, "xroad.dataspace.issuer.did-port");
            statement.setString(2, String.valueOf(port));
            statement.executeUpdate();
        }
    }

    private static void insertIssuerDid(String did, String host, int didPort, String issuanceUrl, String statuslistBaseUrl)
            throws SQLException {
        try (var connection = superuserConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO issuer_dids (did, host, did_port, issuance_url, statuslist_base_url, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, now(), now())")) {
            statement.setString(1, did);
            statement.setString(2, host);
            statement.setInt(3, didPort);
            statement.setString(4, issuanceUrl);
            statement.setString(5, statuslistBaseUrl);
            statement.executeUpdate();
        }
    }

    private static void markIssuerDidRemoved(String did) throws SQLException {
        try (var connection = superuserConnection();
                var statement = connection.prepareStatement(
                        "UPDATE issuer_dids SET removed_at = now() WHERE did = ?")) {
            statement.setString(1, did);
            statement.executeUpdate();
        }
    }

    private static Connection superuserConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrlWithSchema(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String jdbcUrlWithSchema() {
        var url = POSTGRES.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public";
    }

    /**
     * Creates the {@code ds-issuer-service} role before the changelog runs, so the real
     * {@code ds-issuer-service-grants} changeset's role-exists precondition passes and it issues the
     * schema usage and read grants itself.
     */
    private static void createRole() throws SQLException {
        try (var connection = superuserConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE \"%s\" LOGIN PASSWORD '%s'".formatted(ROLE, ROLE_PASSWORD));
        }
    }

    /**
     * The production database image provisions the {@code hstore} extension outside Liquibase; this
     * testcontainer needs it created explicitly before {@code centerui-changelog.xml} can run, since
     * {@code 001-baseline.xml} declares a column of that type.
     */
    private static void createHstoreExtension() throws SQLException {
        try (var connection = superuserConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS hstore");
        }
    }

    /**
     * Production runs the centerui changelog against a named schema, never {@code public}
     * ({@code setup_centerui_db.sh}'s {@code --defaultSchemaName}); Liquibase does not create that
     * schema itself.
     */
    private static void createCenteruiSchema() throws SQLException {
        try (var connection = superuserConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS \"%s\"".formatted(SCHEMA));
        }
    }

    /**
     * Applies the same {@code centerui-changelog.xml} production deployments run, against the
     * {@code centerui} schema (not {@code public}), excluding only the last changeset
     * ({@code separate-admin-user}): it {@code GRANT}/{@code REVOKE}s permissions between two
     * Postgres roles, a split this single-role testcontainer has no use for. The
     * {@code ds-issuer-service-grants} changeset runs for real here, since {@link #createRole()}
     * creates the {@code ds-issuer-service} role before this call.
     */
    private static void applyCenteruiChangelog() throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            var changeLogParameters = new ChangeLogParameters();
            changeLogParameters.set("db_schema", SCHEMA);
            update.addArgumentValue("changelogFile", "liquibase/centerui-changelog.xml");
            update.addArgumentValue("url", jdbcUrlWithSchema());
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("defaultSchemaName", SCHEMA);
            update.addArgumentValue("contexts", "!admin");
            update.addArgumentValue("changelogParameters", changeLogParameters);
            update.execute();
        });
    }
}
