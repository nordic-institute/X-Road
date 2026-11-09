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
package org.niis.xroad.edc.extension.cleaner;

import ee.ria.xroad.common.db.Postgres10FixedImplicitSequenceDialect;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolRemoteMessageDispatcher;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.SqlTransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.command.handlers.TerminateTransferCommandHandler;
import org.eclipse.edc.connector.controlplane.transfer.observe.TransferProcessObservableImpl;
import org.eclipse.edc.connector.controlplane.transfer.processors.TransferProcessorsImpl;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyRegistrationTypes;
import org.eclipse.edc.protocol.spi.ProtocolWebhookResolver;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.retry.ExponentialWaitStrategy;
import org.eclipse.edc.sql.DriverManagerConnectionFactory;
import org.eclipse.edc.sql.SqlQueryExecutor;
import org.eclipse.edc.sql.datasource.ConnectionFactoryDataSource;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessConfiguration;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessFactory;
import org.eclipse.edc.transaction.datasource.spi.DefaultDataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.serverconf.ServerConfDbProperties;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.DataFlowStateDAOImpl;
import org.niis.xroad.serverconf.model.DataFlowLifecycleState;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the cleaner against a Postgres testcontainer holding the real EDC store tables (transfer process, contract
 * negotiation, policy monitor) and the real serverconf schema, with the stock SQL EDC stores.
 */
class DspRecordCleanerPostgresTest {

    private static final Duration AGE = Duration.ofHours(6);
    private static final Duration OLD = AGE.plusHours(1);
    private static final Duration YOUNG = Duration.ofHours(1);
    private static final Duration MAX_AGE = Duration.ofDays(7);
    private static final Duration PAST_MAX_AGE = MAX_AGE.plusHours(1);
    private static final String DATA_SOURCE_NAME = "default";
    private static final String PARTICIPANT_CONTEXT = "participant-1";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static DefaultDataSourceRegistry dataSourceRegistry;
    private static ServerConfDatabaseCtx serverConfDatabaseCtx;
    private static TransferProcessStore transferProcessStore;
    private static ContractNegotiationStore negotiationStore;

    private final Clock clock = Clock.systemUTC();
    private final RecordingMonitor monitor = new RecordingMonitor();
    private final DataFlowStateDAOImpl dataFlowStateDao = new DataFlowStateDAOImpl();
    private final DataAddressStore dataAddressStore = mock();

    @BeforeAll
    static void startDatabase() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        POSTGRES.start();
        applyServerConfChangelog();

        var properties = new Properties();
        properties.put("user", POSTGRES.getUsername());
        properties.put("password", POSTGRES.getPassword());
        dataSourceRegistry = new DefaultDataSourceRegistry();
        dataSourceRegistry.register(DATA_SOURCE_NAME,
                new ConnectionFactoryDataSource(new DriverManagerConnectionFactory(), POSTGRES.getJdbcUrl(), properties));
        for (var schema : List.of("transfer-process-schema.sql", "contract-negotiation-schema.sql", "policy-monitor-schema.sql")) {
            execute(TestUtils.getResourceFileContentAsString(schema));
        }

