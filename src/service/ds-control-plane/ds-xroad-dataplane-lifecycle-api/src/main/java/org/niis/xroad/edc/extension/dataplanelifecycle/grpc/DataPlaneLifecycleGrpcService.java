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

import io.grpc.stub.StreamObserver;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.NotifyPreparedCommand;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.command.NotifyStartedCommand;
import org.eclipse.edc.spi.result.ServiceResult;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataFlowState;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataPlaneLifecycleServiceGrpc;
import org.niis.xroad.edc.dataplanelifecycle.proto.ReportDataFlowStateRequest;
import org.niis.xroad.rpc.common.Empty;

/**
 * gRPC service that receives the provider data plane's lifecycle reports and advances the transfer process
 * the same way EDC's own stock data plane does over the public DSP callbacks ({@code DataPlaneTransferApiController}
 * in {@code data-plane-signaling-core}), minus the HTTP hop: {@code PREPARED} and {@code STARTED} each call
 * {@link TransferProcessService#notifyPrepared} / {@link TransferProcessService#notifyStarted} with no data
 * address — the address content is unchanged from what the data plane already stated synchronously in its
 * signaling response, so nothing new travels here — and {@code COMPLETED} calls
 * {@link TransferProcessService#complete}. A resumed flow is not reported: the control plane drives the resume
 * and advances from the data plane's synchronous response.
 */
@Slf4j
@RequiredArgsConstructor
class DataPlaneLifecycleGrpcService extends DataPlaneLifecycleServiceGrpc.DataPlaneLifecycleServiceImplBase {

    private final TransferProcessService transferProcessService;
    private final RpcResponseHandler responseHandler;

    @Override
    @WithSpan("dsp-dataplane-lifecycle-report")
    public void reportDataFlowState(ReportDataFlowStateRequest request, StreamObserver<Empty> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> reportInternal(request));
    }

    private Empty reportInternal(ReportDataFlowStateRequest request) {
        var result = advance(request.getProcessId(), request.getState());
        if (result.failed()) {
            log.warn("Could not advance transfer process {} to {}: {}",
                    request.getProcessId(), request.getState(), result.getFailureDetail());
            throw XrdRuntimeException.systemException(ErrorCode.INVALID_REQUEST)
                    .details(result.getFailureDetail())
                    .build();
        }
        return Empty.getDefaultInstance();
    }

    private ServiceResult<Void> advance(String processId, DataFlowState state) {
        return switch (state) {
            case PREPARED -> transferProcessService.notifyPrepared(new NotifyPreparedCommand(processId, null));
            case STARTED -> transferProcessService.notifyStarted(new NotifyStartedCommand(processId, null));
            case COMPLETED -> transferProcessService.complete(processId);
            case DATA_FLOW_STATE_UNSPECIFIED, UNRECOGNIZED ->
                    ServiceResult.badRequest("Unsupported data flow state " + state);
        };
    }
}
