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

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transaction.local.DataSourceResource;
import org.eclipse.edc.transaction.local.LocalTransactionContext;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;

import java.net.ConnectException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseReadinessProviderTest {

    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Duration CACHE_TIME = Duration.ofMillis(1);
    private final Monitor monitor = mock(Monitor.class);

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
        verify(statement).executeQuery("SELECT 1");
        verify(connection).close();
    }

    @Test
    void releasesThePooledConnectionAfterASuccessfulCheck() throws SQLException {
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var rows = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(rows);
        var result = pooledProvider(connection).get();
        assertThat(result.succeeded()).isTrue();
        verify(connection, times(1)).close();
    }

    @Test
    void releasesThePooledConnectionAfterAFailedCheck() throws SQLException {
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenThrow(new SQLException("terminating connection", "57P01"));
        var result = pooledProvider(connection).get();
        assertThat(result.succeeded()).isFalse();
        verify(connection, times(1)).close();
    }

    @Test
    void reportsOnlyTheSanitisedPhraseWhenTheConnectionFails() throws SQLException {
        var dataSource = mock(DataSource.class);
        var failure = new SQLException(
                "Connection to db.internal.example:5432 refused for user issuer, jdbc:postgresql://db.internal.example:5432/center",
                "08001");
        when(dataSource.getConnection()).thenThrow(failure);

        var result = provider(() -> dataSource).get();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureMessages()).containsExactly("connection refused");
        assertThat(String.join(" ", result.getFailureMessages()))
                .doesNotContain("jdbc:", "db.internal.example", "issuer");
        var logged = ArgumentCaptor.forClass(Throwable.class);
        verify(monitor).warning(anyString(), logged.capture());
        assertThat(logged.getValue()).hasCause(failure);
    }

    @Test
    void mapsAnEdcExceptionWrappingAConnectExceptionWithoutTheHostText() throws SQLException {
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new EdcException(
                "Connection to db.internal.example:5432 failed for user issuer",
                new ConnectException("Connection refused")));

        var result = provider(() -> dataSource).get();

        assertThat(result.getFailureMessages()).containsExactly("connection refused");
        assertThat(String.join(" ", result.getFailureMessages())).doesNotContain("db.internal.example", "issuer");
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
    void failsWithinTheTimeoutWhenTheConnectionBlocks() throws SQLException {
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

    @Test
    void startsNoSecondCheckWhileAnEarlierOneStillRuns() throws SQLException {
        var release = new CountDownLatch(1);
        var dataSource = mock(DataSource.class);
        var statement = mock(Statement.class);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return readyConnection(statement);
        });
        var provider = provider(() -> dataSource);

        var first = provider.get();
        pauseLongerThanTheCache();
        var second = provider.get();

        assertThat(first.getFailureMessages()).containsExactly("timed out after 300 ms");
        assertThat(second.getFailureMessages()).containsExactly("timed out after 300 ms");
        verify(dataSource, times(1)).getConnection();

        release.countDown();
        await().untilAsserted(() -> assertThat(provider.get().succeeded()).isTrue());
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    void limitsTheQueryToTheProbeTimeout() throws SQLException {
        var statement = mock(Statement.class);
        var connection = readyConnection(statement);
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        var result = provider(() -> dataSource, new NoopTransactionContext(), Duration.ofSeconds(3)).get();

        assertThat(result.succeeded()).isTrue();
        verify(statement).setQueryTimeout(3);
    }

    private static void pauseLongerThanTheCache() {
        await().pollDelay(Duration.ofMillis(50)).until(() -> true);
    }

    private static Connection readyConnection(Statement statement) throws SQLException {
        var connection = mock(Connection.class);
        var rows = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(rows);
        return connection;
    }

    private DatabaseReadinessProvider provider(Supplier<DataSource> dataSource) {
        return provider(dataSource, new NoopTransactionContext());
    }

    private DatabaseReadinessProvider pooledProvider(Connection connection) throws SQLException {
        var pool = mock(DataSource.class);
        when(pool.getConnection()).thenReturn(connection);
        var resource = new DataSourceResource(pool);
        var transactionContext = new LocalTransactionContext(monitor);
        transactionContext.registerResource(resource);
        return provider(() -> resource, transactionContext);
    }

    private DatabaseReadinessProvider provider(Supplier<DataSource> dataSource, TransactionContext transactionContext) {
        return provider(dataSource, transactionContext, TIMEOUT);
    }

    private DatabaseReadinessProvider provider(Supplier<DataSource> dataSource, TransactionContext transactionContext,
                                               Duration timeout) {
        return new DatabaseReadinessProvider(dataSource, transactionContext, monitor, timeout, CACHE_TIME);
    }
}
