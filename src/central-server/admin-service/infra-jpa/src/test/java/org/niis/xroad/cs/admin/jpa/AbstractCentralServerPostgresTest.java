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

package org.niis.xroad.cs.admin.jpa;

import jakarta.persistence.EntityManager;
import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs the real {@code centerui-changelog.xml} against a Postgres testcontainer and wires the Central Server
 * persistence layer, the published value store and the client deletion handler on top of it.
 *
 * <p>The container comes from {@link SharedPostgres}, one per run.
 */
@SpringBootTest(
        classes = PostgresTestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
                "spring.jpa.properties.hibernate.id.db_structure_naming_strategy=single",
                "spring.jpa.properties.hibernate.dialect=ee.ria.xroad.common.db.Postgres10FixedImplicitSequenceDialect",
                "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                        + "org.niis.xroad.cs.admin.jpa.StatementRecorder",
                "spring.liquibase.enabled=false"})
abstract class AbstractCentralServerPostgresTest {

    static final long WAIT_SECONDS = 30;

    private static final long LOCK_WAIT_POLL_MILLIS = 20;
    private static final long LOCK_WAIT_BUDGET_MILLIS = 10_000;
    private static boolean migrated;

    @MockitoBean
    protected AuditEventLoggingFacade auditLogging;
    @MockitoBean
    protected RequestMapper requestMapper;

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate transactions;
    @Autowired
    protected EntityManager entityManager;

    protected CentralServerSeed seed;

    @BeforeAll
    static synchronized void startAndMigrateDatabase() throws Exception {
        SharedPostgres.start();
        if (migrated) {
            return;
        }
        createHstoreExtension();
        applyCentralServerChangelog();
        migrated = true;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgres.CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", SharedPostgres.CONTAINER::getUsername);
        registry.add("spring.datasource.password", SharedPostgres.CONTAINER::getPassword);
    }

    @BeforeEach
    void createSeed() {
        StatementRecorder.clear();
        seed = new CentralServerSeed(transactions, entityManager);
    }

    protected final int countMemberDids(int securityServerId) {
        return jdbc.queryForObject("select count(*) from member_dids where security_server_id = ?",
                Integer.class, securityServerId);
    }

    protected final void insertMemberDid(int securityServerId, long memberId, String did) {
        jdbc.update("insert into member_dids (security_server_id, member_id, did, created_at, updated_at) "
                + "values (?, ?, ?, now(), now())", securityServerId, memberId, did);
    }

    /**
     * Wraps work so that it runs in one transaction and records the Postgres backend that serves it in
     * {@code backendPid}, for {@link #awaitLockedSession(AtomicInteger)}.
     */
    protected final <T> Callable<T> inTransaction(AtomicInteger backendPid, Supplier<T> work) {
        return () -> transactions.execute(status -> {
            backendPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            return work.get();
        });
    }

    protected final void awaitLockedSession(AtomicInteger waiterBackendPid) {
        Awaitility.await("the waiter's backend to wait for a lock")
                .atMost(Duration.ofMillis(LOCK_WAIT_BUDGET_MILLIS))
                .pollInterval(Duration.ofMillis(LOCK_WAIT_POLL_MILLIS))
                .until(() -> isWaitingForLock(waiterBackendPid.get()));
    }

    private boolean isWaitingForLock(int backendPid) {
        if (backendPid == 0) {
            return false;
        }
        var waiting = jdbc.queryForObject(
                "select count(*) from pg_stat_activity where pid = ? and wait_event_type = 'Lock' "
                        + "and datname = current_database()",
                Integer.class, backendPid);
        return waiting != null && waiting > 0;
    }

    /**
     * Waits until the holder has taken its lock. A holder that fails first is reported with its own exception.
     */
    protected static void awaitHolding(CountDownLatch locked, Future<?> holder) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!locked.await(LOCK_WAIT_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
            if (holder.isDone()) {
                throw new IllegalStateException("the lock holder ended before taking its lock", holderFailure(holder));
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("lock holder did not take its lock in time");
            }
        }
    }

    private static Throwable holderFailure(Future<?> holder) throws InterruptedException {
        try {
            holder.get();
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    protected static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not released in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void createHstoreExtension() throws Exception {
        try (var connection = DriverManager.getConnection(
                SharedPostgres.CONTAINER.getJdbcUrl(), SharedPostgres.CONTAINER.getUsername(), SharedPostgres.CONTAINER.getPassword())) {
            connection.createStatement().execute("CREATE EXTENSION IF NOT EXISTS hstore");
        }
    }

    private static void applyCentralServerChangelog() throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/centerui-changelog.xml");
            update.addArgumentValue("url", SharedPostgres.CONTAINER.getJdbcUrl());
            update.addArgumentValue("username", SharedPostgres.CONTAINER.getUsername());
            update.addArgumentValue("password", SharedPostgres.CONTAINER.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
