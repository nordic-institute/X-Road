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

import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantContextConfigDeleterTest {

    private static final String DATASOURCE_NAME = "default";

    @Mock
    private DataSourceRegistry dataSourceRegistry;
    @Mock
    private QueryExecutor queryExecutor;
    @Mock
    private DataSource dataSource;

    private Connection connection;
    private ParticipantContextConfigDeleter deleter;

    @BeforeEach
    void setUp() throws SQLException {
        connection = mock(Connection.class);
        when(dataSourceRegistry.resolve(DATASOURCE_NAME)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);

        deleter = new ParticipantContextConfigDeleter(dataSourceRegistry, DATASOURCE_NAME,
                new NoopTransactionContext(), queryExecutor, null);
    }

    @Test
    void deleteByParticipantContextIdIssuesDeleteScopedToTheParticipant() {
        deleter.deleteByParticipantContextId("ctx-1");

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(queryExecutor).execute(eq(connection), sqlCaptor.capture(), eq("ctx-1"));

        assertThat(sqlCaptor.getValue())
                .contains("DELETE FROM edc_participant_context_config")
                .contains("participant_context_id = ?");
    }

    @Test
    void deleteByParticipantContextIdOfAbsentRowDoesNotThrow() {
        when(queryExecutor.execute(any(), any(), any())).thenReturn(0);

        deleter.deleteByParticipantContextId("nonexistent-ctx");

        verify(queryExecutor).execute(eq(connection), any(), eq("nonexistent-ctx"));
    }
}
