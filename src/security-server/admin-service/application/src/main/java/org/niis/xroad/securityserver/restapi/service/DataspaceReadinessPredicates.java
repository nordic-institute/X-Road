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
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared readiness predicates used by both the DSP reconciler and the DSP status service.
 * Both callers key their logic on the same state; a single implementation prevents the two
 * from silently diverging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataspaceReadinessPredicates {

    private final GlobalConfProvider globalConfProvider;
    private final ServerConfRepository serverConfRepository;
    private final SignerRpcClient signerRpcClient;

    /**
     * Returns {@code true} if the SS has at least one authentication certificate in REGISTERED state.
     * Treats signer unavailability as "not registered" and restores the thread interrupt flag when
     * an {@link InterruptedException} is encountered.
     */
    public boolean hasRegisteredAuthCert() {
        try {
            return signerRpcClient.getTokens().stream()
                    .flatMap(t -> t.getKeyInfo().stream())
                    .filter(k -> KeyUsageInfo.AUTHENTICATION.equals(k.getUsage()))
                    .flatMap(k -> k.getCerts().stream())
                    .anyMatch(cert -> CertificateInfo.STATUS_REGISTERED.equals(cert.getStatus()));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.debug("Data space: could not read signer state, treating auth cert as not registered", e);
            return false;
        }
    }

    /**
     * Returns {@code true} if the management-request subsystem is registered as a client on this SS.
     * Treats globalconf or serverconf unavailability as "not registered".
     *
     * @param serverConf the already-loaded server configuration entity
     */
    public boolean isManagementSubsystemRegistered(ServerConfEntity serverConf) {
        ClientId managementService = globalConfProvider.getManagementRequestService();
        if (managementService == null) {
            return false;
        }
        return serverConf.getClients().stream()
                .anyMatch(client -> managementService.equals(client.getIdentifier())
                        && Client.STATUS_REGISTERED.equals(client.getClientStatus()));
    }

    /**
     * Returns {@code true} if the management-request subsystem is registered as a client on this SS.
     * Loads server configuration from the repository inside the active transaction.
     * Treats globalconf or serverconf unavailability as "not registered".
     */
    @Transactional(readOnly = true)
    public boolean isManagementSubsystemRegistered() {
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
            log.debug("Data space: could not determine management subsystem registration, treating as not registered", e);
            return false;
        }
    }
}
