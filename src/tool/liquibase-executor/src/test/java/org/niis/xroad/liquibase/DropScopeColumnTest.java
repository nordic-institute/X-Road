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

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers configuration-properties/002-drop-scope.xml: the table collapses to unique
 * {@code property_key} rows before the scope column is dropped, preserving what a deployment
 * effectively resolved — an app-scoped row beats the scope-less one for the same key (the config
 * source used to order {@code scope NULLS LAST}), and between app-scoped rows the most recently
 * updated wins.
 *
 * <p>Runs on H2 against a stand-in for the pre-drop table — the production changelog for the
 * original table is PostgreSQL-only (plpgsql triggers, partial indexes), as is the NOTICE-emitting
 * report changeset, which is {@code dbms="postgresql"} and skipped here.
 */
class DropScopeColumnTest {

    private static final String H2_URL = "jdbc:h2:mem:drop-scope-test;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    @BeforeAll
    static void migrate() throws Exception {
        System.setProperty("liquibase.analytics.enabled", "false");
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            CommandScope update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "test-drop-scope-changelog.xml");
            update.addArgumentValue("url", H2_URL);
            update.addArgumentValue("username", H2_USER);
            update.addArgumentValue("password", H2_PASSWORD);
            update.execute();
        });
    }

    @Test
    void appScopedValueBeatsTheScopelessOne() throws Exception {
        assertEquals(Optional.of("2222"), value("xroad.proxy.health-check-port"));
    }

    @Test
    void mostRecentlyUpdatedWinsBetweenAppScopedRows() throws Exception {
        assertEquals(Optional.of("true"), value("xroad.common-rpc.use-tls"));
    }

    @Test
    void singletonsSurviveUntouched() throws Exception {
        assertEquals(Optional.of("true"), value("xroad.message-log-encryption.db.encryption-enabled"));
        assertEquals(Optional.of("30"), value("xroad.signer.ocsp-retry-delay"));
    }

    @Test
    void tableIsUniqueByPropertyKeyWithoutAScopeColumn() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE LOWER(table_name) = 'configuration_properties' AND LOWER(column_name) = 'scope'")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "scope column should be dropped");
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM configuration_properties")) {
                rs.next();
                assertEquals(4, rs.getInt(1), "6 seeded rows collapse to 4 unique keys");
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.indexes"
                            + " WHERE LOWER(index_name) = 'uniq_property_key'")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "index uniq_property_key exists");
            }
            // prove the index is unique behaviourally: a duplicate key must be rejected
            var duplicate = "INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at)"
                    + " VALUES ('xroad.signer.ocsp-retry-delay', '60', now(), now())";
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                    () -> stmt.executeUpdate(duplicate), "duplicate property_key must violate the unique index");
        }
    }

    private static Optional<String> value(String propertyKey) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT property_value FROM configuration_properties WHERE property_key = ?")) {
            stmt.setString(1, propertyKey);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
        }
        assertFalse(values.size() > 1, "expected at most one row for " + propertyKey + ", got " + values);
        assertTrue(values.size() <= 1);
        return values.stream().findFirst();
    }
}
