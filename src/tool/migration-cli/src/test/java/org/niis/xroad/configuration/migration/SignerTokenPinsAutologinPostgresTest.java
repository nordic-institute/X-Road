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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.migration.tokenpin.PinSourceLocations;
import org.niis.xroad.migration.utils.InstallationType;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs {@code signer-token-pins} against a real PostgreSQL database that holds the schema of the real
 * {@code serverconf-changelog.xml} (Security Server) or {@code centerui-changelog.xml} (Central Server), and reads
 * the autologin flag back from {@code configuration_properties}.
 */
@ExtendWith(SystemStubsExtension.class)
class SignerTokenPinsAutologinPostgresTest {

    private static final String AUTOLOGIN_KEY = "xroad.signer.autologin.enabled";
    private static final String PIN_FIXTURE = "fixturepin4711";
    private static final NodeIdentity NODE = new NodeIdentity("node_b", true);

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    private FakeOpenBao openBao;

    @BeforeAll
    static void startDatabase() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        POSTGRES.start();
        for (InstallationType installation : InstallationType.values()) {
            execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + templateOf(installation));
            if (installation == InstallationType.CENTRAL_SERVER) {
                execute(templateOf(installation), "CREATE EXTENSION IF NOT EXISTS hstore");
            }
            migrateSchema(templateOf(installation), changelogOf(installation));
        }
    }

    @AfterAll
    static void stopDatabase() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void startFakeOpenBao() throws IOException {
        openBao = new FakeOpenBao();
        openBao.pointEnvironmentAt(envVars);
    }

    @AfterEach
    void stopFakeOpenBao() {
        openBao.close();
    }

    @ParameterizedTest
    @EnumSource(InstallationType.class)
    void shouldEnableAutologinWhenAPinIsStored(InstallationType installation) throws Exception {
        String database = freshDatabase(installation);
        Path dbProperties = writeDbProperties(installation, database);

        migrate(installation, dbProperties, pinFile(PIN_FIXTURE + "\n"));

        assertThat(openBao.store()).containsOnlyKeys("signer/token-pins/node_b/0");
        assertThat(autologinFlag(database)).containsExactly("true");
    }

    @ParameterizedTest
    @EnumSource(InstallationType.class)
    void shouldLeaveAutologinUnchangedWhenTheRerunStoresNothing(InstallationType installation) throws Exception {
        String database = freshDatabase(installation);
        Path dbProperties = writeDbProperties(installation, database);
        Path pinFile = pinFile(PIN_FIXTURE + "\n");
        migrate(installation, dbProperties, pinFile);
        execute(database, "UPDATE configuration_properties SET property_value = 'false' WHERE property_key = '"
                + AUTOLOGIN_KEY + "'");

        migrate(installation, dbProperties, pinFile);

        assertThat(autologinFlag(database)).containsExactly("false");
    }

    @ParameterizedTest
    @EnumSource(InstallationType.class)
    void shouldNotWriteTheFlagWhenNoPinIsStored(InstallationType installation) throws Exception {
        String database = freshDatabase(installation);
        Path dbProperties = writeDbProperties(installation, database);

        migrate(installation, dbProperties, pinFile("\n"));

        assertThat(openBao.store()).isEmpty();
        assertThat(autologinFlag(database)).isEmpty();
    }

    private void migrate(InstallationType installation, Path dbProperties, Path pinFile) throws IOException {
        PinSourceLocations locations = new PinSourceLocations(null, tempDir.resolve("no-custom.sh"),
                tempDir.resolve("no-default.sh"), pinFile);
        LegacyConfigMigrationCLI.migrateSignerTokenPins(NODE, installation, dbProperties.toString(), locations);
    }

    private Path pinFile(String content) throws IOException {
        return Files.writeString(tempDir.resolve("autologin"), content);
    }

    private Path writeDbProperties(InstallationType installation, String database) throws IOException {
        String content = installation == InstallationType.SECURITY_SERVER
                ? """
                xroad.db.serverconf.hibernate.connection.url=%s
                xroad.db.serverconf.hibernate.connection.username=%s
                xroad.db.serverconf.hibernate.connection.password=%s
                """
                : """
                spring.datasource.url=%s
                spring.datasource.username=%s
                spring.datasource.password=%s
                """;
        return Files.writeString(tempDir.resolve("db.properties"),
                content.formatted(url(database), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private String freshDatabase(InstallationType installation) throws SQLException {
        String database = "autologin_" + DATABASE_COUNTER.incrementAndGet();
        execute(POSTGRES.getDatabaseName(), "CREATE DATABASE " + database + " TEMPLATE " + templateOf(installation));
        return database;
    }

    private List<String> autologinFlag(String database) throws SQLException {
        try (var connection = DriverManager.getConnection(
                url(database), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "SELECT property_value FROM configuration_properties WHERE property_key = ?")) {
            statement.setString(1, AUTOLOGIN_KEY);
            try (var resultSet = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
                return values;
            }
        }
    }

    private static String templateOf(InstallationType installation) {
        return "autologin_template_" + installation.name().toLowerCase();
    }

    private static String changelogOf(InstallationType installation) {
        return installation == InstallationType.SECURITY_SERVER
                ? "liquibase/serverconf-changelog.xml"
                : "liquibase/centerui-changelog.xml";
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

    private static void migrateSchema(String targetDatabase, String changelog) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", changelog);
            update.addArgumentValue("url", url(targetDatabase));
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
