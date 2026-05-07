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
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowProvisionMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowResponseMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowTerminateMessage;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory manager for active data flows in the X-Road proxy data plane.
 * <p>
 * Encapsulates the {@code Xrd-PULL} semantics: when a {@link DataFlowStartMessage} arrives,
 * the proxy fabricates a {@link DataAddress} pointing to its own signaling endpoint and
 * returns it wrapped in a {@link DataFlowResponseMessage}. No real data pipeline is started —
 * the proxy itself serves data on subsequent HTTP calls.
 * <p>
 * Flow state is tracked in-memory via a {@link ConcurrentHashMap}. The CP terminates flows
 * on completion, keeping the map bounded.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class XRoadDataPlaneManager {

    /** Transfer type destination for Xrd-PULL flows. */
    static final String XRD_PULL_DESTINATION = "Xrd";

    private final ProxyDspProperties dspProperties;
    private final ConcurrentHashMap<String, DataFlowStates> activeFlows = new ConcurrentHashMap<>();

    /**
     * Handles a provision request. For {@code Xrd-PULL} there is no async provisioning —
     * the response is built immediately using the same logic as {@link #start}.
     *
     * @param message incoming provision message
     * @return response with {@code dataAddress.endpoint} set to the proxy data flow endpoint
     */
    public DataFlowResponseMessage prepare(DataFlowProvisionMessage message) {
        log.info("Preparing data flow for process {}", message.getProcessId());
        var dataAddress = buildXrdPullDataAddress();
        storeState(message.getProcessId(), DataFlowStates.PROVISIONED);
        return DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(dataAddress)
                .build();
    }

    /**
     * Handles a start request, preserving {@code Xrd-PULL} semantics from the legacy
     * {@code ProxyDataPlaneRegistry.DataplaneOnStart}: validates the transfer type,
     * fabricates a {@code DataAddress(type=http, endpoint=dataFlowEndpoint)}, and returns
     * it wrapped in a {@link DataFlowResponseMessage}.
     *
     * @param message incoming start message
     * @return response with {@code dataAddress.endpoint} set to the proxy data flow endpoint
     * @throws XrdRuntimeException if the transfer type is not {@code Xrd-PULL}
     */
    public DataFlowResponseMessage start(DataFlowStartMessage message) {
        validateXrdPull(message);
        log.info("Starting Xrd-PULL data flow for process {}", message.getProcessId());
        var dataAddress = buildXrdPullDataAddress();
        storeState(message.getProcessId(), DataFlowStates.STARTED);
        return DataFlowResponseMessage.Builder.newInstance()
                .dataAddress(dataAddress)
                .build();
    }

    /**
     * Terminates an active data flow, transitioning it to {@link DataFlowStates#TERMINATED}.
     *
     * @param flowId  process ID of the flow to terminate
     * @param message termination message (reason is logged)
     */
    public void terminate(String flowId, DataFlowTerminateMessage message) {
        log.info("Terminating data flow {} — reason: {}", flowId, message.getReason());
        storeState(flowId, DataFlowStates.TERMINATED);
    }

    /**
     * Suspends an active data flow, transitioning it to {@link DataFlowStates#SUSPENDED}.
     *
     * @param flowId  process ID of the flow to suspend
     * @param message suspend message (reason is logged)
     */
    public void suspend(String flowId, DataFlowSuspendMessage message) {
        log.info("Suspending data flow {} — reason: {}", flowId, message.getReason());
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
        if (transferType == null || !XRD_PULL_DESTINATION.equals(transferType.destinationType())) {
            var actual = transferType != null ? transferType.asString() : "null";
            throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR,
                    "TransferType %s not supported — only Xrd-PULL is accepted".formatted(actual));
        }
    }

    private DataAddress buildXrdPullDataAddress() {
        return DataAddress.Builder.newInstance()
                .type("http")
                .property("endpoint", dspProperties.dataFlowEndpoint())
                .build();
    }

    private void storeState(String processId, DataFlowStates state) {
        activeFlows.put(processId, state);
    }
}
