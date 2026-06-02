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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.signaling.domain.DataFlowPrepareMessage;
import org.eclipse.edc.signaling.domain.DataFlowStartMessage;
import org.eclipse.edc.signaling.domain.DataFlowStatusMessage;
import org.eclipse.edc.signaling.domain.DspDataAddress;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory manager for active data flows in the X-Road proxy data plane.
 * <p>
 * Encapsulates the {@code Xrd-PULL} semantics: when a {@link DataFlowStartMessage} arrives,
 * the proxy fabricates a {@link DspDataAddress} pointing to its own signaling endpoint and
 * returns it wrapped in a {@link DataFlowStatusMessage}. No real data pipeline is started —
 * the proxy itself serves data on subsequent HTTP calls.
 * <p>
 * Flow state is tracked in-memory via a {@link ConcurrentHashMap}. The CP terminates flows
 * on completion, keeping the map bounded.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class XRoadDataPlaneManager {

    /** Full transfer-type string for Xrd-PULL flows (matches the wire value). */
    static final String XRD_PULL_TRANSFER_TYPE = "Xrd-PULL";

    private final DataPlaneServerProperties dspProperties;
    private final ConcurrentHashMap<String, DataFlowStates> activeFlows = new ConcurrentHashMap<>();

    /**
     * Handles a prepare request. For {@code Xrd-PULL} there is no async provisioning —
     * the response is built immediately using the proxy data-flow endpoint.
     *
     * @param message incoming prepare message
     * @return status message with {@code dataAddress.endpoint} set to the proxy data flow endpoint
     */
    public DataFlowStatusMessage prepare(DataFlowPrepareMessage message) {
        log.info("Preparing data flow for process {}", message.getProcessId());
        storeState(message.getProcessId(), DataFlowStates.PROVISIONED);
        return buildStatusMessage(DataFlowStates.PROVISIONED);
    }

    /**
     * Handles a start request, preserving {@code Xrd-PULL} semantics: validates the transfer type,
     * fabricates a {@link DspDataAddress} pointing to the proxy's signaling endpoint, and returns it
     * wrapped in a {@link DataFlowStatusMessage}.
     *
     * @param message incoming start message
     * @return status message with {@code dataAddress.endpoint} set to the proxy data flow endpoint
     * @throws XrdRuntimeException if the transfer type is not {@code Xrd-PULL}
     */
    public DataFlowStatusMessage start(DataFlowStartMessage message) {
        validateXrdPull(message);
        log.info("Starting Xrd-PULL data flow for process {}", message.getProcessId());
        storeState(message.getProcessId(), DataFlowStates.STARTED);
        return buildStatusMessage(DataFlowStates.STARTED);
    }

    /**
     * Terminates an active data flow, transitioning it to {@link DataFlowStates#TERMINATED}.
     *
     * @param flowId process ID of the flow to terminate
     */
    public void terminate(String flowId) {
        log.info("Terminating data flow {}", flowId);
        storeState(flowId, DataFlowStates.TERMINATED);
    }

    /**
     * Suspends an active data flow, transitioning it to {@link DataFlowStates#SUSPENDED}.
     *
     * @param flowId  process ID of the flow to suspend
     * @param reason  optional suspend reason (may be null)
     */
    public void suspend(String flowId, String reason) {
        log.info("Suspending data flow {} — reason: {}", flowId, reason);
        storeState(flowId, DataFlowStates.SUSPENDED);
    }

    /**
     * Returns the current state of a data flow.
     *
     * @param flowId process ID of the flow
     * @return current {@link DataFlowStates}; {@link DataFlowStates#FAILED} if not found
     */
    public DataFlowStates state(String flowId) {
        return activeFlows.getOrDefault(flowId, DataFlowStates.FAILED);
    }

    private void validateXrdPull(DataFlowStartMessage message) {
        var transferType = message.getTransferType();
        if (!XRD_PULL_TRANSFER_TYPE.equals(transferType)) {
            throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR,
                    "TransferType %s not supported — only Xrd-PULL is accepted".formatted(transferType));
        }
    }

    private DataFlowStatusMessage buildStatusMessage(DataFlowStates state) {
        var dataAddress = DspDataAddress.Builder.newInstance()
                .endpointType("http")
                .endpoint(dspProperties.dataFlowEndpoint())
                .build();
        return DataFlowStatusMessage.Builder.newInstance()
                .dataAddress(dataAddress)
                .state(state.toString())
                .build();
    }

    private void storeState(String processId, DataFlowStates state) {
        activeFlows.put(processId, state);
    }
}
