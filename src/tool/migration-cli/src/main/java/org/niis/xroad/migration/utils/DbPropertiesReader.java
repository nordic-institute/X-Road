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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DbPropertiesReader {

    private static final PropertyKeys SECURITY_SERVER_KEYS = new PropertyKeys(
            "xroad.db.serverconf.hibernate.connection.url",
            "xroad.db.serverconf.hibernate.connection.username",
            "xroad.db.serverconf.hibernate.connection.password",
            "xroad.db.serverconf.hibernate.hikari.dataSource.currentSchema");

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
            case SECURITY_SERVER -> read(propertiesPath, SECURITY_SERVER_KEYS);
            case CENTRAL_SERVER -> read(propertiesPath, CENTRAL_SERVER_KEYS);
        };
    }

    private static DbCredentials read(Path propertiesPath, PropertyKeys keys) {
        Properties properties = load(propertiesPath);

        String jdbcUrl = requireProperty(properties, keys.url(), propertiesPath);
        String username = requireProperty(properties, keys.username(), propertiesPath);
        String password = requireProperty(properties, keys.password(), propertiesPath);
        String schema = trimToNull(properties.getProperty(keys.schema()));

        return new DbCredentials(jdbcUrl, username, password.toCharArray(), schema);
    }

    private static Properties load(Path propertiesPath) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesPath)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to read DB credentials from " + propertiesPath, e);
        }
        return properties;
    }

    private static String requireProperty(Properties properties, String key, Path propertiesPath) {
        String value = trimToNull(properties.getProperty(key));
        if (value == null) {
            throw new IllegalStateException(
                    "Failed to read DB credentials from " + propertiesPath + ": missing property '" + key + "'");
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
