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
package org.niis.xroad.edc.extension.dataplanelifecycle.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.NotifyPreparedCommand;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.NotifyStartedCommand;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataFlowState;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataPlaneLifecycleServiceGrpc;
import org.niis.xroad.edc.dataplanelifecycle.proto.ReportDataFlowStateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlaneLifecycleGrpcServiceTest {

    @Mock
    private TransferProcessService transferProcessService;

    private Server server;
    private ManagedChannel channel;
    private DataPlaneLifecycleServiceGrpc.DataPlaneLifecycleServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        var grpcService = new DataPlaneLifecycleGrpcService(transferProcessService, new RpcResponseHandler());
        server = ServerBuilder.forPort(0)
                .addService(grpcService)
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        stub = DataPlaneLifecycleServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void preparedNotifiesPreparedWithNoDataAddress() {
        when(transferProcessService.notifyPrepared(any())).thenReturn(ServiceResult.success());

        stub.reportDataFlowState(request("transfer-1", DataFlowState.PREPARED));

        var captor = ArgumentCaptor.forClass(NotifyPreparedCommand.class);
        verify(transferProcessService).notifyPrepared(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo("transfer-1");
        assertThat(captor.getValue().getDataAddress()).isNull();
        verifyNoMoreInteractions(transferProcessService);
    }

    @Test
    void startedNotifiesStarted() {
        when(transferProcessService.notifyStarted(any())).thenReturn(ServiceResult.success());

        stub.reportDataFlowState(request("transfer-2", DataFlowState.STARTED));

        var captor = ArgumentCaptor.forClass(NotifyStartedCommand.class);
        verify(transferProcessService).notifyStarted(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo("transfer-2");
        assertThat(captor.getValue().getDataAddress()).isNull();
        verifyNoMoreInteractions(transferProcessService);
    }


    @Test
    void completedCompletesTheTransferProcess() {
        when(transferProcessService.complete(eq("transfer-3"))).thenReturn(ServiceResult.success());

        stub.reportDataFlowState(request("transfer-3", DataFlowState.COMPLETED));

        verify(transferProcessService).complete("transfer-3");
        verifyNoMoreInteractions(transferProcessService);
    }

    @Test
    void unknownProcessIdBecomesGrpcError() {
        when(transferProcessService.complete(eq("unknown")))
                .thenReturn(ServiceResult.notFound("Transfer process unknown not found"));

        assertThatThrownBy(() -> stub.reportDataFlowState(request("unknown", DataFlowState.COMPLETED)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    @Test
    void illegalTransitionOnControlPlaneSideBecomesGrpcError() {
        when(transferProcessService.notifyStarted(any()))
                .thenReturn(ServiceResult.badRequest("Cannot notify started in current state"));

        assertThatThrownBy(() -> stub.reportDataFlowState(request("transfer-4", DataFlowState.STARTED)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    @Test
    void serviceThrowingBecomesGrpcInternalError() {
        when(transferProcessService.complete(eq("boom"))).thenThrow(new RuntimeException("store unavailable"));

        assertThatThrownBy(() -> stub.reportDataFlowState(request("boom", DataFlowState.COMPLETED)))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.INTERNAL.getCode()));
    }

    private static ReportDataFlowStateRequest request(String processId, DataFlowState state) {
        return ReportDataFlowStateRequest.newBuilder()
                .setProcessId(processId)
                .setState(state)
                .build();
    }
}
