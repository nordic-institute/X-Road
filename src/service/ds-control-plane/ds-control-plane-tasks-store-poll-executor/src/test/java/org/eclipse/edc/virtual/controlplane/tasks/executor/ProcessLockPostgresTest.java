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

package org.eclipse.edc.virtual.controlplane.tasks.executor;

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.SqlTransferProcessStore;
import org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.controlplane.tasks.Task;
import org.eclipse.edc.controlplane.tasks.store.TaskStore;
import org.eclipse.edc.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.controlplane.transfer.spi.tasks.PrepareTransfer;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.local.LocalDataSourceRegistry;
import org.eclipse.edc.transaction.local.LocalTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link ProcessLock}'s race-safety directly against Postgres: the in-memory stores cannot reproduce advisory
 * locking at all, so they are never used here as evidence. Follows the Testcontainers/{@code CyclicBarrier} style of
 * {@code XRoadContractNegotiationStoreConvergeTest}.
 *
 * <p>{@link PostgresqlStoreSetupExtension} wires its stores to a {@code NoopTransactionContext}, which hands every
 * SQL call its own physical connection; a transaction-scoped advisory lock taken on one such connection is gone the
 * instant that call returns. Production runs {@link ProcessLock} inside a real {@link LocalTransactionContext}
 * instead, where one connection is held for the whole task transaction, so this test builds that same real
 * transaction context and data source registry (as {@code TwoInstanceCompletionPostgresTest} does) and runs the
 * lock and the stores through it, the only setup that can actually exercise the lock's lifetime.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class ProcessLockPostgresTest {

    private final org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements
            negotiationStatements =
            new org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements(
                    Clock.systemUTC());
    private final PostgresDialectStatements transferStatements = new PostgresDialectStatements(Clock.systemUTC());

    private PostgresqlStoreSetupExtension extension;
    private TransactionContext transactionContext;
    private DataSourceRegistry dataSourceRegistry;
    private ProcessLock processLock;
    private SqlContractNegotiationStore negotiationStore;
    private SqlTransferProcessStore transferStore;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension setupExtension, QueryExecutor queryExecutor) throws IOException {
        this.extension = setupExtension;
        var localTransactionContext = new LocalTransactionContext(new ConsoleMonitor());
        this.transactionContext = localTransactionContext;
        this.dataSourceRegistry = new LocalDataSourceRegistry(localTransactionContext);
        dataSourceRegistry.register(extension.getDatasourceName(),
                extension.getDataSourceRegistry().resolve(extension.getDatasourceName()));

        extension.runQuery(TestUtils.getResourceFileContentAsString("contract-negotiation-schema.sql"));
        extension.runQuery(TestUtils.getResourceFileContentAsString("transfer-process-schema.sql"));

        var manager = new JacksonTypeManager();

        negotiationStore = new SqlContractNegotiationStore(dataSourceRegistry, extension.getDatasourceName(),
                transactionContext, manager::getMapper, negotiationStatements, queryExecutor, "test-connector", Clock.systemUTC());

        transferStore = new SqlTransferProcessStore(dataSourceRegistry, extension.getDatasourceName(),
                transactionContext, manager::getMapper, transferStatements, queryExecutor, "test-connector", Clock.systemUTC());

        processLock = new ProcessLock(dataSourceRegistry, extension.getDatasourceName());
    }

    @AfterEach
    void tearDown() {
        extension.runQuery("DROP TABLE " + transferStatements.getTransferProcessTableName() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractAgreementTable() + " CASCADE");
    }

    @Test
    void contractNegotiationExistsReturnsFalseWhenTheRowDoesNotExist() {
        transactionContext.execute(() ->
                assertThat(processLock.contractNegotiationExists("missing-negotiation")).isFalse());
    }

    @Test
    void transferProcessExistsReturnsFalseWhenTheRowDoesNotExist() {
        transactionContext.execute(() ->
                assertThat(processLock.transferProcessExists("missing-transfer")).isFalse());
    }

    @Test
    void lockOnANegotiationReleasesOnCommitSoAnotherTransactionLocksItAtOnce() {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.REQUESTED));

        transactionContext.execute(() -> assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue());
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                transactionContext.execute(() -> assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue()));
    }

    @Test
    void lockOnANegotiationReleasesOnRollbackSoAnotherTransactionLocksItAtOnce() {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.REQUESTED));

        assertThatThrows(() -> transactionContext.execute(() -> {
            assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue();
            throw new RuntimeException("simulated failure");
        }));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                transactionContext.execute(() -> assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue()));
    }

    @Test
    void tryLockContractNegotiationReturnsFalseWhileAnotherTransactionHoldsItThenTrueOnceThatTransactionEnds() throws Exception {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.REQUESTED));

        assertBusyThenAvailable(() -> processLock.tryLockContractNegotiation(negotiationId));
    }

    @Test
    void tryLockTransferProcessReturnsFalseWhileAnotherTransactionHoldsItThenTrueOnceThatTransactionEnds() throws Exception {
        var transferProcessId = "tp-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        transferStore.save(transferProcess(transferProcessId, participantContextId, TransferProcessStates.REQUESTED));

        assertBusyThenAvailable(() -> processLock.tryLockTransferProcess(transferProcessId));
    }

    private void assertBusyThenAvailable(BooleanSupplier tryLock) throws Exception {
        var holderLockedLatch = new CountDownLatch(1);
        var releaseHolderLatch = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> {
                transactionContext.execute(() -> {
                    assertThat(tryLock.getAsBoolean()).isTrue();
                    holderLockedLatch.countDown();
                    awaitUninterruptibly(releaseHolderLatch);
                });
                return null;
            });

            assertThat(holderLockedLatch.await(10, TimeUnit.SECONDS)).isTrue();

            var challenger = executor.submit(() -> transactionContext.execute(tryLock::getAsBoolean));
            assertThat(challenger.get(10, TimeUnit.SECONDS)).isFalse();

            releaseHolderLatch.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        transactionContext.execute(() -> assertThat(tryLock.getAsBoolean()).isTrue());
    }

    @Test
    void twoWorkersOnDifferentNegotiationsBothLockWithoutWaitingOnEachOthersLock() throws Exception {
        var negotiationIdA = "neg-" + UUID.randomUUID();
        var negotiationIdB = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationIdA, participantContextId, ContractNegotiationStates.REQUESTED));
        negotiationStore.save(negotiation(negotiationIdB, participantContextId, ContractNegotiationStates.REQUESTED));

        var maxConcurrent = runTwoLocksConcurrently(
                () -> processLock.tryLockContractNegotiation(negotiationIdA),
                () -> processLock.tryLockContractNegotiation(negotiationIdB));

        assertThat(maxConcurrent).isEqualTo(2);
    }

    @Test
    void aNegotiationKeyAndATransferKeyWithTheSameIdDoNotBlockEachOther() throws Exception {
        var sharedId = "shared-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(sharedId, participantContextId, ContractNegotiationStates.REQUESTED));
        transferStore.save(transferProcess(sharedId, participantContextId, TransferProcessStates.REQUESTED));

        var maxConcurrent = runTwoLocksConcurrently(
                () -> processLock.tryLockContractNegotiation(sharedId),
                () -> processLock.tryLockTransferProcess(sharedId));

        assertThat(maxConcurrent).isEqualTo(2);
    }

    private int runTwoLocksConcurrently(BooleanSupplier lockA, BooleanSupplier lockB) throws Exception {
        var barrier = new CyclicBarrier(2);
        var runningCount = new AtomicInteger();
        var maxConcurrent = new AtomicInteger();
        var release = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> lockAndHold(lockA, barrier, runningCount, maxConcurrent, release));
            var second = executor.submit(() -> lockAndHold(lockB, barrier, runningCount, maxConcurrent, release));

            await10sUntilEqual(runningCount, 2);
            release.countDown();

            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        return maxConcurrent.get();
    }

    private Void lockAndHold(BooleanSupplier lock, CyclicBarrier barrier, AtomicInteger runningCount,
                              AtomicInteger maxConcurrent, CountDownLatch release) throws Exception {
        barrier.await();
        transactionContext.execute(() -> {
            assertThat(lock.getAsBoolean()).isTrue();
            var current = runningCount.incrementAndGet();
            maxConcurrent.updateAndGet(max -> Math.max(max, current));
            awaitUninterruptibly(release);
            runningCount.decrementAndGet();
        });
        return null;
    }

    @Test
    void theLockIsVisibleInPgLocksOnlyForTheLockingBackendWhileItIsHeld() {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.REQUESTED));

        var backendPid = new AtomicInteger();
        var advisoryLocksWhileHeld = new AtomicInteger();

        transactionContext.execute(() -> {
            assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue();
            backendPid.set(currentBackendPid());
            advisoryLocksWhileHeld.set(advisoryLockCountForBackend(backendPid.get()));
        });

        assertThat(advisoryLocksWhileHeld.get()).isEqualTo(1);
        assertThat(advisoryLockCountForBackend(backendPid.get())).isZero();
    }

    @Test
    void twoWorkersOnTheSameNegotiationExactlyOneRunsTheHandlerWhileTheOtherRequeues() throws Exception {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.REQUESTED));

        var task1 = negotiationTask(negotiationId).toBuilder().retryCount(2).build();
        var task2 = negotiationTask(negotiationId).toBuilder().retryCount(2).build();
        var contractNegotiationTaskExecutor = mock(ContractNegotiationTaskExecutor.class);
        var loserRequeuedLatch = new CountDownLatch(1);
        when(contractNegotiationTaskExecutor.handle(any())).thenAnswer(invocation -> {
            assertThat(loserRequeuedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            return StatusResult.success();
        });

        var outcome = runTwoWorkersOnTheSameProcess(task1, task2, contractNegotiationTaskExecutor,
                mock(TransferProcessTaskExecutor.class), loserRequeuedLatch);

        verify(contractNegotiationTaskExecutor, times(1)).handle(any());
        assertThat(Set.of(task1.getId(), task2.getId())).containsExactlyInAnyOrder(outcome.deletedTaskId(), outcome.requeuedTask().getId());
        assertThat(outcome.requeuedTask().getRetryCount()).isEqualTo(2);
        assertThat(outcome.requeuedTask().getAt()).isGreaterThanOrEqualTo(outcome.startedAt() + 90);
    }

    @Test
    void twoWorkersOnTheSameTransferProcessExactlyOneRunsTheHandlerWhileTheOtherRequeues() throws Exception {
        var transferProcessId = "tp-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        transferStore.save(transferProcess(transferProcessId, participantContextId, TransferProcessStates.REQUESTED));

        var task1 = transferTask(transferProcessId).toBuilder().retryCount(2).build();
        var task2 = transferTask(transferProcessId).toBuilder().retryCount(2).build();
        var transferProcessTaskExecutor = mock(TransferProcessTaskExecutor.class);
        var loserRequeuedLatch = new CountDownLatch(1);
        when(transferProcessTaskExecutor.handle(any())).thenAnswer(invocation -> {
            assertThat(loserRequeuedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            return StatusResult.success();
        });

        var outcome = runTwoWorkersOnTheSameProcess(task1, task2, mock(ContractNegotiationTaskExecutor.class),
                transferProcessTaskExecutor, loserRequeuedLatch);

        verify(transferProcessTaskExecutor, times(1)).handle(any());
        assertThat(Set.of(task1.getId(), task2.getId())).containsExactlyInAnyOrder(outcome.deletedTaskId(), outcome.requeuedTask().getId());
        assertThat(outcome.requeuedTask().getRetryCount()).isEqualTo(2);
        assertThat(outcome.requeuedTask().getAt()).isGreaterThanOrEqualTo(outcome.startedAt() + 90);
    }

    private TwoWorkerOutcome runTwoWorkersOnTheSameProcess(Task task1, Task task2,
            ContractNegotiationTaskExecutor contractNegotiationTaskExecutor, TransferProcessTaskExecutor transferProcessTaskExecutor,
            CountDownLatch loserRequeuedLatch) throws InterruptedException {
        var taskStore = mock(TaskStore.class);
        var remaining = new ConcurrentLinkedQueue<>(List.of(task1, task2));
        when(taskStore.fetchForUpdate(any(QuerySpec.class))).thenAnswer(invocation -> {
            var next = remaining.poll();
            return next == null ? List.of() : List.of(next);
        });
        doAnswer(invocation -> {
            loserRequeuedLatch.countDown();
            return null;
        }).when(taskStore).update(any());

        var instrumentation = mock(ExecutorInstrumentation.class);
        when(instrumentation.instrument(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        var clock = Clock.systemUTC();
        var startedAt = clock.millis();
        var executor = new TaskPollExecutor(new TaskPollConfig(10, 3), 2, instrumentation,
                contractNegotiationTaskExecutor, transferProcessTaskExecutor, taskStore, transactionContext,
                mock(Monitor.class), clock, processLock);

        try {
            executor.start();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(taskStore).delete(any());
                verify(taskStore).update(any());
            });
        } finally {
            executor.stop();
        }

        var deleteCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskStore).delete(deleteCaptor.capture());
        var updateCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskStore).update(updateCaptor.capture());

        return new TwoWorkerOutcome(deleteCaptor.getValue(), updateCaptor.getValue(), startedAt);
    }

    private record TwoWorkerOutcome(String deletedTaskId, Task requeuedTask, long startedAt) {
    }

    private int currentBackendPid() {
        try (
                var connection = dataSourceRegistry.resolve(extension.getDatasourceName()).getConnection();
                var statement = connection.prepareStatement("SELECT pg_backend_pid()");
                var resultSet = statement.executeQuery()
        ) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int advisoryLockCountForBackend(int pid) {
        var dataSource = extension.getDataSourceRegistry().resolve(extension.getDatasourceName());
        try (
                var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = ?")
        ) {
            statement.setInt(1, pid);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertThatThrows(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected an exception to be thrown");
        } catch (RuntimeException e) {
            assertThat(e).hasMessageContaining("simulated failure");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private static void await10sUntilEqual(AtomicInteger actual, int expected) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (actual.get() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(actual.get()).isEqualTo(expected);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Task negotiationTask(String processId) {
        var payload = RequestNegotiation.Builder.newInstance()
                .processId(processId)
                .processState(100)
                .processType("CONSUMER")
                .build();
        return Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();
    }

    private static Task transferTask(String processId) {
        var payload = PrepareTransfer.Builder.newInstance()
                .processId(processId)
                .processState(100)
                .processType("CONSUMER")
                .build();
        return Task.Builder.newInstance()
                .at(System.currentTimeMillis())
                .payload(payload)
                .build();
    }

    private ContractNegotiation negotiation(String id, String participantContextId, ContractNegotiationStates state) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .type(ContractNegotiation.Type.CONSUMER)
                .state(state.code())
                .correlationId("corr-" + id)
                .counterPartyAddress("http://provider/dsp")
                .counterPartyId("provider-1")
                .protocol("protocol")
                .participantContextId(participantContextId)
                .build();
    }

    private TransferProcess transferProcess(String id, String participantContextId, TransferProcessStates state) {
        return TransferProcess.Builder.newInstance()
                .id(id)
                .type(TransferProcess.Type.CONSUMER)
                .state(state.code())
                .participantContextId(participantContextId)
                .build();
    }
}
