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

import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.contract.negotiation.NegotiationProcessorsImpl;
import org.eclipse.edc.connector.controlplane.contract.observe.ContractNegotiationObservableImpl;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.ContractNegotiationPendingGuard;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.defaults.storage.contractnegotiation.InMemoryContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.defaults.storage.transferprocess.InMemoryTransferProcessStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyArchive;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolRemoteMessageDispatcher;
import org.eclipse.edc.connector.controlplane.transfer.observe.TransferProcessObservableImpl;
import org.eclipse.edc.connector.controlplane.transfer.processors.TransferProcessorsImpl;
import org.eclipse.edc.connector.controlplane.transfer.spi.TransferProcessPendingGuard;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.controlplane.ProtocolRemoteMessage;
import org.eclipse.edc.controlplane.contract.negotiation.tasks.executor.ContractNegotiationTaskExecutorImpl;
import org.eclipse.edc.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.ContractNegotiationTaskPayload;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.SendRequestNegotiation;
import org.eclipse.edc.controlplane.contract.spi.negotiation.tasks.SendVerificationNegotiation;
import org.eclipse.edc.controlplane.tasks.ProcessTaskPayload;
import org.eclipse.edc.controlplane.tasks.Task;
import org.eclipse.edc.controlplane.tasks.TaskObservableImpl;
import org.eclipse.edc.controlplane.tasks.TaskServiceImpl;
import org.eclipse.edc.controlplane.tasks.defaults.InMemoryTaskStore;
import org.eclipse.edc.controlplane.transfer.process.tasks.executor.TransferProcessTaskExecutorImpl;
import org.eclipse.edc.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.controlplane.transfer.spi.tasks.SendTransferRequest;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.spi.ProtocolWebhook;
import org.eclipse.edc.protocol.spi.ProtocolWebhookResolver;
import org.eclipse.edc.query.CriterionOperatorRegistryImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.retry.ExponentialWaitStrategy;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessConfiguration;
import org.eclipse.edc.statemachine.retry.EntityRetryProcessFactory;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the real EDC processors and task executors under {@link TaskPollExecutor} with a dispatcher that
 * always fails, to check that a failed protocol send ends the record within the poller's attempt cap.
 */
class TaskPollExecutorSendFailureTest {
    private static final int CONFIGURED_SEND_RETRY_LIMIT = 1;
    private static final int MAX_SEND_RETRY_LIMIT_UNDER_POLLER_CAP = 3;
    private static final int POLLER_MAX_RETRY = 3;

    private final Clock clock = Clock.systemUTC();
    private final List<Integer> savedNegotiationStates = new CopyOnWriteArrayList<>();
    private final InMemoryContractNegotiationStore negotiationStore =
            new InMemoryContractNegotiationStore(clock, CriterionOperatorRegistryImpl.ofDefaults()) {
                @Override
                public StoreResult<Void> save(ContractNegotiation entity) {
                    savedNegotiationStates.add(entity.getState());
                    return super.save(entity);
                }
            };
    private final InMemoryTransferProcessStore transferStore =
            new InMemoryTransferProcessStore(clock, CriterionOperatorRegistryImpl.ofDefaults());
    private final InMemoryTaskStore taskStore = new InMemoryTaskStore(CriterionOperatorRegistryImpl.ofDefaults());
    private final NoopTransactionContext transactionContext = new NoopTransactionContext();
    private final AtomicInteger dispatchCount = new AtomicInteger();
    private final List<String> dispatchedMessageTypes = new CopyOnWriteArrayList<>();
    private TaskPollExecutor poller;

    @AfterEach
    void tearDown() {
        if (poller != null) {
            poller.stop();
        }
    }

    @Test
    void consumerRequestSendFailureEndsTerminatedAfterTwoSendsWithErrorDetail() {
        var negotiation = requestingNegotiation();
        startNegotiationPoller(CONFIGURED_SEND_RETRY_LIMIT, SendRequestNegotiation.Builder.newInstance(), negotiation);
        await().atMost(Duration.ofSeconds(10)).until(() -> isTerminated(negotiation));
        var stored = negotiationStore.findById(negotiation.getId());
        assertThat(dispatchCount.get()).isEqualTo(2);
        assertThat(stored.getErrorDetail()).contains("Failed to request contract to provider");
        assertThat(taskStoreEmpty()).isTrue();
    }

