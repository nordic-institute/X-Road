/*
 * The MIT License
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

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibaseExecutorTest {
    private static final String H2_URL = "jdbc:h2:mem:liquibase-test;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    @Test
    void shouldExecuteChangelogAgainstH2() throws Exception {
        System.setProperty("liquibase.analytics.enabled", "false");

        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            CommandScope update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "test-changelog.xml");
            update.addArgumentValue("url", H2_URL);
            update.addArgumentValue("username", H2_USER);
            update.addArgumentValue("password", H2_PASSWORD);
            update.execute();
        });

        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD)) {
            // Verify the migration data was inserted
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT name FROM test_migration WHERE name = 'migration-works'")) {
                assertTrue(rs.next(), "Expected row with name='migration-works' in test_migration table");
                assertEquals("migration-works", rs.getString("name"));
            }

            // Verify DATABASECHANGELOG has 2 executed changesets
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Expected 2 changesets in DATABASECHANGELOG");
            }
        }
    }

    @Test
    void shouldResolveAllChangelogRootsFromClasspath() {
        String[] changelogs = {"centerui-changelog.xml", "serverconf-changelog.xml",
                "messagelog-changelog.xml", "op-monitor-changelog.xml"};
        for (String changelog : changelogs) {
            URL resource = getClass().getClassLoader().getResource("liquibase/" + changelog);
            assertNotNull(resource, "Changelog not found on classpath: liquibase/" + changelog);
        }
    }

    @Test
    void shouldResolveSignerChangelogFromClasspath() {
        URL resource = getClass().getClassLoader().getResource("liquibase/signer/001-signer.xml");
        assertNotNull(resource, "Signer changelog not found on classpath: liquibase/signer/001-signer.xml");
    }

    @Test
    void shouldParseChangelogEqualsFormat() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test", "update");
        assertEquals("serverconf", executor.changelog);
        assertEquals("jdbc:h2:mem:test", executor.url);
        assertEquals("update", executor.command);
    }

    @Test
    void shouldParseChangelogSpaceSeparated() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog", "centerui", "--url=jdbc:h2:mem:test", "update");
        assertEquals("centerui", executor.changelog);
    }

    @Test
    void shouldParseAllOptionFields() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs(
                "--changelog=serverconf",
                "--url=jdbc:h2:mem:test",
                "--username=user1",
                "--password=pass1",
                "--defaultSchemaName=myschema",
                "--contexts=user",
                "--prop-db-user=xroad",
                "--prop-proxy-ui-superuser=admin",
                "--prop-proxy-ui-superuser-password=secret",
                "update"
        );
        assertEquals("serverconf", executor.changelog);
        assertEquals("jdbc:h2:mem:test", executor.url);
        assertEquals("user1", executor.username);
        assertEquals("pass1", executor.password);
        assertEquals("myschema", executor.defaultSchemaName);
        assertEquals("user", executor.contexts);
        assertEquals("xroad", executor.propDbUser);
        assertEquals("admin", executor.propProxyUiSuperuser);
        assertEquals("secret", executor.propProxyUiSuperuserPassword);
        assertEquals("update", executor.command);
    }

    @Test
    void shouldTranslateChangelogToChangeLogFile() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        assertTrue(args.contains("--changeLogFile=liquibase/serverconf-changelog.xml"),
                "Should translate --changelog to --changeLogFile, got: " + args);
    }

    @Test
    void shouldPassThroughUrlUsernamePasswordContexts() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs(
                "--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--username=user1", "--password=pass1",
                "--defaultSchemaName=myschema", "--contexts=user", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        assertTrue(args.contains("--url=jdbc:h2:mem:test"), "Should pass through --url");
        assertTrue(args.contains("--username=user1"), "Should pass through --username");
        assertTrue(args.contains("--password=pass1"), "Should pass through --password");
        assertTrue(args.contains("--defaultSchemaName=myschema"), "Should pass through --defaultSchemaName");
        assertTrue(args.contains("--contexts=user"), "Should pass through --contexts");
        assertTrue(args.contains("update"), "Should include command word");
    }

    @Test
    void shouldTranslatePropDbUser() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--prop-db-user=xroad", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        assertTrue(args.contains("-Ddb_user=xroad"),
                "Should translate --prop-db-user to -Ddb_user, got: " + args);
    }

    @Test
    void shouldTranslatePropProxyUiSuperuser() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--prop-proxy-ui-superuser=admin", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        assertTrue(args.contains("-Dproxy_ui_superuser=admin"),
                "Should translate --prop-proxy-ui-superuser, got: " + args);
    }

    @Test
    void shouldTranslatePropProxyUiSuperuserPassword() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--prop-proxy-ui-superuser-password=secret", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        assertTrue(args.contains("-Dproxy_ui_superuser_password=secret"),
                "Should translate --prop-proxy-ui-superuser-password, got: " + args);
    }

    @Test
    void shouldAutoDeriveDbSchemaFromDefaultSchemaName() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--defaultSchemaName=myschema", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        assertTrue(args.contains("-Ddb_schema=myschema"),
                "Should auto-derive -Ddb_schema from --defaultSchemaName, got: " + args);
    }

    @Test
    void shouldNotAddDbSchemaWhenNoDefaultSchemaName() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs("--changelog=serverconf", "--url=jdbc:h2:mem:test", "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());
        for (String arg : args) {
            assertFalse(arg.startsWith("-Ddb_schema"),
                    "Should not add -Ddb_schema when no --defaultSchemaName, got: " + args);
        }
    }

    @Test
    void shouldPlaceDFlagsAfterCommandWord() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs(
                "--changelog=centerui",
                "--url=jdbc:postgresql://localhost/centerui_production",
                "--defaultSchemaName=centerui",
                "--prop-db-user=centerui",
                "--contexts=admin",
                "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());

        int updateIdx = args.indexOf("update");
        assertTrue(updateIdx >= 0, "Result should contain 'update' command word");

        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).startsWith("-D")) {
                assertTrue(i > updateIdx,
                        "Flag " + args.get(i) + " at index " + i
                                + " should appear after 'update' at index " + updateIdx
                                + ". Full result: " + args);
            }
        }

        assertTrue(args.contains("-Ddb_user=centerui"), "Should have -Ddb_user=centerui");
        assertTrue(args.contains("-Ddb_schema=centerui"), "Should have -Ddb_schema=centerui");
    }

    @Test
    void shouldPlaceDFlagsAfterCommandWordWithMultipleProps() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs(
                "--changelog=serverconf",
                "--url=jdbc:postgresql://localhost/serverconf",
                "--defaultSchemaName=serverconf",
                "--prop-db-user=xroad",
                "--prop-proxy-ui-superuser=admin",
                "update");
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());

        int updateIdx = args.indexOf("update");
        assertTrue(updateIdx >= 0, "Result should contain 'update' command word");

        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).startsWith("-D")) {
                assertTrue(i > updateIdx,
                        "Flag " + args.get(i) + " at index " + i
                                + " should appear after 'update' at index " + updateIdx
                                + ". Full result: " + args);
            }
        }

        assertTrue(args.contains("-Ddb_user=xroad"), "Should have -Ddb_user");
        assertTrue(args.contains("-Dproxy_ui_superuser=admin"), "Should have -Dproxy_ui_superuser");
        assertTrue(args.contains("-Ddb_schema=serverconf"), "Should have -Ddb_schema");
    }

    @Test
    void shouldShowHelpWithAllXRoadOptions() {
        var executor = new LiquibaseExecutor();
        var cmd = new CommandLine(executor);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode);
        String help = sw.toString();
        assertTrue(help.contains("--changelog"), "Help should mention --changelog");
        assertTrue(help.contains("--url"), "Help should mention --url");
        assertTrue(help.contains("--prop-db-user"), "Help should mention --prop-db-user");
        assertTrue(help.contains("--prop-proxy-ui-superuser"), "Help should mention --prop-proxy-ui-superuser");
        assertTrue(help.contains("--prop-proxy-ui-superuser-password"),
                "Help should mention --prop-proxy-ui-superuser-password");
    }

    @Test
    void shouldShowVersion() {
        var executor = new LiquibaseExecutor();
        var cmd = new CommandLine(executor);
        StringWriter sw = new StringWriter();
        cmd.setOut(new PrintWriter(sw));
        int exitCode = cmd.execute("--version");
        assertEquals(0, exitCode);
        String version = sw.toString();
        assertTrue(version.contains("X-Road Liquibase Executor"),
                "Version should contain executor name, got: " + version);
    }

    @Test
    void shouldRejectUnknownOption() {
        var executor = new LiquibaseExecutor();
        var cmd = new CommandLine(executor);
        StringWriter errSw = new StringWriter();
        cmd.setErr(new PrintWriter(errSw));
        int exitCode = cmd.execute("--unknown=foo", "--changelog=serverconf", "--url=jdbc:h2:mem:test", "update");
        assertTrue(exitCode != 0, "Unknown option should cause non-zero exit code");
    }

    @Test
    void shouldRejectRawDFlag() {
        var executor = new LiquibaseExecutor();
        var cmd = new CommandLine(executor);
        StringWriter errSw = new StringWriter();
        cmd.setErr(new PrintWriter(errSw));
        int exitCode = cmd.execute("-Dfoo=bar", "--changelog=serverconf", "--url=jdbc:h2:mem:test", "update");
        assertTrue(exitCode != 0, "Raw -D flag should cause non-zero exit code");
    }

    @Test
    void shouldRejectMissingChangelog() {
        var executor = new LiquibaseExecutor();
        var cmd = new CommandLine(executor);
        StringWriter errSw = new StringWriter();
        cmd.setErr(new PrintWriter(errSw));
        int exitCode = cmd.execute("--url=jdbc:h2:mem:test", "update");
        assertTrue(exitCode != 0, "Missing --changelog should cause non-zero exit code");
    }

    @Test
    void shouldRejectMissingUrl() {
        var executor = new LiquibaseExecutor();
        var cmd = new CommandLine(executor);
        StringWriter errSw = new StringWriter();
        cmd.setErr(new PrintWriter(errSw));
        int exitCode = cmd.execute("--changelog=serverconf", "update");
        assertTrue(exitCode != 0, "Missing --url should cause non-zero exit code");
    }

    @Test
    void shouldTranslateFullPipeline() {
        var executor = new LiquibaseExecutor();
        new CommandLine(executor).parseArgs(
                "--changelog=serverconf",
                "--url=jdbc:pg:test",
                "--defaultSchemaName=myschema",
                "--prop-db-user=xroad",
                "--contexts=user",
                "update"
        );
        List<String> args = Arrays.asList(executor.buildLiquibaseArgs());

        assertTrue(args.contains("--changeLogFile=liquibase/serverconf-changelog.xml"),
                "Should translate --changelog");
        assertTrue(args.contains("--url=jdbc:pg:test"), "Should preserve --url");
        assertTrue(args.contains("--defaultSchemaName=myschema"), "Should preserve --defaultSchemaName");
        assertTrue(args.contains("-Ddb_user=xroad"), "Should translate --prop-db-user");
        assertTrue(args.contains("--contexts=user"), "Should preserve --contexts");
        assertTrue(args.contains("update"), "Should preserve update command");
        assertTrue(args.contains("-Ddb_schema=myschema"), "Should auto-derive -Ddb_schema");
    }
}
