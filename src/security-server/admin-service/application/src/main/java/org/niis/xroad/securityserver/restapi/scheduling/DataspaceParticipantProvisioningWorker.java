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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.securityserver.restapi.service.DataspaceParticipantBindingService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.TombstonedParticipant;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Level-triggered provisioning worker that drives dataspace participant context provisioning and
 * teardown from real lifecycle state. One idempotent, non-blocking step is performed per tick; no
 * success is cached, so convergence is re-derived every tick from serverconf and the binding table.
 * Every tick — whether from {@link #scheduledProvision()} or {@link #provisionParticipantAsync()} —
 * runs through {@link #provisionParticipantBestEffort()}, which is {@code synchronized} so at most
 * one tick converges at a time within this node.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(DataspaceParticipantProvisioningWorker.IsActive.class)
public final class DataspaceParticipantProvisioningWorker implements DataspaceParticipantProvisioningTrigger {

    static final int JOB_REPEAT_INTERVAL_MS = 30000;
    static final int INITIAL_DELAY_MS = 30000;

    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceParticipantBindingService participantBindingService;

    /**
     * Scheduled provisioning tick. Runs at a fixed rate; failures are non-fatal and
     * retried on the next tick.
     */
    @Scheduled(fixedRate = JOB_REPEAT_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void scheduledProvision() {
        provisionParticipantBestEffort();
    }

    /**
     * Runs one best-effort provisioning step on a background thread. For callers on a request path:
     * an unreachable ds-* dependency must not stall the request (each unreachable context costs a
     * full gRPC deadline), and the scheduled tick remains the convergence guarantee.
     */
    @Override
    public void provisionParticipantAsync() {
        CompletableFuture.runAsync(this::provisionParticipantBestEffort);
    }

    /**
     * Executes one provisioning step, logging and swallowing any failure. Used by callers that must
     * not fail on a provisioning problem: the scheduled tick and the eager run right after security
     * server initialization.
     */
    public synchronized void provisionParticipantBestEffort() {
        try {
            provisionParticipant();
        } catch (Exception e) {
            log.error("Dataspace participant provisioning failed; the scheduled worker will converge "
                    + "once the dependency recovers", e);
        }
    }

    /**
     * Executes one idempotent provisioning and teardown step. A failure in one participant context
     * (or one tombstone) is logged and does not block the remaining ones; a context whose creation
     * failed, or whose SYSTEM member-id re-anchor the identity hub has not confirmed, is skipped in
     * the credential pass of the same tick — see {@link #ensureContexts}.
     *
     * <p>Teardown runs first and is gated on nothing: a decommissioned binding must keep converging
     * toward absence even while the owner or the registered address is unknown.
     *
     * <p>Members are bound only after their participant context has been ensured, so the DID written
     * to {@code ds_participant} is one the identity hub has just confirmed or been created with. A
     * member whose context is in DID drift is left unbound and stays recoverable by correcting the
     * configuration the DID is derived from.
     */
    public void provisionParticipant() {
        teardownDecommissioned();

        var contexts = dataspaceProvisioningService.participantContexts(true);
        if (ownerUnknown(contexts)) {
            log.debug("Dataspace provisioning: SS owner not yet known, skipping");
            return;
        }
        if (!dataspaceProvisioningService.registeredAddressKnown()) {
            log.debug("Dataspace provisioning: registered address not in GlobalConf yet, skipping");
            return;
        }

        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        log.debug("Dataspace provisioning: authCertRegistered={}", authCertRegistered);

        var ensuredContexts = ensureContexts(contexts);

        participantBindingService.bindMembersIfAbsent(memberIdsOf(ensuredContexts), authCertRegistered);

        if (!authCertRegistered) {
            log.debug("Dataspace provisioning: auth cert not yet REGISTERED, deferring credential request");
            return;
        }

        ensureCredentials(ensuredContexts);
    }

    private static List<ClientId> memberIdsOf(List<ParticipantContext> contexts) {
        return contexts.stream()
                .filter(context -> context.kind() == ParticipantKind.MEMBER)
                .map(ParticipantContext::memberId)
                .toList();
    }

    private static boolean ownerUnknown(List<ParticipantContext> contexts) {
        return contexts.stream().anyMatch(context -> context.memberId() == null);
    }

    /**
     * Converges every decommissioned binding one step closer to absence. A failure tearing down one
     * tombstone is logged and does not block the rest; the row (and whichever steps did not complete)
     * is left for the next tick.
     */
    private void teardownDecommissioned() {
        List<TombstonedParticipant> tombstones = dataspaceProvisioningService.decommissionedParticipants();
        for (var tombstone : tombstones) {
            log.debug("Dataspace provisioning: tearing down tombstoned participant {} (row id {})",
                    tombstone.participantContextId(), tombstone.id());
            try {
                dataspaceProvisioningService.teardownParticipant(tombstone);
            } catch (Exception e) {
                log.error("Dataspace provisioning: failed to tear down participant {}, continuing with the rest",
                        tombstone.participantContextId(), e);
            }
        }
    }

    /**
     * Ensures every context, then returns only those eligible for the credential pass in this tick:
     * the ensure call must not have thrown, and {@link DataspaceProvisioningService#ensureParticipantContext}
     * must report it safe to issue a credential. For a SYSTEM context that means the identity hub has
     * confirmed the member-id re-anchor to the current owner; while unconfirmed, the context itself is
     * still created/updated as usual, only its credential request is deferred to a later tick.
     */
    private List<ParticipantContext> ensureContexts(List<ParticipantContext> contexts) {
        List<ParticipantContext> ensured = new ArrayList<>();
        for (var context : contexts) {
            try {
                if (dataspaceProvisioningService.ensureParticipantContext(context)) {
                    ensured.add(context);
                } else {
                    log.debug("Dataspace provisioning: deferring credential issuance for participant {} until the "
                            + "SYSTEM member-id re-anchor is confirmed", context.participantId());
                }
            } catch (Exception e) {
                log.error("Dataspace provisioning: failed to ensure participant context {}, continuing with the rest",
                        context.participantId(), e);
            }
        }
        return ensured;
    }

    private void ensureCredentials(List<ParticipantContext> contexts) {
        for (var context : contexts) {
            try {
                dataspaceProvisioningService.ensureMembershipCredential(context);
            } catch (Exception e) {
                log.error("Dataspace provisioning: credential step failed for participant {}, continuing with the rest",
                        context.participantId(), e);
            }
        }
    }

    static class IsActive implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isActive();
        }

        static boolean isActive() {
            return !NodeProperties.isSecondaryNode();
        }
    }
}
