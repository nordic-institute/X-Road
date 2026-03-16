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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests validating that logicalFilePath attributes correctly handle
 * upgrade paths from both v4.19 native and v4.33 containerized Liquibase histories,
 * as well as fresh installs.
 *
 * <p>Uses H2 in-memory databases with purpose-built test changelogs (production changelogs
 * use PostgreSQL-specific SQL that H2 cannot execute).
 *
 * <p>The upgrade simulation strategy:
 * <ol>
 *   <li>Run Liquibase update on a fresh DB (creates tables + correct databasechangelog entries)</li>
 *   <li>Modify FILENAME values to simulate old native or containerized paths</li>
 *   <li>Run Liquibase update again and verify changeset recognition</li>
 * </ol>
 * This avoids synthetic checksums -- Liquibase's own checksums are used throughout.
 */
class LiquibaseUpgradeValidationTest {

    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";
    private static final String CHANGELOG_FILE = "test-upgrade-changelog.xml";

    @BeforeEach
    void setUp() {
        System.setProperty("liquibase.analytics.enabled", "false");
    }

    /**
     * Test 1: Verify that logicalFilePath values are stored as FILENAME in databasechangelog,
     * not the classpath resolution path.
     */
    @Test
    void shouldStoreLogicalFilePathAsFilename() throws Exception {
        String h2Url = "jdbc:h2:mem:val-test-1;DB_CLOSE_DELAY=-1";

        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT filename FROM DATABASECHANGELOG ORDER BY orderexecuted")) {

            List<String> filenames = new ArrayList<>();
            while (rs.next()) {
                filenames.add(rs.getString("filename"));
            }

            assertTrue(filenames.contains("test-upgrade/001-create-table.xml"),
                    "Expected logicalFilePath 'test-upgrade/001-create-table.xml' in FILENAME column, got: " + filenames);
            assertTrue(filenames.contains("test-master.xml"),
                    "Expected logicalFilePath 'test-master.xml' in FILENAME column, got: " + filenames);
        }
    }

    /**
     * Test 2: Verify that an upgrade from native v4.19 works.
     * Native installations stored FILENAME without any prefix (matching logicalFilePath values).
     * After adding logicalFilePath to changelogs, Liquibase should still recognize
     * previously executed changesets because the FILENAME values match.
     *
     * <p>Strategy: Run fresh install, then verify a second run does NOT re-execute changesets.
     * The native case is the baseline -- logicalFilePath was designed to match native FILENAME format.
     */
    @Test
    void shouldRecognizeExistingNativeChangesets() throws Exception {
        String h2Url = "jdbc:h2:mem:val-test-2;DB_CLOSE_DELAY=-1";

        // First run simulates the initial native installation
        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {
            // Verify FILENAME values match native format (no prefix) -- this is the key assertion
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT filename FROM DATABASECHANGELOG WHERE id = '001-create-table'")) {
                assertTrue(rs.next());
                assertEquals("test-upgrade/001-create-table.xml", rs.getString("filename"),
                        "FILENAME should match native path format (no prefix)");
            }
        }

        // Second run simulates the upgrade -- Liquibase should recognize all changesets
        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {
            // The changeset should NOT be re-executed (still just 1 row)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE id = '001-create-table'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "Changeset '001-create-table' should not be re-executed (expected 1 row, not 2)");
            }

            // runAlways changeset should still have only 1 row
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE id = 'run-always-check'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "runAlways changeset 'run-always-check' should have exactly 1 row");
            }
        }
    }

    /**
     * Test 3: Verify that an upgrade from containerized v4.33 works after FILENAME normalization.
     * Containerized installations stored FILENAME with "changelog/" prefix. The normalization
     * step (UPDATE ... SET filename = REPLACE(filename, 'changelog/', '')) must make them
     * match the logicalFilePath values so Liquibase recognizes them as already executed.
     *
     * <p>Strategy: Run fresh install, mutate FILENAME to add "changelog/" prefix (simulating
     * containerized history), apply normalization, then re-run and verify recognition.
     */
    @Test
    void shouldRecognizeExistingContainerizedChangesetsAfterNormalization() throws Exception {
        String h2Url = "jdbc:h2:mem:val-test-3;DB_CLOSE_DELAY=-1";

        // First run: creates correct state
        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {
            // Simulate containerized history: add "changelog/" prefix to all FILENAME values
            stmt.execute("UPDATE DATABASECHANGELOG SET filename = CONCAT('changelog/', filename)");

            // Verify the prefix was applied
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT filename FROM DATABASECHANGELOG WHERE id = '001-create-table'")) {
                assertTrue(rs.next());
                assertEquals("changelog/test-upgrade/001-create-table.xml", rs.getString("filename"),
                        "FILENAME should have containerized prefix after mutation");
            }

            // Apply FILENAME normalization: strip 'changelog/' prefix from containerized history
            stmt.execute("UPDATE DATABASECHANGELOG SET filename = REPLACE(filename, 'changelog/', '') "
                    + "WHERE filename LIKE 'changelog/%'");

            // Verify normalization restored the original path
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT filename FROM DATABASECHANGELOG WHERE id = '001-create-table'")) {
                assertTrue(rs.next());
                assertEquals("test-upgrade/001-create-table.xml", rs.getString("filename"),
                        "FILENAME should match logicalFilePath after normalization");
            }
        }

        // Second run: Liquibase should recognize all changesets after normalization
        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {
            // The changeset should NOT be re-executed
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE id = '001-create-table'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "Normalized changeset '001-create-table' should not be re-executed");
            }

            // runAlways changeset should still have only 1 row
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE id = 'run-always-check'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "runAlways changeset 'run-always-check' should have exactly 1 row");
            }
        }
    }

    /**
     * Test 4: Verify fresh install against an empty database works correctly.
     */
    @Test
    void shouldMigrateFreshDatabase() throws Exception {
        String h2Url = "jdbc:h2:mem:val-test-4;DB_CLOSE_DELAY=-1";

        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Verify test_data table was created
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test_data")) {
                assertTrue(rs.next(), "test_data table should exist after fresh install");
                assertEquals(0, rs.getInt(1), "test_data table should be empty (no seed data)");
            }

            // Verify databasechangelog has expected rows
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1),
                        "Expected 2 changesets in DATABASECHANGELOG (001-create-table + run-always-check)");
            }
        }
    }

    /**
     * Test 5: Verify that runAlways changesets do NOT create duplicate rows in databasechangelog
     * when the changeset was already executed in a previous run.
     */
    @Test
    void shouldNotCreateDuplicateRowForRunAlwaysChangeset() throws Exception {
        String h2Url = "jdbc:h2:mem:val-test-5;DB_CLOSE_DELAY=-1";

        // First run: creates all entries
        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        // Second run: runAlways changeset should execute but NOT create a duplicate row
        runLiquibaseUpdate(h2Url, CHANGELOG_FILE);

        try (Connection conn = DriverManager.getConnection(h2Url, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE id = 'run-always-check'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "runAlways changeset should have exactly 1 row (not duplicated across runs)");
            }

            // Total rows should still be 2
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1),
                        "Total changesets should remain 2 after second run");
            }
        }
    }

    private void runLiquibaseUpdate(String url, String changelogFile) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            CommandScope update = new CommandScope("update");
            update.addArgumentValue("changelogFile", changelogFile);
            update.addArgumentValue("url", url);
            update.addArgumentValue("username", H2_USER);
            update.addArgumentValue("password", H2_PASSWORD);
            update.execute();
        });
    }
}
