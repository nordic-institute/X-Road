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
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
     * provisioning run at SS init. A failure in one participant context is logged and does not
     * block the remaining contexts; a context whose creation failed is skipped in the credential
     * pass of the same tick.
     */
    public void provisionParticipant() {
        var contexts = dataspaceProvisioningService.participantContexts(true);
        if (ownerUnknown(contexts)) {
            log.debug("Data space provisioning: SS owner not yet known, skipping");
            return;
        }

        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        log.debug("Data space provisioning: authCertRegistered={}", authCertRegistered);

        var ensuredContexts = ensureContexts(contexts);

        if (!authCertRegistered) {
            log.debug("Data space provisioning: auth cert not yet REGISTERED, deferring credential request");
            return;
        }

        ensureCredentials(ensuredContexts);
    }

    private static boolean ownerUnknown(List<ParticipantContext> contexts) {
        return contexts.stream().anyMatch(context -> context.memberId() == null);
    }

    private List<ParticipantContext> ensureContexts(List<ParticipantContext> contexts) {
        List<ParticipantContext> ensured = new ArrayList<>();
        for (var context : contexts) {
            try {
                dataspaceProvisioningService.ensureParticipantContext(context.participantId(), context.kind(),
                        context.memberId());
                ensured.add(context);
            } catch (Exception e) {
                log.error("Data space provisioning: failed to ensure participant context {}, continuing with the rest",
                        context.participantId(), e);
            }
        }
        return ensured;
    }

    private void ensureCredentials(List<ParticipantContext> contexts) {
        for (var context : contexts) {
            try {
                dataspaceProvisioningService.ensureMembershipCredential(context.participantId());
            } catch (Exception e) {
                log.error("Data space provisioning: credential step failed for participant {}, continuing with the rest",
                        context.participantId(), e);
            }
        }
    }
}
