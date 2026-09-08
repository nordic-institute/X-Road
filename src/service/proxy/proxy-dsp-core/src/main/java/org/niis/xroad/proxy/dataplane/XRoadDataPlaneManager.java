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
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory manager for active data flows in the X-Road proxy data plane.
 * <p>
 * Encapsulates the {@code Xrd-PULL} semantics: when a {@link DataFlowStartMessage} or
 * {@link DataFlowPrepareMessage} arrives, the proxy fabricates a {@link DspDataAddress}
 * advertising the provider serverproxy endpoint and returns it wrapped in a
 * {@link DataFlowStatusMessage}. The serverproxy endpoint (mTLS) is the dataplane —
 * consumers send signed X-Road requests directly to it via the existing PKI pipeline.
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
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final ProxyProperties proxyProperties;
    private final ConcurrentHashMap<String, DataFlowStates> activeFlows = new ConcurrentHashMap<>();

    /**
     * Handles a prepare request. For {@code Xrd-PULL} there is no async provisioning —
     * the response is built immediately using the serverproxy endpoint.
     *
     * @param message incoming prepare message
     * @return status message with {@code dataAddress.endpoint} set to the provider serverproxy endpoint
     */
    public DataFlowStatusMessage prepare(DataFlowPrepareMessage message) {
        log.info("Preparing data flow for process {}", message.getProcessId());
        storeState(message.getProcessId(), DataFlowStates.PROVISIONED);
        return buildStatusMessage(DataFlowStates.PROVISIONED);
    }

    /**
     * Handles a start request, preserving {@code Xrd-PULL} semantics: validates the transfer type,
     * fabricates a {@link DspDataAddress} advertising the provider serverproxy endpoint, and returns it
     * wrapped in a {@link DataFlowStatusMessage}.
     *
     * @param message incoming start message
     * @return status message with {@code dataAddress.endpoint} set to the provider serverproxy endpoint
     * @throws XrdRuntimeException if the transfer type is not {@code Xrd-PULL}
     */
    public DataFlowStatusMessage start(DataFlowStartMessage message) {
        validateXrdPull(message);
        log.info("Starting Xrd-PULL data flow for process {}", message.getProcessId());
        storeState(message.getProcessId(), DataFlowStates.STARTED);
        return buildStatusMessage(DataFlowStates.STARTED);
    }

    /**
     * Handles the consumer-side started notification: the provider control plane has started
     * the transfer and, for {@code Xrd-PULL}, data may now be pulled through the proxy.
     *
     * @param flowId process ID of the flow that started
     * @return status message with state {@link DataFlowStates#STARTED}
     */
    public DataFlowStatusMessage started(String flowId) {
        log.info("Data flow {} started", flowId);
        storeState(flowId, DataFlowStates.STARTED);
        return DataFlowStatusMessage.Builder.newInstance()
                .state(DataFlowStates.STARTED.toString())
                .build();
    }

    /**
     * Completes a data flow, transitioning it to {@link DataFlowStates#COMPLETED}.
     *
     * @param flowId process ID of the flow to complete
     */
    public void completed(String flowId) {
        log.info("Completing data flow {}", flowId);
        storeState(flowId, DataFlowStates.COMPLETED);
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
        var protocol = proxyProperties.sslEnabled() ? "https" : "http";
        var endpoint = resolveServerproxyEndpoint(protocol);
        var dataAddress = DspDataAddress.Builder.newInstance()
                .endpointType(protocol)
                .endpoint(endpoint)
                .build();
        return DataFlowStatusMessage.Builder.newInstance()
                .dataAddress(dataAddress)
                .state(state.toString())
                .build();
    }

    private String resolveServerproxyEndpoint(String protocol) {
        try {
            var ownAddress = globalConfProvider.getSecurityServerAddress(serverConfProvider.getIdentifier());
            if (ownAddress != null && !ownAddress.isBlank()) {
                var endpoint = "%s://%s:%d".formatted(protocol, ownAddress, proxyProperties.serverProxyPort());
                log.debug("Advertising dataplane serverproxy endpoint {}", endpoint);
                return endpoint;
            }
            log.warn("Own security-server address is blank; falling back to configured serverproxy endpoint");
        } catch (Exception e) {
            log.warn("Could not resolve own security-server address; falling back to configured serverproxy endpoint", e);
        }
        return dspProperties.serverproxyEndpoint();
    }

    private void storeState(String processId, DataFlowStates state) {
        activeFlows.put(processId, state);
    }
}
