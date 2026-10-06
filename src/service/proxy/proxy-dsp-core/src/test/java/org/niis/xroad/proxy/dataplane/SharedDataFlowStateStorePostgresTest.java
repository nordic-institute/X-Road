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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.COMPLETE;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.PREPARE;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.START;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.SUSPEND;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.TERMINATE;

/**
 * Runs the real {@code serverconf-changelog.xml} against a Postgres testcontainer, then drives two
 * independent {@link SharedDataFlowStateStore} instances against that one database to prove
 * cross-node visibility and race behaviour against the actual schema and store implementation.
 *
 * <p>The container is started manually, gated behind {@link DockerClientFactory#isDockerAvailable()},
 * instead of via {@code @Testcontainers}/{@code @Container}: that extension starts the container
 * before a {@code @BeforeAll} can check Docker availability, so a Docker-less run would fail
 * instead of skip.
 */
class SharedDataFlowStateStorePostgresTest {

    private static final int RACING_PAIRS = 25;

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
    void flowStartedThroughOneNodeIsVisibleThroughAnother() {
        var flowId = uniqueFlowId();

        nodeA.apply(flowId, START);

        assertThat(nodeB.find(flowId)).contains(DataFlowStates.STARTED);
    }

    @Test
    void lifecycleTransitionsFromEitherNodeAreVisibleOnTheOther() {
        var flowId = uniqueFlowId();

        nodeA.apply(flowId, PREPARE);
        assertThat(nodeB.find(flowId)).contains(DataFlowStates.PROVISIONED);

        nodeB.apply(flowId, START);
        assertThat(nodeA.find(flowId)).contains(DataFlowStates.STARTED);

        nodeA.apply(flowId, SUSPEND);
        assertThat(nodeB.find(flowId)).contains(DataFlowStates.SUSPENDED);
    }

    @Test
    void wholeLifecycleIncludingResumeLeavesExactlyOneRow() throws Exception {
        var flowId = uniqueFlowId();

        nodeA.apply(flowId, PREPARE);
        nodeB.apply(flowId, START);
        nodeA.apply(flowId, SUSPEND);
        nodeB.apply(flowId, START);
        nodeA.apply(flowId, TERMINATE);

        assertThat(nodeB.find(flowId)).contains(DataFlowStates.TERMINATED);
        assertThat(countRows(flowId)).isEqualTo(1);
    }

    @Test
    void terminatedAndCompletedFlowsDoNotLeaveStaleEntries() throws Exception {
        var terminatedFlowId = uniqueFlowId();
        var completedFlowId = uniqueFlowId();

        nodeA.apply(terminatedFlowId, START);
        nodeB.apply(terminatedFlowId, TERMINATE);
        nodeB.apply(completedFlowId, START);
        nodeA.apply(completedFlowId, COMPLETE);

        assertThat(nodeA.find(terminatedFlowId)).contains(DataFlowStates.TERMINATED);
        assertThat(nodeB.find(completedFlowId)).contains(DataFlowStates.COMPLETED);
        assertThat(countRows(terminatedFlowId)).isEqualTo(1);
        assertThat(countRows(completedFlowId)).isEqualTo(1);
    }

    @Test
    void outcomeTellsWhetherTheCallChangedTheState() {
        var flowId = uniqueFlowId();

        var first = nodeA.apply(flowId, START);
        var repeat = nodeB.apply(flowId, START);
        var suspend = nodeA.apply(flowId, SUSPEND);

        assertThat(first).isEqualTo(new DataFlowTransitionOutcome(DataFlowStates.STARTED, true));
        assertThat(repeat).isEqualTo(new DataFlowTransitionOutcome(DataFlowStates.STARTED, false));
        assertThat(suspend).isEqualTo(new DataFlowTransitionOutcome(DataFlowStates.SUSPENDED, true));
    }

    @Test
    void terminatedFlowRejectsStart() {
        var flowId = uniqueFlowId();
        nodeA.apply(flowId, START);
        nodeA.apply(flowId, TERMINATE);

        assertThatThrownBy(() -> nodeB.apply(flowId, START)).isInstanceOf(XrdRuntimeException.class);

        assertThat(nodeA.find(flowId)).contains(DataFlowStates.TERMINATED);
    }

    @Test
    void completedFlowRejectsSuspend() {
        var flowId = uniqueFlowId();
        nodeA.apply(flowId, START);
        nodeA.apply(flowId, COMPLETE);

        assertThatThrownBy(() -> nodeB.apply(flowId, SUSPEND)).isInstanceOf(XrdRuntimeException.class);

        assertThat(nodeA.find(flowId)).contains(DataFlowStates.COMPLETED);
    }

    @Test
    void repeatingATerminalTransitionIsAcceptedAsANoOp() throws Exception {
        var flowId = uniqueFlowId();
        nodeA.apply(flowId, START);
        nodeA.apply(flowId, TERMINATE);

        var repeat = nodeB.apply(flowId, TERMINATE);

        assertThat(repeat).isEqualTo(new DataFlowTransitionOutcome(DataFlowStates.TERMINATED, false));
        assertThat(countRows(flowId)).isEqualTo(1);
    }

