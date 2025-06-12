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

package org.niis.xroad.confclient.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
public class GlobalConfSourceLocationRepositoryImpl implements GlobalConfSourceLocationRepository {

    private final DataSource dataSource;

    @SuppressWarnings("checkstyle:MagicNumber")
    @Override
    public void saveGlobalConfLocation(GlobalConfSourceLocation globalConfSourceLocation) {
        String deleteSql = "DELETE FROM globalconf_source WHERE instance_identifier = ?";
        String insertSql = "INSERT INTO globalconf_source "
                + " (instance_identifier, address, internal_verification_certs, external_verification_certs) "
                + " VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false); // start transaction

            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

                deleteStmt.setString(1, globalConfSourceLocation.getInstanceIdentifier());
                deleteStmt.executeUpdate();

                globalConfSourceLocation.getLocations().forEach((address, certs) -> {
                    try {
                        insertStmt.setString(1, globalConfSourceLocation.getInstanceIdentifier());
                        insertStmt.setString(2, address);
                        insertStmt.setArray(3, createSqlArray(conn, certs.internalVerificationCerts()));
                        insertStmt.setArray(4, createSqlArray(conn, certs.externalVerificationCerts()));
                        insertStmt.addBatch();
                    } catch (SQLException e) {
                        throw new RuntimeException("Error preparing batch insert", e);
                    }
                });
                insertStmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                log.error("DB exception occurred", e);
                conn.rollback();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database connection error", e);
        }
    }

    private Array createSqlArray(Connection conn, List<byte[]> list) throws SQLException {
        if (list == null || list.isEmpty()) {
            return conn.createArrayOf("VARCHAR", new String[0]);
        }

        String[] base64Array = list.stream()
                .map(bytes -> Base64.getEncoder().encodeToString(bytes))
                .toArray(String[]::new);

        return conn.createArrayOf("VARCHAR", base64Array);
    }

    @Override
    public boolean hasLocations(String instanceIdentifier) {
        String sql = "SELECT 1 FROM globalconf_source WHERE instance_identifier = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, instanceIdentifier);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database error in hasLocations", e);
        }
    }

    @Override
    public Map<String, VerificationCertificates> getByInstanceIdentifier(String instanceIdentifier) {
        String sql = "SELECT instance_identifier, address, internal_verification_certs, external_verification_certs "
                + " FROM globalconf_source WHERE instance_identifier = ?";

        GlobalConfSourceLocation location = new GlobalConfSourceLocation();
        location.setInstanceIdentifier(instanceIdentifier);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, instanceIdentifier);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String address = rs.getString("address");

                    location.getLocations().put(address, new VerificationCertificates(
                            getArray(rs, "internal_verification_certs"),
                            getArray(rs, "external_verification_certs")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch GlobalConfSourceLocation", e);
        }

        return location.getLocations();
    }

    private List<byte[]> getArray(ResultSet rs, String columnName) throws SQLException {
        Array base64Array = rs.getArray(columnName);

        if (base64Array == null) {
            return List.of();
        }

        return Arrays.stream((String[]) base64Array.getArray())
                .filter(Objects::nonNull)
                .map(s -> Base64.getDecoder().decode(s))
                .toList();
    }

}
