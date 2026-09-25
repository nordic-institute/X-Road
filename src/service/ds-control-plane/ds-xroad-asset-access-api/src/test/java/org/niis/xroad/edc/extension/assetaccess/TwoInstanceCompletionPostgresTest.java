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

package org.niis.xroad.edc.extension.assetaccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.SqlTransferProcessStore;
import org.eclipse.edc.connector.controlplane.store.sql.transferprocess.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.connector.controlplane.transfer.dataaddress.VaultDataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataPlaneProtocolInUse;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyRegistrationTypes;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.BaseSqlLeaseStatements;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilderImpl;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.eclipse.edc.transaction.local.LocalDataSourceRegistry;
import org.eclipse.edc.transaction.local.LocalTransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.extension.store.contractnegotiation.XRoadContractNegotiationStore;
import org.niis.xroad.edc.extension.store.contractnegotiation.schema.postgres.XRoadPostgresContractNegotiationStatements;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that two independently constructed ds-control-plane instances, sharing only the database and one
 * {@link Vault} standing in for the shared OpenBao, complete an asset access acquisition no matter which
 * instance drives the negotiation or transfer to its terminal state.
 *
 * <p>Instance A and instance B are each an independently constructed {@link AssetAccessCompletionPoller}
 * with its own clock, an {@link XRoadContractNegotiationStore} over its own {@link SqlContractNegotiationStore},
 * its own {@link SqlTransferProcessStore}, its own {@link VaultDataAddressStore} and its own
 * {@link ReusableAgreementLookup}. The two instances never share a Java object other than the one {@link Vault};
 * every other collaborator is a fresh instance per side, so the only channel between them is the PostgreSQL
 * container from {@link PostgresqlStoreSetupExtension}. Completion is driven by calling the waiting instance's
 * {@link AssetAccessCompletionPoller#poll()} once after the other instance's write, since every write here
 * commits before the call that issued it returns.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class TwoInstanceCompletionPostgresTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(30);

    private final LeaseStatements leaseStatements = new BaseSqlLeaseStatements();
    private final XRoadPostgresContractNegotiationStatements negotiationStatements =
            new XRoadPostgresContractNegotiationStatements(leaseStatements, Clock.systemUTC());
    private final PostgresDialectStatements transferStatements = new PostgresDialectStatements(leaseStatements, Clock.systemUTC());
    private final List<AssetAccessCompletionPoller> pollers = new ArrayList<>();

    private PostgresqlStoreSetupExtension extension;
    private QueryExecutor queryExecutor;
    private Vault sharedVault;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension postgresExtension, QueryExecutor sqlQueryExecutor) throws IOException {
        this.extension = postgresExtension;
        this.queryExecutor = sqlQueryExecutor;
        this.sharedVault = new SharedVault();

        extension.runQuery(TestUtils.getResourceFileContentAsString("contract-negotiation-schema.sql"));
        extension.runQuery(TestUtils.getResourceFileContentAsString("transfer-process-schema.sql"));
    }

    @AfterEach
    void tearDown() {
        pollers.forEach(AssetAccessCompletionPoller::stop);
        extension.runQuery("DROP TABLE " + transferStatements.getTransferProcessTableName() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + negotiationStatements.getContractAgreementTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + leaseStatements.getLeaseTableName() + " CASCADE");
    }

    @Test
    void negotiationFinalizedByOtherInstanceCompletesWaiterWithAgreement() throws Exception {
        var a = buildInstance(Clock.systemUTC());
        var b = buildInstance(Clock.systemUTC());

        var participantContextId = "participant-" + UUID.randomUUID();
        var negotiationId = "neg-" + UUID.randomUUID();

        assertThat(a.negotiationStore().save(negotiation(negotiationId, participantContextId,
                ContractNegotiationStates.REQUESTED, null, null)).succeeded()).isTrue();

        var future = a.poller().awaitNegotiation(negotiationId, LONG_TIMEOUT);

        var agreement = agreement("agreement-" + negotiationId, participantContextId, "asset-1", "provider-1");
        assertThat(b.negotiationStore().save(negotiation(negotiationId, participantContextId,
                ContractNegotiationStates.FINALIZED, agreement, null)).succeeded()).isTrue();

        a.poller().poll();

        var result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.getAgreementId()).isEqualTo(agreement.getAgreementId());
        assertThat(result.getAssetId()).isEqualTo(agreement.getAssetId());
        assertThat(result.getProviderId()).isEqualTo(agreement.getProviderId());
    }

    @Test
    void transferStartedByOtherInstanceCompletesWaiterWithResolvedDataAddress() throws Exception {
        var a = buildInstance(Clock.systemUTC());
        var b = buildInstance(Clock.systemUTC());

        var participantContextId = "participant-" + UUID.randomUUID();
        var transferProcessId = "tp-" + UUID.randomUUID();

        assertThat(a.transferProcessStore().save(transferProcess(transferProcessId, participantContextId,
                TransferProcessStates.REQUESTED, null)).succeeded()).isTrue();

        var future = a.poller().awaitTransfer(transferProcessId, LONG_TIMEOUT);

        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("baseUrl", "https://provider.example/data")
                .build();
        var startedTransferProcess = transferProcess(transferProcessId, participantContextId, TransferProcessStates.STARTED, null);
        assertThat(b.dataAddressStore().store(address, startedTransferProcess).succeeded()).isTrue();
        assertThat(b.transferProcessStore().save(startedTransferProcess).succeeded()).isTrue();

        a.poller().poll();

        var resolved = future.get(10, TimeUnit.SECONDS);
        assertThat(resolved.getType()).isEqualTo("HttpData");
        assertThat(resolved.getStringProperty("baseUrl")).isEqualTo("https://provider.example/data");
    }

    @Test
    void negotiationTerminatedByOtherInstanceFailsWaiterWithStoredErrorDetail() throws IOException {
        var a = buildInstance(Clock.systemUTC());
        var b = buildInstance(Clock.systemUTC());

        var participantContextId = "participant-" + UUID.randomUUID();
        var negotiationId = "neg-" + UUID.randomUUID();

        assertThat(a.negotiationStore().save(negotiation(negotiationId, participantContextId,
                ContractNegotiationStates.REQUESTED, null, null)).succeeded()).isTrue();

        var future = a.poller().awaitNegotiation(negotiationId, LONG_TIMEOUT);

        assertThat(b.negotiationStore().save(negotiation(negotiationId, participantContextId,
                ContractNegotiationStates.TERMINATED, null, "provider refused the offer")).succeeded()).isTrue();

        a.poller().poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getDetails())
                        .isEqualTo("provider refused the offer"));
    }

    @Test
    void transferTerminatedByOtherInstanceFailsWaiterWithStoredErrorDetail() throws IOException {
        var a = buildInstance(Clock.systemUTC());
        var b = buildInstance(Clock.systemUTC());

        var participantContextId = "participant-" + UUID.randomUUID();
        var transferProcessId = "tp-" + UUID.randomUUID();

        assertThat(a.transferProcessStore().save(transferProcess(transferProcessId, participantContextId,
                TransferProcessStates.REQUESTED, null)).succeeded()).isTrue();

        var future = a.poller().awaitTransfer(transferProcessId, LONG_TIMEOUT);

        assertThat(b.transferProcessStore().save(transferProcess(transferProcessId, participantContextId,
                TransferProcessStates.TERMINATED, "provider terminated the transfer")).succeeded()).isTrue();

        a.poller().poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getDetails())
                        .isEqualTo("provider terminated the transfer"));
    }

    @Test
    void negotiationWaiterFailsAtDeadlineWhenNothingIsWritten() throws IOException {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var a = buildInstance(clock);

        var future = a.poller().awaitNegotiation("neg-" + UUID.randomUUID(), Duration.ofMillis(50));
        clock.advanceTo(clock.instant().plusMillis(51));

        a.poller().poll();

        assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void transferWaiterFailsAtDeadlineWhenNothingIsWritten() throws IOException {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var a = buildInstance(clock);

        var future = a.poller().awaitTransfer("tp-" + UUID.randomUUID(), Duration.ofMillis(50));
        clock.advanceTo(clock.instant().plusMillis(51));

        a.poller().poll();

        assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void reusableAgreementLookupFindsAgreementNegotiatedThroughOtherInstance() throws IOException {
        var a = buildInstance(Clock.systemUTC());
        var b = buildInstance(Clock.systemUTC());

        var participantContextId = "participant-" + UUID.randomUUID();
        var negotiationId = "neg-" + UUID.randomUUID();
        var agreement = agreement("agreement-" + negotiationId, participantContextId, "asset-1", "provider-1");

        assertThat(b.negotiationStore().save(negotiation(negotiationId, participantContextId,
                ContractNegotiationStates.FINALIZED, agreement, null)).succeeded()).isTrue();

        var found = a.lookup().find(participantContextId, "consumer", "asset-1", "provider-1");

        assertThat(found).isPresent();
        assertThat(found.get().getAgreementId()).isEqualTo(agreement.getAgreementId());
    }

    @Test
    void completionWriteThroughAStoreSurvivesAnotherWaitersReadFailureUnderRealTransactionContext() throws Exception {
        var realMonitor = new ConsoleMonitor();
        var realTransactionContext = new LocalTransactionContext(realMonitor);
        var realDataSourceRegistry = new LocalDataSourceRegistry(realTransactionContext);
        realDataSourceRegistry.register(extension.getDatasourceName(),
                extension.getDataSourceRegistry().resolve(extension.getDatasourceName()));

        var manager = new JacksonTypeManager();
        manager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));

        var negotiationLeaseContext = SqlLeaseContextBuilderImpl.with(realTransactionContext, "test-connector",
                negotiationStatements.getContractNegotiationTable(), leaseStatements, Clock.systemUTC(), queryExecutor);
        var realNegotiationStore = new SqlContractNegotiationStore(realDataSourceRegistry, extension.getDatasourceName(),
                realTransactionContext, manager.getMapper(), negotiationStatements, negotiationLeaseContext, queryExecutor);

        var transferLeaseContext = SqlLeaseContextBuilderImpl.with(realTransactionContext, "test-connector",
                transferStatements.getTransferProcessTableName(), leaseStatements, Clock.systemUTC(), queryExecutor);
        var realTransferProcessStore = new SqlTransferProcessStore(realDataSourceRegistry, extension.getDatasourceName(),
                realTransactionContext, manager.getMapper(), transferStatements, transferLeaseContext, queryExecutor);

        var participantContextId = "participant-" + UUID.randomUUID();
        var goodTransferProcessId = "tp-good-" + UUID.randomUUID();
        var badTransferProcessId = "tp-bad-" + UUID.randomUUID();
        var writtenTransferProcessId = "tp-written-" + UUID.randomUUID();

        var dataAddressStore = new VaultDataAddressStore(sharedVault, null, null, new DataPlaneProtocolInUse(), ObjectMapper::new);
        var address = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("baseUrl", "https://provider.example/data")
                .build();
        var startedTransferProcess = transferProcess(goodTransferProcessId, participantContextId,
                TransferProcessStates.STARTED, null);
        assertThat(dataAddressStore.store(address, startedTransferProcess).succeeded()).isTrue();
        assertThat(realTransferProcessStore.save(startedTransferProcess).succeeded()).isTrue();

        var decoratedTransferProcessStore = new ThrowOnceTransferProcessStore(realTransferProcessStore, badTransferProcessId);

        var poller = new AssetAccessCompletionPoller(realNegotiationStore, decoratedTransferProcessStore, dataAddressStore,
                realTransactionContext, ExecutorInstrumentation.noop(), Clock.systemUTC(), realMonitor, Duration.ofMillis(100));
        pollers.add(poller);

        var goodFuture = poller.awaitTransfer(goodTransferProcessId, LONG_TIMEOUT);
        var badFuture = poller.awaitTransfer(badTransferProcessId, LONG_TIMEOUT);

        var writeCompleted = new CompletableFuture<Void>();
        goodFuture.whenComplete((resolvedAddress, throwable) -> {
            if (throwable == null) {
                var written = transferProcess(writtenTransferProcessId, participantContextId, TransferProcessStates.STARTED, null);
                decoratedTransferProcessStore.save(written);
            }
            writeCompleted.complete(null);
        });

        poller.poll();
        writeCompleted.get(10, TimeUnit.SECONDS);

        assertThat(goodFuture.get(2, TimeUnit.SECONDS)).isNotNull();
        assertThat(badFuture).isNotDone();

        var b = buildInstance(Clock.systemUTC());
        var readBack = b.transferProcessStore().findById(writtenTransferProcessId);
        assertThat(readBack).isNotNull();
        assertThat(TransferProcessStates.from(readBack.getState())).isEqualTo(TransferProcessStates.STARTED);
    }

    private Instance buildInstance(Clock clock) throws IOException {
        var manager = new JacksonTypeManager();
        manager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));

        var negotiationLeaseContext = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), "test-connector",
                negotiationStatements.getContractNegotiationTable(), leaseStatements, Clock.systemUTC(), queryExecutor);
        var stockNegotiationStore = new SqlContractNegotiationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), negotiationStatements, negotiationLeaseContext, queryExecutor);
        var negotiationStore = new XRoadContractNegotiationStore(stockNegotiationStore, extension.getDataSourceRegistry(),
                extension.getDatasourceName(), extension.getTransactionContext(), manager.getMapper(), negotiationStatements,
                negotiationLeaseContext, queryExecutor);

        var transferLeaseContext = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), "test-connector",
                transferStatements.getTransferProcessTableName(), leaseStatements, Clock.systemUTC(), queryExecutor);
        var transferProcessStore = new SqlTransferProcessStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), transferStatements, transferLeaseContext, queryExecutor);

        var dataAddressStore = new VaultDataAddressStore(sharedVault, null, null, new DataPlaneProtocolInUse(), ObjectMapper::new);

        var lookup = new ReusableAgreementLookup(negotiationStore, extension.getTransactionContext());

        var poller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                extension.getTransactionContext(), ExecutorInstrumentation.noop(), clock, new ConsoleMonitor(), Duration.ofMillis(100));
        pollers.add(poller);

        return new Instance(negotiationStore, transferProcessStore, dataAddressStore, lookup, poller);
    }

    private ContractNegotiation negotiation(String id, String participantContextId, ContractNegotiationStates state,
                                             ContractAgreement agreement, String errorDetail) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .type(ContractNegotiation.Type.CONSUMER)
                .state(state.code())
                .correlationId("corr-" + id)
                .counterPartyAddress("http://provider/dsp")
                .counterPartyId("provider-1")
                .protocol("protocol")
                .participantContextId(participantContextId)
                .contractAgreement(agreement)
                .errorDetail(errorDetail)
                .build();
    }

    private ContractAgreement agreement(String agreementId, String participantContextId, String assetId, String providerId) {
        return ContractAgreement.Builder.newInstance()
                .id("agr-" + agreementId)
                .agreementId(agreementId)
                .providerId(providerId)
                .consumerId("consumer")
                .assetId(assetId)
                .contractSigningDate(Instant.now().getEpochSecond())
                .participantContextId(participantContextId)
                .policy(policy())
                .build();
    }

    private TransferProcess transferProcess(String id, String participantContextId, TransferProcessStates state, String errorDetail) {
        return TransferProcess.Builder.newInstance()
                .id(id)
                .type(TransferProcess.Type.CONSUMER)
                .state(state.code())
                .participantContextId(participantContextId)
                .errorDetail(errorDetail)
                .build();
    }

    private Policy policy() {
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance()
                        .action(Action.Builder.newInstance().type("use").build())
                        .build())
                .build();
    }

    private record Instance(ContractNegotiationStore negotiationStore, TransferProcessStore transferProcessStore,
                             DataAddressStore dataAddressStore, ReusableAgreementLookup lookup,
                             AssetAccessCompletionPoller poller) {
    }

    /**
     * Minimal map-backed {@link Vault} shared by instances A and B, standing in for the shared OpenBao that
     * production instances use. Partitioning is irrelevant here: every alias {@link VaultDataAddressStore}
     * generates already includes the transfer process id, so a flat key space is enough to prove that a
     * secret written by one instance is readable by the other.
     */
    private static final class SharedVault implements Vault {
        private final Map<String, String> secrets = new ConcurrentHashMap<>();

        @Override
        public String resolveSecret(String key) {
            return secrets.get(key);
        }

        @Override
        public Result<Void> storeSecret(String key, String value) {
            secrets.put(key, value);
            return Result.success();
        }

        @Override
        public Result<Void> deleteSecret(String key) {
            secrets.remove(key);
            return Result.success();
        }
    }

    /**
     * Decorates a real {@link TransferProcessStore}, throwing once when {@link #findById} is called for a
     * chosen id, to simulate a connection blip on one waiter's read within a poll pass. Every other call,
     * including subsequent reads of the same id, is delegated unchanged.
     */
    private static final class ThrowOnceTransferProcessStore implements TransferProcessStore {
        private final TransferProcessStore delegate;
        private final String throwingId;
        private final AtomicBoolean thrown = new AtomicBoolean(false);

        private ThrowOnceTransferProcessStore(TransferProcessStore delegate, String throwingId) {
            this.delegate = delegate;
            this.throwingId = throwingId;
        }

        @Override
        public TransferProcess findById(String id) {
            if (throwingId.equals(id) && thrown.compareAndSet(false, true)) {
                throw new RuntimeException("simulated connection blip");
            }
            return delegate.findById(id);
        }

        @Override
        public TransferProcess findForCorrelationId(String correlationId) {
            return delegate.findForCorrelationId(correlationId);
        }

        @Override
        public StoreResult<Void> delete(String processId) {
            return delegate.delete(processId);
        }

        @Override
        public Stream<TransferProcess> findAll(QuerySpec querySpec) {
            return delegate.findAll(querySpec);
        }

        @Override
        public List<TransferProcess> nextNotLeased(int max, Criterion... criteria) {
            return delegate.nextNotLeased(max, criteria);
        }

        @Override
        public StoreResult<TransferProcess> findByIdAndLease(String id) {
            return delegate.findByIdAndLease(id);
        }

        @Override
        public StoreResult<Void> save(TransferProcess entity) {
            return delegate.save(entity);
        }

        @Override
        public StoreResult<Void> breakLease(TransferProcess entity) {
            return delegate.breakLease(entity);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceTo(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