    @Test
    void consumerVerificationSendFailurePassesThroughTerminatingAndEndsTerminated() {
        var negotiation = verifyingNegotiation();
        startNegotiationPoller(CONFIGURED_SEND_RETRY_LIMIT, SendVerificationNegotiation.Builder.newInstance(), negotiation);
        await().atMost(Duration.ofSeconds(10)).until(() -> isTerminated(negotiation));
        assertThat(savedNegotiationStates).contains(
                ContractNegotiationStates.TERMINATING.code(), ContractNegotiationStates.TERMINATED.code());
        assertThat(savedNegotiationStates.indexOf(ContractNegotiationStates.TERMINATING.code()))
                .isLessThan(savedNegotiationStates.lastIndexOf(ContractNegotiationStates.TERMINATED.code()));
        assertThat(dispatchedMessageTypes).containsExactly(
                "ContractAgreementVerificationMessage", "ContractAgreementVerificationMessage",
                "ContractNegotiationTerminationMessage", "ContractNegotiationTerminationMessage");
        assertThat(negotiationStore.findById(negotiation.getId()).getErrorDetail()).isNotBlank();
    }

    @Test
    void largestRetryLimitUnderThePollerCapStillTerminatesTheNegotiation() {
        var negotiation = requestingNegotiation();
        startNegotiationPoller(MAX_SEND_RETRY_LIMIT_UNDER_POLLER_CAP, SendRequestNegotiation.Builder.newInstance(), negotiation);
        await().atMost(Duration.ofSeconds(10)).until(() -> isTerminated(negotiation));
        assertThat(dispatchCount.get()).isEqualTo(MAX_SEND_RETRY_LIMIT_UNDER_POLLER_CAP + 1);
    }

    @Test
    void transferRequestSendFailureEndsTerminatedAfterTwoSendsWithErrorDetail() {
        var transfer = requestingTransfer();
        startTransferPoller(CONFIGURED_SEND_RETRY_LIMIT, transfer);
        await().atMost(Duration.ofSeconds(10)).until(() -> transferState(transfer) == TransferProcessStates.TERMINATED.code());
        assertThat(dispatchCount.get()).isEqualTo(2);
        assertThat(transferStore.findById(transfer.getId()).getErrorDetail()).isNotBlank();
        assertThat(taskStoreEmpty()).isTrue();
    }

    private <T extends ContractNegotiationTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> void startNegotiationPoller(
            int sendRetryLimit, B payloadBuilder, ContractNegotiation negotiation) {
        negotiationStore.save(negotiation);
        taskStore.create(task(payloadBuilder, negotiation.getId(), negotiation.getState(), negotiation.getType().name()));
        var monitor = mock(Monitor.class);
        var processors = new NegotiationProcessorsImpl(monitor, webhookResolver(), new ContractNegotiationObservableImpl(),
                negotiationStore, mock(ParticipantIdentityResolver.class), clock, alwaysFailingDispatcher(),
                retryConfiguration(sendRetryLimit));
        var guard = mock(ContractNegotiationPendingGuard.class);
        when(guard.test(any())).thenReturn(false);
        var negotiationExecutor = ContractNegotiationTaskExecutorImpl.Builder.newInstance()
                .negotiationProcessors(processors)
                .taskService(new TaskServiceImpl(taskStore, new TaskObservableImpl(), transactionContext))
                .clock(clock)
                .store(negotiationStore)
                .transactionContext(transactionContext)
                .pendingGuard(guard)
                .monitor(monitor)
                .build();
        startPoller(negotiationExecutor, mock(TransferProcessTaskExecutor.class), monitor);
    }

