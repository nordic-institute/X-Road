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

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibaseExecutorTest {

    private static final String H2_URL = "jdbc:h2:mem:liquibase-test;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    // --- Integration tests (unchanged) ---

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

    // --- Changelog translation (renamed from --schema) ---

    @Test
    void shouldTranslateChangelogEqualsToChangeLogFile() {
        String[] args = {"--url=jdbc:h2:mem:test", "--changelog=serverconf", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("--changeLogFile=liquibase/serverconf-changelog.xml"),
                "Should contain translated --changeLogFile, got: " + resultList);
        assertTrue(resultList.contains("--url=jdbc:h2:mem:test"), "Should preserve --url");
        assertTrue(resultList.contains("update"), "Should preserve update command");
    }

    @Test
    void shouldTranslateChangelogSpaceSeparated() {
        String[] args = {"--changelog", "centerui", "--url=jdbc:h2:mem:test", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("--changeLogFile=liquibase/centerui-changelog.xml"),
                "Should contain translated --changeLogFile, got: " + resultList);
        assertTrue(resultList.contains("--url=jdbc:h2:mem:test"), "Should preserve --url");
    }

    @Test
    void shouldPreserveArgsWhenNoChangelogPresent() {
        String[] args = {"--changeLogFile=custom.xml", "--url=jdbc:h2:mem:test", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        assertArrayEquals(new String[]{"--changeLogFile=custom.xml", "--url=jdbc:h2:mem:test", "update"}, result);
    }

    @Test
    void shouldCoexistChangelogWithOtherArgs() {
        String[] args = {"--url=jdbc:h2:mem:test", "--defaultSchemaName=serverconf", "--changelog=serverconf", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("--url=jdbc:h2:mem:test"));
        assertTrue(resultList.contains("--defaultSchemaName=serverconf"));
        assertTrue(resultList.contains("--changeLogFile=liquibase/serverconf-changelog.xml"));
        assertTrue(resultList.contains("update"));
        // Should also auto-derive -Ddb_schema
        assertTrue(resultList.contains("-Ddb_schema=serverconf"),
                "Should auto-derive -Ddb_schema from --defaultSchemaName, got: " + resultList);
    }

    @Test
    void shouldRejectOldSchemaFlag() {
        String[] args = {"--schema=serverconf", "--url=jdbc:h2:mem:test", "update"};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LiquibaseExecutor.translateArgs(args));
        assertTrue(ex.getMessage().contains("--changelog"), "Error should suggest --changelog");
    }

    // --- Prop translation (--prop-* to -D) ---

    @Test
    void shouldTranslatePropDbUser() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "--prop-db-user=xroad", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("-Ddb_user=xroad"),
                "Should translate --prop-db-user to -Ddb_user, got: " + resultList);
    }

    @Test
    void shouldTranslatePropProxyUiSuperuser() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "--prop-proxy-ui-superuser=admin", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("-Dproxy_ui_superuser=admin"),
                "Should translate --prop-proxy-ui-superuser to -Dproxy_ui_superuser, got: " + resultList);
    }

    @Test
    void shouldTranslatePropProxyUiSuperuserPassword() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--prop-proxy-ui-superuser-password=secret", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("-Dproxy_ui_superuser_password=secret"),
                "Should translate --prop-proxy-ui-superuser-password, got: " + resultList);
    }

    @Test
    void shouldTranslatePropSpaceSeparated() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "--prop-db-user", "xroad", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("-Ddb_user=xroad"),
                "Should translate space-separated --prop-db-user, got: " + resultList);
    }

    // --- Known-set validation ---

    @Test
    void shouldRejectUnknownProp() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "--prop-unknown=val", "update"};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LiquibaseExecutor.translateArgs(args));
        assertTrue(ex.getMessage().contains("unknown"), "Error should mention the unknown property name");
    }

    // --- Raw -D rejection ---

    @Test
    void shouldRejectRawDFlag() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "-Ddb_user=xroad", "update"};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LiquibaseExecutor.translateArgs(args));
        assertTrue(ex.getMessage().contains("--prop-"), "Error should suggest --prop-* instead");
    }

    @Test
    void shouldRejectAnyRawDFlag() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "-Dfoo=bar", "update"};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LiquibaseExecutor.translateArgs(args));
        assertTrue(ex.getMessage().contains("--prop-"), "Error should suggest --prop-* instead");
    }

    // --- Auto-derive -Ddb_schema ---

    @Test
    void shouldAutoDeriveDbSchemaFromDefaultSchemaName() {
        String[] args = {"--defaultSchemaName=myschema", "--changelog=serverconf", "--url=jdbc:h2:mem:test", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);
        assertTrue(resultList.contains("-Ddb_schema=myschema"),
                "Should auto-derive -Ddb_schema from --defaultSchemaName, got: " + resultList);
    }

    @Test
    void shouldNotAddDbSchemaWhenNoDefaultSchemaName() {
        String[] args = {"--changelog=serverconf", "--url=jdbc:h2:mem:test", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        for (String arg : result) {
            assertTrue(!arg.startsWith("-Ddb_schema"),
                    "Should not add -Ddb_schema when no --defaultSchemaName, got: " + Arrays.asList(result));
        }
    }

    @Test
    void shouldNotDuplicateDbSchemaWhenExplicitPropProvided() {
        String[] args = {"--defaultSchemaName=myschema", "--changelog=serverconf", "--url=jdbc:h2:mem:test",
                "--prop-db-schema=myschema", "update"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        long count = Arrays.stream(result).filter(a -> a.startsWith("-Ddb_schema=")).count();
        assertEquals(1, count, "Should have exactly one -Ddb_schema, got: " + Arrays.asList(result));
    }

    // --- Required arg validation ---

    @Test
    void shouldValidateRequiredChangelogMissing() {
        String[] args = {"--url=jdbc:h2:mem:test", "update"};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LiquibaseExecutor.translateArgs(args));
        assertTrue(ex.getMessage().contains("--changelog"), "Error should mention --changelog");
    }

    @Test
    void shouldValidateRequiredUrlMissing() {
        String[] args = {"--changelog=serverconf", "update"};
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> LiquibaseExecutor.translateArgs(args));
        assertTrue(ex.getMessage().contains("--url"), "Error should mention --url");
    }

    @Test
    void shouldBypassValidationForHelp() {
        // --help should not require --changelog or --url
        String[] args = {"--help"};
        // Should not throw - help bypasses validation
        String[] result = LiquibaseExecutor.translateArgs(args);
        assertArrayEquals(new String[]{"--help"}, result);
    }

    @Test
    void shouldBypassValidationForVersion() {
        String[] args = {"--version"};
        String[] result = LiquibaseExecutor.translateArgs(args);
        assertArrayEquals(new String[]{"--version"}, result);
    }

    // --- Help output ---

    @Test
    void shouldPrintHelpTextContainingXRoadFlags() {
        String helpText = LiquibaseExecutor.getHelpText();
        assertTrue(helpText.contains("--changelog"), "Help should mention --changelog");
        assertTrue(helpText.contains("--prop-db-user"), "Help should mention --prop-db-user");
        assertTrue(helpText.contains("--prop-proxy-ui-superuser"), "Help should mention --prop-proxy-ui-superuser");
        assertTrue(helpText.contains("--prop-proxy-ui-superuser-password"),
                "Help should mention --prop-proxy-ui-superuser-password");
        assertTrue(helpText.contains("-Ddb_schema"), "Help should mention -Ddb_schema auto-derivation");
        assertTrue(helpText.contains("--prop-"), "Help should mention --prop- usage");
    }

    // --- Full pipeline ---

    @Test
    void shouldTranslateFullPipeline() {
        String[] args = {
                "--changelog=serverconf",
                "--url=jdbc:pg:test",
                "--defaultSchemaName=myschema",
                "--prop-db-user=xroad",
                "--contexts=user",
                "update"
        };
        String[] result = LiquibaseExecutor.translateArgs(args);
        List<String> resultList = Arrays.asList(result);

        assertTrue(resultList.contains("--changeLogFile=liquibase/serverconf-changelog.xml"),
                "Should translate --changelog");
        assertTrue(resultList.contains("--url=jdbc:pg:test"), "Should preserve --url");
        assertTrue(resultList.contains("--defaultSchemaName=myschema"), "Should preserve --defaultSchemaName");
        assertTrue(resultList.contains("-Ddb_user=xroad"), "Should translate --prop-db-user");
        assertTrue(resultList.contains("--contexts=user"), "Should preserve --contexts");
        assertTrue(resultList.contains("update"), "Should preserve update command");
        assertTrue(resultList.contains("-Ddb_schema=myschema"), "Should auto-derive -Ddb_schema");
    }
}
