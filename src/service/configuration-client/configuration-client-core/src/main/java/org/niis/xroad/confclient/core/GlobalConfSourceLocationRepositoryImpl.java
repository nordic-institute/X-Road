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
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.sql.DataSource;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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
            try {
                conn.setAutoCommit(false); // start transaction

                QueryRunner queryRunner = new QueryRunner();
                queryRunner.update(conn, deleteSql, globalConfSourceLocation.getInstanceIdentifier());
                queryRunner.batch(conn, insertSql, buildBatchParams(conn, globalConfSourceLocation));

                conn.commit();
            } catch (SQLException e) {
                log.error("DB exception occurred", e);
                conn.rollback();
            } finally {
                conn.setAutoCommit(true); // reset auto-commit
            }
        } catch (SQLException e) {
            throw XrdRuntimeException.systemInternalError("Database connection error", e);
        }
    }

    private Object[][] buildBatchParams(Connection connection, GlobalConfSourceLocation globalConfSourceLocation) throws SQLException {
        List<Object[]> params = new ArrayList<>();

        for (Map.Entry<String, VerificationCertificates> entry : globalConfSourceLocation.getLocations().entrySet()) {
            params.add(new Object[]{globalConfSourceLocation.getInstanceIdentifier(),
                    entry.getKey(),
                    createSqlArray(connection, entry.getValue().internalVerificationCerts()),
                    createSqlArray(connection, entry.getValue().externalVerificationCerts())
            });
        }
        return params.toArray(new Object[0][]);
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

        QueryRunner queryRunner = new QueryRunner(dataSource);
        ResultSetHandler<Boolean> handler = ResultSet::next;
        try {
            return queryRunner.query(sql, handler, instanceIdentifier);
        } catch (SQLException e) {
            throw XrdRuntimeException.systemInternalError("Database error in hasLocations", e);
        }
    }

    @Override
    public Map<String, VerificationCertificates> getByInstanceIdentifier(String instanceIdentifier) {
        String sql = "SELECT instance_identifier, address, internal_verification_certs, external_verification_certs "
                + " FROM globalconf_source WHERE instance_identifier = ?";

        ResultSetHandler<Map<String, VerificationCertificates>> resultSetHandler = rs -> {
            Map<String, VerificationCertificates> result = new HashMap<>();
            while (rs.next()) {
                String address = rs.getString("address");

                result.put(address, new VerificationCertificates(
                        getArray(rs, "internal_verification_certs"),
                        getArray(rs, "external_verification_certs")
                ));
            }
            return result;
        };

        QueryRunner queryRunner = new QueryRunner(dataSource);
        try {
            return queryRunner.query(sql, resultSetHandler, instanceIdentifier);
        } catch (SQLException e) {
            throw XrdRuntimeException.systemInternalError("Failed to fetch GlobalConfSourceLocation", e);
        }
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