    private void startTransferPoller(int sendRetryLimit, TransferProcess transfer) {
        transferStore.save(transfer);
        taskStore.create(task(SendTransferRequest.Builder.newInstance(), transfer.getId(), transfer.getState(),
                transfer.getType().name()));
        var monitor = mock(Monitor.class);
        var policyArchive = mock(PolicyArchive.class);
        when(policyArchive.findPolicyForContract(anyString())).thenReturn(Policy.Builder.newInstance().build());
        when(policyArchive.getAgreementIdForContract(anyString())).thenReturn("agreement-1");
        var dataAddressStore = mock(DataAddressStore.class);
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.notFound("no data address"));
        when(dataAddressStore.remove(any())).thenReturn(StoreResult.success());
        var processors = new TransferProcessorsImpl(policyArchive,
                new EntityRetryProcessFactory(monitor, clock, retryConfiguration(sendRetryLimit)),
                mock(DataFlowController.class), dataAddressStore, new TransferProcessObservableImpl(), transferStore, monitor,
                mock(DataAddressResolver.class), webhookResolver(), alwaysFailingDispatcher());
        var guard = mock(TransferProcessPendingGuard.class);
        when(guard.test(any())).thenReturn(false);
        var transferExecutor = TransferProcessTaskExecutorImpl.Builder.newInstance()
                .transferProcessors(processors)
                .taskService(new TaskServiceImpl(taskStore, new TaskObservableImpl(), transactionContext))
                .clock(clock)
                .store(transferStore)
                .transactionContext(transactionContext)
                .pendingGuard(guard)
                .monitor(monitor)
                .build();
        startPoller(mock(ContractNegotiationTaskExecutor.class),
                transferExecutor, monitor);
    }

    private void startPoller(ContractNegotiationTaskExecutor negotiationExecutor,
                             TransferProcessTaskExecutor transferExecutor, Monitor monitor) {
        poller = new TaskPollExecutor(new TaskPollConfig(10, POLLER_MAX_RETRY), noopInstrumentation(),
                negotiationExecutor, transferExecutor, taskStore, transactionContext, monitor, clock);
        poller.start();
    }

    private static EntityRetryProcessConfiguration retryConfiguration(int sendRetryLimit) {
        return new EntityRetryProcessConfiguration(sendRetryLimit, () -> new ExponentialWaitStrategy(0));
    }

    private static ProtocolWebhookResolver webhookResolver() {
        var webhook = mock(ProtocolWebhook.class);
        when(webhook.url()).thenReturn("http://consumer/callback");
        var webhookResolver = mock(ProtocolWebhookResolver.class);
        when(webhookResolver.getWebhook(anyString(), anyString())).thenReturn(webhook);
        return webhookResolver;
    }

    private ProtocolRemoteMessageDispatcher alwaysFailingDispatcher() {
        var dispatcher = mock(ProtocolRemoteMessageDispatcher.class);
        when(dispatcher.dispatch(anyString(), any(), any())).thenAnswer(invocation -> {
            dispatchCount.incrementAndGet();
            ProtocolRemoteMessage message = invocation.getArgument(2);
            dispatchedMessageTypes.add(message.getClass().getSimpleName());
            return CompletableFuture.completedFuture(
                    StatusResult.failure(ResponseStatus.ERROR_RETRY, "counter-party unavailable"));
        });
        return dispatcher;
    }

    private static ExecutorInstrumentation noopInstrumentation() {
        var instrumentation = mock(ExecutorInstrumentation.class);
        when(instrumentation.instrument(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return instrumentation;
    }

    private <T extends ProcessTaskPayload, B extends ProcessTaskPayload.Builder<T, B>> Task task(
            B payloadBuilder, String processId, int processState, String processType) {
        var payload = payloadBuilder.processId(processId).processState(processState).processType(processType).build();
        return Task.Builder.newInstance().at(clock.millis()).payload(payload).build();
    }

    private boolean taskStoreEmpty() {
        return taskStore.fetchForUpdate(QuerySpec.max()).isEmpty();
    }

    private boolean isTerminated(ContractNegotiation negotiation) {
        return negotiationStore.findById(negotiation.getId()).getState() == ContractNegotiationStates.TERMINATED.code();
    }

    private int transferState(TransferProcess transfer) {
        return transferStore.findById(transfer.getId()).getState();
    }

    private static ContractOffer offer() {
        return ContractOffer.Builder.newInstance().id("offer-1").assetId("asset-1")
                .policy(Policy.Builder.newInstance().build()).build();
    }

    private static ContractNegotiation requestingNegotiation() {
        return baseNegotiation().state(ContractNegotiationStates.REQUESTING.code()).stateCount(1).build();
    }

    private static ContractNegotiation verifyingNegotiation() {
        var agreement = ContractAgreement.Builder.newInstance().id("agreement-1").providerId("provider")
                .consumerId("consumer").assetId("asset-1").policy(Policy.Builder.newInstance().build()).build();
        return baseNegotiation().correlationId("provider-pid").contractAgreement(agreement)
                .state(ContractNegotiationStates.VERIFYING.code()).stateCount(1).build();
    }

    private static ContractNegotiation.Builder baseNegotiation() {
        return ContractNegotiation.Builder.newInstance()
                .counterPartyId("provider")
                .counterPartyAddress("http://provider/dsp")
                .protocol("dataspace-protocol-http")
                .participantContextId("consumer-context")
                .type(ContractNegotiation.Type.CONSUMER)
                .contractOffer(offer());
    }

    private static TransferProcess requestingTransfer() {
        return TransferProcess.Builder.newInstance()
                .id("transfer-1")
                .type(TransferProcess.Type.CONSUMER)
                .state(TransferProcessStates.REQUESTING.code())
                .stateCount(1)
                .correlationId("provider-pid")
                .counterPartyAddress("http://provider/dsp")
                .contractId("contract-1")
                .assetId("asset-1")
                .participantContextId("consumer-context")
                .protocol("dataspace-protocol-http")
                .transferType("Xrd-PULL")
                .build();
    }
}
