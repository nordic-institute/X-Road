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

import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredTransferSweeperTest {

    private static final int BATCH_SIZE = 500;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final Duration INTERVAL = Duration.ofSeconds(30);

    @Mock
    TransferProcessStore transferProcessStore;
    @Mock
    DataAddressStore dataAddressStore;
    @Mock
    TransferProcessService transferProcessService;
    @Mock
    Monitor monitor;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ExpiredTransferSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new ExpiredTransferSweeper(transferProcessStore, dataAddressStore, transferProcessService,
                new NoopTransactionContext(), clock, monitor, INTERVAL, GRACE);
    }

    @Test
    void expiredBeyondGraceIsCompleted() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.of(startedConsumerTransfer("tp-1")));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.success(addressExpiringAt(NOW.minusSeconds(61))));
        when(transferProcessService.complete("tp-1")).thenReturn(ServiceResult.success());

        sweeper.sweep();

        verify(transferProcessService).complete("tp-1");
        verify(monitor).info(anyString());
    }

    @Test
    void expiredWithinGraceIsNotCompleted() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.of(startedConsumerTransfer("tp-1")));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.success(addressExpiringAt(NOW.minusSeconds(10))));

        sweeper.sweep();

        verify(transferProcessService, never()).complete(any());
    }

    @Test
    void notYetExpiredIsNotCompleted() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.of(startedConsumerTransfer("tp-1")));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.success(addressExpiringAt(NOW.plusSeconds(100))));

        sweeper.sweep();

        verify(transferProcessService, never()).complete(any());
    }

    @Test
    void transferWithNoDataAddressIsSkipped() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.of(startedConsumerTransfer("tp-1")));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.notFound("no data address stored"));

        sweeper.sweep();

        verify(transferProcessService, never()).complete(any());
    }

    @Test
    void transferWithNoTokenExpiryIsSkipped() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.of(startedConsumerTransfer("tp-1")));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.success(addressWithoutToken()));

        sweeper.sweep();

        verify(transferProcessService, never()).complete(any());
    }

    @Test
    void queryCarriesConsumerTypeAndStartedStateCriteria() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.empty());

        sweeper.sweep();

        var captor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(transferProcessStore).findAll(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).contains(
                Criterion.criterion("type", "=", TransferProcess.Type.CONSUMER.name()),
                Criterion.criterion("state", "=", TransferProcessStates.STARTED.code()));
    }

    @Test
    void pagingReadsASecondPageWhenTheFirstIsFull() {
        List<TransferProcess> transfers = new ArrayList<>();
        for (int i = 0; i < BATCH_SIZE + 1; i++) {
            transfers.add(startedConsumerTransfer("tp-" + i));
        }
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.notFound("no data address stored"));
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> {
            QuerySpec spec = inv.getArgument(0);
            return transfers.stream().skip(spec.getOffset()).limit(spec.getLimit());
        });

        sweeper.sweep();

        var captor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(transferProcessStore, times(2)).findAll(captor.capture());
        var offsets = captor.getAllValues().stream().map(QuerySpec::getOffset).sorted().toList();
        assertThat(offsets).containsExactly(0, BATCH_SIZE);
    }

    @Test
    void failingAndThrowingCompletesAreLoggedAndDoNotStopTheOthers() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.of(
                startedConsumerTransfer("tp-1"), startedConsumerTransfer("tp-2"), startedConsumerTransfer("tp-3")));
        when(dataAddressStore.resolve(any())).thenReturn(StoreResult.success(addressExpiringAt(NOW.minusSeconds(61))));
        when(transferProcessService.complete("tp-1")).thenReturn(ServiceResult.success());
        when(transferProcessService.complete("tp-2")).thenReturn(ServiceResult.conflict("not completable"));
        when(transferProcessService.complete("tp-3")).thenThrow(new EdcException("completion backend unavailable"));

        sweeper.sweep();

        verify(transferProcessService).complete("tp-1");
        verify(transferProcessService).complete("tp-2");
        verify(transferProcessService).complete("tp-3");
        verify(monitor).warning(anyString());
        verify(monitor).warning(anyString(), any(Throwable.class));

        var infoCaptor = ArgumentCaptor.forClass(String.class);
        verify(monitor).info(infoCaptor.capture());
        assertThat(infoCaptor.getValue()).contains("1");
    }

    @Test
    void startRunsSweepPassesUntilStopped() {
        when(transferProcessStore.findAll(any())).thenAnswer(inv -> Stream.empty());
        var fastSweeper = new ExpiredTransferSweeper(transferProcessStore, dataAddressStore, transferProcessService,
                new NoopTransactionContext(), clock, monitor, Duration.ofMillis(10), GRACE);

        fastSweeper.start();
        verify(transferProcessStore, timeout(2000).atLeast(2)).findAll(any());
        fastSweeper.stop();
    }

    private static TransferProcess startedConsumerTransfer(String id) {
        return TransferProcess.Builder.newInstance()
                .id(id)
                .type(TransferProcess.Type.CONSUMER)
                .state(TransferProcessStates.STARTED.code())
                .build();
    }

    private static DataAddress addressExpiringAt(Instant expiry) {
        return DataAddress.Builder.newInstance()
                .type("HttpData")
                .property(EDC_NAMESPACE + "authorization", buildJwt(expiry.getEpochSecond()))
                .build();
    }

    private static DataAddress addressWithoutToken() {
        return DataAddress.Builder.newInstance().type("HttpData").build();
    }

    private static String buildJwt(long expEpochSeconds) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        var header = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        var payload = encoder.encodeToString(("{\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        var signature = encoder.encodeToString("sig".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }
}
