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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
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
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessCompletionPollerTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);

    @Mock
    ContractNegotiationStore negotiationStore;
    @Mock
    TransferProcessStore transferProcessStore;
    @Mock
    Monitor monitor;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    private AssetAccessCompletionPoller poller;

    @BeforeEach
    void setUp() {
        poller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, new NoopTransactionContext(),
                ExecutorInstrumentation.noop(), clock, monitor, Duration.ofDays(1));
    }

    @AfterEach
    void tearDown() {
        poller.stop();
    }

    @Test
    void awaitNegotiationCompletesWithAgreementWhenFinalized() {
        var agreement = buildAgreement();
        when(negotiationStore.findById("neg-1")).thenReturn(buildNegotiation(ContractNegotiationStates.FINALIZED, agreement, null));

        var future = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future).isCompletedWithValue(agreement);
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
    void awaitNegotiationFailsWithTimeoutWhenDeadlinePasses() {
        when(negotiationStore.findById("neg-1")).thenReturn(null);

        var future = poller.awaitNegotiation("neg-1", Duration.ofMillis(10));
        clock.advanceTo(clock.instant().plusMillis(20));
        poller.poll();

        assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void awaitTransferCompletesWithDataAddressWhenStarted() {
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        when(transferProcessStore.findById("tp-1"))
                .thenReturn(buildTransferProcess(TransferProcessStates.STARTED, dataAddress, null));

        var future = poller.awaitTransfer("tp-1", LONG_TIMEOUT);
        poller.poll();

        assertThat(future).isCompletedWithValue(dataAddress);
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
    void stopFailsPendingWaitersWithAcquisitionFailure() {
        var negotiationFuture = poller.awaitNegotiation("neg-1", LONG_TIMEOUT);
        var transferFuture = poller.awaitTransfer("tp-1", LONG_TIMEOUT);

        poller.stop();

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
        return TransferProcess.Builder.newInstance()
                .id("tp-1")
                .state(state.code())
                .contentDataAddress(dataAddress)
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
}
