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
import org.niis.xroad.cs.admin.core.entity.IssuerDidEntity;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code centerui-changelog.xml} against a Postgres testcontainer to prove the database,
 * not just the entity mapping, enforces the Issuer identity binding's invariants: every identity column
 * is required, and a DID can be bound only once. Prior art: {@code IssuerSetUpLeasePostgresTest}.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * instead of via {@code @Testcontainers}/{@code @Container}: that extension starts the container
 * before a {@code @BeforeAll} can check Docker availability, so a Docker-less run would fail instead
 * of skip.
 */
class IssuerDidPostgresTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final String DID = "did:web:cs.example%3A6183:issuer";
    private static final String HOST = "cs.example";
    private static final int DID_PORT = 6183;
    private static final String ISSUANCE_URL = "https://cs.example:6185/api/issuance/v1/participants/issuer";
    private static final String STATUSLIST_BASE_URL = "https://cs.example:6187/statuslist";

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
    void clearIssuerDidsRow() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + IssuerDidEntity.TABLE_NAME);
        }
    }

    @Test
    void bindingStoresTheFullIssuerIdentity() throws Exception {
        var entityManager = sessionFactory.createEntityManager();
        try {
            var repository = didRepository(entityManager);
            withTransaction(entityManager, () -> {
                repository.insert(DID, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
                return null;
            });
        } finally {
            entityManager.close();
        }

        assertEquals(Map.of(
                        "did", DID,
                        "host", HOST,
                        "did_port", DID_PORT,
                        "issuance_url", ISSUANCE_URL,
                        "statuslist_base_url", STATUSLIST_BASE_URL),
                readStoredRow());
    }

    @Test
    void bindingTheSameDidTwiceFails() {
        var entityManager = sessionFactory.createEntityManager();
        try {
            var repository = didRepository(entityManager);
            withTransaction(entityManager, () -> {
                repository.insert(DID, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
                return null;
            });

            assertThrows(RuntimeException.class, () -> withTransaction(entityManager, () -> {
                repository.insert(DID, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
                return null;
            }));
        } finally {
            entityManager.close();
        }
    }

    @Test
    void nullHostFails() {
        assertThrows(Exception.class, () -> insertRawWithNullColumn("host"));
    }

    @Test
    void nullDidPortFails() {
        assertThrows(Exception.class, () -> insertRawWithNullColumn("did_port"));
    }

    @Test
    void nullIssuanceUrlFails() {
        assertThrows(Exception.class, () -> insertRawWithNullColumn("issuance_url"));
    }

    @Test
    void nullStatuslistBaseUrlFails() {
        assertThrows(Exception.class, () -> insertRawWithNullColumn("statuslist_base_url"));
    }

    @Test
    void nullDidFails() {
        assertThrows(Exception.class, () -> insertRawWithNullColumn("did"));
    }

    private Map<String, Object> readStoredRow() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                        "SELECT did, host, did_port, issuance_url, statuslist_base_url FROM "
                                + IssuerDidEntity.TABLE_NAME)) {
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return Map.of(
                        "did", resultSet.getString("did"),
                        "host", resultSet.getString("host"),
                        "did_port", resultSet.getInt("did_port"),
                        "issuance_url", resultSet.getString("issuance_url"),
                        "statuslist_base_url", resultSet.getString("statuslist_base_url"));
            }
        }
    }

    /**
     * Inserts a row with every identity column set, except {@code nullColumn} which is left {@code NULL} —
     * used to prove the database, not just application code, rejects it.
     */
    private void insertRawWithNullColumn(String nullColumn) throws Exception {
        var columns = Map.of(
                "did", (Object) DID,
                "host", HOST,
                "did_port", DID_PORT,
                "issuance_url", ISSUANCE_URL,
                "statuslist_base_url", STATUSLIST_BASE_URL);
        var columnNames = columns.keySet();
        var valuesSql = columnNames.stream()
                .map(column -> column.equals(nullColumn) ? "NULL" : "?")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        var sql = "INSERT INTO " + IssuerDidEntity.TABLE_NAME + " (" + String.join(", ", columnNames)
                + ", created_at, updated_at) VALUES (" + valuesSql + ", now(), now())";

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(sql)) {
            var parameterIndex = 1;
            for (var column : columnNames) {
                if (!column.equals(nullColumn)) {
                    statement.setObject(parameterIndex++, columns.get(column));
                }
            }
            statement.execute();
        }
    }

    private static <T> T withTransaction(EntityManager entityManager, Callable<T> action) {
        entityManager.getTransaction().begin();
        try {
            var result = action.call();
            entityManager.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            entityManager.getTransaction().rollback();
            throw e;
        } catch (Exception e) {
            entityManager.getTransaction().rollback();
            throw new IllegalStateException(e);
        }
    }

    private static JpaIssuerDidRepository didRepository(EntityManager entityManager) {
        return new JpaRepositoryFactory(entityManager).getRepository(JpaIssuerDidRepository.class);
    }

    private static SessionFactory buildSessionFactory() {
        var configuration = new Configuration();
        configuration.addAnnotatedClass(IssuerDidEntity.class);
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