        var typeManager = new JacksonTypeManager();
        typeManager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));
        var queryExecutor = new SqlQueryExecutor();
        var transactionContext = new NoopTransactionContext();
        transferProcessStore = new SqlTransferProcessStore(dataSourceRegistry, DATA_SOURCE_NAME, transactionContext,
                typeManager::getMapper,
                new org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.schema.postgres
                        .PostgresDialectStatements(Clock.systemUTC()),
                queryExecutor, "cleaner-test", Clock.systemUTC());
        negotiationStore = new SqlContractNegotiationStore(dataSourceRegistry, DATA_SOURCE_NAME, transactionContext,
                typeManager::getMapper, new PostgresDialectStatements(Clock.systemUTC()), queryExecutor,
                "cleaner-test", Clock.systemUTC());
        serverConfDatabaseCtx = new ServerConfDatabaseCtx(serverConfDbProperties());
    }

    @AfterAll
    static void stopDatabase() {
        if (serverConfDatabaseCtx != null) {
            serverConfDatabaseCtx.destroy();
        }
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void emptyTables() throws SQLException {
        when(dataAddressStore.remove(any())).thenReturn(StoreResult.success());
        execute("TRUNCATE edc_transfer_process, edc_contract_negotiation, edc_contract_agreement, "
                + "edc_policy_monitor, dataflow_state");
    }

    @Test
    void stuckTransferIsTerminatedThroughEdcAndItsDataAddressSecretRemoved() {
        saveTransfer("stuck", TransferProcess.Type.PROVIDER, TransferProcessStates.STARTED, OLD);
        saveTransfer("active", TransferProcess.Type.PROVIDER, TransferProcessStates.STARTED, YOUNG);
        var edc = new EdcTerminationChain();

        var summary = newCleaner(edc.service()).run();

        assertThat(summary.terminatedTransfers()).isEqualTo(1);
        assertThat(transferProcessStore.findById("stuck").getState()).isEqualTo(TransferProcessStates.TERMINATING.code());
        assertThat(transferProcessStore.findById("active").getState()).isEqualTo(TransferProcessStates.STARTED.code());

        edc.processTerminating(transferProcessStore.findById("stuck"));

        assertThat(transferProcessStore.findById("stuck").getState()).isEqualTo(TransferProcessStates.TERMINATED.code());
        verify(edc.dataAddressStore).remove(any(TransferProcess.class));
        monitor.assertNoProblems();
    }

    @Test
    void onlyOldTransfersInStatesThatEdcCanTerminateAreTerminated() {
        var terminable = new ArrayList<String>();
        for (var state : TransferProcessStates.values()) {
            saveTransfer("old-" + state.name(), TransferProcess.Type.PROVIDER, state, OLD);
            saveTransfer("young-" + state.name(), TransferProcess.Type.PROVIDER, state, YOUNG);
            if (state.code() < TransferProcessStates.COMPLETED.code() && canBeTerminated(state)) {
                terminable.add("old-" + state.name());
            }
        }
        TransferProcessService service = mock();
        when(service.terminate(any(TerminateTransferCommand.class))).thenReturn(ServiceResult.success());

        var summary = newCleaner(service).run();

        var commands = ArgumentCaptor.forClass(TerminateTransferCommand.class);
        verify(service, times(terminable.size())).terminate(commands.capture());
        var terminated = commands.getAllValues().stream().map(TerminateTransferCommand::getEntityId).toList();
        assertThat(terminated).containsExactlyInAnyOrderElementsOf(terminable)
                .doesNotContain("old-" + TransferProcessStates.RESUMED.name());
        assertThat(summary.terminatedTransfers()).isEqualTo(terminable.size());
        assertThat(transferProcessStore.findById("old-" + TransferProcessStates.COMPLETED.name())).isNull();
        assertThat(transferProcessStore.findById("old-" + TransferProcessStates.TERMINATED.name())).isNull();
        assertThat(transferProcessStore.findById("old-" + TransferProcessStates.RESUMED.name())).isNotNull();
        assertThat(transferProcessStore.findById("young-" + TransferProcessStates.COMPLETED.name())).isNotNull();
        monitor.assertNoProblems();
    }

    @Test
    void finishedTransferBacklogLargerThanOneBatchIsDeletedInOnePass() {
        var backlog = DspRecordCleaner.PAGE_SIZE * 2 + 1;
        for (int i = 0; i < backlog; i++) {
            saveTransfer("old-%05d".formatted(i), TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, OLD);
        }
        saveTransfer("young", TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, YOUNG);

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary.deletedTransfers()).isEqualTo(backlog);
        assertThat(transferProcessStore.findById("young")).isNotNull();
        assertThat(transferProcessStore.findById("old-00000")).isNull();
        assertThat(transferProcessStore.findById("old-%05d".formatted(backlog - 1))).isNull();
        monitor.assertNoProblems();
    }

    @Test
    void finishedRowsOlderThanTheAgeAreDeletedAndYoungerOnesStay() throws Exception {
        saveTransfer("old-completed", TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, OLD);
        saveTransfer("old-terminated", TransferProcess.Type.CONSUMER, TransferProcessStates.TERMINATED, OLD);
        saveTransfer("young-completed", TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, YOUNG);
        insertPolicyEntry("old-completed", 200, OLD);
        insertPolicyEntry("old-failed", 300, OLD);
        insertPolicyEntry("old-started", 100, OLD);
        insertPolicyEntry("young-completed", 200, YOUNG);
        insertFlow("old-completed", DataFlowLifecycleState.COMPLETED, OLD);
        insertFlow("old-terminated", DataFlowLifecycleState.TERMINATED, OLD);
        insertFlow("old-started", DataFlowLifecycleState.STARTED, OLD);
        insertFlow("young-terminated", DataFlowLifecycleState.TERMINATED, YOUNG);
        saveNegotiation("old-no-agreement", OLD, false);
        saveNegotiation("young-no-agreement", YOUNG, false);

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary).isEqualTo(new DspRecordCleaner.Summary(0, 2, 2, 2, 1, 0));
        assertThat(ids("edc_transfer_process", "transferprocess_id")).containsExactly("young-completed");
        assertThat(ids("edc_policy_monitor", "entry_id")).containsExactlyInAnyOrder("old-started", "young-completed");
        assertThat(ids("dataflow_state", "flow_id")).containsExactlyInAnyOrder("old-started", "young-terminated");
        assertThat(ids("edc_contract_negotiation", "id")).containsExactly("young-no-agreement");
        monitor.assertNoProblems();
    }

    @Test
    void flowRowFinishedInsideTheMinimumAgeSurvivesSoALateSignalStillFindsIt() throws Exception {
        insertFlow("inside-floor", DataFlowLifecycleState.TERMINATED, DspRecordCleaner.MINIMUM_AGE.minusMinutes(5));
        var age = DspRecordCleaner.requireValidAge(DspRecordCleaner.MINIMUM_AGE.plusSeconds(1));
        var cleaner = new DspRecordCleaner(transferProcessStore, new EdcTerminationChain().service(), dataAddressStore,
                negotiationStore, transferProcessIdReader(), negotiationsWithoutAgreementReader(), policyMonitorEntryPurger(),
                serverConfDatabaseCtx, clock, monitor, age, DspRecordCleaner.requireValidMaxAge(MAX_AGE, age));

        cleaner.run();

        assertThat(ids("dataflow_state", "flow_id")).containsExactly("inside-floor");
    }

    @Test
    void flowRowsInAnyStateOlderThanTheMaxAgeAreDeletedAndYoungerUnfinishedOnesStay() throws Exception {
        insertFlow("provisioned-past-max", DataFlowLifecycleState.PROVISIONED, PAST_MAX_AGE);
        insertFlow("started-past-max", DataFlowLifecycleState.STARTED, PAST_MAX_AGE);
        insertFlow("suspended-past-max", DataFlowLifecycleState.SUSPENDED, PAST_MAX_AGE);
        insertFlow("terminated-past-max", DataFlowLifecycleState.TERMINATED, PAST_MAX_AGE);
        insertFlow("started-past-age", DataFlowLifecycleState.STARTED, OLD);
        insertFlow("suspended-past-age", DataFlowLifecycleState.SUSPENDED, OLD);
        insertFlow("terminated-past-age", DataFlowLifecycleState.TERMINATED, OLD);
        insertFlow("started-young", DataFlowLifecycleState.STARTED, YOUNG);

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary.deletedDataFlowStates()).isEqualTo(5);
        assertThat(ids("dataflow_state", "flow_id"))
                .containsExactlyInAnyOrder("started-past-age", "suspended-past-age", "started-young");
        monitor.assertNoProblems();
    }

    @Test
    void startedRowRecreatedByALateStartSignalIsDeletedOnceOlderThanTheMaxAge() throws Exception {
        insertFlow("flow-1", DataFlowLifecycleState.TERMINATED, OLD);
        var cleaner = newCleaner(new EdcTerminationChain().service());
        cleaner.run();
        assertThat(ids("dataflow_state", "flow_id")).isEmpty();

        serverConfDatabaseCtx.doInTransaction(session -> dataFlowStateDao.insert(session, "flow-1", DataFlowLifecycleState.STARTED));
        assertThat(cleaner.run().deletedDataFlowStates()).isZero();
        assertThat(ids("dataflow_state", "flow_id")).containsExactly("flow-1");

        ageFlow("flow-1", PAST_MAX_AGE);
        var summary = cleaner.run();

        assertThat(summary.deletedDataFlowStates()).isEqualTo(1);
        assertThat(ids("dataflow_state", "flow_id")).isEmpty();
    }

    @Test
    void resumedTransfersOlderThanTheMaxAgeAreDeletedWithTheirSecretAndYoungerOnesStay() {
        saveTransfer("resumed-past-max", TransferProcess.Type.PROVIDER, TransferProcessStates.RESUMED, PAST_MAX_AGE);
        saveTransfer("resumed-past-age", TransferProcess.Type.PROVIDER, TransferProcessStates.RESUMED, OLD);
        var edc = new EdcTerminationChain();

        var summary = newCleaner(edc.service()).run();

        assertThat(summary).isEqualTo(new DspRecordCleaner.Summary(0, 0, 0, 0, 0, 1));
        assertThat(transferProcessStore.findById("resumed-past-max")).isNull();
        assertThat(transferProcessStore.findById("resumed-past-age")).isNotNull();
        verify(dataAddressStore).remove(argThat(transfer -> "resumed-past-max".equals(transfer.getId())));
        verify(dataAddressStore, never()).remove(argThat(transfer -> "resumed-past-age".equals(transfer.getId())));
        verify(edc.service, never()).terminate(any(TerminateTransferCommand.class));
        monitor.assertNoProblems();
    }

    @Test
    void resumedTransferIsKeptWhenItsDataAddressSecretCannotBeRemoved() {
        saveTransfer("resumed-past-max", TransferProcess.Type.PROVIDER, TransferProcessStates.RESUMED, PAST_MAX_AGE);
        when(dataAddressStore.remove(any())).thenReturn(StoreResult.generalError("vault unavailable"));

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary.deletedResumedTransfers()).isZero();
        assertThat(transferProcessStore.findById("resumed-past-max")).isNotNull();
    }

    @Test
    void negotiationsWithAnAgreementAreKeptAndTheAgreementIsNeverDeleted() throws Exception {
        saveNegotiation("old-agreed", OLD, true);
        saveNegotiation("old-no-agreement", OLD, false);

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary.deletedNegotiations()).isEqualTo(1);
        assertThat(ids("edc_contract_negotiation", "id")).containsExactly("old-agreed");
        assertThat(ids("edc_contract_agreement", "agr_id")).containsExactly("agreement-old-agreed");
    }

    @Test
    void negotiationsWithAnAgreementAreNeitherLoadedNorOfferedForDeletion() {
        saveNegotiation("old-agreed", OLD, true);
        saveNegotiation("old-no-agreement", OLD, false);
        var spyStore = spy(negotiationStore);

        newCleaner(new EdcTerminationChain().service(), spyStore).run();

        verify(spyStore, never()).queryNegotiations(any());
        verify(spyStore, never()).deleteById("old-agreed");
        verify(spyStore).deleteById("old-no-agreement");
    }

    @Test
    void backlogLargerThanOneBatchIsDeletedInOnePassAndAgreementsInBetweenStay() throws Exception {
        var backlog = DspRecordCleaner.PAGE_SIZE * 2 + 1;
        for (int i = 0; i < backlog; i++) {
            saveNegotiation("old-%05d".formatted(i), OLD, false);
        }
        saveNegotiation("old-00250-agreed", OLD, true);
        saveNegotiation("young", YOUNG, false);

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary.deletedNegotiations()).isEqualTo(backlog);
        assertThat(ids("edc_contract_negotiation", "id")).containsExactlyInAnyOrder("old-00250-agreed", "young");
        monitor.assertNoProblems();
    }

    @Test
    void negotiationLeasedByAnotherRuntimeIsSkippedWithoutBlockingTheBatch() throws Exception {
        saveNegotiation("a-leased", OLD, false);
        saveNegotiation("b-free", OLD, false);
        assertThat(negotiationStore.findByIdAndLease("a-leased").succeeded()).isTrue();

        var summary = newCleaner(new EdcTerminationChain().service()).run();

        assertThat(summary.deletedNegotiations()).isEqualTo(1);
        assertThat(ids("edc_contract_negotiation", "id")).containsExactly("a-leased");
    }

    @Test
    void secondRunDeletesNothingMore() throws Exception {
        seedMixedRows();
        var cleaner = newCleaner(new EdcTerminationChain().service());

        var first = cleaner.run();
        var rowsAfterFirst = rowCounts();
        var second = cleaner.run();

        assertThat(first.deletedTransfers()).isPositive();
        assertThat(first.deletedPolicyEntries()).isPositive();
        assertThat(first.deletedDataFlowStates()).isPositive();
        assertThat(first.deletedNegotiations()).isPositive();
        assertThat(second).isEqualTo(new DspRecordCleaner.Summary(0, 0, 0, 0, 0, 0));
        assertThat(rowCounts()).isEqualTo(rowsAfterFirst);
        monitor.assertNoProblems();
    }

    @Test
    void twoCleanersRunningAtOnceDeleteEachRowOnceAndFailNothing() throws Exception {
        var rows = 120;
        for (int i = 0; i < rows; i++) {
            saveTransfer("old-" + i, TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, OLD);
            insertPolicyEntry("old-" + i, 200, OLD);
            insertFlow("old-" + i, DataFlowLifecycleState.TERMINATED, OLD);
            saveNegotiation("old-" + i, OLD, false);
        }
        saveTransfer("young", TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, YOUNG);
        insertPolicyEntry("young", 200, YOUNG);
        insertFlow("young", DataFlowLifecycleState.TERMINATED, YOUNG);
        saveNegotiation("young", YOUNG, false);
        saveNegotiation("agreed", OLD, true);
        var service = new EdcTerminationChain().service();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Supplier<CompletableFuture<DspRecordCleaner.Summary>> start = () -> CompletableFuture.supplyAsync(() -> {
                var cleaner = newCleaner(service);
                awaitTogether(barrier);
                return cleaner.run();
            }, executor);
            var first = start.get();
            var second = start.get();

            var a = first.get(2, TimeUnit.MINUTES);
            var b = second.get(2, TimeUnit.MINUTES);

            assertThat(a.deletedPolicyEntries() + b.deletedPolicyEntries()).isEqualTo(rows);
            assertThat(a.deletedDataFlowStates() + b.deletedDataFlowStates()).isEqualTo(rows);
        } finally {
            executor.shutdownNow();
        }
        assertThat(ids("edc_transfer_process", "transferprocess_id")).containsExactly("young");
        assertThat(ids("edc_policy_monitor", "entry_id")).containsExactly("young");
        assertThat(ids("dataflow_state", "flow_id")).containsExactly("young");
        assertThat(ids("edc_contract_negotiation", "id")).containsExactlyInAnyOrder("young", "agreed");
        assertThat(ids("edc_contract_agreement", "agr_id")).containsExactly("agreement-agreed");
        monitor.assertNoProblems();
    }

    private void seedMixedRows() throws Exception {
        saveTransfer("old-completed", TransferProcess.Type.PROVIDER, TransferProcessStates.COMPLETED, OLD);
        saveTransfer("young-started", TransferProcess.Type.PROVIDER, TransferProcessStates.STARTED, YOUNG);
        saveTransfer("stuck", TransferProcess.Type.PROVIDER, TransferProcessStates.STARTED, OLD);
        insertPolicyEntry("old-completed", 200, OLD);
        insertPolicyEntry("young-started", 100, YOUNG);
        insertFlow("old-completed", DataFlowLifecycleState.COMPLETED, OLD);
        insertFlow("young-started", DataFlowLifecycleState.STARTED, YOUNG);
        saveNegotiation("old-no-agreement", OLD, false);
        saveNegotiation("old-agreed", OLD, true);
    }

    private Map<String, Long> rowCounts() throws SQLException {
        var counts = new java.util.LinkedHashMap<String, Long>();
        for (var table : List.of("edc_transfer_process", "edc_contract_negotiation", "edc_contract_agreement",
                "edc_policy_monitor", "dataflow_state")) {
            counts.put(table, count(table));
        }
        return counts;
    }

    private DspRecordCleaner newCleaner(TransferProcessService transferProcessService) {
        return newCleaner(transferProcessService, negotiationStore);
    }

    private DspRecordCleaner newCleaner(TransferProcessService transferProcessService, ContractNegotiationStore store) {
        return new DspRecordCleaner(transferProcessStore, transferProcessService, dataAddressStore, store,
                transferProcessIdReader(), negotiationsWithoutAgreementReader(), policyMonitorEntryPurger(),
                serverConfDatabaseCtx, clock, monitor, AGE, MAX_AGE);
    }

    private TransferProcessIdReader transferProcessIdReader() {
        return new TransferProcessIdReader(dataSourceRegistry, DATA_SOURCE_NAME, new NoopTransactionContext(),
                new SqlQueryExecutor(),
                new org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.schema.postgres
                        .PostgresDialectStatements(Clock.systemUTC()));
    }

    private NegotiationsWithoutAgreementReader negotiationsWithoutAgreementReader() {
        return new NegotiationsWithoutAgreementReader(dataSourceRegistry, DATA_SOURCE_NAME, new NoopTransactionContext(),
                new SqlQueryExecutor(), new PostgresDialectStatements(Clock.systemUTC()));
    }

    private PolicyMonitorEntryPurger policyMonitorEntryPurger() {
        return new PolicyMonitorEntryPurger(dataSourceRegistry, DATA_SOURCE_NAME, new NoopTransactionContext(),
                new SqlQueryExecutor(),
                new org.eclipse.edc.connector.policy.monitor.store.sql.schema.PostgresPolicyMonitorStatements());
    }

    private boolean canBeTerminated(TransferProcessStates state) {
        return TransferProcess.Builder.newInstance()
                .id("probe")
                .state(state.code())
                .build()
                .canBeTerminated();
    }

    private void saveTransfer(String id, TransferProcess.Type type, TransferProcessStates state, Duration age) {
        var transfer = TransferProcess.Builder.newInstance()
                .id(id)
                .type(type)
                .state(state.code())
                .stateTimestamp(clock.millis() - age.toMillis())
                .participantContextId(PARTICIPANT_CONTEXT)
                .correlationId("corr-" + id)
                .contractId("contract-" + id)
                .protocol("dataspace-protocol-http")
                .counterPartyAddress("http://counterparty/dsp")
                .build();
        assertThat(transferProcessStore.save(transfer).succeeded()).isTrue();
    }

    private void saveNegotiation(String id, Duration age, boolean withAgreement) {
        var builder = ContractNegotiation.Builder.newInstance()
                .id(id)
                .type(ContractNegotiation.Type.CONSUMER)
                .state(withAgreement ? ContractNegotiationStates.FINALIZED.code() : ContractNegotiationStates.TERMINATED.code())
                .stateTimestamp(clock.millis() - age.toMillis())
                .correlationId("corr-" + id)
                .counterPartyAddress("http://provider/dsp")
                .counterPartyId("provider-1")
                .protocol("protocol")
                .participantContextId(PARTICIPANT_CONTEXT);
        if (withAgreement) {
            builder.contractAgreement(ContractAgreement.Builder.newInstance()
                    .id("agreement-" + id)
                    .agreementId("wire-" + id)
                    .providerId("provider-1")
                    .consumerId("consumer")
                    .assetId("asset-1")
                    .contractSigningDate(clock.instant().getEpochSecond())
                    .participantContextId(PARTICIPANT_CONTEXT)
                    .policy(Policy.Builder.newInstance().build())
                    .build());
        }
        assertThat(negotiationStore.save(builder.build()).succeeded()).isTrue();
    }

    private void insertPolicyEntry(String id, int state, Duration age) throws SQLException {
        var millis = clock.millis() - age.toMillis();
        execute("INSERT INTO edc_policy_monitor (entry_id, state, created_at, updated_at, state_time_stamp) VALUES ('%s', %d, %d, %d, %d)"
                .formatted(id, state, millis, millis, millis));
    }

    private void insertFlow(String flowId, DataFlowLifecycleState state, Duration age) throws SQLException {
        serverConfDatabaseCtx.doInTransaction(session -> dataFlowStateDao.insert(session, flowId, state));
        ageFlow(flowId, age);
    }

    private static void ageFlow(String flowId, Duration age) throws SQLException {
        execute("ALTER TABLE dataflow_state DISABLE TRIGGER set_timestamps");
        try {
            execute("UPDATE dataflow_state SET updated_at = CURRENT_TIMESTAMP - INTERVAL '%d seconds' WHERE flow_id = '%s'"
                    .formatted(age.toSeconds(), flowId));
        } finally {
            execute("ALTER TABLE dataflow_state ENABLE TRIGGER set_timestamps");
        }
    }

    private static void awaitTogether(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> ids(String table, String column) throws SQLException {
        var ids = new ArrayList<String>();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT %s FROM %s".formatted(column, table))) {
            while (resultSet.next()) {
                ids.add(resultSet.getString(1));
            }
        }
        return ids;
    }

    private static long count(String table) throws SQLException {
        return ids(table, "1").size();
    }

    private static void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static ServerConfDbProperties serverConfDbProperties() {
        Map<String, String> hibernateProperties = Map.of(
                "dialect", Postgres10FixedImplicitSequenceDialect.class.getName(),
                "connection.driver_class", "org.postgresql.Driver",
                "connection.url", POSTGRES.getJdbcUrl(),
                "connection.username", POSTGRES.getUsername(),
                "connection.password", POSTGRES.getPassword(),
                "hikari.maximumPoolSize", "5");
        return () -> hibernateProperties;
    }

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

    /**
     * EDC's own termination path over the real transfer store: the terminate command handler moves the transfer to
     * {@code TERMINATING}, and the stock processor ends it, removing the data-address secret.
     */
    private static final class EdcTerminationChain {

        private final DataAddressStore dataAddressStore = mock();
        private final TransferProcessService service = mock();
        private final DataFlowController dataFlowController = mock();
        private final ProtocolRemoteMessageDispatcher messageDispatcher = mock();
        private final TransferProcessorsImpl processors;
        private final TerminateTransferCommandHandler handler =
                new TerminateTransferCommandHandler(transferProcessStore, new TransferProcessObservableImpl());

        EdcTerminationChain() {
            PolicyArchive policyArchive = mock();
            ProtocolWebhookResolver webhookResolver = mock();
            when(dataAddressStore.remove(any())).thenReturn(StoreResult.success());
            when(dataFlowController.terminate(any())).thenReturn(StatusResult.success());
            when(policyArchive.findPolicyForContract(any()))
                    .thenReturn(Policy.Builder.newInstance().assignee("consumer").assigner("provider").build());
            when(policyArchive.getAgreementIdForContract(any())).thenReturn("agreement");
            when(webhookResolver.getWebhook(any(), any())).thenReturn(() -> "http://protocol.webhook/url");
            when(messageDispatcher.dispatch(any(), any(), isA(TransferTerminationMessage.class)))
                    .thenReturn(completedFuture(StatusResult.success("ack")));
            var retryFactory = new EntityRetryProcessFactory(mock(), Clock.systemUTC(),
                    new EntityRetryProcessConfiguration(1, () -> new ExponentialWaitStrategy(0L)));
            processors = new TransferProcessorsImpl(policyArchive, retryFactory, dataFlowController, dataAddressStore,
                    new TransferProcessObservableImpl(), transferProcessStore, mock(), mock(DataAddressResolver.class),
                    webhookResolver, messageDispatcher);
        }

        TransferProcessService service() {
            when(service.terminate(any(TerminateTransferCommand.class))).thenAnswer(invocation -> {
                var result = handler.handle(invocation.getArgument(0));
                return result.succeeded() ? ServiceResult.success() : ServiceResult.conflict(result.getFailureDetail());
            });
            return service;
        }

        void processTerminating(TransferProcess transfer) {
            processors.processTerminating(transfer).join();
        }
    }

    private static final class RecordingMonitor implements Monitor {

        private final List<String> problems = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void severe(Supplier<String> supplier, Throwable... errors) {
            problems.add("severe: " + supplier.get());
        }

        @Override
        public void severe(String message, Throwable... errors) {
            problems.add("severe: " + message);
        }

        @Override
        public void warning(Supplier<String> supplier, Throwable... errors) {
            problems.add("warning: " + supplier.get());
        }

        @Override
        public void warning(String message, Throwable... errors) {
            problems.add("warning: " + message);
        }

        void assertNoProblems() {
            assertThat(problems).isEmpty();
        }
    }
}
