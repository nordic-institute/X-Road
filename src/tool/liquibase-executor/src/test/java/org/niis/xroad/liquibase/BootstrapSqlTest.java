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

import lombok.With;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.AfterParameterizedClassInvocation;
import org.junit.jupiter.params.BeforeParameterizedClassInvocation;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Applies the shared bootstrap SQL pair to a real PostgreSQL, the way the native runner does: a {@code \set}
 * preamble and both files on psql's stdin, run inside the container. Asserts the resulting roles, owners and
 * privileges, and that applying the files again with other passwords changes nothing.
 */
@ParameterizedClass(name = "{0}")
@ValueSource(strings = {"postgres:15-alpine", "postgres:18-alpine"})
class BootstrapSqlTest {

    private static final String SQL_DIR_PROPERTY = "bootstrap.sql.dir";
    private static final String FIRST_PASSWORD_SUFFIX = "-one";
    private static final String SECOND_PASSWORD_SUFFIX = "-two";
    private static final String MASTER_PASSWORD = "master-pw";
    private static final String MAINTENANCE_DB = "postgres";
    private static final int PSQL_ON_ERROR_STOP_EXIT_CODE = 3;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static PostgreSQLContainer postgres;

    @Parameter
    @SuppressWarnings("unused")
    private String image;

    @BeforeParameterizedClassInvocation
    static void startDatabase(String image) {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        postgres = new PostgreSQLContainer(image);
        postgres.start();
    }

    @AfterParameterizedClassInvocation
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void superuserMasterCreatesTwoRolesWithHyphenatedNamesAndAwkwardPasswords() throws Exception {
        var scenario = scenario("ds-control-plane", true, false)
                .withMaster(postgres.getUsername()).withMasterPassword(postgres.getPassword())
                .withPasswordTail("p'a\\ss");

        assertAppliedTwice(scenario);
    }

    @Test
    void masterWithOnlyCreatedbAndCreateroleWorksUnderTheRemoteSearchPath() throws Exception {
        var master = "master_b" + SEQUENCE.incrementAndGet();
        execute("CREATE ROLE \"%s\" LOGIN PASSWORD '%s' CREATEDB CREATEROLE".formatted(master, MASTER_PASSWORD));
        var scenario = scenario("limited", true, false).withMaster(master).withMasterPassword(MASTER_PASSWORD).withRemoteSearchPath();

        assertAppliedTwice(scenario);

        assertThat(isMember(master, scenario.adminRole())).isTrue();
        assertThat(isMember(master, scenario.runtimeRole())).isTrue();
    }

    @Test
    void singleRoleModeCreatesNoAdminRoleAndTheRuntimeRoleOwnsTheSchema() throws Exception {
        var scenario = scenario("single", false, true).withMaster(postgres.getUsername())
                .withMasterPassword(postgres.getPassword());

        assertAppliedTwice(scenario);

        assertThat(roleExists(scenario.name() + "_admin")).isFalse();
    }

    @Test
    void publicSchemaIsTakenOverByTheAdminRoleSoItCanCreateTablesThere() throws Exception {
        var scenario = scenario("pub", false, false).withMaster(postgres.getUsername())
                .withMasterPassword(postgres.getPassword()).withPublicSchema(true);
        assertSuccess(apply(scenario, FIRST_PASSWORD_SUFFIX));
        assertSuccess(apply(scenario, SECOND_PASSWORD_SUFFIX));
        assertThat(query(scenario.name(), "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = 'public'"))
                .containsExactly(scenario.adminRole());
        assertThat(hasSchemaPrivilege(scenario, scenario.adminRole(), "public", "CREATE")).isTrue();
        assertThat(hasSchemaPrivilege(scenario, scenario.runtimeRole(), "public", "USAGE")).isTrue();
        assertThat(hasSchemaPrivilege(scenario, scenario.runtimeRole(), "public", "CREATE")).isFalse();
        assertThat(query(scenario.name(), "SELECT 1 FROM pg_namespace WHERE nspname = " + literal(scenario.name()))).isEmpty();
        try (var connection = DriverManager.getConnection(url(scenario.name()), scenario.adminRole(),
                scenario.adminPassword(FIRST_PASSWORD_SUFFIX));
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE public.probe (id int)");
        }
    }