    @Test
    void suspendedFlowMustBeResumedBeforeItCompletes() {
        var flowId = uniqueFlowId();
        nodeA.apply(flowId, START);
        nodeA.apply(flowId, SUSPEND);

        assertThatThrownBy(() -> nodeB.apply(flowId, COMPLETE)).isInstanceOf(XrdRuntimeException.class);
        assertThat(nodeA.find(flowId)).contains(DataFlowStates.SUSPENDED);

        nodeB.apply(flowId, START);
        nodeB.apply(flowId, COMPLETE);
        assertThat(nodeA.find(flowId)).contains(DataFlowStates.COMPLETED);
    }

    /**
     * Two nodes handling the first signal for the same new {@code flowId} can both see no existing
     * row and both insert, racing on {@code uniq_dataflow_state_flow_id}. A {@link CyclicBarrier}
     * lines up both {@link SharedDataFlowStateStore#apply} calls across many distinct {@code flowId}s
     * so at least some pairs genuinely race; the loser must then be validated against the winner's row.
     */
    @Test
    void concurrentFirstStartForTheSameNewFlowIdFailsNeitherNode() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var flowIds = IntStream.range(0, RACING_PAIRS).mapToObj(i -> uniqueFlowId()).toList();
            for (var flowId : flowIds) {
                var barrier = new CyclicBarrier(2);
                List<Future<Boolean>> results = List.of(
                        executor.submit(() -> raceToApply(nodeA, flowId, START, barrier)),
                        executor.submit(() -> raceToApply(nodeB, flowId, START, barrier)));
                for (var result : results) {
                    assertThat(result.get(10, TimeUnit.SECONDS)).isTrue();
                }
            }
            for (var flowId : flowIds) {
                assertThat(nodeA.find(flowId)).contains(DataFlowStates.STARTED);
                assertThat(countRows(flowId)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Same race, nodeA starting and nodeB preparing the same new flow. Start is legal both first and after
     * prepare, so it always succeeds; prepare after start is rejected. Either way the flow ends started.
     */
    @Test
    void concurrentPrepareAndStartForTheSameNewFlowEndStartedWhicheverCommitsFirst() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var flowIds = IntStream.range(0, RACING_PAIRS).mapToObj(i -> uniqueFlowId()).toList();
            for (var flowId : flowIds) {
                var barrier = new CyclicBarrier(2);
                var start = executor.submit(() -> raceToApply(nodeA, flowId, START, barrier));
                var prepare = executor.submit(() -> raceToApply(nodeB, flowId, PREPARE, barrier));
                assertThat(start.get(10, TimeUnit.SECONDS)).isTrue();
                prepare.get(10, TimeUnit.SECONDS);
            }
            for (var flowId : flowIds) {
                assertThat(nodeA.find(flowId)).contains(DataFlowStates.STARTED);
                assertThat(countRows(flowId)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Both nodes see the same {@code STARTED} row, then race incompatible transitions — nodeA completing,
     * nodeB suspending. Whichever commits first wins; the other is validated against the winner's state and
     * rejected, so the stored state is always the winner's and never a stale overwrite.
     */
    @Test
    void concurrentCompleteAndSuspendOnAStartedFlowLetExactlyOneWin() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var flowIds = IntStream.range(0, RACING_PAIRS).mapToObj(i -> uniqueFlowId()).toList();
            for (var flowId : flowIds) {
                nodeA.apply(flowId, START);
            }
            for (var flowId : flowIds) {
                var barrier = new CyclicBarrier(2);
                var complete = executor.submit(() -> raceToApply(nodeA, flowId, COMPLETE, barrier));
                var suspend = executor.submit(() -> raceToApply(nodeB, flowId, SUSPEND, barrier));
                var completed = complete.get(10, TimeUnit.SECONDS);
                var suspended = suspend.get(10, TimeUnit.SECONDS);

                assertThat(completed ^ suspended).isTrue();
                assertThat(nodeA.find(flowId)).contains(completed ? DataFlowStates.COMPLETED : DataFlowStates.SUSPENDED);
                assertThat(countRows(flowId)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean raceToApply(SharedDataFlowStateStore node, String flowId, DataFlowTransition transition,
            CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            node.apply(flowId, transition);
            return true;
        } catch (XrdRuntimeException e) {
            return false;
        }
    }

    /**
     * {@code created_at}/{@code updated_at} are set by the {@code set_timestamps} Postgres trigger
     * (see {@code 013-dataflow-state.xml}), never by the application — so this reads the raw columns
     * directly rather than through {@link SharedDataFlowStateStore}, which has no reason to expose them.
     */
    @Test
    void auditTimestampsAreSetOnInsertAndUpdatedOnLifecycleChange() throws Exception {
        var flowId = uniqueFlowId();

        nodeA.apply(flowId, PREPARE);
        var afterInsert = selectTimestamps(flowId);
        assertThat(afterInsert.createdAt()).isNotNull();
        assertThat(afterInsert.updatedAt()).isNotNull();

        // Ensures the "later" assertion below holds regardless of how fast the two transactions run.
        Thread.sleep(50);

        nodeB.apply(flowId, START);
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
     * last changeset ({@code separate-admin-user}): it {@code GRANT}/{@code REVOKE}s permissions
     * between two Postgres roles, a split this single-role testcontainer has no use for.
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
