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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
