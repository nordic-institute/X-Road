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
package org.niis.xroad.securityserver.restapi.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Level-triggered provisioning worker that drives data space participant context provisioning
 * from real lifecycle state. One idempotent, non-blocking step is performed per tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataspaceParticipantProvisioningWorker {

    static final int JOB_REPEAT_INTERVAL_MS = 30000;
    static final int INITIAL_DELAY_MS = 30000;

    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final ScheduledJobHelper scheduledJobHelper;
    private final DataspaceReadinessPredicates readinessPredicates;

    /**
     * Scheduled provisioning tick. Runs at a fixed rate; failures are non-fatal and
     * retried on the next tick.
     */
    @Scheduled(fixedRate = JOB_REPEAT_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void scheduledProvision() {
        try {
            provisionParticipant();
        } catch (Exception e) {
            log.error("Data space participant provisioning tick failed", e);
        }
    }

    /**
     * Executes one idempotent provisioning step. Safe to call directly for an eager first
     * provisioning run at SS init; in that context a failure propagates to the caller.
     */
    public void provisionParticipant() {
        ServerConfEntity serverConf = loadServerConf();
        if (serverConf == null) {
            log.debug("Data space provisioning: SS not yet initialized, skipping");
            return;
        }

        var owner = serverConf.getOwner();
        if (owner == null) {
            log.debug("Data space provisioning: server owner not set, skipping");
            return;
        }
        var ownerId = owner.getIdentifier();
        var ownerMemberIdSlashForm = "%s/%s/%s".formatted(ownerId.getXRoadInstance(), ownerId.getMemberClass(), ownerId.getMemberCode());

        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        log.debug("Data space provisioning: authCertRegistered={}", authCertRegistered);

        for (var participantId : dataspaceProvisioningService.participantContextIds(true)) {
            dataspaceProvisioningService.ensureParticipantContext(participantId, ownerMemberIdSlashForm);
        }

        if (!authCertRegistered) {
            log.debug("Data space provisioning: auth cert not yet REGISTERED, deferring credential request");
            return;
        }

        for (var participantId : dataspaceProvisioningService.participantContextIds(true)) {
            var status = dataspaceProvisioningService.readCredentialStatus(participantId);
            if (DataspaceProvisioningService.STATUS_ISSUED.equals(status)) {
                log.debug("Data space provisioning: participant {} credential already ISSUED", participantId);
            } else if (DataspaceProvisioningService.STATUS_PENDING.equals(status)) {
                log.debug("Data space provisioning: participant {} credential PENDING, waiting for next tick", participantId);
            } else {
                log.info("Data space provisioning: submitting credential request for participant {}", participantId);
                dataspaceProvisioningService.submitCredentialRequest(participantId);
            }
        }
    }

    private ServerConfEntity loadServerConf() {
        try {
            return scheduledJobHelper.getServerConf();
        } catch (XrdRuntimeException e) {
            return null;
        }
    }
}
