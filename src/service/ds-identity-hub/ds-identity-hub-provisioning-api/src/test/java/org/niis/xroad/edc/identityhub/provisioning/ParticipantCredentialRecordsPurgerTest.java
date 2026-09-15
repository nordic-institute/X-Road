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
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantCredentialRecordsPurgerTest {

    private static final String DATASOURCE_NAME = "default";
    private static final String PARTICIPANT_CONTEXT_ID = "ctx-1";
    private static final String DELETE_HOLDER_CREDENTIAL_REQUESTS_SQL =
            "DELETE FROM edc_holder_credentialrequest WHERE participant_context_id = ?";

    @Mock
    private CredentialStore credentialStore;
    @Mock
    private DataSourceRegistry dataSourceRegistry;
    @Mock
    private TransactionContext transactionContext;
    @Mock
    private QueryExecutor queryExecutor;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;

    private ParticipantCredentialRecordsPurger purger;

    @BeforeEach
    void setUp() {
        purger = new ParticipantCredentialRecordsPurger(credentialStore, dataSourceRegistry, DATASOURCE_NAME,
                transactionContext, new ObjectMapper(), queryExecutor);
    }

    @Test
    void purgeDeletesEveryMatchingVerifiableCredentialAndHolderCredentialRequests() throws Exception {
        var vc1 = mock(VerifiableCredentialResource.class);
        when(vc1.getId()).thenReturn("vc-1");
        var vc2 = mock(VerifiableCredentialResource.class);
        when(vc2.getId()).thenReturn("vc-2");
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(vc1, vc2)));
        when(credentialStore.deleteById(anyString())).thenReturn(StoreResult.success());
        when(queryExecutor.execute(eq(connection), eq(DELETE_HOLDER_CREDENTIAL_REQUESTS_SQL), eq(PARTICIPANT_CONTEXT_ID))).thenReturn(3);
        stubJdbcConnection();

        var counts = purger.purge(PARTICIPANT_CONTEXT_ID);

        assertThat(counts.credentialCount()).isEqualTo(2);
        assertThat(counts.holderRequestCount()).isEqualTo(3);
        var querySpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(credentialStore).query(querySpecCaptor.capture());
        assertThat(querySpecCaptor.getValue().getFilterExpression())
                .singleElement()
                .satisfies(criterion -> {
                    assertThat(criterion.getOperandLeft()).isEqualTo("participantContextId");
                    assertThat(criterion.getOperator()).isEqualTo("=");
                    assertThat(criterion.getOperandRight()).isEqualTo(PARTICIPANT_CONTEXT_ID);
                });
        verify(credentialStore).deleteById("vc-1");
        verify(credentialStore).deleteById("vc-2");

        verify(queryExecutor).execute(eq(connection), eq(DELETE_HOLDER_CREDENTIAL_REQUESTS_SQL), eq(PARTICIPANT_CONTEXT_ID));
    }

    @Test
    void purgeOfParticipantWithNoCredentialsIsANoop() throws Exception {
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of()));
        stubJdbcConnection();

        var counts = purger.purge(PARTICIPANT_CONTEXT_ID);

        assertThat(counts.credentialCount()).isZero();
        assertThat(counts.holderRequestCount()).isZero();
        verify(credentialStore, never()).deleteById(anyString());
        verify(queryExecutor).execute(eq(connection), eq(DELETE_HOLDER_CREDENTIAL_REQUESTS_SQL), eq(PARTICIPANT_CONTEXT_ID));
    }

    @Test
    void purgePropagatesCredentialQueryFailureAndSkipsHolderCredentialRequestPurge() {
        when(credentialStore.query(any())).thenReturn(StoreResult.generalError("storage error"));

        assertThatThrownBy(() -> purger.purge(PARTICIPANT_CONTEXT_ID))
                .isInstanceOf(EdcPersistenceException.class);

        verify(queryExecutor, never()).execute(any(), anyString(), any());
    }

    @Test
    void purgeWrapsSqlFailureFromHolderCredentialRequestDelete() throws Exception {
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of()));
        stubTransactionExecutesImmediately();
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        assertThatThrownBy(() -> purger.purge(PARTICIPANT_CONTEXT_ID))
                .isInstanceOf(EdcPersistenceException.class);
    }

    private void stubJdbcConnection() throws SQLException {
        stubTransactionExecutesImmediately();
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    private void stubTransactionExecutesImmediately() {
        doAnswer(invocation -> {
            TransactionContext.ResultTransactionBlock<?> block = invocation.getArgument(0);
            return block.execute();
        }).when(transactionContext).execute(any(TransactionContext.ResultTransactionBlock.class));
    }
}
