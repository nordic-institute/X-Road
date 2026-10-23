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
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.core.entity.SystemParameterEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SystemParameterMapperImpl;
import org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code centerui-changelog.xml} against a Postgres testcontainer, then drives two
 * independent {@link SystemParameterServiceImpl} instances — standing in for two Central Server
 * nodes, each with its own HA node name, JPA repository and database connection — against that one
 * database to prove the advisory lock in {@link JpaSystemParameterRepository#lockKeyForWrite} actually
 * serialises the first save of a shared key.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * instead of via {@code @Testcontainers}/{@code @Container}: that extension starts the container
 * before a {@code @BeforeAll} can check Docker availability, so a Docker-less run would fail instead
 * of skip.
 */
class SystemParameterLockingPostgresTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

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

    /**
     * Two Central Server nodes save the same shared key, which has no row yet, at the same time. Each
     * node has its own HA node name, so the unique index on {@code (key, ha_node_name)} does not stop
     * them from both inserting: only the advisory lock taken before the existing-row lookup does.
     */
    @Test
    void concurrentFirstSavesOfANewSharedKeyLeaveExactlyOneRow() throws Exception {
        var key = "concurrencyTestKey-" + UUID.randomUUID();
        var nodeAEntityManager = sessionFactory.createEntityManager();
        var nodeBEntityManager = sessionFactory.createEntityManager();
        var nodeAService = systemParameterService(nodeAEntityManager, "node-a");
        var nodeBService = systemParameterService(nodeBEntityManager, "node-b");

        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var nodeA = executor.submit(() -> save(nodeAEntityManager, nodeAService, key, "value-from-node-a", barrier));
            var nodeB = executor.submit(() -> save(nodeBEntityManager, nodeBService, key, "value-from-node-b", barrier));

            nodeA.get(15, TimeUnit.SECONDS);
            nodeB.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            nodeAEntityManager.close();
            nodeBEntityManager.close();
        }

        assertEquals(1, countRows(key));
    }

    private static Void save(EntityManager entityManager, SystemParameterServiceImpl service, String key, String value,
            CyclicBarrier barrier) throws Exception {
        entityManager.getTransaction().begin();
        barrier.await(10, TimeUnit.SECONDS);
        service.updateOrCreateParameter(key, value);
        entityManager.getTransaction().commit();
        return null;
    }

    private static SystemParameterServiceImpl systemParameterService(EntityManager entityManager, String haNodeName) {
        var repository = new JpaRepositoryFactory(entityManager).getRepository(JpaSystemParameterRepository.class);
        var haConfigStatus = new HAConfigStatus(haNodeName, true);
        return new SystemParameterServiceImpl(repository, haConfigStatus, new SystemParameterMapperImpl());
    }

    private long countRows(String key) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement("select count(*) from system_parameters where key = ?")) {
            statement.setString(1, key);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static SessionFactory buildSessionFactory() {
        var configuration = new Configuration();
        configuration.addAnnotatedClass(SystemParameterEntity.class);
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
