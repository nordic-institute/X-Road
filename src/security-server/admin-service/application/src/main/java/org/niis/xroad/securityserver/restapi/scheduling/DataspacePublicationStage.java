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

import com.google.common.base.Suppliers;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.PublicationBackoff.Key;
import org.niis.xroad.securityserver.restapi.scheduling.PublicationBackoff.RequestKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DidRegistrationSender;
import org.niis.xroad.securityserver.restapi.service.MemberDidRegistrationSendOutcome;
import org.niis.xroad.securityserver.restapi.service.PublicationOutcomeClassifier;
import org.niis.xroad.securityserver.restapi.service.PublicationOutcomeClassifier.BackoffClass;
import org.niis.xroad.securityserver.restapi.service.SystemDidRegistrationSendOutcome;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Last stage of every provisioning reconciler pass: sends the SYSTEM values, the SYSTEM DID together with
 * the DSP base URL, to the Central Server once the server is ready and stores the classified result on the
 * SYSTEM binding row. After that it sends the DIDs of the hosted members in batches. Exists only where the
 * reconciler is active, so a secondary node never sends.
 *
 * <p>A value is sent only when the data space is ready, a registered authentication certificate is usable (its
 * key is available and the certificate is currently valid), the registered address is known and the SYSTEM
 * binding is bound, provisioned and not sent.
 * Values already submitted or rejected are not sent again. A published value that globalconf loses is moved
 * back to not sent by the globalconf check and sent again.
 *
 * <p>After a failed send the stage waits, with growing pauses, before it sends again: a send failure pauses
 * every send of its request kind, a conflict or not resolved result pauses only its value. The pauses are kept
 * in memory. Every send is cut off after {@link #SEND_TIMEOUT}.
 *
 * <p>Member DIDs are sent only after the SYSTEM values are submitted or published, and while the owner member
 * context holds an issued credential. A member is hosted when it has a provisioned member context. A pass sends
 * one request of at most the provisioning batch size, plus the resends after a too-large refusal. Values left
 * over join the next pass. A request the Central Server refuses as too large is resent in the same pass with the
 * first half of its entries, until it is accepted or one entry is left; later passes start no larger than the
 * smallest size accepted after a refusal. A single entry that is still too large stays not sent for the longest
 * pause and pauses the member kind. After a request in which no entry resolved, the next request has one entry:
 * the member after the one probed last by encoded member id, wrapping to the first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(DataspaceParticipantProvisioningWorker.IsActive.class)
public class DataspacePublicationStage {

    static final Duration SEND_TIMEOUT = Duration.ofSeconds(60);

    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;
    private final DsParticipantRepository dsParticipantRepository;
    private final DidRegistrationSender didRegistrationSender;
    private final AdminServiceProperties adminServiceProperties;

    @Setter(AccessLevel.PACKAGE)
    private PublicationBackoff backoff =
            new PublicationBackoff(Clock.systemUTC(), new SecureRandom()::nextDouble);
    @Setter(AccessLevel.PACKAGE)
    private Duration sendTimeout = SEND_TIMEOUT;

    /**
     * Runs one publication step. Never throws: a failure is logged and the next pass retries.
     *
     * @param provisioned          the contexts that were converged at the start of the pass or whose anchor the identity
     *                             hub confirmed while ensuring them in this pass
     * @param ownerCredentialIssued whether the credential of the owner member context was issued at the start of the pass
     */
    public void publish(Set<ParticipantContext> provisioned, boolean ownerCredentialIssued) {
        var pass = new Pass();
        try {
            publishSystemValues(provisioned, pass);
        } catch (Exception e) {
            log.error("Dataspace publication: SYSTEM values step failed, the next pass retries", e);
        }
        try {
            publishMemberValues(provisioned, ownerCredentialIssued, pass);
        } catch (Exception e) {
            log.error("Dataspace publication: member DIDs step failed, the next pass retries", e);
        }
    }

    private void publishSystemValues(Set<ParticipantContext> provisioned, Pass pass) {
        if (provisioned.stream().noneMatch(context -> context.kind() == ParticipantKind.SYSTEM)) {
            log.debug("Dataspace publication: SYSTEM context not provisioned, nothing to send");
            return;
        }
        var row = pass.systemRow.get().filter(
                system -> system.getPublicationState() == null || system.getPublicationState() == PublicationState.NOT_SENT);
        if (row.isEmpty()) {
            return;
        }
        var kindKey = Key.ofKind(RequestKind.SYSTEM);
        var valueKey = Key.ofValue(RequestKind.SYSTEM, row.get().getDid());
        if (!backoff.mayAttempt(kindKey) || !backoff.mayAttempt(valueKey)) {
            log.debug("Dataspace publication: SYSTEM values are in a backoff pause, not sending");
            return;
        }
        if (!pass.dataspaceReady.get()) {
            log.debug("Dataspace publication: dataspace not ready, not sending the SYSTEM values");
            return;
        }
        var authCert = pass.authCert.get();
        if (authCert.isEmpty()) {
            if (readinessPredicates.hasRegisteredAuthCert()) {
                log.warn("Dataspace publication: the registered auth cert is not usable (key unavailable, not valid yet"
                        + " or expired), not sending the SYSTEM values");
            } else {
                log.debug("Dataspace publication: no usable auth cert, not sending the SYSTEM values");
            }
            return;
        }
        var dspBaseUrl = pass.dspBaseUrl.get();
        if (dspBaseUrl.isEmpty()) {
            log.debug("Dataspace publication: registered address unknown or invalid, not sending the SYSTEM values");
            return;
        }

        var outcome = send(row.get().getDid(), dspBaseUrl.get(), authCert.get());
        if (outcome.isEmpty()) {
            return;
        }
        var transition = PublicationOutcomeClassifier.classify(outcome.get());
        recordBackoff(transition.backoff(), kindKey, valueKey);
        if (apply(row.get(), transition, "SYSTEM values")) {
            pass.systemStateAfterSend(transition.state());
        }
    }

    private void recordBackoff(BackoffClass backoffClass, Key kindKey, Key valueKey) {
        if (backoffClass == BackoffClass.SEND_FAILURE) {
            backoff.recordFailure(kindKey);
            return;
        }
        backoff.recordSuccess(kindKey);
        recordValueBackoff(backoffClass, valueKey);
    }

    private void recordValueBackoff(BackoffClass backoffClass, Key valueKey) {
        if (backoffClass == BackoffClass.NONE) {
            backoff.recordSuccess(valueKey);
        } else {
            backoff.recordFailure(valueKey);
        }
    }

    private Optional<SystemDidRegistrationSendOutcome> send(String systemDid, String dspBaseUrl, byte[] authCert) {
        return sendWithTimeout("SYSTEM values",
                () -> didRegistrationSender.sendSystemDidRegisterRequest(systemDid, dspBaseUrl, authCert),
                SystemDidRegistrationSendOutcome::sendFailure);
    }

    private <T> Optional<T> sendWithTimeout(String values, Supplier<T> call, Function<Throwable, T> failure) {
        var result = new CompletableFuture<T>();
        var sender = Thread.ofVirtual().name("dataspace-publication-send").start(() -> {
            try {
                result.complete(call.get());
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        try {
            return Optional.of(result.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            sender.interrupt();
            log.warn("Dataspace publication: sending the {} timed out after {}", values, sendTimeout);
            return Optional.of(failure.apply(e));
        } catch (ExecutionException e) {
            var cause = e.getCause() == null ? e : e.getCause();
            logSendFailure(values, cause);
            return Optional.of(failure.apply(cause));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            sender.interrupt();
            return Optional.empty();
        }
    }

    private static void logSendFailure(String values, Throwable e) {
        if (e instanceof XrdRuntimeException xrd && xrd.isCausedBy(ErrorCode.GLOBAL_CONF_OUTDATED)) {
            log.debug("Dataspace publication: sending the {} failed, global configuration is outdated: {}",
                    values, xrd.getMessage());
        } else {
            log.warn("Dataspace publication: sending the {} failed", values, e);
        }
    }

    private boolean apply(DsParticipantEntity row, PublicationOutcomeClassifier.Transition transition, String subject) {
        if (transition.changesNothing(row.getPublicationState(), row.getPublicationReason())) {
            log.debug("Dataspace publication: {} still not sent ({}), reason unchanged", subject, transition.backoff());
            return false;
        }
        boolean applied = dsParticipantRepository.transitionPublication(row.getId(), PublicationState.NOT_SENT,
                transition.state(), transition.reason());
        if (applied) {
            log.info("Dataspace publication: {} now {}{}{}", subject,
                    transition.state(), transition.reason() == null ? "" : " (" + transition.reason() + ")",
                    transition.state() == PublicationState.NOT_SENT ? ", backoff " + transition.backoff() : "");
        } else {
            log.debug("Dataspace publication: {} changed concurrently, result not applied", subject);
        }
        return applied;
    }

    private void publishMemberValues(Set<ParticipantContext> provisioned, boolean ownerCredentialIssued, Pass pass) {
        var hostedMembers = provisionedMemberIds(provisioned);
        var kindKey = Key.ofKind(RequestKind.MEMBER);
        if (hostedMembers.isEmpty() || !backoff.mayAttempt(kindKey)) {
            log.debug("Dataspace publication: no hosted member or member DIDs are in a backoff pause, not sending");
            return;
        }
        if (!ownerCredentialIssued) {
            log.debug("Dataspace publication: owner member context has no issued credential, not sending member DIDs");
            return;
        }
        if (!pass.systemValuesSent()) {
            return;
        }
        var candidates = dsParticipantRepository.findActiveMembers(Set.of(PublicationState.NOT_SENT)).stream()
                .filter(row -> hostedMembers.contains(encodedMemberId(row)))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        var allowed = candidates.stream().filter(row -> backoff.mayAttempt(memberValueKey(row))).toList();
        if (allowed.isEmpty()) {
            log.debug("Dataspace publication: member DIDs are in a backoff pause, not sending");
            return;
        }
        if (!pass.dataspaceReady.get() || pass.authCert.get().isEmpty() || pass.dspBaseUrl.get().isEmpty()) {
            log.debug("Dataspace publication: server not ready to send, not sending member DIDs");
            return;
        }

        var ordered = allowed.stream()
                .sorted(Comparator.comparing(DataspacePublicationStage::encodedMemberId))
                .toList();
        if (backoff.isProbing(RequestKind.MEMBER)) {
            var probed = nextProbed(ordered);
            backoff.rememberProbedMember(RequestKind.MEMBER, encodedMemberId(probed));
            sendMemberBatch(List.of(probed), kindKey);
        } else {
            var size = backoff.batchSize(RequestKind.MEMBER,
                    adminServiceProperties.getDataspace().getProvisioningBatchSize());
            sendMemberBatch(ordered.stream().limit(size).toList(), kindKey);
        }
    }

    private DsParticipantEntity nextProbed(List<DsParticipantEntity> ordered) {
        var last = backoff.lastProbedMember(RequestKind.MEMBER);
        return last.flatMap(id -> ordered.stream().filter(row -> encodedMemberId(row).compareTo(id) > 0).findFirst())
                .orElse(ordered.getFirst());
    }

    private static String encodedMemberId(DsParticipantEntity row) {
        return row.getMemberIdentifier().asEncodedId();
    }

    private void sendMemberBatch(List<DsParticipantEntity> batch, Key kindKey) {
        var sent = batch;
        var outcome = sendMembers(sent);
        while (outcome.isPresent() && isTooLarge(outcome.get()) && sent.size() > 1) {
            var half = sent.size() / 2;
            log.debug("Dataspace publication: {} member DIDs are too large for one request, resending {}",
                    sent.size(), half);
            sent = sent.subList(0, half);
            outcome = sendMembers(sent);
        }
        if (outcome.isEmpty()) {
            return;
        }
        if (outcome.get().status() == MemberDidRegistrationSendOutcome.Status.ANSWERED) {
            if (sent.size() < batch.size()) {
                backoff.rememberAcceptedSize(RequestKind.MEMBER, sent.size());
            }
            applyAnswered(sent, outcome.get(), kindKey);
        } else {
            applyFailedRequest(sent, outcome.get(), kindKey);
        }
    }

    private Optional<MemberDidRegistrationSendOutcome> sendMembers(List<DsParticipantEntity> batch) {
        Map<ClientId.Conf, String> request = new LinkedHashMap<>();
        batch.forEach(row -> request.put(ClientId.Conf.ensure(row.getMemberIdentifier()), row.getDid()));
        return sendWithTimeout("member DIDs", () -> didRegistrationSender.sendMemberDidRegisterRequest(request),
                MemberDidRegistrationSendOutcome::sendFailure);
    }

    private static boolean isTooLarge(MemberDidRegistrationSendOutcome outcome) {
        return outcome.status() == MemberDidRegistrationSendOutcome.Status.REQUEST_TOO_LARGE;
    }

    private void applyAnswered(List<DsParticipantEntity> batch, MemberDidRegistrationSendOutcome outcome, Key kindKey) {
        boolean sendFailure = false;
        boolean resolved = false;
        for (int i = 0; i < batch.size(); i++) {
            var row = batch.get(i);
            var transition = PublicationOutcomeClassifier.classify(outcome.entries().get(i));
            sendFailure |= transition.backoff() == BackoffClass.SEND_FAILURE;
            resolved |= isCentralServerVerdict(transition.backoff());
            recordValueBackoff(transition.backoff(), memberValueKey(row));
            applyMember(row, transition);
        }
        if (sendFailure) {
            backoff.recordFailure(kindKey);
        } else {
            backoff.recordSuccess(kindKey);
        }
        if (resolved) {
            backoff.clearProbe(RequestKind.MEMBER);
        } else {
            backoff.requireProbe(RequestKind.MEMBER);
        }
    }

    private static boolean isCentralServerVerdict(BackoffClass backoffClass) {
        return backoffClass == BackoffClass.NONE || backoffClass == BackoffClass.FIXABLE_CONFLICT;
    }

    private void applyFailedRequest(List<DsParticipantEntity> batch, MemberDidRegistrationSendOutcome outcome,
            Key kindKey) {
        backoff.recordFailure(kindKey);
        var transition = PublicationOutcomeClassifier.classifyFailedRequest(outcome);
        var tooLarge = isTooLarge(outcome);
        if (tooLarge) {
            log.warn("Dataspace publication: the Central Server refuses even one member DID as too large, "
                    + "check its request limits");
        }
        batch.forEach(row -> {
            if (tooLarge) {
                backoff.recordMaximumFailure(memberValueKey(row));
            }
            applyMember(row, transition);
        });
    }

    private void applyMember(DsParticipantEntity row, PublicationOutcomeClassifier.Transition transition) {
        apply(row, transition, "member DID " + row.getDid());
    }

    private static Key memberValueKey(DsParticipantEntity row) {
        return Key.ofValue(RequestKind.MEMBER, row.getDid());
    }

    private static Set<String> provisionedMemberIds(Set<ParticipantContext> provisioned) {
        return provisioned.stream()
                .filter(context -> context.kind() == ParticipantKind.MEMBER)
                .map(context -> context.memberId().asEncodedId())
                .collect(Collectors.toSet());
    }

    /**
     * What one {@link #publish} call reads from the server: each value is read on first use and then shared by the
     * SYSTEM and member steps, and the SYSTEM state follows the SYSTEM step's own transition.
     */
    private final class Pass {
        private final Supplier<Optional<DsParticipantEntity>> systemRow =
                Suppliers.memoize(dsParticipantRepository::findActiveSystemParticipant);
        private final Supplier<Boolean> dataspaceReady = Suppliers.memoize(readinessPredicates::isDataspaceReady);
        private final Supplier<Optional<byte[]>> authCert = Suppliers.memoize(readinessPredicates::registeredAuthCertBytes);
        private final Supplier<Optional<String>> dspBaseUrl = Suppliers.memoize(didAuthority::dspBaseUrlIfValid);
        private PublicationState systemStateAfterSend;

        void systemStateAfterSend(PublicationState state) {
            systemStateAfterSend = state;
        }

        boolean systemValuesSent() {
            var state = systemStateAfterSend != null ? systemStateAfterSend
                    : systemRow.get().map(DsParticipantEntity::getPublicationState).orElse(null);
            return state == PublicationState.SUBMITTED || state == PublicationState.PUBLISHED;
        }
    }
}
