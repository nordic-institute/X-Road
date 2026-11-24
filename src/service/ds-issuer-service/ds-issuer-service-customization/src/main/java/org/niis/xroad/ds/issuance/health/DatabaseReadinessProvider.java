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

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.system.health.HealthCheckResult;
import org.eclipse.edc.spi.system.health.ReadinessProvider;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.niis.xroad.common.healthcheck.CachingHealthCheck;
import org.niis.xroad.common.healthcheck.TimedHealthCheck;

import javax.sql.DataSource;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.ERROR;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.TIMEOUT;

/**
 * Reports ready only while a {@code SELECT 1} on a connection from the EDC default datasource succeeds. A failure
 * carries a short fixed phrase and never the driver message, which can hold the JDBC URL, host, database or user.
 */
public final class DatabaseReadinessProvider implements ReadinessProvider {

    static final String COMPONENT = "database";
    static final String CONNECTION_REFUSED = "connection refused";
    static final String AUTHENTICATION_FAILED = "authentication failed";
    static final String UNAVAILABLE = "database unavailable";

    private static final Duration MAX_ERROR_TTL = Duration.ofSeconds(10);
    private static final int ERROR_BACKOFF_MULTIPLIER = 2;
    private static final String TIMEOUT_MS = "timeout_ms";
    private static final String SQL_STATE_AUTHENTICATION_CLASS = "28";
    private static final String SQL_STATE_CONNECTION_CLASS = "08";

    private final HealthCheck check;

    /**
     * @param dataSource resolves the datasource on each check, as the pool registers it after this provider
     * @param transactionContext runs the query inside a transaction so the pooled connection is released
     * @param monitor receives the full cause of a failed check, which the response body leaves out
     * @param timeout longest wait for one check before it counts as failed
     * @param cacheTime how long a result, ready or failed, is reused
     */
    public DatabaseReadinessProvider(Supplier<DataSource> dataSource, TransactionContext transactionContext,
                                     Monitor monitor, Duration timeout, Duration cacheTime) {
        var query = new SelectOneCheck(dataSource, transactionContext, monitor, timeout);
        this.check = new CachingHealthCheck(new TimedHealthCheck(query, timeout, COMPONENT),
                cacheTime, cacheTime, MAX_ERROR_TTL, ERROR_BACKOFF_MULTIPLIER);
    }

    @Override
    public HealthCheckResult get() {
        var result = HealthCheckResult.Builder.newInstance().component(COMPONENT);
        var response = check.call();
        if (response.getStatus() == HealthCheckResponse.Status.UP) {
            return result.success().build();
        }
        return result.failure(failurePhrase(response)).build();
    }

    static String sanitise(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var authenticationFailed = false;
        var connectionFailed = false;
        for (var cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                var state = sql.getSQLState();
                authenticationFailed |= state != null && state.startsWith(SQL_STATE_AUTHENTICATION_CLASS);
                connectionFailed |= state != null && state.startsWith(SQL_STATE_CONNECTION_CLASS);
            }
            connectionFailed |= cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException;
        }
        if (authenticationFailed) {
            return AUTHENTICATION_FAILED;
        }
        return connectionFailed ? CONNECTION_REFUSED : UNAVAILABLE;
    }

    private static String failurePhrase(HealthCheckResponse response) {
        var data = response.getData().orElse(Map.of());
        var error = data.get(ERROR);
        if (TIMEOUT.equals(error)) {
            return "timed out after " + data.get(TIMEOUT_MS) + " ms";
        }
        if (CONNECTION_REFUSED.equals(error) || AUTHENTICATION_FAILED.equals(error)) {
            return (String) error;
        }
        return UNAVAILABLE;
    }

    /**
     * Runs {@code SELECT 1} with at most one query in flight. A caller that arrives while an earlier query still
     * runs waits for that query instead of starting another, up to the timeout. A query that outlives the timeout
     * is interrupted, which ends a wait for a pooled connection, and runs with a matching JDBC query timeout.
     */
    @RequiredArgsConstructor
    private static final class SelectOneCheck implements HealthCheck {

        private final Supplier<DataSource> dataSource;
        private final TransactionContext transactionContext;
        private final Monitor monitor;
        private final Duration timeout;
        private final AtomicReference<CompletableFuture<HealthCheckResponse>> inFlight = new AtomicReference<>();

        @Override
        public HealthCheckResponse call() {
            var own = new CompletableFuture<HealthCheckResponse>();
            var running = inFlight.compareAndExchange(null, own);
            if (running != null) {
                return awaitRunning(running);
            }
            var response = timedOut();
            try {
                response = runQuery();
            } finally {
                inFlight.set(null);
                own.complete(response);
            }
            return response;
        }

        private HealthCheckResponse awaitRunning(CompletableFuture<HealthCheckResponse> running) {
            try {
                return running.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException | ExecutionException _) {
                // no result in time
            }
            return timedOut();
        }

        private HealthCheckResponse runQuery() {
            var builder = HealthCheckResponse.builder().name(COMPONENT);
            var expired = new AtomicBoolean();
            var watchdog = interruptAfterTimeout(Thread.currentThread(), expired);
            try {
                var source = dataSource.get();
                if (source == null) {
                    return builder.down().withData(ERROR, UNAVAILABLE).build();
                }
                transactionContext.execute(() -> selectOne(source));
                return builder.up().build();
            } catch (Exception e) {
                monitor.warning("Database readiness check failed", e);
                return expired.get() ? timedOut() : builder.down().withData(ERROR, sanitise(e)).build();
            } finally {
                watchdog.interrupt();
            }
        }

        private Thread interruptAfterTimeout(Thread worker, AtomicBoolean expired) {
            return Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(timeout);
                    expired.set(true);
                    worker.interrupt();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        private void selectOne(DataSource source) {
            try (var connection = source.getConnection();
                 var statement = connection.createStatement()) {
                statement.setQueryTimeout(queryTimeoutSeconds());
                try (var rows = statement.executeQuery("SELECT 1")) {
                    rows.next();
                }
            } catch (SQLException e) {
                throw new EdcPersistenceException(e);
            }
        }

        /** JDBC counts whole seconds; a timeout under one second rounds up to the one second JDBC allows. */
        private int queryTimeoutSeconds() {
            return (int) Math.max(1, timeout.toSeconds());
        }

        private HealthCheckResponse timedOut() {
            return HealthCheckResponse.builder().name(COMPONENT).down()
                    .withData(ERROR, TIMEOUT)
                    .withData(TIMEOUT_MS, timeout.toMillis())
                    .build();
        }
    }
}
