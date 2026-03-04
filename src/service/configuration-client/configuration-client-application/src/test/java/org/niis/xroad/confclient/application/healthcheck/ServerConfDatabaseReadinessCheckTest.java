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
package org.niis.xroad.confclient.application.healthcheck;

import jakarta.inject.Provider;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerConfDatabaseReadinessCheckTest {

    @Mock
    private Provider<DataSource> dataSourceProvider;

    @InjectMocks
    private ServerConfDatabaseReadinessCheck check;

    @Test
    void nullDataSourceReturnsUpNotConfigured() {
        when(dataSourceProvider.get()).thenReturn(null);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("NOT_CONFIGURED", response.getData().orElseThrow().get("status"));
    }

    @Test
    void validConnectionReturnsUp() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSourceProvider.get()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void invalidConnectionReturnsDown() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSourceProvider.get()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("Connection validation failed", response.getData().orElseThrow().get("error"));
    }

    @Test
    void connectionThrowsSqlExceptionReturnsDown() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSourceProvider.get()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("Connection refused", response.getData().orElseThrow().get("error"));
    }
}
