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
package org.niis.xroad.cs.admin.jpa.repository;

import jakarta.persistence.EntityManager;
import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.entity.IssuerSetUpLeaseEntity;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code centerui-changelog.xml} against a Postgres testcontainer, then drives two
 * independent {@link JpaIssuerSetUpLeaseRepository} instances — standing in for two Central Server
 * nodes, each with its own database connection — against that one database to prove the lease's
 * conditional upsert actually serialises acquisition. Prior art: {@code SystemParameterLockingPostgresTest}.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * instead of via {@code @Testcontainers}/{@code @Container}: that extension starts the container
 * before a {@code @BeforeAll} can check Docker availability, so a Docker-less run would fail instead
 * of skip.
 */
class IssuerSetUpLeasePostgresTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final Duration TTL = Duration.ofMinutes(5);

    private static SessionFactory sessionFactory;

    @BeforeAll
    static void migrateAndConnect() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping the Postgres-backed integration test");
        POSTGRES.start();
        createHstoreExtension();
        applyCenteruiChangelog();
        sessionFactory = buildSessionFactory();
    }

    @AfterAll
    static void disconnect() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void clearLeaseRow() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + IssuerSetUpLeaseEntity.TABLE_NAME);
        }
    }

    @Test
    void concurrentAcquirersLeaveExactlyOneWinner() throws Exception {
        var nodeAEntityManager = sessionFactory.createEntityManager();
        var nodeBEntityManager = sessionFactory.createEntityManager();
        var nodeARepository = leaseRepository(nodeAEntityManager);
        var nodeBRepository = leaseRepository(nodeBEntityManager);

        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> nodeA = executor.submit(acquire(nodeAEntityManager, nodeARepository, "node-a", barrier));
            Future<Boolean> nodeB = executor.submit(acquire(nodeBEntityManager, nodeBRepository, "node-b", barrier));

            var nodeAWon = nodeA.get(15, TimeUnit.SECONDS);
            var nodeBWon = nodeB.get(15, TimeUnit.SECONDS);

            assertTrue(nodeAWon ^ nodeBWon, "exactly one of the two concurrent acquirers must win");
        } finally {
            executor.shutdownNow();
            nodeAEntityManager.close();
            nodeBEntityManager.close();
        }

        assertEquals(1, countRows());
    }

    @Test
    void anExpiredLeaseCanBeTakenByAnotherNodeButAnUnexpiredOneCannot() {
        var entityManager = sessionFactory.createEntityManager();
        try {
            var repository = leaseRepository(entityManager);

            assertTrue(withTransaction(entityManager, () -> repository.tryAcquire("node-a", Duration.ZERO)));
            assertEquals(Optional.empty(), withTransaction(entityManager, repository::findHolder));

            var expiredTakeover = withTransaction(entityManager, () -> repository.tryAcquire("node-b", TTL));
            assertTrue(expiredTakeover, "an expired lease must be takeable by another node");
            assertEquals(Optional.of("node-b"), withTransaction(entityManager, repository::findHolder));

            var unexpiredTakeover = withTransaction(entityManager, () -> repository.tryAcquire("node-a", TTL));
            assertFalse(unexpiredTakeover, "an unexpired lease must not be takeable by another node");
            assertEquals(Optional.of("node-b"), withTransaction(entityManager, repository::findHolder));
        } finally {
            entityManager.close();
        }
    }

    @Test
    void anUnexpiredLeaseHoldsAcrossNodesInDifferentTimeZones() {
        var westEntityManager = sessionFactory.createEntityManager();
        var eastEntityManager = sessionFactory.createEntityManager();
        try {
            var westRepository = leaseRepository(westEntityManager);
            var eastRepository = leaseRepository(eastEntityManager);

            var westAcquired = withTransaction(westEntityManager, () -> {
                setSessionTimeZone(westEntityManager, "Etc/GMT+12");
                return westRepository.tryAcquire("node-west", TTL);
            });
            assertTrue(westAcquired);

            var eastTakeover = withTransaction(eastEntityManager, () -> {
                setSessionTimeZone(eastEntityManager, "Pacific/Kiritimati");
                return eastRepository.tryAcquire("node-east", TTL);
            });
            assertFalse(eastTakeover, "a node 26 hours ahead in wall-clock time must still see the lease as unexpired");
            assertEquals(Optional.of("node-west"), withTransaction(eastEntityManager, () -> {
                setSessionTimeZone(eastEntityManager, "Pacific/Kiritimati");
                return eastRepository.findHolder();
            }));
        } finally {
            westEntityManager.close();
            eastEntityManager.close();
        }
    }

    @Test
    void acquiringAgainAfterReleaseSucceedsAndLeavesOneRow() throws Exception {
        var entityManager = sessionFactory.createEntityManager();
        try {
            var repository = leaseRepository(entityManager);

            var firstAcquire = withTransaction(entityManager, () -> repository.tryAcquire("node-a", TTL));
            assertTrue(firstAcquire);
            withTransaction(entityManager, () -> {
                repository.release("node-a");
                return null;
            });

            var secondAcquire = withTransaction(entityManager, () -> repository.tryAcquire("node-a", TTL));
            assertTrue(secondAcquire, "re-acquiring after release must succeed");
        } finally {
            entityManager.close();
        }

        assertEquals(1, countRows());
    }

    private static Callable<Boolean> acquire(EntityManager entityManager, JpaIssuerSetUpLeaseRepository repository, String node,
            CyclicBarrier barrier) {
        return () -> {
            entityManager.getTransaction().begin();
            barrier.await(10, TimeUnit.SECONDS);
            var acquired = repository.tryAcquire(node, TTL);
            entityManager.getTransaction().commit();
            return acquired;
        };
    }

    private static <T> T withTransaction(EntityManager entityManager, Callable<T> action) {
        entityManager.getTransaction().begin();
        try {
            var result = action.call();
            entityManager.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setSessionTimeZone(EntityManager entityManager, String zone) {
        entityManager.createNativeQuery("SET LOCAL TIME ZONE '" + zone + "'").executeUpdate();
    }

    private static JpaIssuerSetUpLeaseRepository leaseRepository(EntityManager entityManager) {
        return new JpaRepositoryFactory(entityManager).getRepository(JpaIssuerSetUpLeaseRepository.class);
    }

    private long countRows() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement("select count(*) from " + IssuerSetUpLeaseEntity.TABLE_NAME)) {
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static SessionFactory buildSessionFactory() {
        var configuration = new Configuration();
        configuration.addAnnotatedClass(IssuerSetUpLeaseEntity.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl());
        configuration.setProperty("hibernate.connection.username", POSTGRES.getUsername());
        configuration.setProperty("hibernate.connection.password", POSTGRES.getPassword());
        configuration.setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.setProperty("hibernate.hikari.maximumPoolSize", "5");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        return configuration.buildSessionFactory();
    }

    /**
     * The production database image provisions the {@code hstore} extension outside Liquibase; this
     * testcontainer needs it created explicitly before {@code centerui-changelog.xml} can run, since
     * {@code 001-baseline.xml} declares a column of that type.
     */
    private static void createHstoreExtension() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS hstore");
        }
    }

    /**
     * Applies the same {@code centerui-changelog.xml} production deployments run, excluding only the
     * last changeset ({@code separate-admin-user}): it {@code GRANT}/{@code REVOKE}s permissions
     * between two Postgres roles, a split this single-role testcontainer has no use for.
     */
    private static void applyCenteruiChangelog() throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/centerui-changelog.xml");
            update.addArgumentValue("url", POSTGRES.getJdbcUrl());
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
