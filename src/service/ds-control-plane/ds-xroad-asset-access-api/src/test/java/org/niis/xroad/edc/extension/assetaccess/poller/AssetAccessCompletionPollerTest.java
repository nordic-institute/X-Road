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
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessCompletionPollerTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration ASYNC_AWAIT = Duration.ofSeconds(2);
    private static final String DEFAULT_TERMINATION_DETAIL = "provider terminated";

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
        when(negotiationStore.findById("neg-1")).thenReturn(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(agreement);
    }

    @Test
    void awaitNegotiationFailsWithStoredErrorDetailWhenTerminated() {
        when(negotiationStore.findById("neg-1"))
                .thenReturn(buildNegotiation(ContractNegotiationStates.TERMINATED, null, "provider refused"));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_negotiation_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("neg-1");
                    assertThat(cause.getDetails()).isEqualTo("provider refused");
                });
    }

    @Test
    void awaitNegotiationKeepsPendingForNonTerminalState() {
        when(negotiationStore.findById("neg-1"))
                .thenReturn(buildNegotiation(ContractNegotiationStates.REQUESTED, null, null));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future).isNotDone();
    }

    @Test
    void awaitNegotiationFailsWithDefaultDetailWhenTerminatedWithoutErrorDetail() {
        when(negotiationStore.findById("neg-1"))
                .thenReturn(buildNegotiation(ContractNegotiationStates.TERMINATED, null, null));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getDetails())
                        .isEqualTo(DEFAULT_TERMINATION_DETAIL));
    }

    @Test
    void awaitNegotiationFailsWithTimeoutWhenDeadlinePasses() {
        when(negotiationStore.findById("neg-1")).thenReturn(null);

        var future = poller.awaitNegotiation("neg-1", Duration.ofMillis(10));
        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void awaitTransferCompletesWithDataAddressWhenStarted() throws Exception {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(dataAddress);
    }

    @Test
    void awaitTransferFailsWithStoredErrorDetailWhenTerminated() {
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.TERMINATED, null, "provider terminated the transfer"));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("tp-1");
                    assertThat(cause.getDetails()).isEqualTo("provider terminated the transfer");
                });
    }

    @Test
    void awaitTransferFailsWithDefaultDetailWhenTerminatedWithoutErrorDetail() {
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.TERMINATED, null, null));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getDetails())
                        .isEqualTo(DEFAULT_TERMINATION_DETAIL));
    }

    @Test
    void awaitTransferFailsWhenResolveFails() {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.notFound("no data address stored"));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
                    assertThat(cause.getDetails()).isEqualTo("no data address stored");
                });
    }

    @Test
    void awaitTransferDoesNotCompleteWhenCompleted() {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.COMPLETED, dataAddress, null));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future).isNotDone();
    }

    @Test
    void awaitTransferFailsWithTimeoutWhenDeadlinePasses() {
        when(transferProcessStore.findById("tp-1")).thenReturn(null);

        var future = poller.awaitTransfer("tp-1", Duration.ofMillis(10));
        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void stopFailsPendingWaitersWithAcquisitionFailureBeforeReturning() {
        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        var transferFuture = poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.stop();

        assertThat(negotiationFuture).isDone();
        assertThat(transferFuture).isDone();
        assertThatThrownBy(negotiationFuture::join)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                });
        assertThatThrownBy(transferFuture::join).hasCauseInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void pollDoesNothingAfterStop() {
        poller.stop();

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        verify(negotiationStore, never()).findById(anyString());
        assertThat(future).isNotDone();
    }

    @Test
    void negotiationReadFailureDoesNotPreventTransferWaiterFromCompletingInSamePass() throws Exception {
        when(negotiationStore.findById("neg-1")).thenThrow(new RuntimeException("store unavailable"));

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null));

        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        var transferFuture = poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.poll();

        assertThat(transferFuture.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(dataAddress);
        assertThat(negotiationFuture).isNotDone();
    }

    @Test
    void completionRunsOffThePollThreadWithNoActiveTransaction() throws Exception {
        var recordingContext = new RecordingTransactionContext();
        var localPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                recordingContext, ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
        try {
            var agreement = buildAgreement();
            when(negotiationStore.findById("neg-1"))
                    .thenReturn(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null));

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
            var agreement = buildAgreement();
            when(negotiationStore.findById("neg-a"))
                    .thenReturn(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null));
            when(negotiationStore.findById("neg-b")).thenThrow(new RuntimeException("connection reset"));

            var futureA = localPoller.awaitNegotiation("neg-a", LONG_TIMEOUT);
            var futureB = localPoller.awaitNegotiation("neg-b", LONG_TIMEOUT);

            localPoller.poll();

            assertThat(futureA.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(agreement);
            assertThat(futureB).isNotDone();
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
            when(transferProcessStore.findById("tp-1"))
                    .thenReturn(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null));
            var resolveDepth = new CompletableFuture<Integer>();
            when(dataAddressStore.resolve(any())).thenAnswer(invocation -> {
                resolveDepth.complete(recordingContext.currentDepth());
                throw new RuntimeException("vault unreachable");
            });

            var future = localPoller.awaitTransfer("tp-1", LONG_TIMEOUT);

            localPoller.poll();

            assertThat(resolveDepth.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS)).isZero();
            assertThatThrownBy(() -> future.get(ASYNC_AWAIT.toSeconds(), TimeUnit.SECONDS))
                    .hasCauseInstanceOf(XrdRuntimeException.class)
                    .satisfies(ex -> {
                        var cause = (XrdRuntimeException) ex.getCause();
                        assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
                        assertThat(cause.getDetails()).isEqualTo("vault unreachable");
                    });
        } finally {
            localPoller.stop();
        }
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
        return ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .state(state.code())
                .contractAgreement(agreement)
                .errorDetail(errorDetail)
                .build();
    }

    private TransferProcess buildTransferProcess(TransferProcessStates state, DataAddress dataAddress, String errorDetail) {
        if (dataAddress != null) {
            resolvableAddresses.put("tp-1", dataAddress);
        }
        return TransferProcess.Builder.newInstance()
                .id("tp-1")
                .state(state.code())
                .errorDetail(errorDetail)
                .build();
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
}
