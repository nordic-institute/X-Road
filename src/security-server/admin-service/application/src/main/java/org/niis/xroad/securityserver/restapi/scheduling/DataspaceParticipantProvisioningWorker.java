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

import com.apicatalog.did.Did;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.securityserver.restapi.service.DataspaceParticipantBindingService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.CredentialStatus.ISSUED;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus.OK;

/**
 * Level-triggered provisioning worker that drives dataspace participant context provisioning
 * from real lifecycle state. One idempotent, non-blocking step is performed per tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(DataspaceParticipantProvisioningWorker.IsActive.class)
public final class DataspaceParticipantProvisioningWorker implements DataspaceParticipantProvisioningTrigger, DisposableBean {

    static final int INITIAL_DELAY_MS = 30000;
    private static final long SHUTDOWN_GRACE_SECONDS = 10;

    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceParticipantBindingService participantBindingService;

    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "dataspace-provisioning");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean runQueued = new AtomicBoolean();

    /**
     * Scheduled provisioning tick. Runs at a fixed rate; failures are non-fatal and
     * retried on the next tick.
     */
    @Scheduled(fixedRateString = "${xroad.proxy-ui-api.dataspace.provisioning-tick-ms}", initialDelay = INITIAL_DELAY_MS)
    public void scheduledProvision() {
        provisionParticipantBestEffort();
    }

    /**
     * Schedules one best-effort provisioning step on a background thread. For callers on a request
     * path: an unreachable ds-* dependency must not stall the request (each unreachable context costs
     * a full gRPC deadline), and the scheduled tick remains the convergence guarantee.
     *
     * <p>When a transaction is active around the caller, the run is deferred until that transaction
     * commits, so it never observes pre-commit state, and repeated calls within one transaction
     * schedule a single run. Runs execute one at a time on a dedicated thread; a trigger arriving
     * while a run is already queued is dropped, since that run will observe its state. Never throws:
     * scheduling itself is best-effort, logged at WARN on failure, exactly like the step it schedules.</p>
     */
    @Override
    public void provisionParticipantAsync() {
        try {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                dispatchAsync();
            } else if (!afterCommitRunScheduled()) {
                TransactionSynchronizationManager.registerSynchronization(new AfterCommitProvisioning());
            }
        } catch (Exception e) {
            log.warn("Dataspace: failed to schedule participant provisioning; the scheduled worker will "
                    + "converge on its next tick", e);
        }
    }

    private void dispatchAsync() {
        if (!runQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            dispatcher.execute(() -> {
                runQueued.set(false);
                provisionParticipantBestEffort();
            });
        } catch (RejectedExecutionException _) {
            runQueued.set(false);
            log.debug("Dataspace: provisioning dispatcher is shut down, skipping the triggered run");
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        dispatcher.shutdown();
        if (!dispatcher.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
            dispatcher.shutdownNow();
        }
    }

    private static boolean afterCommitRunScheduled() {
        return TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(AfterCommitProvisioning.class::isInstance);
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
     * Executes one idempotent provisioning step: {@link #ensure()} followed by {@link #teardown()}.
     */
    public void provisionParticipant() {
        ensure();
        teardown();
    }

    /**
     * Drives every participant context towards its converged state. A failure in one participant
     * context is logged and does not block the remaining contexts; a context whose creation failed,
     * or whose SYSTEM member-id re-anchor the identity hub has not confirmed, is skipped in the
     * credential pass of the same tick — see {@link #ensureContexts}.
     *
     * <p>A context already converged — served by the identity hub under the DID this server derives
     * today, membership credential ISSUED and, for a MEMBER, its identity OK — gets no identity hub
     * write, no bind and no credential request; only its Control Plane records are re-applied, because
     * the Control Plane has no status to read. A context of any kind whose hub DID differs from the
     * derived one is never converged: it takes the ensure pass, which refuses to touch it and reports
     * the drift instead of re-applying the stale DID. Members are
     * bound only after their participant context has been ensured, so the DID written to
     * {@code ds_participant} is one the identity hub has just confirmed or been created with. A
     * member whose context is in DID drift is left unbound and stays recoverable by correcting the
     * configuration the DID is derived from.
     */
    private void ensure() {
        var contexts = dataspaceProvisioningService.participantContexts(true);
        if (ownerUnknown(contexts)) {
            log.debug("Dataspace provisioning: SS owner not yet known, skipping");
            return;
        }
        if (!dataspaceProvisioningService.registeredAddressKnown()) {
            log.debug("Dataspace provisioning: registered address not in GlobalConf yet, skipping");
            return;
        }

        var statuses = statusesOf(contexts);
        List<ParticipantContext> nonConverged = new ArrayList<>();
        for (var context : contexts) {
            var status = statuses.get(context);
            if (converged(status)) {
                refreshControlPlane(context, status.contextDid());
            } else {
                nonConverged.add(context);
            }
        }
        if (nonConverged.isEmpty()) {
            log.debug("Dataspace provisioning: all {} participant context(s) converged, only Control Plane records re-applied",
                    contexts.size());
            return;
        }

        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        log.debug("Dataspace provisioning: authCertRegistered={}", authCertRegistered);

        var ensuredContexts = ensureContexts(nonConverged);

        participantBindingService.bindMembersIfAbsent(memberIdsOf(ensuredContexts), authCertRegistered);

        if (!authCertRegistered) {
            log.debug("Dataspace provisioning: auth cert not yet REGISTERED, deferring credential request");
            return;
        }

        ensureCredentials(ensuredContexts, statuses);
    }

    private void teardown() {
        // Deprovisioning slot for a client no longer registered on this security server; no deprovisioning logic exists yet.
    }

    private Map<ParticipantContext, ParticipantContextStatus> statusesOf(List<ParticipantContext> contexts) {
        Map<ParticipantContext, ParticipantContextStatus> statuses = new LinkedHashMap<>();
        for (var context : contexts) {
            statuses.put(context, dataspaceProvisioningService.readContextStatus(context));
        }
        return statuses;
    }

    private void refreshControlPlane(ParticipantContext context, Did did) {
        try {
            dataspaceProvisioningService.ensureControlPlaneContext(context, did);
        } catch (Exception e) {
            log.error("Dataspace provisioning: failed to re-apply Control Plane records of participant context {}, "
                    + "continuing with the rest", context.participantId(), e);
        }
    }

    private static boolean converged(ParticipantContextStatus status) {
        return status.contextDidMatchesIntended()
                && status.credentialStatus() == ISSUED
                && (status.identityStatus() == null || status.identityStatus() == OK);
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
                boolean anchorConfirmed = dataspaceProvisioningService.ensureParticipantContext(context);
                if (anchorConfirmed) {
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

    private void ensureCredentials(List<ParticipantContext> contexts, Map<ParticipantContext, ParticipantContextStatus> statuses) {
        for (var context : contexts) {
            try {
                var newStatus = dataspaceProvisioningService.ensureMembershipCredential(context);
                var previousStatus = statuses.get(context).credentialStatus();
                if (newStatus != previousStatus) {
                    log.info("Dataspace provisioning: participant {} credential {} -> {}",
                            context.participantId(), previousStatus, newStatus);
                }
            } catch (Exception e) {
                log.error("Dataspace provisioning: credential step failed for participant {}, continuing with the rest",
                        context.participantId(), e);
            }
        }
    }

    private final class AfterCommitProvisioning implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            dispatchAsync();
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
