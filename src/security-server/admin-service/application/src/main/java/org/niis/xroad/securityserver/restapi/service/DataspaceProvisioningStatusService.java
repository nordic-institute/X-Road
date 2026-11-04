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

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only aggregator for data space provisioning status, consumed by the status REST endpoint.
 * Mirrors the precondition observations of {@link DataspaceParticipantProvisioningWorker}
 * without triggering any provisioning action.
 */
@Service
@RequiredArgsConstructor
public class DataspaceProvisioningStatusService {

    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;
    private final DsParticipantRepository dsParticipantRepository;

    /**
     * Returns a snapshot of the current provisioning status. Deliberately not transactional:
     * only the context enumeration and the SYSTEM binding row read touch the database (each in its
     * own transaction), while the per-context status reads are blocking gRPC calls that must not hold a pooled
     * DB connection.
     */
    public DataspaceStatus readStatus() {
        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        List<ParticipantContextStatus> contextStatuses = dataspaceProvisioningService.participantContexts().stream()
                .map(dataspaceProvisioningService::readContextStatus)
                .toList();

        return new DataspaceStatus(true, authCertRegistered, readServerStatus(), contextStatuses);
    }

    private ServerStatus readServerStatus() {
        var system = dsParticipantRepository.findSystemParticipant()
                .filter(row -> row.getState() == ParticipantState.ACTIVE);
        var state = system.map(DsParticipantEntity::getPublicationState).orElse(PublicationState.NOT_SENT);
        return new ServerStatus(readinessPredicates.isDataspaceReady(), didAuthority.dspBaseUrlIfValid().orElse(null),
                state,
                system.map(DsParticipantEntity::getPublicationReason).orElse(null));
    }

    /**
     * Snapshot of the SS data space provisioning status at a point in time.
     *
     * @param enabled             always {@code true}; kept for API backward compatibility
     * @param authCertRegistered  whether the SS auth cert is in REGISTERED state (Signer read)
     * @param server              server-level status, including the publication of the SYSTEM values
     * @param participantContexts per-context provisioning statuses
     */
    public record DataspaceStatus(
            boolean enabled,
            boolean authCertRegistered,
            ServerStatus server,
            List<ParticipantContextStatus> participantContexts
    ) {
    }

    /**
     * Server-level status.
     *
     * @param dataspaceReady    whether globalconf lists a credential issuer for this instance
     * @param dspBaseUrl        the DSP base URL, {@code null} while the registered address is unknown or invalid
     * @param publicationState  publication state of the SYSTEM values
     * @param publicationReason reason for the publication state, {@code null} when there is none
     */
    public record ServerStatus(
            boolean dataspaceReady,
            @Nullable String dspBaseUrl,
            PublicationState publicationState,
            @Nullable String publicationReason
    ) {
    }
}
