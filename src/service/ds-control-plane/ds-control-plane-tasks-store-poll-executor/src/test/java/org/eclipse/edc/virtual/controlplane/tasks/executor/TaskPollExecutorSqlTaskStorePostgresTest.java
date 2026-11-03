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
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.RequestNegotiation;
import org.eclipse.edc.controlplane.tasks.Task;
import org.eclipse.edc.controlplane.tasks.TaskTypes;
import org.eclipse.edc.controlplane.tasks.sql.SqlTaskStore;
import org.eclipse.edc.controlplane.tasks.sql.schema.postgres.PostgresDialectStatementsTask;
import org.eclipse.edc.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.monitor.Monitor;
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

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.spi.response.ResponseStatus.ERROR_RETRY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs {@link TaskPollExecutor} against the real EDC {@link SqlTaskStore} (so {@code fetchForUpdate}'s
 * {@code FOR UPDATE SKIP LOCKED} is the genuine one) and the real {@link ProcessLock}, the only setup that can
 * show whether the due-time criterion on the fetch actually holds a re-queued task back from every worker, not
 * only from the worker that re-queued it.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class TaskPollExecutorSqlTaskStorePostgresTest {

    private final PostgresDialectStatements negotiationStatements = new PostgresDialectStatements(Clock.systemUTC());
    private final PostgresDialectStatementsTask taskStatements = new PostgresDialectStatementsTask();

    private PostgresqlStoreSetupExtension extension;
    private TransactionContext transactionContext;
    private ProcessLock processLock;
    private SqlContractNegotiationStore negotiationStore;
    private SqlTaskStore taskStore;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension setupExtension, QueryExecutor queryExecutor) {
        this.extension = setupExtension;
        var localTransactionContext = new LocalTransactionContext(new ConsoleMonitor());
        this.transactionContext = localTransactionContext;
        DataSourceRegistry dataSourceRegistry = new LocalDataSourceRegistry(localTransactionContext);
        dataSourceRegistry.register(extension.getDatasourceName(),
                extension.getDataSourceRegistry().resolve(extension.getDatasourceName()));

        extension.runQuery(TestUtils.getResourceFileContentAsString("contract-negotiation-schema.sql"));
        extension.runQuery(TestUtils.getResourceFileContentAsString("task-schema.sql"));

        var manager = new JacksonTypeManager();
        manager.registerTypes(TaskTypes.TYPES.toArray(Class<?>[]::new));

        negotiationStore = new SqlContractNegotiationStore(dataSourceRegistry, extension.getDatasourceName(),
                transactionContext, manager::getMapper, negotiationStatements, queryExecutor, "test-connector", Clock.systemUTC());

        taskStore = new SqlTaskStore(dataSourceRegistry, extension.getDatasourceName(), transactionContext,
                manager::getMapper, queryExecutor, taskStatements);

        processLock = new ProcessLock(dataSourceRegistry, extension.getDatasourceName());
    }

    @AfterEach
    void tearDown() {
        extension.runQuery("DROP TABLE " + taskStatements.getTaskTable());
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractAgreementTable() + " CASCADE");
    }

    @Test
    void transientRetryPauseHoldsAcrossWorkersAndTheRetryCapStillEndsTheTask() {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId));

        var task = negotiationTask(negotiationId);
        taskStore.create(task);

        var handleTimestamps = new CopyOnWriteArrayList<Long>();
        var contractNegotiationTaskExecutor = mock(ContractNegotiationTaskExecutor.class);
        when(contractNegotiationTaskExecutor.handle(any())).thenAnswer(invocation -> {
            handleTimestamps.add(System.nanoTime());
            return StatusResult.failure(ERROR_RETRY, "transient failure");
        });

        var maxRetries = 3;
        var executor = new TaskPollExecutor(new TaskPollConfig(10, maxRetries), 3, instrumentation(),
                contractNegotiationTaskExecutor, mock(TransferProcessTaskExecutor.class), taskStore, transactionContext,
                mock(Monitor.class), Clock.systemUTC(), processLock);

        try {
            executor.start();
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(taskStore.findById(task.getId())).isNull());
        } finally {
            executor.stop();
        }

        assertThat(handleTimestamps).hasSize(maxRetries + 1);
        for (int i = 1; i < handleTimestamps.size(); i++) {
            var gapMillis = Duration.ofNanos(handleTimestamps.get(i) - handleTimestamps.get(i - 1)).toMillis();
            assertThat(gapMillis).as("gap between handle calls %d and %d", i - 1, i).isGreaterThanOrEqualTo(90);
        }
    }

    @Test
    void anIndependentProcessCompletesWhileAnothersHandlerIsStillHeldThenCompletesAfterRelease() throws Exception {
        var heldNegotiationId = "neg-" + UUID.randomUUID();
        var independentNegotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(heldNegotiationId, participantContextId));
        negotiationStore.save(negotiation(independentNegotiationId, participantContextId));

        var heldTask = negotiationTask(heldNegotiationId);
        var independentTask = negotiationTask(independentNegotiationId);
        taskStore.create(heldTask);
        taskStore.create(independentTask);

        var heldHandlerEntered = new CountDownLatch(1);
        var releaseHeldHandler = new CountDownLatch(1);
        var contractNegotiationTaskExecutor = mock(ContractNegotiationTaskExecutor.class);
        when(contractNegotiationTaskExecutor.handle(any())).thenAnswer(invocation -> {
            ContractNegotiationTaskPayload payload = invocation.getArgument(0);
            if (payload.getProcessId().equals(heldNegotiationId)) {
                heldHandlerEntered.countDown();
                assertThat(releaseHeldHandler.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return StatusResult.success();
        });

        var executor = new TaskPollExecutor(new TaskPollConfig(10, 3), 2, instrumentation(),
                contractNegotiationTaskExecutor, mock(TransferProcessTaskExecutor.class), taskStore, transactionContext,
                mock(Monitor.class), Clock.systemUTC(), processLock);

        try {
            executor.start();
            assertThat(heldHandlerEntered.await(10, TimeUnit.SECONDS)).isTrue();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(taskStore.findById(independentTask.getId())).isNull());
            assertThat(taskStore.findById(heldTask.getId())).isNotNull();

            releaseHeldHandler.countDown();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(taskStore.findById(heldTask.getId())).isNull());
        } finally {
            executor.stop();
        }
    }

    private static ExecutorInstrumentation instrumentation() {
        var instrumentation = mock(ExecutorInstrumentation.class);
        when(instrumentation.instrument(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        return instrumentation;
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

    private ContractNegotiation negotiation(String id, String participantContextId) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .type(ContractNegotiation.Type.CONSUMER)
                .state(ContractNegotiationStates.REQUESTED.code())
                .correlationId("corr-" + id)
                .counterPartyAddress("http://provider/dsp")
                .counterPartyId("provider-1")
                .protocol("protocol")
                .participantContextId(participantContextId)
                .build();
    }
}
