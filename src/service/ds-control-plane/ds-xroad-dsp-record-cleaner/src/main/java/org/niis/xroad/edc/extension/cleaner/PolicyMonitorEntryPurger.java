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

import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorEntryStates;
import org.eclipse.edc.connector.policy.monitor.store.sql.schema.PolicyMonitorStatements;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Deletes finished policy monitor entries directly via SQL, because {@code PolicyMonitorStore} has no delete.
 * Entries still {@code STARTED} are never touched: the monitor refreshes their {@code updated_at} on every
 * evaluation and moves an entry whose transfer is gone to a finished state.
 */
class PolicyMonitorEntryPurger {

    private final DataSourceRegistry dataSourceRegistry;
    private final String dataSourceName;
    private final TransactionContext transactionContext;
    private final QueryExecutor queryExecutor;
    private final PolicyMonitorStatements statements;

    PolicyMonitorEntryPurger(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                             TransactionContext transactionContext, QueryExecutor queryExecutor,
                             PolicyMonitorStatements statements) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.dataSourceName = dataSourceName;
        this.transactionContext = transactionContext;
        this.queryExecutor = queryExecutor;
        this.statements = statements;
    }

    /**
     * Deletes finished entries last updated before the cutoff. Rows locked by a concurrent purger are skipped,
     * so each row is deleted by exactly one caller.
     *
     * @param cutoffMillis epoch milliseconds; entries updated before it are deleted
     * @return the number of entries deleted
     */
    int purgeFinishedUpdatedBefore(long cutoffMillis) {
        return transactionContext.execute(() -> {
            var dataSource = Objects.requireNonNull(dataSourceRegistry.resolve(dataSourceName),
                    "DataSource %s could not be resolved".formatted(dataSourceName));
            try (var connection = dataSource.getConnection()) {
                return queryExecutor.execute(connection, deleteStatement(),
                        PolicyMonitorEntryStates.STARTED.code(), cutoffMillis);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }

    private String deleteStatement() {
        var table = statements.getPolicyMonitorTable();
        var id = statements.getIdColumn();
        return "DELETE FROM %1$s WHERE %2$s IN (SELECT %2$s FROM %1$s WHERE %3$s <> ? AND %4$s < ? FOR UPDATE SKIP LOCKED)"
                .formatted(table, id, statements.getStateColumn(), statements.getUpdatedAtColumn());
    }
}
