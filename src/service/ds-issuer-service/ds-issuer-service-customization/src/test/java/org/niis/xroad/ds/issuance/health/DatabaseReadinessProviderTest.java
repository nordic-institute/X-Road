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

package org.niis.xroad.ds.issuance.health;

import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseReadinessProviderTest {

    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Duration CACHE_TIME = Duration.ofMillis(1);

    @Test
    void reportsReadyWhenTheQuerySucceeds() throws SQLException {
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var rows = mock(ResultSet.class);
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(rows);

        var result = provider(() -> dataSource).get();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getComponent()).isEqualTo("database");
        verify(connection).close();
    }

    @Test
    void reportsOnlyTheSanitisedPhraseWhenTheConnectionFails() throws SQLException {
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException(
                "Connection to db.internal.example:5432 refused for user issuer, jdbc:postgresql://db.internal.example:5432/center",
                "08001"));

        var result = provider(() -> dataSource).get();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).containsExactly("connection refused");
        assertThat(String.join(" ", result.getFailureMessages()))
                .doesNotContain("jdbc:", "db.internal.example", "issuer");
    }

    @Test
    void mapsAuthenticationFailureAndAnythingElse() throws SQLException {
        var rejecting = mock(DataSource.class);
        when(rejecting.getConnection()).thenThrow(new SQLException("password for user issuer rejected", "28P01"));
        var broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("terminating connection", "57P01"));

        assertThat(provider(() -> rejecting).get().getFailureMessages()).containsExactly("authentication failed");
        assertThat(provider(() -> broken).get().getFailureMessages()).containsExactly("database unavailable");
    }

    @Test
    void reportsUnavailableWhenNoDatasourceIsRegisteredYet() {
        var result = provider(() -> null).get();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).containsExactly("database unavailable");
    }

    @Test
    void failsWithinTheTimeoutWhenTheConnectionBlocks() throws SQLException, InterruptedException {
        var release = new CountDownLatch(1);
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            throw new SQLException("released");
        });

        var started = System.nanoTime();
        var result = provider(() -> dataSource).get();
        var elapsed = Duration.ofNanos(System.nanoTime() - started);
        release.countDown();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).containsExactly("timed out after 300 ms");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    private static DatabaseReadinessProvider provider(Supplier<DataSource> dataSource) {
        return new DatabaseReadinessProvider(dataSource, new NoopTransactionContext(), TIMEOUT, CACHE_TIME);
    }
}
