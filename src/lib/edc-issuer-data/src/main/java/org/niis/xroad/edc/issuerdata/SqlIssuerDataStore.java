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

package org.niis.xroad.edc.issuerdata;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;

/**
 * The {@code xroad_issuer_data} table behind the given data source. Times go in and out as UTC without a time zone,
 * so the stored values do not depend on the time zone of the node or of the database session.
 */
@RequiredArgsConstructor
public class SqlIssuerDataStore implements IssuerDataStore {

    private static final String UPSERT = """
            INSERT INTO xroad_issuer_data (kind, key, payload, fetched_at, valid_until) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (kind, key) DO UPDATE
            SET payload = EXCLUDED.payload, fetched_at = EXCLUDED.fetched_at, valid_until = EXCLUDED.valid_until
            WHERE xroad_issuer_data.fetched_at < EXCLUDED.fetched_at""";

    private static final String SELECT = """
            SELECT payload, fetched_at, valid_until FROM xroad_issuer_data WHERE kind = ? AND key = ?""";

    private static final String DELETE_EXPIRED = """
            DELETE FROM xroad_issuer_data WHERE (kind, key) IN (
                SELECT kind, key FROM xroad_issuer_data WHERE valid_until < ? FOR UPDATE SKIP LOCKED)""";

    private final DataSourceRegistry dataSourceRegistry;
    private final String dataSourceName;
    private final TransactionContext transactionContext;
    private final QueryExecutor queryExecutor;

    @Override
    public StoreResult<Void> write(IssuerDataKind kind, String key, StoredIssuerData data) {
        return inConnection(connection -> {
            queryExecutor.execute(connection, UPSERT, kind.name(), key, data.payload(),
                    utc(data.fetchedAt()), utc(data.validUntil()));
            return StoreResult.success();
        });
    }

    @Override
    public StoreResult<StoredIssuerData> read(IssuerDataKind kind, String key) {
        return inConnection(connection -> {
            var row = queryExecutor.single(connection, false, SqlIssuerDataStore::toStoredData, SELECT, kind.name(), key);
            return row == null ? StoreResult.<StoredIssuerData>notFound("No stored %s data for %s".formatted(kind, key))
                    : StoreResult.success(row);
        });
    }

    @Override
    public StoreResult<Integer> deleteExpired(Instant now) {
        return inConnection(connection -> StoreResult.success(queryExecutor.execute(connection, DELETE_EXPIRED, utc(now))));
    }

    private <T> StoreResult<T> inConnection(Function<Connection, StoreResult<T>> action) {
        try {
            return transactionContext.execute(() -> {
                var dataSource = dataSourceRegistry.resolve(dataSourceName);
                if (dataSource == null) {
                    return StoreResult.<T>generalError("DataSource %s could not be resolved".formatted(dataSourceName));
                }
                try (var connection = dataSource.getConnection()) {
                    return action.apply(connection);
                } catch (SQLException e) {
                    throw new EdcPersistenceException(e);
                }
            });
        } catch (EdcException e) {
            return StoreResult.generalError(e.getMessage());
        }
    }

    private static StoredIssuerData toStoredData(ResultSet rs) throws SQLException {
        return new StoredIssuerData(rs.getString("payload"), instant(rs, "fetched_at"), instant(rs, "valid_until"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
