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
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.spring.SpringConditionConfig;
import org.niis.xroad.securityserver.restapi.service.DataspaceParticipantBindingService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.TombstonedParticipant;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus.REJECTED;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus.UNBOUND;

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
public final class DataspaceParticipantProvisioningWorker implements DataspaceParticipantProvisioningTrigger, DisposableBean {

    static final int INITIAL_DELAY_MS = 30000;
    private static final long SHUTDOWN_GRACE_SECONDS = 10;

    private static final Logger CYCLE_LOG =
            LoggerFactory.getLogger(DataspaceParticipantProvisioningWorker.class.getName() + ".cycles");

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
     * Executes one idempotent provisioning step: {@link #teardown()} followed by {@link #ensure()}.
     *
     * <p>Teardown runs first and is gated on nothing: a decommissioned binding must keep converging
     * toward absence even while the owner or the registered address is unknown.
     *
     * <p>Whether this call returns normally or throws, one cycle summary line is logged to the
     * dedicated {@code .cycles} logger at DEBUG, with the cycle's start timestamp, its duration and
     * the number of participant contexts {@link #ensure()} reached before the cycle ended ({@code -1}
     * when it never returned).
     */
    public void provisionParticipant() {
        var start = Instant.now();
        int contexts = -1;
        try {
            teardown();
            contexts = ensure();
        } finally {
            CYCLE_LOG.debug("Dataspace provisioning: cycle summary cycleStart={} durationMs={} contexts={}",
                    start, Duration.between(start, Instant.now()).toMillis(), contexts);
        }
    }

    /**
     * Drives every participant context towards its converged state. A failure in one participant
     * context is logged and does not block the remaining contexts; a context whose creation failed,
     * or whose SYSTEM member-id re-anchor the identity hub has not confirmed, is skipped in the
     * credential pass of the same tick — see {@link #ensureContexts}.
     *
     * <p>A context already converged — served by the identity hub under the DID this server derives
     * today, membership credential ISSUED and its identity OK — gets no identity hub
     * write, no bind and no credential request; only its Control Plane records are re-applied, because
     * the Control Plane has no status to read. A context of any kind whose hub DID differs from the
     * derived one is never converged: it takes the ensure pass, which refuses to touch it and reports
     * the drift instead of re-applying the stale DID. A context whose identity hub state could not be
     * read is left for the next tick, rather than spending a second identity hub call on it.
     *
     * <p>Contexts are bound first and provisioned second: no context, SYSTEM included, is provisioned
     * without an active binding row. Only the binding of unbound contexts waits until dataspace
     * readiness holds and this server has a registered authentication certificate, which for SYSTEM is
     * the approval of its registration; a context that already holds an active binding row is
     * provisioned regardless, so it recovers from a lost hub or Control Plane context. A context whose
     * identity is drifted, unreadable or rejected is neither bound nor provisioned. A context that cannot be
     * bound is skipped and retried on the next tick.
     */
    private int ensure() {
        var contexts = dataspaceProvisioningService.participantContexts();
        int contextCount = contexts.size();
        if (ownerUnknown(contexts)) {
            log.debug("Dataspace provisioning: SS owner not yet known, skipping");
            return contextCount;
        }
        if (!dataspaceProvisioningService.registeredAddressKnown()) {
            log.debug("Dataspace provisioning: registered address not in GlobalConf yet, skipping");
            return contextCount;
        }

        var statuses = statusesOf(contexts);
        List<ParticipantContext> nonConverged = new ArrayList<>();
        for (var context : contexts) {
            var status = statuses.get(context);
            if (converged(status)) {
                refreshControlPlane(context, status.contextDid());
            } else if (status.contextDidUnreadable()) {
                log.debug("Dataspace provisioning: identity hub state of participant context {} unreadable, retrying next tick",
                        context.participantId());
            } else {
                nonConverged.add(context);
            }
        }
        if (nonConverged.isEmpty()) {
            log.debug("Dataspace provisioning: all {} participant context(s) converged, only Control Plane records re-applied",
                    contexts.size());
            return contextCount;
        }

        boolean authCertRegistered = readinessPredicates.hasRegisteredAuthCert();
        boolean dataspaceReady = readinessPredicates.isDataspaceReady();
        log.debug("Dataspace provisioning: dataspaceReady={} authCertRegistered={}", dataspaceReady, authCertRegistered);

        var provisionable = provisionableContexts(nonConverged, statuses, dataspaceReady && authCertRegistered);
        var ensuredContexts = ensureContexts(provisionable);

        if (!authCertRegistered) {
            log.debug("Dataspace provisioning: auth cert not yet REGISTERED, deferring credential request");
            return contextCount;
        }

        ensureCredentials(ensuredContexts, statuses);
        return contextCount;
    }

    /**
     * Returns the non-converged contexts to provision in this tick: those that hold an active binding
     * row (identity status {@code OK}). Contexts whose identity status is {@code UNBOUND} are bound
     * first, and only when {@code mayBind}; a context in any other state, such as drifted or
     * rejected, is neither bound nor provisioned.
     */
    private List<ParticipantContext> provisionableContexts(List<ParticipantContext> nonConverged,
            Map<ParticipantContext, ParticipantContextStatus> statuses, boolean mayBind) {
        var bound = new HashSet<>(contextsWithIdentityStatus(nonConverged, statuses, OK));
        contextsWithIdentityStatus(nonConverged, statuses, REJECTED).forEach(context -> log.warn(
                "Dataspace provisioning: skipping participant context {}, its identity is rejected because the "
                        + "registered address cannot carry a DID", context.participantId()));
        var unbound = contextsWithIdentityStatus(nonConverged, statuses, UNBOUND);
        if (!unbound.isEmpty()) {
            if (mayBind) {
                bound.addAll(bind(unbound));
            } else {
                log.debug("Dataspace provisioning: dataspace not ready or auth cert not registered, "
                        + "leaving {} context(s) unbound and unprovisioned", unbound.size());
            }
        }
        return nonConverged.stream().filter(bound::contains).toList();
    }

    private List<ParticipantContext> bind(List<ParticipantContext> unbound) {
        var unboundMembers = unbound.stream()
                .filter(context -> context.kind() == ParticipantKind.MEMBER)
                .map(ParticipantContext::memberId)
                .toList();
        var boundMembers = unboundMembers.isEmpty()
                ? List.<ClientId>of()
                : participantBindingService.bindMembersIfAbsent(unboundMembers);
        var systemBound = unbound.stream().anyMatch(context -> context.kind() == ParticipantKind.SYSTEM)
                && participantBindingService.bindSystemIfAbsent();
        return unbound.stream()
                .filter(context -> switch (context.kind()) {
                    case SYSTEM -> systemBound;
                    case MEMBER -> boundMembers.contains(context.memberId());
                })
                .toList();
    }

    private static List<ParticipantContext> contextsWithIdentityStatus(List<ParticipantContext> contexts,
            Map<ParticipantContext, ParticipantContextStatus> statuses, IdentityStatus identityStatus) {
        return contexts.stream()
                .filter(context -> statuses.get(context).identityStatus() == identityStatus)
                .toList();
    }

    /**
     * Converges every decommissioned binding one step closer to absence. A failure tearing down one
     * tombstone is logged and does not block the rest; the row (and whichever steps did not complete)
     * is left for the next tick.
     */
    private void teardown() {
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
                && status.identityStatus() == OK;
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
            var config = SpringConditionConfig.resolve(context.getEnvironment(), AdminServiceConfigKeys.instance());
            return DataspaceParticipantProvisioningTrigger.isActive(NodeProperties.isSecondaryNode(),
                    config.value(AdminServiceConfigKeys.DATASPACE_PROVISIONING_ENABLED));
        }
    }
}
