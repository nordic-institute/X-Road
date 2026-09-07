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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers serverconf/011-rename-acme-certificate-wait-properties.xml: stored overrides under the
 * orphaned {@code acme-certification-wait-*} keys must move onto the {@code acme-certificate-wait-*}
 * keys the admin-service reads, and must not overwrite a value already stored under the new key.
 *
 * <p>Runs on H2 against a stand-in {@code configuration_properties} table — the production changelog
 * for that table is PostgreSQL-only (plpgsql triggers, partial indexes).
 */
class AcmeCertificateWaitRenameTest {

    private static final String H2_URL = "jdbc:h2:mem:acme-rename-test;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    private static final String OLD_ATTEMPTS = "xroad.proxy-ui-api.acme-certification-wait-attempts";
    private static final String OLD_INTERVAL = "xroad.proxy-ui-api.acme-certification-wait-interval";
    private static final String NEW_ATTEMPTS = "xroad.proxy-ui-api.acme-certificate-wait-attempts";
    private static final String NEW_INTERVAL = "xroad.proxy-ui-api.acme-certificate-wait-interval";

    @BeforeAll
    static void migrate() throws Exception {
        System.setProperty("liquibase.analytics.enabled", "false");
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            CommandScope update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "test-acme-rename-changelog.xml");
            update.addArgumentValue("url", H2_URL);
            update.addArgumentValue("username", H2_USER);
            update.addArgumentValue("password", H2_PASSWORD);
            update.execute();
        });
    }

    @Test
    void storedOverrideMovesToTheKeyTheCodeReads() throws Exception {
        assertEquals(Optional.of("9"), value(NEW_ATTEMPTS, "proxy-ui-api"));
    }

    @Test
    void staleKeysAreGone() throws Exception {
        assertEquals(Optional.empty(), value(OLD_ATTEMPTS, "proxy-ui-api"));
        assertEquals(Optional.empty(), value(OLD_INTERVAL, "collision-scope"));
    }

    @Test
    void valueAlreadyStoredUnderTheNewKeyWins() throws Exception {
        assertEquals(Optional.of("3"), value(NEW_INTERVAL, "collision-scope"));
        assertEquals(1, count(NEW_INTERVAL, "collision-scope"));
    }

    @Test
    void unrelatedPropertiesAreUntouched() throws Exception {
        assertEquals(Optional.of("3600"), value("xroad.proxy-ui-api.acme-renewal-interval", "proxy-ui-api"));
    }

    private static Optional<String> value(String propertyKey, String scope) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT property_value FROM configuration_properties WHERE property_key = ? AND scope = ?")) {
            stmt.setString(1, propertyKey);
            stmt.setString(2, scope);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
        }
        assertTrue(values.size() <= 1, "expected at most one row for " + propertyKey + "/" + scope + ", got " + values);
        return values.stream().findFirst();
    }

    private static int count(String propertyKey, String scope) throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM configuration_properties WHERE property_key = ? AND scope = ?")) {
            stmt.setString(1, propertyKey);
            stmt.setString(2, scope);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
