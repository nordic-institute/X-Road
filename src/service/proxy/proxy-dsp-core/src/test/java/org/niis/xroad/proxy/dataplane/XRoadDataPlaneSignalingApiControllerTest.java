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
package org.niis.xroad.proxy.dataplane;

import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.signaling.domain.DataFlowPrepareMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartedNotificationMessage;
import org.eclipse.edc.signaling.domain.DataFlowStatusMessage;
import org.eclipse.edc.signaling.domain.DspDataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneSignalingApiControllerTest {

    private static final String DATA_FLOW_ENDPOINT = "http://127.0.0.1:5590/full/api/v1/dataflows";

    @Mock
    private XRoadDataPlaneManager manager;

    private XRoadDataPlaneSignalingApiController controller;

    @BeforeEach
    void setUp() {
        controller = new XRoadDataPlaneSignalingApiController(manager);
    }

    @Test
    void prepareDelegatesToManagerAndReturnsStatusMessage() {
        var prepareMessage = DataFlowPrepareMessage.Builder.newInstance()
                .processId("flow-6")
                .transferType("Xrd-PULL")
                .build();
        var statusMessage = buildStatusMessage(DataFlowStates.PROVISIONED);

        when(manager.prepare(prepareMessage)).thenReturn(statusMessage);

        var result = controller.prepare(prepareMessage);

        assertThat(result).isSameAs(statusMessage);
        verify(manager).prepare(prepareMessage);
    }

    @Test
    void startReturnsStatusMessageWithProxyEndpoint() {
        var startMessage = buildStartMessage("flow-1");
        var statusMessage = buildStatusMessage(DataFlowStates.STARTED);

        when(manager.start(startMessage)).thenReturn(statusMessage);

        var result = controller.start(startMessage);

        assertThat(result).isSameAs(statusMessage);
        verify(manager).start(startMessage);
    }

    @Test
    void startWithFlowIdDelegatesToManagerStart() {
        var startMessage = buildStartMessage("flow-2");
        var statusMessage = buildStatusMessage(DataFlowStates.STARTED);

        when(manager.start(startMessage)).thenReturn(statusMessage);

        var result = controller.start("flow-2", startMessage);

        assertThat(result).isSameAs(statusMessage);
        verify(manager).start(startMessage);
    }

    @Test
    void startedDelegatesToManagerAndReturnsStatusMessage() {
        var notification = DataFlowStartedNotificationMessage.Builder.newInstance().messageId("msg-1").build();
        var statusMessage = buildStatusMessage(DataFlowStates.STARTED);

        when(manager.started("flow-7")).thenReturn(statusMessage);

        var result = controller.started("flow-7", notification);

        assertThat(result).isSameAs(statusMessage);
        verify(manager).started("flow-7");
    }

    @Test
    void completedDelegatesToManagerCompleted() {
        controller.completed("flow-8", Map.of());

        verify(manager).completed("flow-8");
    }

    @Test
    void terminateDelegatesToManagerTerminate() {
        controller.terminate("flow-3", Map.of());

        verify(manager).terminate("flow-3");
    }

    @Test
    void suspendDelegatesToManagerSuspend() {
        var suspendMessage = DataFlowSuspendMessage.Builder.newInstance().reason("pause").build();

        controller.suspend("flow-4", suspendMessage);

        verify(manager).suspend("flow-4", "pause");
    }

    @Test
    void suspendWithNullMessagePassesNullReason() {
        controller.suspend("flow-4", null);

        verify(manager).suspend("flow-4", null);
    }

    @Test
    void getTransferStateReturnsStateJsonObject() {
        when(manager.state("flow-5")).thenReturn(DataFlowStates.STARTED);

        var result = controller.getTransferState("flow-5");

        assertThat(result.getString("@type")).isEqualTo("DataFlowState");
        assertThat(result.getString(EDC_NAMESPACE + "state")).isEqualTo("STARTED");
    }

    @Test
    void getTransferStateUnknownIdReturnsFailedState() {
        when(manager.state("unknown")).thenReturn(DataFlowStates.FAILED);

        var result = controller.getTransferState("unknown");

        assertThat(result.getString(EDC_NAMESPACE + "state")).isEqualTo("FAILED");
    }

    private DataFlowStartMessage buildStartMessage(String processId) {
        return DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType("Xrd-PULL")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .build();
    }

    private DataFlowStatusMessage buildStatusMessage(DataFlowStates state) {
        var dataAddress = DspDataAddress.Builder.newInstance()
                .endpointType("http")
                .endpoint(DATA_FLOW_ENDPOINT)
                .build();
        return DataFlowStatusMessage.Builder.newInstance()
                .dataAddress(dataAddress)
                .state(state.toString())
                .build();
    }
}
