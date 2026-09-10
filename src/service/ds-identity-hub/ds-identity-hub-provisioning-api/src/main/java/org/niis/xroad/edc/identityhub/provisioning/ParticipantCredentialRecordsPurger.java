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
package org.niis.xroad.edc.identityhub.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.SQLException;

/**
 * Purges verifiable-credential and holder credential-request records left behind by a deleted
 * participant context. Neither record type is cleaned up by the upstream participant-context
 * deletion cascade, which would otherwise leave them visible to a context recreated with the same id.
 *
 * <p>Verifiable credentials are purged through the {@link CredentialStore} SPI, which supports
 * deletion. Holder credential requests have no delete operation anywhere in their SPI, so they are
 * purged with a direct statement against the table the upstream {@code holder-credential-request-store-sql}
 * extension creates.
 */
class ParticipantCredentialRecordsPurger extends AbstractSqlStore {

    private static final String PARTICIPANT_CONTEXT_ID_PROPERTY = "participantContextId";
    private static final String DELETE_HOLDER_CREDENTIAL_REQUESTS_SQL =
            "DELETE FROM edc_holder_credentialrequest WHERE participant_context_id = ?";

    private final CredentialStore credentialStore;

    ParticipantCredentialRecordsPurger(CredentialStore credentialStore, DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                       TransactionContext transactionContext, ObjectMapper objectMapper, QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.credentialStore = credentialStore;
    }

    void purge(String participantContextId) {
        purgeCredentials(participantContextId);
        purgeHolderCredentialRequests(participantContextId);
    }

    private void purgeCredentials(String participantContextId) {
        var query = QuerySpec.Builder.newInstance()
                .filter(new Criterion(PARTICIPANT_CONTEXT_ID_PROPERTY, "=", participantContextId))
                .limit(Integer.MAX_VALUE)
                .build();

        var result = credentialStore.query(query);
        if (result.failed()) {
            throw new EdcPersistenceException("Failed to query verifiable credentials for participant context '%s': %s"
                    .formatted(participantContextId, result.getFailureDetail()));
        }
        result.getContent().forEach(vc -> credentialStore.deleteById(vc.getId()));
    }

    private void purgeHolderCredentialRequests(String participantContextId) {
        transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                queryExecutor.execute(connection, DELETE_HOLDER_CREDENTIAL_REQUESTS_SQL, participantContextId);
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        });
    }
}
