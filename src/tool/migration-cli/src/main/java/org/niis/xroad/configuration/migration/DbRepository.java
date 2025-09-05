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

package org.niis.xroad.configuration.migration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Properties;

@Slf4j
public class DbRepository implements AutoCloseable {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schema;
    private Connection connection;

    private static final String UPSERT_KEY_VALUE =
            "INSERT INTO configuration_properties(property_key, property_value) VALUES (?, ?) "
                    + " ON CONFLICT (property_key) WHERE scope IS NULL DO UPDATE SET property_value = EXCLUDED.property_value";
    private static final String UPSERT_KEY_VALUE_SCOPE =
            "INSERT INTO configuration_properties(property_key, property_value, scope) VALUES (?, ?, ?) "
                    + " ON CONFLICT (property_key, scope) WHERE scope IS NOT NULL DO UPDATE SET property_value = EXCLUDED.property_value";

    private static final String UPSERT_CONFIGURATION_ANCHOR =
            "INSERT INTO configuration_client(name, content) VALUES ('configuration-anchor', ?) "
                    + " ON CONFLICT (name) DO UPDATE SET content = EXCLUDED.content";

    public DbRepository(String dbPropertiesFilePath) {
        try (FileInputStream fis = new FileInputStream(dbPropertiesFilePath)) {
            Properties props = new Properties();
            props.load(fis);

            // todo recheck prop names in xrd7
            this.jdbcUrl = props.getProperty("xroad.db.serverconf.hibernate.connection.url");
            this.username = props.getProperty("xroad.db.serverconf.hibernate.connection.username");
            this.password = props.getProperty("xroad.db.serverconf.hibernate.connection.password");
            this.schema = props.getProperty("xroad.db.serverconf.hibernate.hikari.dataSource.currentSchema");
        } catch (IOException e) {
            throw new MigrationException("Failed to read DB credentials from [%s]".formatted(dbPropertiesFilePath), e);
        }
        init();
    }

    private void init() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(jdbcUrl);
        ds.setUser(username);
        ds.setPassword(password);
        if (StringUtils.isNotBlank(schema)) {
            ds.setCurrentSchema(schema);
        }
        try {
            this.connection = ds.getConnection();
        } catch (Exception e) {
            throw new MigrationException("Failed to initialize DB connection", e);
        }
    }

    @Override
    public void close() {
        try {
            this.connection.close();
        } catch (Exception e) {
            log.warn("Failed to close DB connection", e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public void saveProperty(String key, String value, String scope) {
        boolean withScope = StringUtils.isNotBlank(scope);

        String sql = withScope ? UPSERT_KEY_VALUE_SCOPE : UPSERT_KEY_VALUE;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            if (withScope) {
                statement.setString(3, scope);
            }
            statement.executeUpdate();
        } catch (Exception e) {
            throw new MigrationException("Failed to save configuration property %s".formatted(key), e);
        }
    }

    public void saveConfigurationAnchor(String configurationAnchor) {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_CONFIGURATION_ANCHOR)) {
            statement.setString(1, configurationAnchor);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new MigrationException("Failed to save configuration anchor", e);
        }
    }

}
