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

package org.eclipse.edc.virtual.controlplane.tasks.executor;

import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Tries a transaction-scoped Postgres advisory lock on one contract negotiation or transfer process, so that only
 * one worker advances a given process even when several workers, or several control-plane instances sharing one
 * database, pick up duplicate or retried tasks for it. A try returns at once: {@code true} once locked, or
 * {@code false} when another transaction already holds it (busy); it never blocks.
 *
 * <p>{@code pg_try_advisory_xact_lock(kind, hashtext(processId))} takes no row lock, so it blocks neither EDC's own
 * state save after a send, which runs in its own transaction on the OkHttp callback thread while the worker waits
 * for the send with no timeout, nor an inbound DSP save on a Jetty thread. A {@code SELECT ... FOR UPDATE} on the
 * process row deadlocks against that save instead, because the worker holds the row lock the save needs while it
 * waits for the save to return. The two kinds (negotiation, transfer) use distinct constant first keys, so a
 * negotiation and a transfer process that happen to share an id never contend with each other.
 *
 * <p>This must run on the datasource the task store itself uses, inside the same
 * {@link org.eclipse.edc.transaction.spi.TransactionContext#execute} block as the task fetch, resolved through
 * {@link DataSourceRegistry#resolve}: only then does the lock live on the connection bound to the task transaction,
 * so it is released exactly when that transaction commits or rolls back. Raw JDBC; neither the negotiation store
 * nor the transfer process store is involved.
 *
 * <p>An advisory lock sees no rows, so it cannot tell a locked, still-existing process from a locked, deleted one.
 * A caller that must tell the two apart checks existence separately, with a plain query (no {@code FOR UPDATE})
 * against the stock EDC schema: the negotiation row by {@code edc_contract_negotiation.id}, the transfer
 * row by {@code edc_transfer_process.transferprocess_id}.
 */
public class ProcessLock {

    private static final int CONTRACT_NEGOTIATION_LOCK_KIND = 1;
    private static final int TRANSFER_PROCESS_LOCK_KIND = 2;

    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_xact_lock(?, hashtext(?))";
    private static final String CONTRACT_NEGOTIATION_EXISTS_SQL = "SELECT 1 FROM edc_contract_negotiation WHERE id = ?";
    private static final String TRANSFER_PROCESS_EXISTS_SQL = "SELECT 1 FROM edc_transfer_process WHERE transferprocess_id = ?";

    private final DataSourceRegistry dataSourceRegistry;
    private final String dataSourceName;

    public ProcessLock(DataSourceRegistry dataSourceRegistry, String dataSourceName) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.dataSourceName = dataSourceName;
    }

    /**
     * @return {@code true} once the negotiation is locked for the current transaction, {@code false} when another
     *         transaction already holds it
     */
    public boolean tryLockContractNegotiation(String negotiationId) {
        return tryLock(CONTRACT_NEGOTIATION_LOCK_KIND, negotiationId);
    }

    /**
     * @return {@code true} once the transfer process is locked for the current transaction, {@code false} when
     *         another transaction already holds it
     */
    public boolean tryLockTransferProcess(String transferProcessId) {
        return tryLock(TRANSFER_PROCESS_LOCK_KIND, transferProcessId);
    }

    /**
     * @return {@code true} when the negotiation row still exists
     */
    public boolean contractNegotiationExists(String negotiationId) {
        return exists(CONTRACT_NEGOTIATION_EXISTS_SQL, negotiationId);
    }

    /**
     * @return {@code true} when the transfer process row still exists
     */
    public boolean transferProcessExists(String transferProcessId) {
        return exists(TRANSFER_PROCESS_EXISTS_SQL, transferProcessId);
    }

    private boolean tryLock(int kind, String processId) {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TRY_LOCK_SQL)) {
                statement.setInt(1, kind);
                statement.setString(2, processId);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getBoolean(1);
                }
            }
        });
    }

    private boolean exists(String sql, String id) {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    private boolean withConnection(SqlBooleanQuery query) {
        var dataSource = Objects.requireNonNull(dataSourceRegistry.resolve(dataSourceName),
                format("DataSource %s could not be resolved", dataSourceName));
        try (var connection = dataSource.getConnection()) {
            return query.execute(connection);
        } catch (SQLException e) {
            throw new EdcPersistenceException(e);
        }
    }

    @FunctionalInterface
    private interface SqlBooleanQuery {
        boolean execute(Connection connection) throws SQLException;
    }
}