    @Test
    void masterWithoutCreateroleStopsPsqlWithPermissionDenied() throws Exception {
        var master = "master_d" + SEQUENCE.incrementAndGet();
        execute("CREATE ROLE \"%s\" LOGIN PASSWORD '%s' CREATEDB".formatted(master, MASTER_PASSWORD));
        var scenario = scenario("denied", true, false).withMaster(master).withMasterPassword(MASTER_PASSWORD);

        var result = apply(scenario, FIRST_PASSWORD_SUFFIX);

        assertThat(result.getExitCode()).isEqualTo(PSQL_ON_ERROR_STOP_EXIT_CODE);
        assertThat(result.getStderr()).contains("permission denied to create role");
        assertThat(roleExists(scenario.adminRole())).isFalse();
    }

    private static Scenario scenario(String namePrefix, boolean hstore, boolean singleRole) {
        var name = namePrefix + "-" + SEQUENCE.incrementAndGet();
        return new Scenario(name, hstore, singleRole, null, null, "", "", false);
    }

    private static void assertAppliedTwice(Scenario scenario) throws Exception {
        assertSuccess(apply(scenario, FIRST_PASSWORD_SUFFIX));
        assertState(scenario);

        assertSuccess(apply(scenario, SECOND_PASSWORD_SUFFIX));
        assertState(scenario);

        assertCannotLogIn(scenario.runtimeRole(), scenario.runtimePassword(SECOND_PASSWORD_SUFFIX), scenario.name());
        assertCanLogIn(scenario.runtimeRole(), scenario.runtimePassword(FIRST_PASSWORD_SUFFIX), scenario.name());
        if (!scenario.singleRole()) {
            assertCanLogIn(scenario.adminRole(), scenario.adminPassword(FIRST_PASSWORD_SUFFIX), scenario.name());
        }
    }

