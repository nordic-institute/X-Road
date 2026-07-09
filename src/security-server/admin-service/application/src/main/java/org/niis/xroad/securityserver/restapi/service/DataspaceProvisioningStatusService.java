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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only aggregator for data space provisioning status, consumed by the status REST endpoint.
 * Mirrors the precondition observations of {@link org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantReconciler}
 * without triggering any provisioning action.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceProvisioningStatusService {

    private final AdminServiceProperties adminServiceProperties;
    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final ServerConfRepository serverConfRepository;
    private final GlobalConfProvider globalConfProvider;
    private final SignerRpcClient signerRpcClient;

    /**
     * Returns a snapshot of the current provisioning status.
     * When dataspace is disabled ({@code dataspace.enabled=false}) the returned record carries
     * {@code enabled=false} and empty context list; no gRPC calls are made.
     */
    @Transactional(readOnly = true)
    public DataspaceStatus readStatus() {
        if (!adminServiceProperties.getDataspace().isEnabled()) {
            return new DataspaceStatus(false, false, List.of());
        }

        boolean authCertRegistered = hasRegisteredAuthCert();
        boolean managementRegistered = isManagementSubsystemRegistered();
        List<ParticipantContextStatus> contextStatuses =
                dataspaceProvisioningService.readParticipantContextStatuses(managementRegistered);

        return new DataspaceStatus(true, authCertRegistered, contextStatuses);
    }

    /**
     * Snapshot of the SS data space provisioning status at a point in time.
     *
     * @param enabled             whether dataspace is enabled on this SS
     * @param authCertRegistered  whether the SS auth cert is in REGISTERED state (Signer read)
     * @param participantContexts per-context provisioning statuses; empty when disabled
     */
    public record DataspaceStatus(
            boolean enabled,
            boolean authCertRegistered,
            List<ParticipantContextStatus> participantContexts
    ) {
    }

    private boolean isManagementSubsystemRegistered() {
        try {
            ClientId managementService = globalConfProvider.getManagementRequestService();
            if (managementService == null) {
                return false;
            }
            ServerConfEntity serverConf = serverConfRepository.getServerConf();
            return serverConf.getClients().stream()
                    .anyMatch(client -> managementService.equals(client.getIdentifier())
                            && Client.STATUS_REGISTERED.equals(client.getClientStatus()));
        } catch (XrdRuntimeException e) {
            log.debug("Data space status: could not determine management subsystem registration, treating as not registered", e);
            return false;
        }
    }

    private boolean hasRegisteredAuthCert() {
        try {
            return signerRpcClient.getTokens().stream()
                    .flatMap(t -> t.getKeyInfo().stream())
                    .filter(k -> KeyUsageInfo.AUTHENTICATION.equals(k.getUsage()))
                    .flatMap(k -> k.getCerts().stream())
                    .anyMatch(cert -> CertificateInfo.STATUS_REGISTERED.equals(cert.getStatus()));
        } catch (Exception e) {
            log.debug("Data space status: could not read signer state, treating auth cert as not registered", e);
            return false;
        }
    }
}
