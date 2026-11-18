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
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * While a task worker holds the {@link ProcessLock} on a process, a save of that process on another thread and
 * connection must complete. EDC's dispatcher callback saves the state that way after a send, while the worker waits
 * for it with no timeout, and an inbound DSP save runs on a Jetty thread. The lock therefore must not block any
 * row write of the process.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class ProcessLockCrossThreadSaveTest {

    private static final long SAVE_WAIT_SECONDS = 5;

    private final org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements
            negotiationStatements =
            new org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements(
                    Clock.systemUTC());
    private final PostgresDialectStatements transferStatements = new PostgresDialectStatements(Clock.systemUTC());

    private PostgresqlStoreSetupExtension extension;
    private TransactionContext transactionContext;
    private ProcessLock processLock;
    private SqlContractNegotiationStore negotiationStore;
    private SqlTransferProcessStore transferStore;
    private ExecutorService callbackThread;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension setupExtension, QueryExecutor queryExecutor) {
        this.extension = setupExtension;
        var localTransactionContext = new LocalTransactionContext(new ConsoleMonitor());
        this.transactionContext = localTransactionContext;
        DataSourceRegistry dataSourceRegistry = new LocalDataSourceRegistry(localTransactionContext);
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
        callbackThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "OkHttp-callback-stand-in"));
    }

    @AfterEach
    void tearDown() {
        callbackThread.shutdownNow();
        extension.runQuery("DROP TABLE " + transferStatements.getTransferProcessTableName() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractAgreementTable() + " CASCADE");
    }

    @Test
    void negotiationSaveOnAnotherThreadCompletesWithinFiveSecondsWhileTheLockIsHeld() {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.REQUESTING));

        transactionContext.execute(() -> {
            assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue();
            var followUpSave = CompletableFuture.runAsync(() -> {
                var current = negotiationStore.findById(negotiationId);
                current.transitionRequested();
                negotiationStore.save(current);
            }, callbackThread);
            assertThatCode(() -> followUpSave.get(SAVE_WAIT_SECONDS, SECONDS)).doesNotThrowAnyException();
        });

        assertThat(ContractNegotiationStates.from(negotiationStore.findById(negotiationId).getState()))
                .isEqualTo(ContractNegotiationStates.REQUESTED);
    }

    @Test
    void transferSaveOnAnotherThreadCompletesWithinFiveSecondsWhileTheLockIsHeld() {
        var transferProcessId = "tp-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        transferStore.save(transferProcess(transferProcessId, participantContextId, TransferProcessStates.REQUESTING));

        transactionContext.execute(() -> {
            assertThat(processLock.tryLockTransferProcess(transferProcessId)).isTrue();
            var followUpSave = CompletableFuture.runAsync(() -> {
                var current = transferStore.findById(transferProcessId);
                current.transitionRequested();
                transferStore.save(current);
            }, callbackThread);
            assertThatCode(() -> followUpSave.get(SAVE_WAIT_SECONDS, SECONDS)).doesNotThrowAnyException();
        });

        assertThat(TransferProcessStates.from(transferStore.findById(transferProcessId).getState()))
                .isEqualTo(TransferProcessStates.REQUESTED);
    }

    @Test
    void anInboundDspSaveOnAnotherThreadCompletesWhileTheLockIsHeldWithNoDeadlock() {
        var negotiationId = "neg-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();
        negotiationStore.save(negotiation(negotiationId, participantContextId, ContractNegotiationStates.VERIFYING));

        transactionContext.execute(() -> {
            assertThat(processLock.tryLockContractNegotiation(negotiationId)).isTrue();
            var inboundSave = CompletableFuture.runAsync(() -> {
                var leased = negotiationStore.findByIdAndLease(negotiationId).getContent();
                leased.transitionVerified();
                negotiationStore.save(leased);
            }, callbackThread);
            assertThatCode(() -> inboundSave.get(SAVE_WAIT_SECONDS, SECONDS)).doesNotThrowAnyException();
        });

        assertThat(ContractNegotiationStates.from(negotiationStore.findById(negotiationId).getState()))
                .isEqualTo(ContractNegotiationStates.VERIFIED);
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
