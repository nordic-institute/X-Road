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
package org.niis.xroad.proxy.dataplane;

import ee.ria.xroad.common.db.Postgres10FixedImplicitSequenceDialect;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.serverconf.ServerConfDbProperties;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real {@code serverconf-changelog.xml} against a Postgres testcontainer, then drives two
 * independent {@link SharedDataFlowStateStore} instances — each with its own {@link ServerConfDatabaseCtx}
 * and connection pool — against that one database, proving cross-node visibility against the actual
 * schema and store implementation.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * rather than via the declarative {@code @Testcontainers}/{@code @Container} extension: that extension
 * starts the container before any user {@code @BeforeAll} can check Docker availability, so a
 * Docker-less run would fail instead of skip.
 */
class SharedDataFlowStateStorePostgresTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static ServerConfDatabaseCtx nodeADatabaseCtx;
    private static ServerConfDatabaseCtx nodeBDatabaseCtx;
    private static SharedDataFlowStateStore nodeA;
    private static SharedDataFlowStateStore nodeB;

    @BeforeAll
    static void migrateAndConnect() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping the Postgres-backed integration test");

        POSTGRES.start();
        applyServerConfChangelog();

        nodeADatabaseCtx = new ServerConfDatabaseCtx(dbProperties());
        nodeBDatabaseCtx = new ServerConfDatabaseCtx(dbProperties());
        nodeA = new SharedDataFlowStateStore(nodeADatabaseCtx);
        nodeB = new SharedDataFlowStateStore(nodeBDatabaseCtx);
    }

    @AfterAll
    static void disconnect() {
        if (nodeADatabaseCtx != null) {
            nodeADatabaseCtx.destroy();
        }
        if (nodeBDatabaseCtx != null) {
            nodeBDatabaseCtx.destroy();
        }
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Test
    void flowSavedThroughOneNodeIsVisibleThroughAnother() {
        var flowId = uniqueFlowId();

        nodeA.save(flowId, DataFlowStates.STARTED);

        assertThat(nodeB.find(flowId)).isEqualTo(DataFlowStates.STARTED);
    }

    @Test
    void lifecycleUpdatesFromEitherNodeAreVisibleOnTheOther() {
        var flowId = uniqueFlowId();

        nodeA.save(flowId, DataFlowStates.PROVISIONED);
        assertThat(nodeB.find(flowId)).isEqualTo(DataFlowStates.PROVISIONED);

        nodeB.save(flowId, DataFlowStates.STARTED);
        assertThat(nodeA.find(flowId)).isEqualTo(DataFlowStates.STARTED);

        nodeA.save(flowId, DataFlowStates.SUSPENDED);
        assertThat(nodeB.find(flowId)).isEqualTo(DataFlowStates.SUSPENDED);
    }

    @Test
    void repeatedLifecycleUpdatesLeaveExactlyOneRow() throws Exception {
        var flowId = uniqueFlowId();

        nodeA.save(flowId, DataFlowStates.PROVISIONED);
        nodeB.save(flowId, DataFlowStates.STARTED);
        nodeA.save(flowId, DataFlowStates.SUSPENDED);
        nodeB.save(flowId, DataFlowStates.STARTED);
        nodeA.save(flowId, DataFlowStates.TERMINATED);

        assertThat(nodeB.find(flowId)).isEqualTo(DataFlowStates.TERMINATED);
        assertThat(countRows(flowId)).isEqualTo(1);
    }

    @Test
    void terminatedAndCompletedFlowsDoNotLeaveStaleEntries() throws Exception {
        var terminatedFlowId = uniqueFlowId();
        var completedFlowId = uniqueFlowId();

        nodeA.save(terminatedFlowId, DataFlowStates.STARTED);
        nodeB.save(terminatedFlowId, DataFlowStates.TERMINATED);

        nodeB.save(completedFlowId, DataFlowStates.STARTED);
        nodeA.save(completedFlowId, DataFlowStates.COMPLETED);

        assertThat(nodeA.find(terminatedFlowId)).isEqualTo(DataFlowStates.TERMINATED);
        assertThat(nodeB.find(completedFlowId)).isEqualTo(DataFlowStates.COMPLETED);
        assertThat(countRows(terminatedFlowId)).isEqualTo(1);
        assertThat(countRows(completedFlowId)).isEqualTo(1);
    }

    /**
     * Two proxy nodes handling the first signal for the same new {@code flowId} can both see no
     * existing row and both insert, racing on {@code uniq_dataflow_state_flow_id}. A {@link CyclicBarrier}
     * lines up nodeA's and nodeB's {@link SharedDataFlowStateStore#save} calls so both reach the database
     * at essentially the same instant; run across many distinct {@code flowId}s so at least some pairs
     * genuinely race. The assertions hold regardless of which pairs actually raced.
     */
    @Test
    void concurrentFirstWriteForTheSameNewFlowIdDoesNotFailEitherNode() throws Exception {
        var racingPairs = 25;
        var executor = Executors.newFixedThreadPool(2);
        try {
            var flowIds = IntStream.range(0, racingPairs).mapToObj(i -> uniqueFlowId()).toList();

            for (var flowId : flowIds) {
                var barrier = new CyclicBarrier(2);
                List<Future<?>> results = List.of(
                        executor.submit(() -> raceToSave(nodeA, flowId, barrier)),
                        executor.submit(() -> raceToSave(nodeB, flowId, barrier)));

                for (var result : results) {
                    result.get(10, TimeUnit.SECONDS);
                }
            }

            for (var flowId : flowIds) {
                assertThat(nodeA.find(flowId)).isEqualTo(DataFlowStates.STARTED);
                assertThat(countRows(flowId)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Void raceToSave(SharedDataFlowStateStore node, String flowId, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        node.save(flowId, DataFlowStates.STARTED);
        return null;
    }

    /**
     * {@code created_at}/{@code updated_at} are set by the {@code set_timestamps} Postgres trigger
     * (see {@code 013-dataflow-state.xml}), never by the application — so this reads the raw columns
     * directly rather than through {@link SharedDataFlowStateStore}, which has no reason to expose them.
     */
    @Test
    void auditTimestampsAreSetOnInsertAndUpdatedOnLifecycleChange() throws Exception {
        var flowId = uniqueFlowId();

        nodeA.save(flowId, DataFlowStates.PROVISIONED);
        var afterInsert = selectTimestamps(flowId);

        assertThat(afterInsert.createdAt()).isNotNull();
        assertThat(afterInsert.updatedAt()).isNotNull();

        // Postgres TIMESTAMP(6) resolves to microseconds; this margin makes the "later" assertion below
        // reliable regardless of how fast the two transactions actually run back to back.
        Thread.sleep(50);

        nodeB.save(flowId, DataFlowStates.STARTED);
        var afterUpdate = selectTimestamps(flowId);

        assertThat(afterUpdate.createdAt()).isEqualTo(afterInsert.createdAt());
        assertThat(afterUpdate.updatedAt()).isAfter(afterInsert.updatedAt());
    }

    private Timestamps selectTimestamps(String flowId) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement("select created_at, updated_at from dataflow_state where flow_id = ?")) {
            statement.setString(1, flowId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return new Timestamps(resultSet.getTimestamp("created_at"), resultSet.getTimestamp("updated_at"));
            }
        }
    }

    private record Timestamps(Timestamp createdAt, Timestamp updatedAt) {
    }

    private long countRows(String flowId) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement("select count(*) from dataflow_state where flow_id = ?")) {
            statement.setString(1, flowId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String uniqueFlowId() {
        return "flow-" + UUID.randomUUID();
    }

    private static ServerConfDbProperties dbProperties() {
        Map<String, String> hibernateProperties = Map.of(
                "dialect", Postgres10FixedImplicitSequenceDialect.class.getName(),
                "connection.driver_class", "org.postgresql.Driver",
                "connection.url", POSTGRES.getJdbcUrl(),
                "connection.username", POSTGRES.getUsername(),
                "connection.password", POSTGRES.getPassword(),
                "hikari.maximumPoolSize", "5"
        );
        return () -> hibernateProperties;
    }

    /**
     * Applies the same {@code serverconf-changelog.xml} production deployments run, excluding only the
     * last changeset ({@code separate-admin-user}): it just {@code GRANT}/{@code REVOKE}s table
     * permissions between two Postgres roles, a split this single-role testcontainer has no use for.
     * Every schema-creating changeset, including {@code 013-dataflow-state.xml}, still runs.
     */
    private static void applyServerConfChangelog() throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/serverconf-changelog.xml");
            update.addArgumentValue("url", POSTGRES.getJdbcUrl());
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
