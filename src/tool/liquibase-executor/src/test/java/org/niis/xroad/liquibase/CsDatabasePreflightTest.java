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
package org.niis.xroad.liquibase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Sources the shared database setup helper inside a Debian PostgreSQL container and calls its preflight functions
 * against the real server, once as the master role and once as a runtime role.
 */
@ParameterizedClass(name = "{0}")
@ValueSource(strings = {"postgres:14", "postgres:15"})
class CsDatabasePreflightTest {

    private static final String SCRIPTS_DIR_PROPERTY = "base.scripts.dir";
    private static final String HELPER_IN_CONTAINER = "/tmp/_setup_db.sh";
    private static final String UNSUPPORTED_IMAGE = "postgres:14";
    private static final String ADDRESS = "db-write.example.test:5432";
    private static final String HOST_LIST = "db1.example.test:5432,db2.example.test:5432";
    private static final String RUNTIME_PASSWORD = "runtime-pw";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static PostgreSQLContainer postgres;

    @Parameter
    private String image;

    @BeforeParameterizedClassInvocation
    static void startDatabase(String image) {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        postgres = new PostgreSQLContainer(image);
        postgres.start();
        postgres.copyFileToContainer(
                MountableFile.forHostPath(Path.of(System.getProperty(SCRIPTS_DIR_PROPERTY), "_setup_db.sh")),
                HELPER_IN_CONTAINER);
    }

    @AfterParameterizedClassInvocation
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void versionBelowFifteenIsRefusedAndFifteenPasses() throws Exception {
        var database = newDatabase();

        var result = run(database, masterAccess(), "check_pg_version psql_check");

        if (UNSUPPORTED_IMAGE.equals(image)) {
            assertThat(result.getExitCode()).as(result.getStderr()).isNotZero();
        } else {
            assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        }
    }

    @Test
    void writableDatabasePassesForMasterAndRuntimeRole() throws Exception {
        var database = newDatabase();

        var master = checkWritable(database, masterAccess());
        var runtime = checkWritable(database, runtimeAccess(database));

        assertThat(master.getExitCode()).as(master.getStderr()).isZero();
        assertThat(runtime.getExitCode()).as(runtime.getStderr()).isZero();
    }

    @Test
    void readOnlyDatabaseIsRefusedForMasterAndNamesTheAddress() throws Exception {
        var database = newDatabase();
        makeReadOnly(database);

        var result = checkWritable(database, masterAccess());

        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains(ADDRESS);
    }

    @Test
    void readOnlyDatabaseIsRefusedForRuntimeRoleWithoutMasterCredentials() throws Exception {
        var database = newDatabase();
        makeReadOnly(database);

        var result = checkWritable(database, runtimeAccess(database));

        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr()).contains(ADDRESS);
    }

    @Test
    void hostListIsRefusedOnFreshInstall() throws Exception {
        assumeHostRuleRunsOnce();

        var result = runHelper("check_db_host_answer '%s' fresh".formatted(HOST_LIST));

        assertThat(result.getExitCode()).as(result.getStderr()).isNotZero();
        assertThat(result.getStderr()).isNotBlank();
    }

    @Test
    void hostListOnExistingInstallWarnsAndPasses() throws Exception {
        assumeHostRuleRunsOnce();

        var result = runHelper("check_db_host_answer '%s' existing".formatted(HOST_LIST));

        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        assertThat(result.getStderr()).isNotBlank();
    }

    @Test
    void singleHostPassesSilentlyForFreshAndExisting() throws Exception {
        assumeHostRuleRunsOnce();

        var fresh = runHelper("check_db_host_answer '%s' fresh".formatted(ADDRESS));
        var existing = runHelper("check_db_host_answer '%s' existing".formatted(ADDRESS));

        assertThat(fresh.getExitCode()).as(fresh.getStderr()).isZero();
        assertThat(fresh.getStdout() + fresh.getStderr()).isEmpty();
        assertThat(existing.getExitCode()).as(existing.getStderr()).isZero();
        assertThat(existing.getStdout() + existing.getStderr()).isEmpty();
    }

    private void assumeHostRuleRunsOnce() {
        assumeTrue(!UNSUPPORTED_IMAGE.equals(image), "The host rule does not use the database, so it runs for one image only");
    }

    private static ExecResult runHelper(String call) throws IOException, InterruptedException {
        return postgres.execInContainer("bash", "-c", ". %s\n%s".formatted(HELPER_IN_CONTAINER, call));
    }

    private static ExecResult checkWritable(Database database, Access access) throws IOException, InterruptedException {
        return run(database, access, "check_db_writable psql_check '%s'".formatted(ADDRESS));
    }

    private static ExecResult run(Database database, Access access, String call) throws IOException, InterruptedException {
        var script = """
                . %s
                psql_check() { PGPASSWORD='%s' psql -h 127.0.0.1 -U '%s' -d '%s' -qtA "$@"; }
                %s
                """.formatted(HELPER_IN_CONTAINER, access.password(), access.user(), database.name(), call);
        return postgres.execInContainer("bash", "-c", script);
    }

    private static Access masterAccess() {
        return new Access(postgres.getUsername(), postgres.getPassword());
    }

    private static Access runtimeAccess(Database database) {
        return new Access(database.runtimeRole(), RUNTIME_PASSWORD);
    }

    private static Database newDatabase() throws SQLException {
        var name = "preflight_" + SEQUENCE.incrementAndGet();
        var database = new Database(name, name + "_rt");
        execute("CREATE ROLE %s LOGIN PASSWORD '%s'".formatted(database.runtimeRole(), RUNTIME_PASSWORD));
        execute("CREATE DATABASE %s".formatted(database.name()));
        return database;
    }

    private static void makeReadOnly(Database database) throws SQLException {
        execute("ALTER DATABASE %s SET default_transaction_read_only = on".formatted(database.name()));
    }

    private static void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private record Database(String name, String runtimeRole) {
    }

    private record Access(String user, String password) {
    }
}
