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

package org.niis.xroad.edc.extension.assetaccess.poller;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.controlplane.DataAddress;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureKind;
import org.niis.xroad.edc.extension.assetaccess.failure.StageOutcome;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessCompletionPollerTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ASYNC_AWAIT = Duration.ofSeconds(2);
    private static final String DEFAULT_TERMINATION_DETAIL = "provider terminated";
    private static final int BATCH_SIZE = 500;

    @Mock
    ContractNegotiationStore negotiationStore;
    @Mock
    TransferProcessStore transferProcessStore;
    @Mock
    DataAddressStore dataAddressStore;
    @Mock
    Monitor monitor;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final Map<String, DataAddress> resolvableAddresses = new HashMap<>();

    private AssetAccessCompletionPoller poller;

    @BeforeEach
    void setUp() {
        lenient().when(negotiationStore.queryNegotiations(any())).thenAnswer(invocation -> Stream.empty());
        lenient().when(transferProcessStore.findAll(any())).thenAnswer(invocation -> Stream.empty());
        lenient().when(dataAddressStore.resolve(any())).thenAnswer(invocation -> {
            TransferProcess transferProcess = invocation.getArgument(0);
            var address = transferProcess == null ? null : resolvableAddresses.get(transferProcess.getId());
            return address != null ? StoreResult.success(address) : StoreResult.notFound("no data address stored");
        });
        poller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore, new NoopTransactionContext(),
                ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
    }

    @AfterEach
    void tearDown() {
        poller.stop();
    }

    @Test
    void awaitNegotiationCompletesWithAgreementWhenFinalized() throws Exception {
        var agreement = buildAgreement();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null)));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(succeededValue(future)).isEqualTo(agreement);
    }

    @Test
    void awaitNegotiationFailsWithStoredErrorDetailWhenTerminated() {
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.TERMINATED, null, "provider refused")));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_negotiation_failed");
            assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
            assertThat(cause.getErrorCodeMetadata()).contains("neg-1");
            assertThat(cause.getDetails()).isEqualTo("provider refused");
        });
    }

    @Test
    void awaitNegotiationFailsWithStoredErrorDetailWhenTerminating() {
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.TERMINATING, null, "send to provider failed")));
        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();
        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_negotiation_failed");
            assertThat(cause.getDetails()).isEqualTo("send to provider failed");
        });
    }

    @Test
    void awaitNegotiationKeepsPendingForNonTerminalState() {
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.REQUESTED, null, null)));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future).isNotDone();
    }

    @Test
    void awaitNegotiationFailsWithDefaultDetailWhenTerminatedWithoutErrorDetail() {
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.TERMINATED, null, null)));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class,
                cause -> assertThat(cause.getDetails()).isEqualTo(DEFAULT_TERMINATION_DETAIL));
    }

    @Test
    void awaitNegotiationFailsAsProviderRejectionWhenTerminatedWithAReceivedMessageAndNoErrorDetail() {
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation("neg-1", ContractNegotiationStates.TERMINATED, null, null, receivedMessage())));
        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();
        assertFailedWith(future, FailureKind.PROVIDER_REJECTION, XrdRuntimeException.class);
    }

    @Test
    void awaitNegotiationFailsAsTransportFaultWhenTheStoredSendFailureHadNoHttpStatus() {
        var errorDetail = "Failed to request contract to provider: " + EdcHttpClientException.class.getName() + ": timeout";
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.TERMINATED, null, errorDetail)));
        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();
        assertFailedWith(future, FailureKind.TRANSPORT_FAULT, XrdRuntimeException.class);
    }

    @Test
    void awaitNegotiationFailsWithTimeoutWhenDeadlinePasses() {
        var future = poller.awaitNegotiation("neg-1", Duration.ofMillis(10));
        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertFailedWith(future, FailureKind.TRANSPORT_FAULT, TimeoutException.class);
    }

    @Test
    void awaitTransferCompletesWithDataAddressWhenStarted() throws Exception {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null)));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(succeededValue(future)).isEqualTo(dataAddress);
    }

    @Test
    void awaitTransferFailsWithStoredErrorDetailWhenTerminated() {
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.TERMINATED, null, "provider terminated the transfer")));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
            assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
            assertThat(cause.getErrorCodeMetadata()).contains("tp-1");
            assertThat(cause.getDetails()).isEqualTo("provider terminated the transfer");
        });
    }

    @Test
    void awaitTransferFailsAsProviderRejectionWhenTerminatedWithAReceivedMessage() {
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.TERMINATED, null, "provider free text", receivedMessage())));
        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();
        assertFailedWith(future, FailureKind.PROVIDER_REJECTION, XrdRuntimeException.class);
    }

    @Test
    void awaitTransferFailsAsTransportFaultWhenTheStoredSendFailureHadNoHttpStatus() {
        var errorDetail = EdcHttpClientException.class.getName() + ": Failed to connect to provider.test/192.0.2.1:80";
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.TERMINATED, null, errorDetail)));
        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();
        assertFailedWith(future, FailureKind.TRANSPORT_FAULT, XrdRuntimeException.class);
    }

    @Test
    void awaitTransferFailsWithStoredErrorDetailWhenTerminating() {
        assertTransferFailsWithStoredErrorDetail(TransferProcessStates.TERMINATING);
    }

    @Test
    void awaitTransferFailsWithStoredErrorDetailWhenTerminatingRequested() {
        assertTransferFailsWithStoredErrorDetail(TransferProcessStates.TERMINATING_REQUESTED);
    }

    private void assertTransferFailsWithStoredErrorDetail(TransferProcessStates state) {
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(state, null, "send to provider failed")));
        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();
        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
            assertThat(cause.getDetails()).isEqualTo("send to provider failed");
        });
    }

    @Test
    void awaitTransferFailsWithDefaultDetailWhenTerminatedWithoutErrorDetail() {
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.TERMINATED, null, null)));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class,
                cause -> assertThat(cause.getDetails()).isEqualTo(DEFAULT_TERMINATION_DETAIL));
    }

    @Test
    void awaitTransferFailsWhenResolveFails() {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null)));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.notFound("no data address stored"));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_dataaddress_invalid");
            assertThat(cause.getDetails()).isEqualTo("no data address stored");
        });
    }

    @Test
    void awaitTransferDoesNotCompleteWhenCompleted() {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.COMPLETED, dataAddress, null)));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future).isNotDone();
    }

    @Test
    void awaitTransferFailsWithTimeoutWhenDeadlinePasses() {
        var future = poller.awaitTransfer("tp-1", Duration.ofMillis(10));
        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertFailedWith(future, FailureKind.TRANSPORT_FAULT, TimeoutException.class);
    }

    @Test
    void stopFailsPendingWaitersWithAcquisitionFailureBeforeReturning() {
        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        var transferFuture = poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.stop();

        assertThat(negotiationFuture).isDone();
        assertThat(transferFuture).isDone();
        var failed = failedOutcome(negotiationFuture);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed");
            assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
        });
        assertFailedWith(transferFuture, FailureKind.OTHER, XrdRuntimeException.class);
    }

    @Test
    void pollDoesNothingAfterStop() {
        poller.stop();

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        verify(negotiationStore, never()).queryNegotiations(any());
        assertThat(future).isDone();
        assertFailedWith(future, FailureKind.OTHER, XrdRuntimeException.class);
    }

    @Test
    void registrationAfterStopReturnsAlreadyFailedFutureWithAcquisitionStoppedFailure() {
        poller.stop();

        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        var transferFuture = poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        assertThat(negotiationFuture).isDone();
        assertThat(transferFuture).isDone();
        var failed = failedOutcome(negotiationFuture);
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
            assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed");
            assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
        });
        assertFailedWith(transferFuture, FailureKind.OTHER, XrdRuntimeException.class);

        verifyNoInteractions(negotiationStore, transferProcessStore);
    }

    @Test
    void negotiationReadFailureDoesNotPreventTransferWaiterFromCompletingInSamePass() throws Exception {
        when(negotiationStore.queryNegotiations(any())).thenThrow(new RuntimeException("store unavailable"));

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null)));

        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        var transferFuture = poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.poll();

        assertThat(succeededValue(transferFuture)).isEqualTo(dataAddress);
        assertThat(negotiationFuture).isNotDone();
    }

    @Test
    void completionRunsOffThePollThreadWithNoActiveTransaction() throws Exception {
        var recordingContext = new RecordingTransactionContext();
        var localPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                recordingContext, ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
        try {
            var agreement = buildAgreement();
            when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                    Stream.of(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null)));

            var pollThread = Thread.currentThread();
            var future = localPoller.awaitNegotiation("neg-1", LONG_TIMEOUT);
            var observedDepth = new CompletableFuture<Integer>();
            var observedThread = new CompletableFuture<Thread>();
            future.whenComplete((result, throwable) -> {
                observedDepth.complete(recordingContext.currentDepth());
                observedThread.complete(Thread.currentThread());
            });

            localPoller.poll();

            assertThat(observedDepth.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isZero();
            assertThat(observedThread.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isNotEqualTo(pollThread);
            assertThat(recordingContext.outcomes()).containsExactly(RecordingTransactionContext.Outcome.COMMITTED);
        } finally {
            localPoller.stop();
        }
    }

    @Test
    void oneWaiterThrowingDuringReadDoesNotAbortPassOrMarkTransactionRollbackOnly() throws Exception {
        var recordingContext = new RecordingTransactionContext();
        var localPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                recordingContext, ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
        try {
            when(negotiationStore.queryNegotiations(any())).thenThrow(new RuntimeException("connection reset"));

            var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
            when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                    Stream.of(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null)));

            var negotiationFuture = localPoller.awaitNegotiation("neg-a", LONG_TIMEOUT);
            var transferFuture = localPoller.awaitTransfer("tp-1", LONG_TIMEOUT);

            localPoller.poll();

            assertThat(succeededValue(transferFuture)).isEqualTo(dataAddress);
            assertThat(negotiationFuture).isNotDone();
            assertThat(recordingContext.outcomes()).containsExactly(RecordingTransactionContext.Outcome.COMMITTED);
        } finally {
            localPoller.stop();
        }
    }

    @Test
    void resolveRunsOutsideTransactionAndThrowingResolveFailsWaiterWithExceptionMessage() throws Exception {
        var recordingContext = new RecordingTransactionContext();
        var localPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                recordingContext, ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
        try {
            var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
            when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                    Stream.of(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null)));
            var resolveDepth = new CompletableFuture<Integer>();
            when(dataAddressStore.resolve(any())).thenAnswer(invocation -> {
                resolveDepth.complete(recordingContext.currentDepth());
                throw new RuntimeException("vault unreachable");
            });

            var future = localPoller.awaitTransfer("tp-1", LONG_TIMEOUT);

            localPoller.poll();

            assertThat(resolveDepth.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isZero();
            var failed = failedOutcome(future);
            assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
            assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, cause -> {
                assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_dataaddress_invalid");
                assertThat(cause.getDetails()).isEqualTo("vault unreachable");
            });
        } finally {
            localPoller.stop();
        }
    }

    @Test
    void bothStoresThrowingOnEveryReadStillFailWaitersAtDeadlineAndStopReadingAfterward() throws Exception {
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenThrow(new RuntimeException("store unavailable"));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenThrow(new RuntimeException("store unavailable"));

        var negotiationFuture = poller.awaitNegotiation("neg-1", Duration.ofMillis(10));
        var transferFuture = poller.awaitTransfer("tp-1", Duration.ofMillis(10));

        poller.poll();
        poller.poll();
        assertThat(negotiationFuture).isNotDone();
        assertThat(transferFuture).isNotDone();

        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertFailedWith(negotiationFuture, FailureKind.TRANSPORT_FAULT, TimeoutException.class);
        assertFailedWith(transferFuture, FailureKind.TRANSPORT_FAULT, TimeoutException.class);
        verify(negotiationStore, times(3)).queryNegotiations(specWithId("neg-1"));
        verify(transferProcessStore, times(3)).findAll(specWithId("tp-1"));

        poller.poll();
        verifyNoMoreInteractions(negotiationStore, transferProcessStore);
    }

    @Test
    void executeThrowingStillFailsWaitersPastDeadlineAndLeavesOthersPendingWithoutTouchingStores() {
        var localPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                new ThrowingTransactionContext(), ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
        try {
            var expiredFuture = localPoller.awaitNegotiation("neg-expired", Duration.ofMillis(10));
            var pendingFuture = localPoller.awaitTransfer("tp-pending", LONG_TIMEOUT);

            clock.advanceTo(clock.instant().plusMillis(20));
            localPoller.poll();

            assertFailedWith(expiredFuture, FailureKind.TRANSPORT_FAULT, TimeoutException.class);
            assertThat(pendingFuture).isNotDone();
            verifyNoInteractions(negotiationStore, transferProcessStore);
        } finally {
            localPoller.stop();
        }
    }

    @Test
    void completionRegisteringTransferWaiterDuringStopDrainIsFailedByTheTimeStopReturns() throws Exception {
        var agreement = buildAgreement();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null)));

        var registrationAttempted = new CountDownLatch(1);
        var releaseRegistration = new CountDownLatch(1);
        var transferFutureRef = new AtomicReference<CompletableFuture<StageOutcome<DataAddress>>>();

        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        negotiationFuture.whenComplete((result, throwable) -> {
            registrationAttempted.countDown();
            try {
                releaseRegistration.await(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            transferFutureRef.set(poller.awaitTransfer("tp-1", LONG_TIMEOUT));
        });

        poller.poll();
        assertThat(registrationAttempted.await(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        var stopThread = new Thread(poller::stop, "stop-under-test");
        stopThread.start();
        releaseRegistration.countDown();
        stopThread.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(stopThread.isAlive()).isFalse();
        assertThat(transferFutureRef.get()).isDone();
        var failed = failedOutcome(transferFutureRef.get());
        assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
        assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class,
                cause -> assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed"));
    }

    @Test
    void pollWithNoWaitersMakesNoStoreCalls() {
        poller.poll();

        verifyNoInteractions(negotiationStore, transferProcessStore);
    }

    @Test
    void pollWithMoreThanBatchSizeNegotiationWaitersQueriesInChunksOfBatchSize() throws Exception {
        var waiterCount = BATCH_SIZE + 1;
        var futures = new HashMap<String, CompletableFuture<StageOutcome<ContractAgreement>>>();
        var agreements = new HashMap<String, ContractAgreement>();
        for (int i = 0; i < waiterCount; i++) {
            var id = "neg-" + i;
            futures.put(id, poller.awaitNegotiation(id, LONG_TIMEOUT));
            agreements.put(id, buildAgreement());
        }

        when(negotiationStore.queryNegotiations(any())).thenAnswer(invocation -> {
            QuerySpec spec = invocation.getArgument(0);
            return idsOf(spec).stream()
                    .map(Object::toString)
                    .map(id -> buildNegotiation(id, ContractNegotiationStates.FINALIZED, agreements.get(id), null));
        });

        poller.poll();

        var specCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(negotiationStore, times(2)).queryNegotiations(specCaptor.capture());
        var chunkSizes = specCaptor.getAllValues().stream().map(spec -> idsOf(spec).size()).sorted().toList();
        assertThat(chunkSizes).containsExactly(1, BATCH_SIZE);

        for (var entry : futures.entrySet()) {
            assertThat(succeededValue(entry.getValue())).isSameAs(agreements.get(entry.getKey()));
        }
    }

    @Test
    void pollSetsQuerySpecLimitToChunkSize() {
        poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.awaitNegotiation("neg-2", LONG_TIMEOUT);
        poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.poll();

        var negotiationSpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(negotiationStore).queryNegotiations(negotiationSpecCaptor.capture());
        assertThat(negotiationSpecCaptor.getValue().getLimit()).isEqualTo(2);
        assertThat(idsOf(negotiationSpecCaptor.getValue()).stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("neg-1", "neg-2");

        var transferSpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(transferProcessStore).findAll(transferSpecCaptor.capture());
        assertThat(transferSpecCaptor.getValue().getLimit()).isEqualTo(1);
    }

    @Test
    void pollSetsStateInCriterionToTerminalStatesPerStore() {
        poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.poll();

        var negotiationSpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(negotiationStore).queryNegotiations(negotiationSpecCaptor.capture());
        assertThat(statesOf(negotiationSpecCaptor.getValue()))
                .containsExactlyInAnyOrder(ContractNegotiationStates.FINALIZED.name(),
                        ContractNegotiationStates.TERMINATING.name(), ContractNegotiationStates.TERMINATED.name());

        var transferSpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(transferProcessStore).findAll(transferSpecCaptor.capture());
        assertThat(statesOf(transferSpecCaptor.getValue()))
                .containsExactlyInAnyOrder(TransferProcessStates.STARTED.name(), TransferProcessStates.TERMINATING.name(),
                        TransferProcessStates.TERMINATING_REQUESTED.name(), TransferProcessStates.TERMINATED.name());
    }

    @Test
    void idAbsentFromBatchResultFallsThroughToDeadlineCheck() {
        var future = poller.awaitNegotiation("neg-1", Duration.ofMillis(10));

        poller.poll();
        assertThat(future).isNotDone();

        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertFailedWith(future, FailureKind.TRANSPORT_FAULT, TimeoutException.class);
    }

    private static StageOutcome.Failed<?> failedOutcome(CompletableFuture<? extends StageOutcome<?>> future) {
        var outcome = future.orTimeout(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS).join();
        assertThat(outcome).isInstanceOf(StageOutcome.Failed.class);
        return (StageOutcome.Failed<?>) outcome;
    }

    private static void assertFailedWith(CompletableFuture<? extends StageOutcome<?>> future, FailureKind kind,
                                         Class<? extends Throwable> failureType) {
        var failed = failedOutcome(future);
        assertThat(failed.failureKind()).isEqualTo(kind);
        assertThat(failed.failure()).isInstanceOf(failureType);
    }

    private static <T> T succeededValue(CompletableFuture<StageOutcome<T>> future) {
        var outcome = future.orTimeout(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS).join();
        assertThat(outcome).isInstanceOf(StageOutcome.Succeeded.class);
        return ((StageOutcome.Succeeded<T>) outcome).value();
    }

    private ContractAgreement buildAgreement() {
        return ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("consumer-1")
                .contractSigningDate(0L)
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
    }

    private ContractNegotiation buildNegotiation(ContractNegotiationStates state, ContractAgreement agreement, String errorDetail) {
        return buildNegotiation("neg-1", state, agreement, errorDetail);
    }

    private ContractNegotiation buildNegotiation(String id, ContractNegotiationStates state, ContractAgreement agreement,
                                                  String errorDetail) {
        return buildNegotiation(id, state, agreement, errorDetail, new ProtocolMessages());
    }

    private ContractNegotiation buildNegotiation(String id, ContractNegotiationStates state, ContractAgreement agreement,
                                                  String errorDetail, ProtocolMessages protocolMessages) {
        return ContractNegotiation.Builder.newInstance()
                .protocolMessages(protocolMessages)
                .id(id)
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .state(state.code())
                .contractAgreement(agreement)
                .errorDetail(errorDetail)
                .build();
    }

    private TransferProcess buildTransferProcess(TransferProcessStates state, DataAddress dataAddress, String errorDetail) {
        return buildTransferProcess(state, dataAddress, errorDetail, new ProtocolMessages());
    }

    private TransferProcess buildTransferProcess(TransferProcessStates state, DataAddress dataAddress, String errorDetail,
                                                 ProtocolMessages protocolMessages) {
        if (dataAddress != null) {
            resolvableAddresses.put("tp-1", dataAddress);
        }
        return TransferProcess.Builder.newInstance()
                .id("tp-1")
                .protocolMessages(protocolMessages)
                .state(state.code())
                .errorDetail(errorDetail)
                .build();
    }

    private static ProtocolMessages receivedMessage() {
        var messages = new ProtocolMessages();
        messages.addReceived("received-1");
        return messages;
    }

    private static QuerySpec specWithId(String id) {
        return argThat(spec -> spec != null && idsOf(spec).contains(id));
    }

    private static Collection<?> idsOf(QuerySpec spec) {
        return spec.getFilterExpression().stream()
                .filter(criterion -> "id".equals(criterion.getOperandLeft()))
                .map(criterion -> (Collection<?>) criterion.getOperandRight())
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> statesOf(QuerySpec spec) {
        return spec.getFilterExpression().stream()
                .filter(criterion -> "state".equals(criterion.getOperandLeft()))
                .map(criterion -> (Collection<String>) criterion.getOperandRight())
                .findFirst()
                .orElseThrow();
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

    /**
     * Models {@code LocalTransactionContext}'s per-thread join semantics: nested calls on the same thread share
     * depth, and only the outermost call's commit/rollback outcome is recorded.
     */
    private static final class RecordingTransactionContext implements TransactionContext {

        enum Outcome { COMMITTED, ROLLED_BACK }

        private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
        private final List<Outcome> outcomes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void execute(TransactionBlock block) {
            execute((ResultTransactionBlock<Void>) () -> {
                block.execute();
                return null;
            });
        }

        @Override
        public <T> T execute(ResultTransactionBlock<T> block) {
            var outer = depth.get() == 0;
            depth.set(depth.get() + 1);
            var rollbackOnly = false;
            try {
                return block.execute();
            } catch (RuntimeException e) {
                rollbackOnly = true;
                throw e;
            } finally {
                depth.set(depth.get() - 1);
                if (outer) {
                    outcomes.add(rollbackOnly ? Outcome.ROLLED_BACK : Outcome.COMMITTED);
                }
            }
        }

        @Override
        public void registerSynchronization(TransactionSynchronization sync) {
            throw new UnsupportedOperationException();
        }

        int currentDepth() {
            return depth.get();
        }

        List<Outcome> outcomes() {
            return outcomes;
        }
    }

    /**
     * Simulates a transaction manager that fails to even open a transaction, e.g. a lost database
     * connection.
     */
    private static final class ThrowingTransactionContext implements TransactionContext {

        @Override
        public void execute(TransactionBlock block) {
            throw new RuntimeException("transaction unavailable");
        }

        @Override
        public <T> T execute(ResultTransactionBlock<T> block) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerSynchronization(TransactionSynchronization sync) {
            throw new UnsupportedOperationException();
        }
    }
}
