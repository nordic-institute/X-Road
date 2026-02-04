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

    private static final String JDBC_URL_KEY = "xroad.db.%s.hibernate.connection.url";
    private static final String SCHEMA_KEY = "xroad.db.%s.hibernate.hikari.dataSource.currentSchema";
    private static final String USERNAME_KEY = "xroad.db.%s.hibernate.connection.username";
    private static final String PASSWORD_KEY = "xroad.db.%s.hibernate.connection.password";

    private DbPropertiesReader() {
    }

    /**
     * Reads the {@code db.properties} file from the given {@link Path} and returns the parsed database credentials.
     *
     * @param propertiesPath path to the {@code db.properties} file
     * @param dbKey          value that replaces the {@code serverconf} segment in property keys
     * @return {@link DbCredentials} containing the JDBC URL, username, password and schema
     */
    public static DbCredentials readDbCredentials(Path propertiesPath, String dbKey) {
        try {
            String resolvedDbKey = trimToNull(dbKey);
            if (resolvedDbKey == null) {
                throw new IllegalArgumentException("dbKey is null");
            }

            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(propertiesPath)) {
                properties.load(reader);
            }

            String jdbcUrl = requireProperty(properties, propertiesPath, formatKey(JDBC_URL_KEY, resolvedDbKey));
            String username = requireProperty(properties, propertiesPath, formatKey(USERNAME_KEY, resolvedDbKey));
            String password = requireProperty(properties, propertiesPath, formatKey(PASSWORD_KEY, resolvedDbKey));
            String schema = trimToNull(properties.getProperty(formatKey(SCHEMA_KEY, resolvedDbKey)));

            return new DbCredentials(jdbcUrl, username, password.toCharArray(), schema);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read DB credentials from " + propertiesPath, e);
        }
    }

    private static String requireProperty(Properties properties, Path propertiesPath, String key) {
        String value = trimToNull(properties.getProperty(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing property '" + key + "' in " + propertiesPath);
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

    private static String formatKey(String template, String keyPart) {
        return String.format(template, keyPart);
    }

}
