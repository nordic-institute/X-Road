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
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the holder-credential-request purge against a real Postgres schema, since that path has
 * no store abstraction to fall back on and runs a hand-written statement against the table the upstream
 * {@code holder-credential-request-store-sql} extension owns. The {@link CredentialStore} side of the
 * purge is a mock here (returning no records), since it is already covered without a real database by
 * {@link ParticipantCredentialRecordsPurgerTest}.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class ParticipantCredentialRecordsPurgerPostgresTest {

    private static final String TABLE = "edc_holder_credentialrequest";

    private ParticipantCredentialRecordsPurger purger;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws IOException {
        var credentialStore = mock(CredentialStore.class);
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of()));

        purger = new ParticipantCredentialRecordsPurger(credentialStore, extension.getDataSourceRegistry(),
                extension.getDatasourceName(), extension.getTransactionContext(), new ObjectMapper(), queryExecutor);

        extension.runQuery(TestUtils.getResourceFileContentAsString("holder-credential-request-schema.sql"));
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE " + TABLE + " CASCADE");
        extension.runQuery("DROP TABLE edc_lease CASCADE");
    }

    @Test
    void purgeDeletesOnlyHolderCredentialRequestsOfTheGivenParticipant(PostgresqlStoreSetupExtension extension,
                                                                       QueryExecutor queryExecutor) throws SQLException {
        insertHolderCredentialRequest(extension, queryExecutor, "req-1", "ctx-1");
        insertHolderCredentialRequest(extension, queryExecutor, "req-2", "ctx-1");
        insertHolderCredentialRequest(extension, queryExecutor, "req-3", "ctx-2");

        purger.purge("ctx-1");

        assertThat(countHolderCredentialRequestsFor(extension, "ctx-1")).isZero();
        assertThat(countHolderCredentialRequestsFor(extension, "ctx-2")).isEqualTo(1);
    }

    @Test
    void purgeOfParticipantWithNoHolderCredentialRequestsIsANoop(PostgresqlStoreSetupExtension extension,
                                                                  QueryExecutor queryExecutor) throws SQLException {
        insertHolderCredentialRequest(extension, queryExecutor, "req-1", "ctx-2");

        purger.purge("ctx-1");

        assertThat(countHolderCredentialRequestsFor(extension, "ctx-2")).isEqualTo(1);
    }

    @Test
    void participantRecreatedWithTheSameContextIdAfterPurgeSeesOnlyItsOwnFreshRequest(
            PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws SQLException {
        insertHolderCredentialRequest(extension, queryExecutor, "req-old", "ctx-1");

        purger.purge("ctx-1");
        insertHolderCredentialRequest(extension, queryExecutor, "req-new", "ctx-1");

        assertThat(countHolderCredentialRequestsFor(extension, "ctx-1")).isEqualTo(1);
    }

    private void insertHolderCredentialRequest(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor,
                                               String id, String participantContextId) throws SQLException {
        try (var connection = extension.getDataSourceRegistry().resolve(extension.getDatasourceName()).getConnection()) {
            queryExecutor.execute(connection,
                    "INSERT INTO " + TABLE
                            + " (id, state, state_count, created_at, updated_at, participant_context_id, issuer_did, ids_and_formats)"
                            + " VALUES (?, 100, 0, 0, 0, ?, 'did:web:issuer.example', '{}')",
                    id, participantContextId);
        }
    }

    private long countHolderCredentialRequestsFor(PostgresqlStoreSetupExtension extension, String participantContextId)
            throws SQLException {
        try (var connection = extension.getConnection();
                var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + TABLE + " WHERE participant_context_id = ?")) {
            statement.setString(1, participantContextId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
