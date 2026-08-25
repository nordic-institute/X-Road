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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    static final int FULL_RECONCILE_EVERY_N_TICKS = 20;

    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final ScheduledJobHelper scheduledJobHelper;
    private final DataspaceReadinessPredicates readinessPredicates;

    private final AtomicInteger tickCount = new AtomicInteger();

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

    private final Set<String> ensuredParticipantIds = ConcurrentHashMap.newKeySet();
    private final Set<String> issuedCredentialParticipantIds = ConcurrentHashMap.newKeySet();

    /**
     * Executes one idempotent provisioning step. Safe to call directly for an eager first
     * provisioning run at SS init. A failure in one participant context is logged and does not
     * block the remaining contexts; a context whose creation failed is skipped in the credential
     * pass of the same tick.
     *
     * <p>Contexts fully created and credentials in terminal ISSUED state are remembered in-process,
     * so a steady-state tick makes no remote calls for them. The caches reset on restart and every
     * {@link #FULL_RECONCILE_EVERY_N_TICKS} ticks, so out-of-band backend state loss (e.g. an
     * identity-hub redeployed with an empty store) is reconciled within a bounded window by the
     * idempotent creation, which also re-verifies each member's pinned identity.
     */
    public void provisionParticipant() {
        if (tickCount.getAndIncrement() % FULL_RECONCILE_EVERY_N_TICKS == 0) {
            ensuredParticipantIds.clear();
            issuedCredentialParticipantIds.clear();
        }

        ServerConfEntity serverConf = loadServerConf();
        if (serverConf == null) {
            log.debug("Data space provisioning: SS not yet initialized, skipping");
            return;
        }

        if (serverConf.getOwner() == null) {
            log.debug("Data space provisioning: server owner not set, skipping");
            return;
        }

        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        log.debug("Data space provisioning: authCertRegistered={}", authCertRegistered);

        List<DataspaceProvisioningService.ParticipantContext> ensuredContexts = new ArrayList<>();
        for (var context : dataspaceProvisioningService.participantContexts(true)) {
            if (ensuredParticipantIds.contains(context.participantId())) {
                ensuredContexts.add(context);
                continue;
            }
            try {
                dataspaceProvisioningService.ensureParticipantContext(context.participantId(), context.kind(),
                        context.memberId());
                ensuredParticipantIds.add(context.participantId());
                ensuredContexts.add(context);
            } catch (Exception e) {
                log.error("Data space provisioning: failed to ensure participant context {}, continuing with the rest",
                        context.participantId(), e);
            }
        }

        if (!authCertRegistered) {
            log.debug("Data space provisioning: auth cert not yet REGISTERED, deferring credential request");
            return;
        }

        for (var context : ensuredContexts) {
            var participantId = context.participantId();
            if (issuedCredentialParticipantIds.contains(participantId)) {
                continue;
            }
            try {
                var status = dataspaceProvisioningService.ensureMembershipCredential(participantId);
                if (DataspaceProvisioningService.STATUS_ISSUED.equals(status)) {
                    issuedCredentialParticipantIds.add(participantId);
                }
            } catch (Exception e) {
                log.error("Data space provisioning: credential step failed for participant {}, continuing with the rest",
                        participantId, e);
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
