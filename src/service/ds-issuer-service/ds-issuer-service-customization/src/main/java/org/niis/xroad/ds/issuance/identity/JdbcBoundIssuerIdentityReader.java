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
package org.niis.xroad.ds.issuance.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.niis.xroad.ds.identity.IssuerDidFormat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.DataspaceConfigKeys.ISSUER_DID_PORT;

/**
 * Reads the bound issuer identity straight from the Central Server's own database, over a plain JDBC
 * connection held as the {@code ds-issuer-service} role: the issuer host from {@code system_parameters},
 * the published DID port from {@code configuration_properties} (or its packaged default when no row
 * exists), the DID rebuilt from both with {@link IssuerDidFormat}, and the matching {@code issuer_dids}
 * row.
 */
public class JdbcBoundIssuerIdentityReader implements BoundIssuerIdentityReader, AutoCloseable {

    private static final String ISSUER_HOST_KEY = "issuerServiceHost";

    private final HikariDataSource dataSource;

    /**
     * @param url the Central Server database's JDBC URL
     * @param username the {@code ds-issuer-service} role's username
     * @param password the {@code ds-issuer-service} role's password
     * @param schema the Central Server schema the issuer identity tables live in (e.g. {@code centerui})
     */
    public JdbcBoundIssuerIdentityReader(String url, String username, String password, String schema) {
        var hikariConfig = new HikariConfig();
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setSchema(schema);
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public Optional<BoundIssuerIdentity> read() {
        try (Connection connection = dataSource.getConnection()) {
            var host = readHost(connection);
            if (host.isEmpty()) {
                return Optional.empty();
            }
            var didPort = readDidPort(connection);
            var did = IssuerDidFormat.didFor(host.get(), didPort);
            return readIssuerDid(connection, did);
        } catch (SQLException e) {
            throw new IllegalStateException("bound-issuer-identity-reader: could not read the bound issuer identity", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static Optional<String> readHost(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM system_parameters WHERE key = ?")) {
            statement.setString(1, ISSUER_HOST_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(resultSet.getString("value")) : Optional.empty();
            }
        }
    }

    private static int readDidPort(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT property_value FROM configuration_properties WHERE property_key = ? AND ha_node_name IS NULL")) {
            statement.setString(1, ISSUER_DID_PORT.key());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? ISSUER_DID_PORT.convert(resultSet.getString("property_value"))
                        : ISSUER_DID_PORT.convertedDefaultValue();
            }
        }
    }

    private static Optional<BoundIssuerIdentity> readIssuerDid(Connection connection, String did) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT did, host, did_port, issuance_url, statuslist_base_url FROM issuer_dids WHERE did = ?")) {
            statement.setString(1, did);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new BoundIssuerIdentity(
                        resultSet.getString("did"),
                        resultSet.getString("host"),
                        resultSet.getInt("did_port"),
                        resultSet.getString("issuance_url"),
                        resultSet.getString("statuslist_base_url")));
            }
        }
    }
}
