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

package org.niis.xroad.migration.utils;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DbPropertiesReader {

    private static final String SERVERCONF_DB_KEY = "serverconf";

    private static final String JDBC_URL_KEY = "xroad.db.%s.hibernate.connection.url";
    private static final String SCHEMA_KEY = "xroad.db.%s.hibernate.hikari.dataSource.currentSchema";
    private static final String USERNAME_KEY = "xroad.db.%s.hibernate.connection.username";
    private static final String PASSWORD_KEY = "xroad.db.%s.hibernate.connection.password";

    private static final PropertyKeys CENTRAL_SERVER_KEYS = new PropertyKeys(
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.datasource.password",
            "spring.datasource.hikari.data-source-properties.currentSchema");

    private DbPropertiesReader() {
    }

    /**
     * Reads the database settings a product stores in its {@code db.properties} file.
     *
     * @param propertiesPath   path to the {@code db.properties} file
     * @param installationType decides the key family: {@code xroad.db.serverconf.hibernate.*} for the Security Server,
     *                         {@code spring.datasource.*} for the Central Server
     * @return {@link DbCredentials} containing the JDBC URL, username, password and schema
     */
    public static DbCredentials readForInstallation(Path propertiesPath, InstallationType installationType) {
        return switch (installationType) {
            case SECURITY_SERVER -> read(propertiesPath, securityServerKeys(SERVERCONF_DB_KEY));
            case CENTRAL_SERVER -> read(propertiesPath, CENTRAL_SERVER_KEYS);
        };
    }

    /**
     * Reads the {@code db.properties} file from the given {@link Path} and returns the parsed database credentials.
     *
     * @param propertiesPath path to the {@code db.properties} file
     * @param dbKey          value that replaces the {@code serverconf} segment in property keys
     * @return {@link DbCredentials} containing the JDBC URL, username, password and schema
     */
    public static DbCredentials readDbCredentials(Path propertiesPath, String dbKey) {
        String resolvedDbKey = trimToNull(dbKey);
        if (resolvedDbKey == null) {
            throw new IllegalStateException("Failed to read DB credentials from " + propertiesPath + ": dbKey is null");
        }
        return read(propertiesPath, securityServerKeys(resolvedDbKey));
    }

    private static PropertyKeys securityServerKeys(String dbKey) {
        return new PropertyKeys(
                JDBC_URL_KEY.formatted(dbKey),
                USERNAME_KEY.formatted(dbKey),
                PASSWORD_KEY.formatted(dbKey),
                SCHEMA_KEY.formatted(dbKey));
    }

    private static DbCredentials read(Path propertiesPath, PropertyKeys keys) {
        try {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(propertiesPath)) {
                properties.load(reader);
            }

            String jdbcUrl = requireProperty(properties, keys.url());
            String username = requireProperty(properties, keys.username());
            String password = requireProperty(properties, keys.password());
            String schema = trimToNull(properties.getProperty(keys.schema()));

            return new DbCredentials(jdbcUrl, username, password.toCharArray(), schema);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to read DB credentials from " + propertiesPath + ": " + e.getMessage(), e);
        }
    }

    private static String requireProperty(Properties properties, String key) {
        String value = trimToNull(properties.getProperty(key));
        if (value == null) {
            throw new IllegalArgumentException("missing property '" + key + "'");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PropertyKeys(String url, String username, String password, String schema) {
    }

}
