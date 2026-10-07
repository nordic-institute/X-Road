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
import org.eclipse.edc.spi.constants.CoreConstants;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

/**
 * Manager for active data flows in the X-Road proxy data plane.
 * <p>
 * Encapsulates the {@code Xrd-PULL} semantics: when a {@link DataFlowStartMessage} or
 * {@link DataFlowPrepareMessage} arrives, the proxy fabricates a {@link DspDataAddress}
 * advertising the provider serverproxy endpoint and returns it wrapped in a
 * {@link DataFlowStatusMessage}. The serverproxy endpoint (mTLS) is the dataplane —
 * consumers send signed X-Road requests directly to it via the existing PKI pipeline.
 * <p>
 * Flow state is tracked in the {@link DataFlowStateStore}, shared by every proxy node of a
 * clustered Security Server: a signaling call handled by any node reads and updates the same
 * record, so a flow's state is never node-local.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class XRoadDataPlaneManager {

    /** Full transfer-type string for Xrd-PULL flows (matches the wire value). */
    static final String XRD_PULL_TRANSFER_TYPE = "Xrd-PULL";

    private static final String AUTHORIZATION_PROPERTY = CoreConstants.EDC_NAMESPACE + "authorization";
    private static final String AUTH_TYPE_PROPERTY = CoreConstants.EDC_NAMESPACE + "authType";
    private static final String BEARER_AUTH_TYPE = "bearer";

    private final DataPlaneServerProperties dspProperties;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final ProxyProperties proxyProperties;
    private final DataFlowStateStore flowStateStore;
    private final AgreementTokenIssuer agreementTokenIssuer;

    /**
     * Handles a prepare request. For {@code Xrd-PULL} there is no async provisioning —
     * the response is built immediately using the serverproxy endpoint.
     *
     * @param message incoming prepare message
     * @return status message with {@code dataAddress.endpoint} set to the provider serverproxy endpoint
     */
    public DataFlowStatusMessage prepare(DataFlowPrepareMessage message) {
        log.info("Preparing data flow for process {}", message.getProcessId());
        applyTransition(message.getProcessId(), DataFlowTransition.PREPARE);
        return buildStatusMessage(DataFlowStates.PROVISIONED, message.getAgreementId());
    }

    /**
     * Handles a start request, preserving {@code Xrd-PULL} semantics: validates the transfer type,
     * fabricates a {@link DspDataAddress} advertising the provider serverproxy endpoint, and returns it
     * wrapped in a {@link DataFlowStatusMessage}. This is also how a suspended flow resumes: EDC's control
     * plane re-sends start and the flow returns to {@link DataFlowStates#STARTED}.
     *
     * @param message incoming start message
     * @return status message with {@code dataAddress.endpoint} set to the provider serverproxy endpoint
     * @throws XrdRuntimeException if the transfer type is not {@code Xrd-PULL} or the flow is completed or terminated
     */
    public DataFlowStatusMessage start(DataFlowStartMessage message) {
        validateXrdPull(message);
        log.info("Starting Xrd-PULL data flow for process {}", message.getProcessId());
        applyTransition(message.getProcessId(), DataFlowTransition.START);
        return buildStatusMessage(DataFlowStates.STARTED, message.getAgreementId());
    }

    /**
     * Handles the started notification this data plane receives from its own control plane: the transfer
     * has started and, for {@code Xrd-PULL}, data may now be pulled through the proxy. The flow lands on
     * {@link DataFlowStates#STARTED}.
     *
     * @param flowId process ID of the flow that started
     * @return status message with state {@link DataFlowStates#STARTED}
     */
    public DataFlowStatusMessage started(String flowId) {
        log.info("Data flow {} started", flowId);
        applyTransition(flowId, DataFlowTransition.NOTIFY_STARTED);
        return DataFlowStatusMessage.Builder.newInstance()
                .state(DataFlowStates.STARTED.toString())
                .build();
    }

    /**
     * Completes a data flow, transitioning it to {@link DataFlowStates#COMPLETED}.
     *
     * @param flowId process ID of the flow to complete
     * @throws XrdRuntimeException if the flow is not {@link DataFlowStates#STARTED} or already completed
     */
    public void completed(String flowId) {
        log.info("Completing data flow {}", flowId);
        applyTransition(flowId, DataFlowTransition.COMPLETE);
    }

    /**
     * Terminates an active data flow, transitioning it to {@link DataFlowStates#TERMINATED}.
     *
     * @param flowId process ID of the flow to terminate
     * @throws XrdRuntimeException if the flow is unknown or already in a terminal state
     */
    public void terminate(String flowId) {
        log.info("Terminating data flow {}", flowId);
        applyTransition(flowId, DataFlowTransition.TERMINATE);
    }

    /**
     * Suspends an active data flow, transitioning it to {@link DataFlowStates#SUSPENDED}.
     *
     * @param flowId  process ID of the flow to suspend
     * @param reason  optional suspend reason (may be null)
     * @throws XrdRuntimeException if the flow is not {@link DataFlowStates#STARTED} or already suspended
     */
    public void suspend(String flowId, String reason) {
        log.info("Suspending data flow {} — reason: {}", flowId, reason);
        applyTransition(flowId, DataFlowTransition.SUSPEND);
    }

    /**
     * Returns the current state of a data flow.
     *
     * @param flowId process ID of the flow
     * @return current {@link DataFlowStates}; {@link DataFlowStates#FAILED} if not found
     */
    public DataFlowStates state(String flowId) {
        return flowStateStore.find(flowId).orElse(DataFlowStates.FAILED);
    }

    private void validateXrdPull(DataFlowStartMessage message) {
        var transferType = message.getTransferType();
        if (!XRD_PULL_TRANSFER_TYPE.equals(transferType)) {
            throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR,
                    "TransferType %s not supported — only Xrd-PULL is accepted".formatted(transferType));
        }
    }

    private DataFlowStatusMessage buildStatusMessage(DataFlowStates state, String agreementId) {
        var protocol = proxyProperties.sslEnabled() ? "https" : "http";
        var endpoint = resolveServerproxyEndpoint(protocol);
        var dataAddressBuilder = DspDataAddress.Builder.newInstance()
                .endpointType(protocol)
                .endpoint(endpoint);
        agreementTokenIssuer.issueToken(agreementId).ifPresent(token -> dataAddressBuilder
                .property(AUTHORIZATION_PROPERTY, token)
                .property(AUTH_TYPE_PROPERTY, BEARER_AUTH_TYPE));
        return DataFlowStatusMessage.Builder.newInstance()
                .dataAddress(dataAddressBuilder.build())
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

    /**
     * Applies {@code transition} through the shared store, which validates and persists it as one operation;
     * a retried signal that finds the flow already in its target state is answered like the original.
     */
    private void applyTransition(String processId, DataFlowTransition transition) {
        flowStateStore.apply(processId, transition);
    }
}
