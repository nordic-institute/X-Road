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

package org.niis.xroad.edc.extension.edr.listener;

import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferCompletionListenerTest {

    TransferCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new TransferCompletionListener();
    }

    @Test
    void registerBeforeStartedCompletsFutureWithDataAddress() throws Exception {
        var edrAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        var startedData = TransferProcessStartedData.Builder.newInstance().dataAddress(edrAddress).build();
        var process = TransferProcess.Builder.newInstance().id("tp-1").build();
        var future = new CompletableFuture<DataAddress>();

        listener.register("tp-1", future);
        listener.started(process, startedData);

        assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(edrAddress);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void registerBeforeTerminatedFailsFutureWithEdcException() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").build();
        var future = new CompletableFuture<DataAddress>();

        listener.register("tp-1", future);
        listener.terminated(process);

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void startedBeforeRegisterCompletesViaDeadLetter() throws Exception {
        var edrAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        var startedData = TransferProcessStartedData.Builder.newInstance().dataAddress(edrAddress).build();
        var process = TransferProcess.Builder.newInstance().id("tp-1").build();
        var future = new CompletableFuture<DataAddress>();

        listener.started(process, startedData);
        listener.register("tp-1", future);

        assertThat(future.isDone()).isTrue();
        assertThat(future.get(1, TimeUnit.SECONDS)).isSameAs(edrAddress);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void terminatedBeforeRegisterFailsViaDeadLetter() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").build();
        var future = new CompletableFuture<DataAddress>();

        listener.terminated(process);
        listener.register("tp-1", future);

        assertThat(future.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
        assertThat(listener.activeWaiters()).isZero();
    }

    @Test
    void deregisterCleansUp() {
        var edrAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        var startedData = TransferProcessStartedData.Builder.newInstance().dataAddress(edrAddress).build();
        var process = TransferProcess.Builder.newInstance().id("tp-1").build();
        var future = new CompletableFuture<DataAddress>();

        listener.register("tp-1", future);
        assertThat(listener.activeWaiters()).isEqualTo(1);

        listener.deregister("tp-1");
        assertThat(listener.activeWaiters()).isZero();

        listener.started(process, startedData);
        assertThat(future.isDone()).isFalse();
    }

    @Test
    void unmatchedEventIsNoOp() {
        var edrAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        var startedData = TransferProcessStartedData.Builder.newInstance().dataAddress(edrAddress).build();
        var process = TransferProcess.Builder.newInstance().id("unknown-id").build();

        listener.started(process, startedData);

        assertThat(listener.activeWaiters()).isZero();
    }
}
