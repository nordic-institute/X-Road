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
package org.niis.xroad.edc.extension.cleaner;

import org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.schema.TransferProcessStoreStatements;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reads the ids of transfer processes in given states, directly via SQL, so that a pass holds one batch of ids
 * at a time instead of every matching {@code TransferProcess} entity.
 */
class TransferProcessIdReader {

    private final DataSourceRegistry dataSourceRegistry;
    private final String dataSourceName;
    private final TransactionContext transactionContext;
    private final QueryExecutor queryExecutor;
    private final TransferProcessStoreStatements statements;

    TransferProcessIdReader(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                            TransactionContext transactionContext, QueryExecutor queryExecutor,
                            TransferProcessStoreStatements statements) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.dataSourceName = dataSourceName;
        this.transactionContext = transactionContext;
        this.queryExecutor = queryExecutor;
        this.statements = statements;
    }

    /**
     * Reads one batch of transfer process ids in ascending order.
     *
     * @param stateCodes   only transfers in one of these states are returned
     * @param cutoffMillis epoch milliseconds; only transfers whose state changed before it are returned
     * @param afterId      only ids greater than this are returned; empty to start from the beginning
     * @param limit        the maximum batch size
     * @return up to {@code limit} transfer process ids
     */
    List<String> readIdsUpdatedBefore(Collection<Integer> stateCodes, long cutoffMillis, String afterId, int limit) {
        return transactionContext.execute(() -> {
            var dataSource = Objects.requireNonNull(dataSourceRegistry.resolve(dataSourceName),
                    "DataSource %s could not be resolved".formatted(dataSourceName));
            var arguments = new ArrayList<Object>(stateCodes);
            arguments.add(cutoffMillis);
            arguments.add(afterId);
            arguments.add(limit);
            try (var connection = dataSource.getConnection();
                 var ids = queryExecutor.query(connection, false, resultSet -> resultSet.getString(1),
                         selectStatement(stateCodes.size()), arguments.toArray())) {
                return ids.toList();
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    private String selectStatement(int stateCount) {
        var id = statements.getIdColumn();
        var placeholders = String.join(", ", Collections.nCopies(stateCount, "?"));
        return "SELECT %1$s FROM %2$s WHERE %3$s IN (%4$s) AND %5$s < ? AND %1$s > ? ORDER BY %1$s LIMIT ?"
                .formatted(id, statements.getTransferProcessTableName(), statements.getStateColumn(), placeholders,
                        statements.getStateTimestampColumn());
    }
}
