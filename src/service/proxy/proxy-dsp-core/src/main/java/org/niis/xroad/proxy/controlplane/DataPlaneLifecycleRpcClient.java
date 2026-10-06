/*
 * The MIT License
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

package org.niis.xroad.proxy.controlplane;

import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataFlowState;
import org.niis.xroad.edc.dataplanelifecycle.proto.DataPlaneLifecycleServiceGrpc;
import org.niis.xroad.edc.dataplanelifecycle.proto.ReportDataFlowStateRequest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Reports the provider data plane's lifecycle state to the control plane over the same gRPC server
 * {@link AgreementGrantRpcClient} and {@link AssetAccessRpcClient} already talk to. Fire-and-forget: every
 * call is handed to a dedicated background thread and returns to the caller immediately, so a slow or
 * unreachable control plane never delays a signaling response. A failed report is logged at warn with only
 * the process id and state — never an exception stack trace reaching the signaling caller — and otherwise
 * dropped.
 *
 * <p>Dropping is safe because the control plane does not depend on these reports for {@code Xrd-PULL}: every
 * reported state is reached synchronously inside a signal the control plane itself sent, and it advances its
 * transfer process from that signal's response. The reports are the second, informational channel that
 * EDC's own data-plane callbacks provide; {@code GET /v1/dataflows/{id}/state} remains available to
 * reconcile a flow after the fact.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class DataPlaneLifecycleRpcClient extends AbstractRpcClient {

    private final RpcChannelFactory rpcChannelFactory;
    private final ControlPlaneRpcChannelProperties channelProperties;

    private static final int REPORT_QUEUE_CAPACITY = 1000;

    private final ExecutorService executorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(REPORT_QUEUE_CAPACITY), runnable -> {
                var thread = new Thread(runnable, "dataplane-lifecycle-report");
                thread.setDaemon(true);
                return thread;
            });

    private ManagedChannel channel;
    private DataPlaneLifecycleServiceGrpc.DataPlaneLifecycleServiceBlockingStub stub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.PROXY;
    }

    @Override
    public ManagedChannel getChannel() {
        return channel;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(),
                channelProperties.host(), channelProperties.port());
        channel = rpcChannelFactory.createChannel(channelProperties);
        stub = DataPlaneLifecycleServiceGrpc.newBlockingStub(channel);
    }

    @Override
    @PreDestroy
    public void close() {
        executorService.shutdown();
        if (channel != null) {
            channel.shutdown();
        }
    }

    /**
     * Reports a data flow's new state to the control plane. Never throws: the gRPC call runs off the
     * caller's thread and any failure — including one raised synchronously before the call is even
     * dispatched — is caught and logged.
     *
     * @param processId the data flow's process ID
     * @param state     the state the flow just entered; must be {@link DataFlowStates#PROVISIONED},
     *                  {@link DataFlowStates#STARTED} or {@link DataFlowStates#COMPLETED}
     */
    public void reportState(String processId, DataFlowStates state) {
        try {
            executorService.execute(() -> doReport(processId, state));
        } catch (Exception e) {
            log.warn("Could not schedule data flow state report (process {}, state {})", processId, state, e);
        }
    }

    private void doReport(String processId, DataFlowStates state) {
        try {
            var request = ReportDataFlowStateRequest.newBuilder()
                    .setProcessId(processId)
                    .setState(toProtoState(state))
                    .build();
            exec(() -> stub.reportDataFlowState(request));
        } catch (Exception e) {
            log.warn("Failed to report data flow state (process {}, state {}) to control plane", processId, state, e);
        }
    }

    private static DataFlowState toProtoState(DataFlowStates state) {
        return switch (state) {
            case PROVISIONED -> DataFlowState.PREPARED;
            case STARTED -> DataFlowState.STARTED;
            case COMPLETED -> DataFlowState.COMPLETED;
            default -> throw new IllegalArgumentException("Data flow state %s is not reported to the control plane"
                    .formatted(state));
        };
    }
}
