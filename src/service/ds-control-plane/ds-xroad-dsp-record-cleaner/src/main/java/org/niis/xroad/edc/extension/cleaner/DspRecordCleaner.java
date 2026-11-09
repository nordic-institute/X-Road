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

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.TerminateTransferCommand;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.DataFlowStateDAOImpl;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static org.eclipse.edc.spi.query.Criterion.criterion;

/**
 * One pass of the DSP record cleaner. Safe to run on every control-plane instance at once: every delete is a
 * lease-guarded store delete or a {@code SKIP LOCKED} SQL delete, and a second run finds nothing left to do.
 *
 * <p>Step 1 terminates transfers that are still not final after {@code age}, through EDC, which releases the
 * data-address secret and signals the data plane. Step 2 deletes finished transfers, policy monitor entries,
 * data-flow state rows of finished flows and negotiations without an agreement once they are older than
 * {@code age}. Negotiations are found by a SQL id query that excludes those with an agreement, and are read and
 * deleted in batches. Agreements are never deleted.
 *
 * <p>{@code maxAge} bounds what {@code age} cannot reach. EDC cannot terminate a {@code RESUMED} transfer and
 * nothing moves it on, so one older than {@code maxAge} has its data-address secret removed and is deleted
 * through the store. A data-flow state row in any state older than {@code maxAge} is deleted, which also
 * removes a row recreated by a start signal that arrived after its deletion.
 *
 * <p>{@code age} must exceed {@link #MINIMUM_AGE}: the policy monitor re-evaluates entries once per
 * {@link #POLICY_MONITOR_PERIOD}, and a data-plane signal may still be retried for {@link #SIGNAL_RETRY_WINDOW}
 * after a transfer ends. Deleting a data-flow state row earlier would let a late start signal recreate it.
 */
@RequiredArgsConstructor
public class DspRecordCleaner {

    static final Duration POLICY_MONITOR_PERIOD = Duration.ofHours(1);
    static final Duration SIGNAL_RETRY_WINDOW = Duration.ofMinutes(10);
    public static final Duration MINIMUM_AGE = POLICY_MONITOR_PERIOD.plus(SIGNAL_RETRY_WINDOW);

    static final int PAGE_SIZE = 500;
    private static final String STATE_TIMESTAMP = "stateTimestamp";
    private static final String STATE = "state";
    private static final String TERMINATION_REASON = "Abandoned transfer closed by the DSP record cleaner";

    private final TransferProcessStore transferProcessStore;
    private final TransferProcessService transferProcessService;
    private final DataAddressStore dataAddressStore;
    private final ContractNegotiationStore contractNegotiationStore;
    private final NegotiationsWithoutAgreementReader negotiationsWithoutAgreementReader;
    private final PolicyMonitorEntryPurger policyMonitorEntryPurger;
    private final ServerConfDatabaseCtx serverConfDatabaseCtx;
    private final Clock clock;
    private final Monitor monitor;
    private final Duration age;
    private final Duration maxAge;
    private final DataFlowStateDAOImpl dataFlowStateDao = new DataFlowStateDAOImpl();

    /**
     * What one pass did.
     *
     * @param terminatedTransfers   transfers sent to termination
     * @param deletedTransfers      finished transfers deleted
     * @param deletedPolicyEntries  policy monitor entries deleted
     * @param deletedDataFlowStates data-flow state rows deleted
     * @param deletedNegotiations   negotiations without an agreement deleted
     * @param deletedResumedTransfers abandoned {@code RESUMED} transfers deleted
     */
    public record Summary(int terminatedTransfers, int deletedTransfers, int deletedPolicyEntries,
                          int deletedDataFlowStates, int deletedNegotiations, int deletedResumedTransfers) {

        boolean isEmpty() {
            return terminatedTransfers + deletedTransfers + deletedPolicyEntries + deletedDataFlowStates
                    + deletedNegotiations + deletedResumedTransfers == 0;
        }
    }

    /**
     * Validates a configured age.
     *
     * @param age the configured age
     * @return {@code age}
     * @throws IllegalArgumentException if {@code age} does not exceed {@link #MINIMUM_AGE}
     */
    public static Duration requireValidAge(Duration age) {
        if (age.compareTo(MINIMUM_AGE) <= 0) {
            throw new IllegalArgumentException("DSP record cleaner age %s must exceed %s".formatted(age, MINIMUM_AGE));
        }
        return age;
    }

    /**
     * Validates a configured upper age limit.
     *
     * @param maxAge the configured upper age limit
     * @param age    the configured age
     * @return {@code maxAge}
     * @throws IllegalArgumentException if {@code maxAge} is not positive or does not exceed {@code age}
     */
    public static Duration requireValidMaxAge(Duration maxAge, Duration age) {
        if (maxAge.isZero() || maxAge.isNegative() || maxAge.compareTo(age) <= 0) {
            throw new IllegalArgumentException(
                    "DSP record cleaner max age %s must be positive and exceed the age %s".formatted(maxAge, age));
        }
        return maxAge;
    }

