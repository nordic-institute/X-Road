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
package org.niis.xroad.edc.controlplane.provisioning;

import org.eclipse.edc.connector.store.sql.participantcontext.config.ParticipantContextConfigStoreStatements;
import org.eclipse.edc.connector.store.sql.participantcontext.config.schema.postgres.PostgresDialectStatementsConfig;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Deletes a participant context's configuration row directly via SQL. The upstream {@code
 * ParticipantContextConfigStore} only supports save/get, so this fills the gap needed to remove the
 * STS-bound configuration when a participant context is deleted, targeting the same table the upstream
 * store writes to.
 */
class ParticipantContextConfigDeleter {

    private final DataSourceRegistry dataSourceRegistry;
    private final String dataSourceName;
    private final TransactionContext transactionContext;
    private final QueryExecutor queryExecutor;
    private final ParticipantContextConfigStoreStatements statements;

    ParticipantContextConfigDeleter(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                    TransactionContext transactionContext, QueryExecutor queryExecutor,
                                    ParticipantContextConfigStoreStatements statements) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.dataSourceName = dataSourceName;
        this.transactionContext = transactionContext;
        this.queryExecutor = queryExecutor;
        this.statements = statements != null ? statements : new PostgresDialectStatementsConfig();
    }

    void deleteByParticipantContextId(String participantContextId) {
        transactionContext.execute(() -> {
            var dataSource = Objects.requireNonNull(dataSourceRegistry.resolve(dataSourceName),
                    "DataSource %s could not be resolved".formatted(dataSourceName));
            try (var connection = dataSource.getConnection()) {
                var sql = "DELETE FROM %s WHERE %s = ?".formatted(
                        statements.getParticipantContextConfigTable(), statements.getIdColumn());
                queryExecutor.execute(connection, sql, participantContextId);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }
}
