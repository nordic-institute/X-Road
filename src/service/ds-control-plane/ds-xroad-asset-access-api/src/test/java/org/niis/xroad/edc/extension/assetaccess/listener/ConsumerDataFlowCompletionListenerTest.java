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

package org.niis.xroad.edc.extension.assetaccess.listener;

import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerDataFlowCompletionListenerTest {

    @Mock
    DataFlowController dataFlowController;
    @Mock
    Monitor monitor;

    ConsumerDataFlowCompletionListener listener;

    @BeforeEach
    void setUp() {
        listener = new ConsumerDataFlowCompletionListener(dataFlowController, monitor);
    }

    @Test
    void completedSignalsTheDataFlowControllerForAConsumerProcessWithADataPlane() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").type(TransferProcess.Type.CONSUMER)
                .dataPlaneId("dp-1").build();
        when(dataFlowController.completed(process)).thenReturn(StatusResult.success());

        listener.completed(process);

        verify(dataFlowController).completed(process);
    }

    @Test
    void completedIgnoresAProviderProcess() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").type(TransferProcess.Type.PROVIDER)
                .dataPlaneId("dp-1").build();

        listener.completed(process);

        verifyNoInteractions(dataFlowController);
    }

    @Test
    void completedIgnoresAConsumerProcessWithNoDataPlaneId() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").type(TransferProcess.Type.CONSUMER).build();

        listener.completed(process);

        verify(dataFlowController, never()).completed(any());
    }

    @Test
    void completedLogsAWarningWhenTheDataFlowControllerReportsFailureButNeverThrows() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").type(TransferProcess.Type.CONSUMER)
                .dataPlaneId("dp-1").build();
        when(dataFlowController.completed(process)).thenReturn(StatusResult.failure(
                ResponseStatus.FATAL_ERROR, "data plane unreachable"));

        listener.completed(process);

        verify(monitor).warning(anyString());
    }

    @Test
    void completedLogsAWarningWhenTheDataFlowControllerThrowsButNeverPropagates() {
        var process = TransferProcess.Builder.newInstance().id("tp-1").type(TransferProcess.Type.CONSUMER)
                .dataPlaneId("dp-1").build();
        when(dataFlowController.completed(process)).thenThrow(new RuntimeException("boom"));

        listener.completed(process);

        verify(monitor).warning(anyString(), any(Throwable.class));
    }
}