    /**
     * Validates a configured interval.
     *
     * @param interval the configured interval
     * @return {@code interval}
     * @throws IllegalArgumentException if {@code interval} is zero or negative
     */
    public static Duration requireValidInterval(Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("DSP record cleaner interval %s must be positive".formatted(interval));
        }
        return interval;
    }

    /**
     * Runs one pass. A step that fails is logged and does not stop the others.
     *
     * @return what the pass did
     */
    public Summary run() {
        var now = clock.millis();
        var cutoff = now - age.toMillis();
        var maxCutoff = now - maxAge.toMillis();
        var summary = new Summary(
                step("terminate stuck transfers", () -> terminateStuckTransfers(cutoff)),
                step("delete finished transfers", () -> deleteFinishedTransfers(cutoff)),
                step("delete policy monitor entries", () -> policyMonitorEntryPurger.purgeFinishedUpdatedBefore(cutoff)),
                step("delete data-flow state rows", this::deleteDataFlowStates),
                step("delete negotiations without agreement", () -> deleteNegotiationsWithoutAgreement(cutoff)),
                step("delete abandoned resumed transfers", () -> deleteAbandonedResumedTransfers(maxCutoff)));
        if (!summary.isEmpty()) {
            monitor.info("DSP record cleaner pass: " + summary);
        }
        return summary;
    }

    private int step(String name, IntSupplier action) {
        try {
            return action.getAsInt();
        } catch (Exception e) {
            monitor.severe("DSP record cleaner step failed: " + name, e);
            return 0;
        }
    }

    private int terminateStuckTransfers(long cutoff) {
        var stuckIds = readAll(offset -> transferProcessStore.findAll(QuerySpec.Builder.newInstance()
                        .filter(List.of(
                                criterion(STATE, "<", TransferProcessStates.COMPLETED.code()),
                                criterion(STATE_TIMESTAMP, "<", cutoff)))
                        .offset(offset)
                        .limit(PAGE_SIZE)
                        .build()))
                .stream()
                .filter(DspRecordCleaner::isTerminable)
                .map(TransferProcess::getId)
                .toList();
        return (int) stuckIds.stream().filter(this::terminate).count();
    }

    private static boolean isTerminable(TransferProcess transfer) {
        return transfer.getState() < TransferProcessStates.TERMINATING.code() && transfer.canBeTerminated();
    }

    private boolean terminate(String transferId) {
        try {
            var result = transferProcessService.terminate(new TerminateTransferCommand(transferId, TERMINATION_REASON));
            if (result.failed()) {
                monitor.warning("Terminating abandoned transfer failed: transferProcessId=%s reason=%s"
                        .formatted(transferId, result.getFailureDetail()));
                return false;
            }
            return true;
        } catch (Exception e) {
            monitor.warning("Terminating abandoned transfer failed: transferProcessId=%s".formatted(transferId), e);
            return false;
        }
    }

    private int deleteFinishedTransfers(long cutoff) {
        var finishedIds = readAll(offset -> transferProcessStore.findAll(QuerySpec.Builder.newInstance()
                        .filter(List.of(
                                criterion(STATE, "in",
                                        List.of(TransferProcessStates.COMPLETED.code(), TransferProcessStates.TERMINATED.code())),
                                criterion(STATE_TIMESTAMP, "<", cutoff)))
                        .offset(offset)
                        .limit(PAGE_SIZE)
                        .build()))
                .stream()
                .map(TransferProcess::getId)
                .toList();
        return (int) finishedIds.stream().filter(id -> deleted(transferProcessStore.delete(id).succeeded(), id)).count();
    }

    private int deleteAbandonedResumedTransfers(long maxCutoff) {
        var resumed = readAll(offset -> transferProcessStore.findAll(QuerySpec.Builder.newInstance()
                .filter(List.of(
                        criterion(STATE, "=", TransferProcessStates.RESUMED.code()),
                        criterion(STATE_TIMESTAMP, "<", maxCutoff)))
                .offset(offset)
                .limit(PAGE_SIZE)
                .build()));
        return (int) resumed.stream().filter(this::removeSecretAndDelete).count();
    }

    private boolean removeSecretAndDelete(TransferProcess transfer) {
        try {
            var removed = dataAddressStore.remove(transfer);
            if (removed.failed()) {
                monitor.warning("Removing the data address of abandoned transfer failed: transferProcessId=%s reason=%s"
                        .formatted(transfer.getId(), removed.getFailureDetail()));
                return false;
            }
            return deleted(transferProcessStore.delete(transfer.getId()).succeeded(), transfer.getId());
        } catch (Exception e) {
            monitor.warning("Deleting abandoned transfer failed: transferProcessId=%s".formatted(transfer.getId()), e);
            return false;
        }
    }

    private int deleteDataFlowStates() {
        return serverConfDatabaseCtx.doInTransaction(session -> dataFlowStateDao.deleteOlderThan(session, age, maxAge));
    }

    private int deleteNegotiationsWithoutAgreement(long cutoff) {
        int deleted = 0;
        String lastId = "";
        int batchSize;
        do {
            var ids = negotiationsWithoutAgreementReader.readIdsUpdatedBefore(cutoff, lastId, PAGE_SIZE);
            deleted += (int) ids.stream().filter(id -> deleted(contractNegotiationStore.deleteById(id).succeeded(), id)).count();
            batchSize = ids.size();
            if (batchSize > 0) {
                lastId = ids.getLast();
            }
        } while (batchSize == PAGE_SIZE);
        return deleted;
    }

    private boolean deleted(boolean succeeded, String id) {
        if (!succeeded) {
            monitor.debug("DSP record cleaner left %s for a later pass: leased or already deleted".formatted(id));
        }
        return succeeded;
    }

    private static <T> List<T> readAll(PageReader<T> reader) {
        var all = new ArrayList<T>();
        int offset = 0;
        int pageSize;
        do {
            try (Stream<T> page = reader.read(offset)) {
                var items = page.toList();
                all.addAll(items);
                pageSize = items.size();
            }
            offset += pageSize;
        } while (pageSize == PAGE_SIZE);
        return all;
    }

    @FunctionalInterface
    private interface PageReader<T> {
        Stream<T> read(int offset);
    }
}