    private static void assertSuccess(ExecResult result) {
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static void assertState(Scenario scenario) throws SQLException {
        var owner = scenario.ownerRole();
        var roles = scenario.singleRole() ? List.of(scenario.runtimeRole())
                : List.of(scenario.adminRole(), scenario.runtimeRole());

        for (var role : roles) {
            assertThat(query(scenario.name(), "SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles "
                    + "WHERE rolname = " + literal(role))).containsExactly("t|f|f|f");
        }

        assertThat(query(MAINTENANCE_DB, "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = "
                + literal(scenario.name()))).containsExactly(scenario.master());
        assertNoPublicAccess(scenario);

        assertThat(hasSchemaPrivilege(scenario, owner, scenario.name(), "CREATE")).isTrue();
        assertThat(hasSchemaPrivilege(scenario, scenario.runtimeRole(), scenario.name(), "CREATE")).isEqualTo(scenario.singleRole());
        assertThat(query(scenario.name(), "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = "
                + literal(scenario.name()))).containsExactly(owner);

        for (var role : roles) {
            assertThat(hasSchemaPrivilege(scenario, role, "public", "USAGE")).isTrue();
            assertThat(hasSchemaPrivilege(scenario, role, "public", "CREATE")).isFalse();
        }

        assertThat(query(scenario.name(), "SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace "
                + "WHERE e.extname = 'hstore'")).isEqualTo(scenario.hstore() ? List.of("public") : List.of());
    }

    private static void assertNoPublicAccess(Scenario scenario) throws SQLException {
        var probe = "probe_" + SEQUENCE.incrementAndGet();
        execute("CREATE ROLE \"" + probe + "\"");
        try {
            assertThat(hasDatabasePrivilege(probe, scenario.name(), "CONNECT")).isFalse();
            assertThat(hasDatabasePrivilege(probe, scenario.name(), "TEMP")).isFalse();
            assertThat(hasSchemaPrivilege(scenario, probe, "public", "USAGE")).isFalse();
            assertThat(hasDatabasePrivilege(scenario.runtimeRole(), scenario.name(), "CONNECT")).isTrue();
            assertThat(hasDatabasePrivilege(scenario.runtimeRole(), scenario.name(), "TEMP")).isTrue();
            assertThat(hasDatabasePrivilege(scenario.ownerRole(), scenario.name(), "CREATE")).isTrue();
            assertThat(hasDatabasePrivilege(scenario.runtimeRole(), scenario.name(), "CREATE")).isEqualTo(scenario.singleRole());
        } finally {
            execute("DROP ROLE \"" + probe + "\"");
        }
    }

    private static boolean hasSchemaPrivilege(Scenario scenario, String role, String schema, String privilege) throws SQLException {
        return queryBoolean(scenario.name(), "SELECT has_schema_privilege(%s, %s, %s)"
                .formatted(literal(role), literal(schema), literal(privilege)));
    }

    private static boolean hasDatabasePrivilege(String role, String database, String privilege) throws SQLException {
        return queryBoolean(MAINTENANCE_DB, "SELECT has_database_privilege(%s, %s, %s)"
                .formatted(literal(role), literal(database), literal(privilege)));
    }

    private static boolean isMember(String member, String role) throws SQLException {
        return queryBoolean(MAINTENANCE_DB, "SELECT pg_has_role(%s, %s, 'MEMBER')".formatted(literal(member), literal(role)));
    }

    private static boolean roleExists(String role) throws SQLException {
        return !query(MAINTENANCE_DB, "SELECT 1 FROM pg_roles WHERE rolname = " + literal(role)).isEmpty();
    }

    private static void assertCanLogIn(String role, String password, String database) throws SQLException {
        try (var connection = DriverManager.getConnection(url(database), role, password)) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    private static void assertCannotLogIn(String role, String password, String database) {
        var url = url(database);
        assertThatThrownBy(() -> DriverManager.getConnection(url, role, password)).isInstanceOf(SQLException.class);
    }
    private static ExecResult apply(Scenario scenario, String passwordSuffix) throws IOException, InterruptedException {
        var script = preamble(scenario, passwordSuffix) + readSql("bootstrap-roles.sql") + readSql("bootstrap-schema.sql");
        var scriptPath = "/tmp/bootstrap-" + SEQUENCE.incrementAndGet() + ".sql";
        postgres.copyFileToContainer(Transferable.of(script), scriptPath);

        var command = "PGPASSWORD='%s' %s psql -h 127.0.0.1 -U '%s' -d %s -qtA < %s"
                .formatted(scenario.masterPassword(), scenario.pgOptions(), scenario.master(), MAINTENANCE_DB, scriptPath);
        return postgres.execInContainer("sh", "-c", command);
    }

    private static String preamble(Scenario scenario, String passwordSuffix) {
        return """
                \\set db_name %s
                \\set admin_role %s
                \\set admin_password %s
                \\set runtime_role %s
                \\set runtime_password %s
                \\set master_role %s
                \\set schema_name %s
                \\set hstore %s
                \\set single_role %s
                """.formatted(psqlQuote(scenario.name()), psqlQuote(scenario.adminRole()),
                psqlQuote(scenario.adminPassword(passwordSuffix)), psqlQuote(scenario.runtimeRole()),
                psqlQuote(scenario.runtimePassword(passwordSuffix)), psqlQuote(scenario.master()),
                psqlQuote(scenario.schema()), scenario.hstore() ? "on" : "off", scenario.singleRole() ? "on" : "off");
    }

    private static String psqlQuote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private static String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String readSql(String file) throws IOException {
        return Files.readString(Path.of(System.getProperty(SQL_DIR_PROPERTY), file));
    }

    private static String url(String database) {
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + database);
    }

    private static Connection superuserConnection(String database) throws SQLException {
        return DriverManager.getConnection(url(database), postgres.getUsername(), postgres.getPassword());
    }

    private static void execute(String sql) throws SQLException {
        try (var connection = superuserConnection(MAINTENANCE_DB);
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean queryBoolean(String database, String sql) throws SQLException {
        return query(database, sql).getFirst().equals("t");
    }

    private static List<String> query(String database, String sql) throws SQLException {
        var rows = new ArrayList<String>();
        try (var connection = superuserConnection(database);
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(sql)) {
            var columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                var row = new ArrayList<String>();
                for (var i = 1; i <= columns; i++) {
                    row.add(resultSet.getString(i));
                }
                rows.add(String.join("|", row));
            }
        }
        return rows;
    }

    @With
    private record Scenario(String name, boolean hstore, boolean singleRole, String master, String masterPassword,
                            String passwordTail, String pgOptions, boolean publicSchema) {

        Scenario withRemoteSearchPath() {
            return withPgOptions("PGOPTIONS='-c client-min-messages=warning -c search_path=%s,public'".formatted(name));
        }

        String schema() {
            return publicSchema ? "public" : name;
        }

        String ownerRole() {
            return singleRole ? runtimeRole() : adminRole();
        }

        String adminRole() {
            return name + "_admin";
        }

        String runtimeRole() {
            return name;
        }

        String adminPassword(String suffix) {
            return "admin" + passwordTail + suffix;
        }

        String runtimePassword(String suffix) {
            return "runtime" + passwordTail + suffix;
        }
    }
}
