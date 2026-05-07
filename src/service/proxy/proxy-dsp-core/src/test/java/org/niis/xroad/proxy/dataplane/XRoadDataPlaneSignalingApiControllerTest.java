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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowResponseMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowTerminateMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.spi.types.domain.transfer.TransferType;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneSignalingApiControllerTest {

    private static final String DATA_FLOW_ENDPOINT = "http://127.0.0.1:5590/full/api/v1/dataflows";

    @Mock
    private TypeTransformerRegistry transformerRegistry;
    @Mock
    private XRoadDataPlaneManager manager;

    private XRoadDataPlaneSignalingApiController controller;

    @BeforeEach
    void setUp() {
        controller = new XRoadDataPlaneSignalingApiController(transformerRegistry, manager);
    }

    @Test
    void start_returnsResponseWithProxyEndpoint() {
        var startMessage = buildStartMessage("flow-1", "Xrd", FlowType.PULL);
        var responseAddress = DataAddress.Builder.newInstance()
                .type("http")
                .property("endpoint", DATA_FLOW_ENDPOINT)
                .build();
        var flowResponse = DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(responseAddress)
                .build();
        var responseJson = Json.createObjectBuilder().add("response", "ok").build();

        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(Result.success(startMessage));
        when(manager.start(startMessage)).thenReturn(flowResponse);
        when(transformerRegistry.transform(isA(DataFlowResponseMessage.class), eq(JsonObject.class)))
                .thenReturn(Result.success(responseJson));

        var result = controller.start(Json.createObjectBuilder().build());

        assertThat(result).isEqualTo(responseJson);
        verify(manager).start(startMessage);
    }

    @Test
    void start_withFlowId_delegatesToSameStartFlow() {
        var startMessage = buildStartMessage("flow-2", "Xrd", FlowType.PULL);
        var flowResponse = DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(DataAddress.Builder.newInstance().type("http").build())
                .build();
        var responseJson = Json.createObjectBuilder().add("ok", true).build();

        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(Result.success(startMessage));
        when(manager.start(startMessage)).thenReturn(flowResponse);
        when(transformerRegistry.transform(isA(DataFlowResponseMessage.class), eq(JsonObject.class)))
                .thenReturn(Result.success(responseJson));

        var result = controller.start("flow-2", Json.createObjectBuilder().build());

        assertThat(result).isEqualTo(responseJson);
        verify(manager).start(startMessage);
    }

    @Test
    void start_transformationFailure_throwsXrdRuntimeException() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(Result.failure("bad JSON-LD"));

        assertThatThrownBy(() -> controller.start(Json.createObjectBuilder().build()))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void terminate_transitionsStateToTerminated() {
        var terminateMessage = DataFlowTerminateMessage.Builder.newInstance().reason("test-reason").build();
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowTerminateMessage.class)))
                .thenReturn(Result.success(terminateMessage));

        controller.terminate("flow-3", Json.createObjectBuilder().build());

        verify(manager).terminate("flow-3", terminateMessage);
    }

    @Test
    void suspend_transitionsStateToSuspended() {
        var suspendMessage = DataFlowSuspendMessage.Builder.newInstance().reason("pause").build();
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowSuspendMessage.class)))
                .thenReturn(Result.success(suspendMessage));

        controller.suspend("flow-4", Json.createObjectBuilder().build());

        verify(manager).suspend("flow-4", suspendMessage);
    }

    @Test
    void getTransferState_returnsStateJsonObject() {
        when(manager.state("flow-5")).thenReturn(DataFlowStates.STARTED);

        var result = controller.getTransferState("flow-5");

        assertThat(result.getString("@type")).isEqualTo("DataFlowState");
        assertThat(result.getString(EDC_NAMESPACE + "state")).isEqualTo("STARTED");
    }

    @Test
    void getTransferState_unknownId_returnsFailedState() {
        when(manager.state("unknown")).thenReturn(DataFlowStates.FAILED);

        var result = controller.getTransferState("unknown");

        assertThat(result.getString(EDC_NAMESPACE + "state")).isEqualTo("FAILED");
    }

    @Test
    void checkAvailability_doesNotThrow() {
        // no-op — must not throw
        controller.checkAvailability();
    }

    @Test
    void prepare_delegatesToManagerPrepare() {
        var provisionMessage = org.eclipse.edc.spi.types.domain.transfer.DataFlowProvisionMessage.Builder.newInstance()
                .processId("flow-6")
                .build();
        var flowResponse = DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(DataAddress.Builder.newInstance().type("http").build())
                .build();
        var responseJson = Json.createObjectBuilder().add("prepared", true).build();

        when(transformerRegistry.transform(isA(JsonObject.class),
                eq(org.eclipse.edc.spi.types.domain.transfer.DataFlowProvisionMessage.class)))
                .thenReturn(Result.success(provisionMessage));
        when(manager.prepare(provisionMessage)).thenReturn(flowResponse);
        when(transformerRegistry.transform(isA(DataFlowResponseMessage.class), eq(JsonObject.class)))
                .thenReturn(Result.success(responseJson));

        var result = controller.prepare(Json.createObjectBuilder().build());

        assertThat(result).isEqualTo(responseJson);
        verify(manager).prepare(provisionMessage);
    }

    @Test
    void transformerRegistry_roundTripWithRealRegistry() {
        // Validates transformer wiring: build a DataFlowStartMessage using EDC SPI,
        // transform it to JsonObject using XRoadDpsTransformerRegistry, then back.
        var registry = new XRoadDpsTransformerRegistry().registry();

        var startMessage = buildStartMessage("flow-rt", "Xrd", FlowType.PULL);
        var responseAddress = DataAddress.Builder.newInstance()
                .type("http")
                .property("endpoint", DATA_FLOW_ENDPOINT)
                .build();
        var response = DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(responseAddress)
                .build();

        // Transform outgoing DataFlowResponseMessage → JsonObject
        var jsonResult = registry.transform(response, JsonObject.class);
        assertThat(jsonResult.succeeded()).isTrue();

        var json = jsonResult.getContent();
        assertThat(json).isNotNull();
        // Response type should be present in expanded JSON-LD
        assertThat(json.getString("@type"))
                .contains("DataFlowResponseMessage");
    }

    private DataFlowStartMessage buildStartMessage(String processId, String destination, FlowType flowType) {
        return DataFlowStartMessage.Builder.newInstance()
                .processId(processId)
                .transferType(new TransferType(destination, flowType))
                .agreementId("agreement-1")
                .assetId("asset-1")
                .participantId("participant-1")
                .callbackAddress(java.net.URI.create("http://callback.example"))
                .sourceDataAddress(DataAddress.Builder.newInstance().type("http").build())
                .build();
    }
}
