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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each transfer step reads ids in batches of {@link DspRecordCleaner#PAGE_SIZE}, keyset-paged by id, and handles
 * a batch before it reads the next one.
 */
class DspRecordCleanerStepsTest {

    private static final int PAGE = DspRecordCleaner.PAGE_SIZE;
    private static final Duration AGE = Duration.ofHours(6);
    private static final Duration MAX_AGE = Duration.ofDays(7);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private static final long CUTOFF = CLOCK.millis() - AGE.toMillis();
    private static final long MAX_CUTOFF = CLOCK.millis() - MAX_AGE.toMillis();

    private final TransferProcessStore transferProcessStore = mock();
    private final TransferProcessService transferProcessService = mock();
    private final DataAddressStore dataAddressStore = mock();
    private final ContractNegotiationStore negotiationStore = mock();
    private final TransferProcessIdReader transferProcessIdReader = mock();
    private final NegotiationsWithoutAgreementReader negotiationReader = mock();
    private final PolicyMonitorEntryPurger policyMonitorEntryPurger = mock();
    private final ServerConfDatabaseCtx serverConfDatabaseCtx = mock();
    private final Monitor monitor = mock();

    private final List<String> firstBatch = ids("a", PAGE);
    private final List<String> secondBatch = ids("b", 3);

    @Test
    void terminableStatesAreExactlyTheNonFinalStatesEdcCanTerminateExceptResumed() {
        var expected = Arrays.stream(TransferProcessStates.values())
                .filter(state -> state.code() < TransferProcessStates.TERMINATING.code())
                .filter(state -> TransferProcess.Builder.newInstance().id("probe").state(state.code()).build()
                        .canBeTerminated())
                .map(TransferProcessStates::code)
                .toList();

        assertThat(DspRecordCleaner.TERMINABLE_STATES).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(DspRecordCleaner.TERMINABLE_STATES).doesNotContain(TransferProcessStates.RESUMED.code());
    }

    @Test
    void stuckTransfersAreTerminatedBatchByBatch() {
        stubBatches(DspRecordCleaner.TERMINABLE_STATES, CUTOFF);
        when(transferProcessService.terminate(any(TerminateTransferCommand.class))).thenReturn(ServiceResult.success());

        var summary = cleaner().run();

        assertThat(summary.terminatedTransfers()).isEqualTo(PAGE + 3);
        var order = inOrder(transferProcessIdReader, transferProcessService);
        order.verify(transferProcessIdReader).readIdsUpdatedBefore(DspRecordCleaner.TERMINABLE_STATES, CUTOFF, "", PAGE);
        order.verify(transferProcessService).terminate(argThat(command -> lastOf(firstBatch).equals(command.getEntityId())));
        order.verify(transferProcessIdReader)
                .readIdsUpdatedBefore(DspRecordCleaner.TERMINABLE_STATES, CUTOFF, lastOf(firstBatch), PAGE);
        order.verify(transferProcessService).terminate(argThat(command -> lastOf(secondBatch).equals(command.getEntityId())));
        verify(transferProcessService, times(PAGE + 3)).terminate(any(TerminateTransferCommand.class));
        verify(transferProcessStore, never()).findAll(any());
    }

    @Test
    void stuckTransferWhoseTerminationFailsDoesNotStopTheBatch() {
        stubBatches(DspRecordCleaner.TERMINABLE_STATES, CUTOFF);
        when(transferProcessService.terminate(any(TerminateTransferCommand.class))).thenReturn(ServiceResult.success());
        when(transferProcessService.terminate(argThat(command -> command != null && "a-00000".equals(command.getEntityId()))))
                .thenReturn(ServiceResult.conflict("leased"));

        var summary = cleaner().run();

        assertThat(summary.terminatedTransfers()).isEqualTo(PAGE + 2);
        verify(transferProcessService, times(PAGE + 3)).terminate(any(TerminateTransferCommand.class));
    }

    @Test
    void finishedTransfersAreDeletedBatchByBatch() {
        stubBatches(DspRecordCleaner.FINISHED_STATES, CUTOFF);
        when(transferProcessStore.delete(anyString())).thenReturn(StoreResult.success());

        var summary = cleaner().run();

        assertThat(summary.deletedTransfers()).isEqualTo(PAGE + 3);
        var order = inOrder(transferProcessIdReader, transferProcessStore);
        order.verify(transferProcessIdReader).readIdsUpdatedBefore(DspRecordCleaner.FINISHED_STATES, CUTOFF, "", PAGE);
        order.verify(transferProcessStore).delete(lastOf(firstBatch));
        order.verify(transferProcessIdReader)
                .readIdsUpdatedBefore(DspRecordCleaner.FINISHED_STATES, CUTOFF, lastOf(firstBatch), PAGE);
        order.verify(transferProcessStore).delete(lastOf(secondBatch));
        verify(transferProcessStore, never()).findAll(any());
        verify(transferProcessStore, never()).findById(anyString());
    }

    @Test
    void resumedTransfersAreLoadedOneByOneOnlyToRemoveTheirSecretThenDeleted() {
        stubBatches(DspRecordCleaner.RESUMED_STATES, MAX_CUTOFF);
        var transfer = mock(TransferProcess.class);
        when(transferProcessStore.findById(anyString())).thenReturn(transfer);
        when(dataAddressStore.remove(transfer)).thenReturn(StoreResult.success());
        when(transferProcessStore.delete(anyString())).thenReturn(StoreResult.success());

        var summary = cleaner().run();

        assertThat(summary.deletedResumedTransfers()).isEqualTo(PAGE + 3);
        var order = inOrder(transferProcessIdReader, transferProcessStore, dataAddressStore);
        order.verify(transferProcessIdReader).readIdsUpdatedBefore(DspRecordCleaner.RESUMED_STATES, MAX_CUTOFF, "", PAGE);
        order.verify(transferProcessStore).findById(lastOf(firstBatch));
        order.verify(dataAddressStore).remove(transfer);
        order.verify(transferProcessStore).delete(lastOf(firstBatch));
        order.verify(transferProcessIdReader)
                .readIdsUpdatedBefore(DspRecordCleaner.RESUMED_STATES, MAX_CUTOFF, lastOf(firstBatch), PAGE);
        order.verify(transferProcessStore).findById(lastOf(secondBatch));
        verify(transferProcessStore, times(PAGE + 3)).findById(anyString());
        verify(transferProcessStore, never()).findAll(any());
    }

    @Test
    void resumedTransferThatIsAlreadyGoneIsSkipped() {
        stubBatches(DspRecordCleaner.RESUMED_STATES, MAX_CUTOFF);
        when(transferProcessStore.findById(anyString())).thenReturn(null);

        var summary = cleaner().run();

        assertThat(summary.deletedResumedTransfers()).isZero();
        verify(dataAddressStore, never()).remove(any());
        verify(transferProcessStore, never()).delete(anyString());
    }

    private void stubBatches(List<Integer> states, long cutoff) {
        when(transferProcessIdReader.readIdsUpdatedBefore(states, cutoff, "", PAGE)).thenReturn(firstBatch);
        when(transferProcessIdReader.readIdsUpdatedBefore(states, cutoff, lastOf(firstBatch), PAGE))
                .thenReturn(secondBatch);
        when(serverConfDatabaseCtx.doInTransaction(any())).thenReturn(0);
    }

    private DspRecordCleaner cleaner() {
        return new DspRecordCleaner(transferProcessStore, transferProcessService, dataAddressStore, negotiationStore,
                transferProcessIdReader, negotiationReader, policyMonitorEntryPurger, serverConfDatabaseCtx, CLOCK,
                monitor, AGE, MAX_AGE);
    }

    private static List<String> ids(String prefix, int count) {
        return IntStream.range(0, count).mapToObj(i -> "%s-%05d".formatted(prefix, i)).toList();
    }

    private static String lastOf(List<String> ids) {
        return ids.getLast();
    }
}
