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

package org.niis.xroad.edc.extension.assetaccess.service;

import jakarta.annotation.Nullable;
import jakarta.json.Json;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.controlplane.DataAddress;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.entity.Entity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.time.CallBudget;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;
import org.niis.xroad.edc.extension.assetaccess.agreement.AgreementReuseNote;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureClassifier;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureKind;
import org.niis.xroad.edc.extension.assetaccess.failure.StageOutcome;
import org.niis.xroad.edc.extension.assetaccess.policy.PolicySubjectMatcher;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.extension.assetaccess.probe.CandidateHandshakeRace;
import org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper;
import org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACCESS_REFUSED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_TIMEOUT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_FETCH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;

/**
 * Orchestrates the full asset access acquisition flow: agreement reuse lookup, catalog fetch, offer
 * selection, contract negotiation, transfer process initiation, and data address resolution.
 *
 * <p>Negotiation and transfer completion are awaited through the shared {@link AssetAccessCompletionPoller},
 * which reads the outcome from the EDC stores rather than an in-process listener; this works identically
 * whether the negotiation or transfer is driven to completion by this instance or another one sharing
 * the same database.
 *
 * <p>Every acquisition starts with the {@link AgreementReuseNote}, which names the agreement that last worked for
 * the calling subsystem, then the {@link ReusableAgreementLookup} against the shared agreement store, scoped to
 * agreements this participant context negotiated as consumer, for each candidate not in cooldown (all of them when
 * every candidate is). The note is what lets a group agreement be reused, which the lookup cannot attribute to a
 * subsystem. The candidate holding the newest agreement is tried first; a hit skips the catalog fetch and
 * negotiation entirely and transfers with the existing agreement. A successful transfer records its agreement in
 * the note.
 *
 * <p>Only a provider refusal ends an agreement's reuse: a refusal of the transfer, as recognised by
 * {@link ProviderFailureClassifier}, or a data address the provider marks refused
 * ({@link DspConventions#ACCESS_REFUSED_PROPERTY}) because the caller's live ACL grants no endpoint. The note
 * entry is removed (unless a newer one replaced it) and the same candidate falls back to one full negotiation,
 * whose newer agreement wins reuse from then on. A transport fault is never a refusal: the candidate goes into
 * cooldown, its agreement and note entry are kept, and the next candidate with an agreement is tried, then the
 * candidates without one by negotiation. Every other failure keeps the agreement and the note entry and fails the
 * acquisition: a provider-side termination without a refusal and a failed initiation. A marked data address is
 * never returned: after a reused agreement's renegotiation, or after a fresh negotiation, it fails the acquisition
 * with {@code DSP_ACCESS_REFUSED}.
 *
 * <p>An acquisition runs under the {@link CallBudget} its caller supplies. The catalog fetch, the negotiation wait
 * and the transfer wait each get the lesser of their own cap and the time left, so together they never outlast the
 * budget; a step that finds no time left fails the acquisition with {@code DSP_ACQUISITION_TIMEOUT} instead of
 * starting. A step whose limit the budget cut short and that then times out fails the acquisition the same way
 * and does not put its candidate into cooldown, while a step that times out on its own full cap is a transport
 * fault. A catalog fetch that times out on its full cap is a transport fault too. The renegotiation after a
 * refusal runs on the same budget. The budget is the one of the caller that started the work; callers that join
 * it bring their own wait limit. A caller giving up cancels its waiter (see {@link AssetAccessStateStore}), and
 * the work stops when its last waiter has given up: pending steps are cancelled and a step registered afterwards
 * is cancelled at once. A negotiation or transfer already started in EDC runs on without being awaited.
 *
 * <p>Several ds-control-plane instances may each acquire access under the same reused agreement, each starting
 * its own transfer process; the previous transfer under that agreement is not completed here, it is cleaned up
 * once its agreement token expires by the periodic sweep in
 * {@link org.niis.xroad.edc.extension.assetaccess.poller.ExpiredTransferSweeper}.
 */
@RequiredArgsConstructor
public class AssetAccessOrchestrator {

    private final AssetAccessStateStore stateStore;
    private final ReusableAgreementLookup reusableAgreementLookup;
    private final AgreementReuseNote reuseNote;
    private final CatalogService catalogService;
    private final ContractNegotiationService contractNegotiationService;
    private final TransferProcessService transferProcessService;
    private final AssetAccessCompletionPoller completionPoller;
    private final CooldownTracker cooldownTracker;
    private final CandidateHandshakeRace candidateHandshakeRace;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final Monitor monitor;
    private final Duration catalogTimeout;
    private final Duration negotiationTimeout;
    private final Duration transferTimeout;
    private final Duration candidateTimeout;

    /**
     * Acquires access to an asset, de-duplicating concurrent requests for the same participant+asset+candidate
     * set(+forced target)(+client) key. Each caller gets a future of its own on the shared work; cancelling it is
     * the caller giving up. Candidates not in cooldown (all of them when every candidate is) are
     * considered. Those holding a reusable agreement are tried first, newest agreement first, without probing. The
     * rest are probed in parallel and tried in the order the candidate handshake race returns, or tried at once,
     * without a probe, when only one is left. A transport fault puts
     * the candidate in cooldown and moves on to the next; any other failure is returned at once, so no other
     * candidate is contacted after a provider answered. A candidate that does not answer within its limit is
     * abandoned the same way as a transport fault: once its attempt is settled, it starts no further stage and its
     * poller wait is cancelled. A stage completing at the moment of abandonment can still start the next one, and a
     * negotiation or transfer already sent is not terminated.
     */
    public CompletableFuture<AssetAccessResult> acquireAssetAccess(ParticipantContext participantContext,
                                                                   AssetAccessRequest request,
                                                                   CallBudget budget) {
        var key = registryKey(participantContext, request);
        monitor.info("%s acquireAssetAccess entered: candidates=%d protocol=%s"
                .formatted(key, request.candidates().size(), request.protocolOrDefault()));
        return stateStore.loadOrStartInFlight(key,
                scope -> acquireFromSelectable(key, participantContext, request, new CallContext(budget, scope)));
    }

    /**
     * Keeps the Security Server at the address out of acquisition for the cooldown period, restarting the period
     * when it is already running. The agreements held with the server are kept.
     */
    public void reportCounterPartyFailure(String counterPartyId, String counterPartyAddress) {
        cooldownTracker.markFailed(counterPartyAddress);
        monitor.info("Counter-party reported failed: counterPartyId=%s counterPartyAddress=%s"
                .formatted(counterPartyId, counterPartyAddress));
    }

    private CompletableFuture<AssetAccessResult> acquireFromSelectable(
            String key, ParticipantContext participantContext, AssetAccessRequest request, CallContext call) {
        var selectable = cooldownTracker.selectable(request.candidates(), AssetAccessRequest.Candidate::counterPartyAddress);
        var reusable = newestReusableAgreementsFirst(participantContext, request, selectable);
        var transportFailures = new ArrayList<Throwable>();
        return acquireInOrder(key, List.copyOf(reusable.keySet()), transportFailures,
                candidate -> attemptReuse(key, participantContext, request, candidate, reusable.get(candidate), call),
                () -> acquireByNegotiation(key, participantContext, request,
                        selectable.stream().filter(candidate -> !reusable.containsKey(candidate)).toList(),
                        transportFailures, call));
    }

    private Map<AssetAccessRequest.Candidate, ContractAgreement> newestReusableAgreementsFirst(
            ParticipantContext participantContext, AssetAccessRequest request, List<AssetAccessRequest.Candidate> candidates) {
        var found = new LinkedHashMap<AssetAccessRequest.Candidate, ContractAgreement>();
        for (var candidate : candidates) {
            findReusableAgreement(participantContext, request, candidate)
                    .ifPresent(agreement -> found.put(candidate, agreement));
        }
        return found.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingLong(ContractAgreement::getContractSigningDate).reversed()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first, LinkedHashMap::new));
    }

    private static AgreementReuseNote.Key noteKey(ParticipantContext participantContext, AssetAccessRequest request,
                                                  AssetAccessRequest.Candidate candidate) {
        return new AgreementReuseNote.Key(participantContext.getParticipantContextId(), participantContext.getIdentity(),
                candidate.counterPartyId(), request.assetId(), request.clientId());
    }

    private Optional<ContractAgreement> findReusableAgreement(ParticipantContext participantContext,
                                                              AssetAccessRequest request,
                                                              AssetAccessRequest.Candidate candidate) {
        var noteKey = noteKey(participantContext, request, candidate);
        var noted = reuseNote.find(noteKey).flatMap(agreementId -> {
            var agreement = reusableAgreementLookup.findById(noteKey.participantContextId(), noteKey.participantDid(),
                    noteKey.assetId(), noteKey.providerDid(), agreementId);
            if (agreement.isEmpty()) {
                reuseNote.evict(noteKey, agreementId);
            }
            return agreement;
        });
        if (noted.isPresent()) {
            return noted;
        }
        return reusableAgreementLookup.find(participantContext.getParticipantContextId(), participantContext.getIdentity(),
                request.assetId(), candidate.counterPartyId(), request.clientId());
    }

    private CompletableFuture<AssetAccessResult> acquireByNegotiation(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            List<AssetAccessRequest.Candidate> candidates, List<Throwable> transportFailures, CallContext call) {
        var ordering = candidates.size() == 1
                ? CompletableFuture.completedFuture(candidates)
                : call.scope().track(candidateHandshakeRace.order(candidates));
        return ordering
                .thenCompose(ordered -> acquireInOrder(key, ordered, transportFailures,
                        candidate -> attemptNegotiation(key, participantContext, request, candidate, call),
                        () -> CompletableFuture.failedFuture(allCandidatesFailed(key, transportFailures))));
    }

    private static String registryKey(ParticipantContext participantContext, AssetAccessRequest request) {
        var candidateSet = request.candidates().stream()
                .map(candidate -> candidate.counterPartyId() + "@" + candidate.counterPartyAddress())
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
        var key = participantContext.getParticipantContextId() + "::" + request.assetId() + "::" + candidateSet;
        if (request.forcedTarget() != null) {
            key += "::forced=" + request.forcedTarget();
        }
        return request.clientId() == null ? key : key + "::" + request.clientId();
    }

    private CompletableFuture<AssetAccessResult> acquireInOrder(
            String key, List<AssetAccessRequest.Candidate> candidates, List<Throwable> transportFailures,
            Function<AssetAccessRequest.Candidate, CompletableFuture<StageOutcome<DataAddress>>> attempt,
            Supplier<CompletableFuture<AssetAccessResult>> whenExhausted) {
        return acquireInOrderFrom(key, candidates, 0, transportFailures, attempt, whenExhausted);
    }

    private CompletableFuture<AssetAccessResult> acquireInOrderFrom(
            String key, List<AssetAccessRequest.Candidate> candidates, int index, List<Throwable> transportFailures,
            Function<AssetAccessRequest.Candidate, CompletableFuture<StageOutcome<DataAddress>>> attempt,
            Supplier<CompletableFuture<AssetAccessResult>> whenExhausted) {
        if (index >= candidates.size()) {
            return whenExhausted.get();
        }
        var candidate = candidates.get(index);
        return attempt.apply(candidate)
                .thenCompose(outcome -> switch (outcome) {
                    case StageOutcome.Succeeded(var dataAddress) -> CompletableFuture.completedFuture(
                            new AssetAccessResult(dataAddress, winnerOf(candidate)));
                    case StageOutcome.Failed(var failureKind, var failure) when failureKind == FailureKind.TRANSPORT_FAULT -> {
                        monitor.warning("%s candidate unreachable, trying next: counterPartyId=%s counterPartyAddress=%s"
                                .formatted(key, candidate.counterPartyId(), candidate.counterPartyAddress()), failure);
                        cooldownTracker.markFailed(candidate.counterPartyAddress());
                        transportFailures.add(unwrap(failure));
                        yield acquireInOrderFrom(key, candidates, index + 1, transportFailures, attempt, whenExhausted);
                    }
                    case StageOutcome.Failed(_, var failure) -> CompletableFuture.<AssetAccessResult>failedFuture(unwrap(failure));
                });
    }

    private XrdRuntimeException allCandidatesFailed(String key, List<Throwable> transportFailures) {
        var message = "All %d candidates failed".formatted(transportFailures.size());
        if (transportFailures.isEmpty()) {
            monitor.warning("%s %s".formatted(key, message));
            return XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                    .origin(ErrorOrigin.DATASPACE)
                    .details(message)
                    .build();
        }
        var lastFailure = transportFailures.getLast();
        monitor.warning("%s %s: %s".formatted(key, message, lastFailure.getMessage()), lastFailure);
        return XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .cause(lastFailure)
                .details("%s: %s".formatted(message, lastFailure.getMessage()))
                .build();
    }

    private static AssetAccessResult.Winner winnerOf(AssetAccessRequest.Candidate candidate) {
        return new AssetAccessResult.Winner(candidate.counterPartyId(), candidate.counterPartyAddress());
    }

    private CompletableFuture<StageOutcome<DataAddress>> attemptReuse(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, ContractAgreement agreement, CallContext call) {
        return classifyWithinCandidateLimit(candidate, attempt ->
                transferUnderReusedAgreement(key, participantContext, request, candidate, agreement, attempt, call));
    }

    private CompletableFuture<StageOutcome<DataAddress>> attemptNegotiation(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, CallContext call) {
        monitor.info("%s no reusable agreement found, negotiating".formatted(key));
        return classifyWithinCandidateLimit(candidate,
                attempt -> negotiateAndTransfer(key, participantContext, request, candidate, attempt, call));
    }

    private CompletableFuture<StageOutcome<DataAddress>> classifyWithinCandidateLimit(
            AssetAccessRequest.Candidate candidate,
            Function<CandidateAttempt, CompletableFuture<StageOutcome<DataAddress>>> start) {
        var attempt = new CandidateAttempt();
        var classified = start.apply(attempt).exceptionally(
                failure -> new StageOutcome.Failed<>(FailureClassifier.classify(failure), unwrap(failure)));
        return abandonAtCandidateLimit(candidate, attempt, classified);
    }

    private CompletableFuture<StageOutcome<DataAddress>> abandonAtCandidateLimit(
            AssetAccessRequest.Candidate candidate, CandidateAttempt attempt,
            CompletableFuture<StageOutcome<DataAddress>> outcome) {
        var deadline = completionPoller.awaitDeadline(candidateTimeout);
        var abandoned = deadline.thenApply(deadlineOutcome -> switch (deadlineOutcome) {
            case StageOutcome.Failed(var failureKind, var failure) when failureKind != FailureKind.TRANSPORT_FAULT ->
                    new StageOutcome.Failed<DataAddress>(failureKind, failure);
            default -> candidateAbandoned(candidate);
        });
        return outcome.applyToEither(abandoned, Function.identity())
                .whenComplete((_, _) -> {
                    deadline.cancel(false);
                    attempt.settle();
                });
    }

    private StageOutcome<DataAddress> candidateAbandoned(AssetAccessRequest.Candidate candidate) {
        var timeout = new TimeoutException("Candidate %s abandoned after %s"
                .formatted(candidate.counterPartyAddress(), candidateTimeout));
        return new StageOutcome.Failed<>(FailureClassifier.classify(timeout), timeout);
    }

    private CompletableFuture<StageOutcome<DataAddress>> transferUnderReusedAgreement(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, ContractAgreement agreement, CandidateAttempt attempt,
            CallContext call) {
        monitor.info("%s reusing agreement: agreementId=%s matchedByClientId=%s"
                .formatted(key, agreement.getId(), request.clientId() != null));
        return startTransfer(key, participantContext, agreement, XRoadTransferType.PULL.wireValue(),
                candidate.counterPartyAddress(), request.protocolOrDefault(), attempt, call)
                .thenCompose(outcome -> switch (outcome) {
                    case StageOutcome.Succeeded<DataAddress> _ -> CompletableFuture.completedFuture(
                            rememberOnSuccess(noteKey(participantContext, request, candidate), agreement, outcome));
                    case StageOutcome.Failed(var failureKind, var failure) -> isTransferRefusal(failureKind, failure)
                            ? unlessSettled(attempt, () -> negotiateAfterRefusal(
                                    key, participantContext, request, candidate, agreement, failure, attempt, call))
                            : CompletableFuture.completedFuture(outcome);
                });
    }

    private CompletableFuture<StageOutcome<DataAddress>> negotiateAfterRefusal(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, ContractAgreement refusedAgreement, Throwable refusal,
            CandidateAttempt attempt, CallContext call) {
        monitor.warning("%s reused agreement refused, negotiating afresh: agreementId=%s reason=%s"
                .formatted(key, refusedAgreement.getId(), unwrap(refusal).getMessage()));
        reuseNote.evict(noteKey(participantContext, request, candidate), refusedAgreement.getId());
        return negotiateAndTransfer(key, participantContext, request, candidate, attempt, call);
    }

    private StageOutcome<DataAddress> rememberOnSuccess(AgreementReuseNote.Key noteKey, ContractAgreement agreement,
                                                        StageOutcome<DataAddress> outcome) {
        if (outcome instanceof StageOutcome.Succeeded<DataAddress>) {
            reuseNote.remember(noteKey, agreement.getId());
        }
        return outcome;
    }

    private static boolean isTransferRefusal(FailureKind failureKind, Throwable failure) {
        return failureKind != FailureKind.TRANSPORT_FAULT
                && unwrap(failure) instanceof XrdRuntimeException xrdException
                && (xrdException.isCausedBy(DSP_ACCESS_REFUSED)
                || xrdException.isCausedBy(DSP_TRANSFER_FAILED) && ProviderFailureClassifier.isRefusal(xrdException.getDetails()));
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private CompletableFuture<StageOutcome<DataAddress>> negotiateAndTransfer(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, CandidateAttempt attempt, CallContext call) {
        return andThen(attempt, fetchCatalog(key, participantContext, request, candidate, call),
                catalog -> acquireFromCatalog(key, participantContext, request, candidate, catalog, attempt, call));
    }

    private CompletableFuture<StageOutcome<DataAddress>> acquireFromCatalog(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, Catalog catalog, CandidateAttempt attempt, CallContext call) {
        var offer = findOffer(key, catalog, request.assetId(), request.clientId());
        return andThen(attempt, negotiateContract(key, participantContext, request, candidate, offer, attempt, call),
                agreement -> startTransfer(key, participantContext, agreement, offer.transferType(),
                        candidate.counterPartyAddress(), request.protocolOrDefault(), attempt, call)
                        .thenApply(outcome -> rememberOnSuccess(noteKey(participantContext, request, candidate), agreement, outcome)));
    }

    private static <T, R> CompletableFuture<StageOutcome<R>> andThen(
            CandidateAttempt attempt, CompletableFuture<StageOutcome<T>> stage,
            Function<T, CompletableFuture<StageOutcome<R>>> next) {
        return stage.thenCompose(outcome -> switch (outcome) {
            case StageOutcome.Succeeded<T> succeeded -> unlessSettled(attempt, () -> next.apply(succeeded.value()));
            case StageOutcome.Failed<T> failed ->
                    CompletableFuture.completedFuture(new StageOutcome.Failed<R>(failed.failureKind(), failed.failure()));
        });
    }

    private static <R> CompletableFuture<StageOutcome<R>> unlessSettled(
            CandidateAttempt attempt, Supplier<CompletableFuture<StageOutcome<R>>> next) {
        return attempt.isSettled()
                ? CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureKind.OTHER, abandonedAttemptFailure()))
                : next.get();
    }

    private static XrdRuntimeException abandonedAttemptFailure() {
        return XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .details("Candidate attempt abandoned")
                .build();
    }

    private static QuerySpec datasetQuery(String assetId) {
        return QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("id", "=", assetId))
                .build();
    }

    CompletableFuture<StageOutcome<Catalog>> fetchCatalog(String key, ParticipantContext participantContext,
                                                          AssetAccessRequest request, AssetAccessRequest.Candidate candidate,
                                                          CallContext call) {
        Duration timeout;
        try {
            timeout = call.stageTimeout(catalogTimeout);
        } catch (XrdRuntimeException e) {
            return CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureKind.OTHER, e));
        }
        monitor.info("%s catalog fetch started".formatted(key));
        try {
            var requested = call.scope().track(catalogService.requestCatalog(participantContext, candidate.counterPartyId(),
                    candidate.counterPartyAddress(), request.protocolOrDefault(), datasetQuery(request.assetId())));
            return within(requested, timeout, () -> catalogTimeout(key, call, timeout))
                    .handle((result, failure) -> failure != null
                            ? catalogFailed(key, failure)
                            : catalogOutcome(key, result));
        } catch (EdcException e) {
            monitor.warning("%s catalog fetch failed with EDC exception".formatted(key), e);
            return CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureClassifier.classify(e),
                    XrdRuntimeException.systemException(DSP_CATALOG_FETCH_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .cause(e)
                            .details(e.getMessage())
                            .build()));
        }
    }

    private StageOutcome<Catalog> catalogFailed(String key, Throwable thrown) {
        var failure = unwrap(thrown);
        var failureKind = FailureClassifier.classify(failure);
        monitor.warning("%s catalog fetch failed: failureKind=%s".formatted(key, failureKind), failure);
        return new StageOutcome.Failed<>(failureKind, failure);
    }

    private StageOutcome<Catalog> catalogOutcome(String key, StatusResult<byte[]> result) {
        if (result.failed()) {
            monitor.warning("%s catalog fetch failed: %s".formatted(key, result.getFailureDetail()));
            return new StageOutcome.Failed<>(FailureClassifier.classify(result),
                    XrdRuntimeException.systemException(DSP_CATALOG_FETCH_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .details(result.getFailureDetail())
                            .build());
        }
        Catalog catalog;
        try {
            catalog = parseCatalog(key, result.getContent());
        } catch (XrdRuntimeException e) {
            return new StageOutcome.Failed<>(FailureKind.OTHER, e);
        }
        monitor.debug("%s catalog fetch succeeded: datasets=%d".formatted(key, catalog.getDatasets().size()));
        return new StageOutcome.Succeeded<>(catalog);
    }

    private XrdRuntimeException catalogTimeout(String key, CallContext call, Duration timeout) {
        monitor.warning("%s catalog fetch timed out after %d ms".formatted(key, timeout.toMillis()));
        if (timeout.compareTo(catalogTimeout) < 0) {
            return call.budgetSpent();
        }
        var details = "Catalog fetch timed out after %d ms".formatted(timeout.toMillis());
        return XrdRuntimeException.systemException(DSP_CATALOG_FETCH_FAILED)
                .origin(ErrorOrigin.DATASPACE)
                .cause(new TimeoutException(details))
                .details(details)
                .build();
    }

    /**
     * Follows {@code step} but fails with {@code onTimeout}'s exception once {@code timeout} has passed, and
     * cancels {@code step} when it fails or times out, so no step is left running that nothing waits for.
     */
    private static <T> CompletableFuture<T> within(CompletableFuture<T> step, Duration timeout,
                                                   Supplier<XrdRuntimeException> onTimeout) {
        var bounded = new CompletableFuture<T>();
        step.whenComplete((value, failure) -> {
            if (failure != null) {
                bounded.completeExceptionally(failure);
            } else {
                bounded.complete(value);
            }
        });
        bounded.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return bounded.exceptionallyCompose(failure -> {
            step.cancel(true);
            var cause = unwrap(failure);
            return CompletableFuture.failedFuture(cause instanceof TimeoutException ? onTimeout.get() : cause);
        });
    }

    private Catalog parseCatalog(String key, byte[] catalogBytes) {
        try (var reader = Json.createReader(new ByteArrayInputStream(catalogBytes))) {
            var catalogJsonObject = reader.readObject();
            return jsonLd.expand(catalogJsonObject)
                    .compose(expanded -> transformerRegistry.transform(expanded, Catalog.class))
                    .orElseThrow(failure -> {
                        monitor.warning("%s catalog parse failed: %s".formatted(key, failure.getFailureDetail()));
                        return XrdRuntimeException.systemException(DSP_CATALOG_PARSE_FAILED)
                                .origin(ErrorOrigin.DATASPACE)
                                .details(failure.getFailureDetail())
                                .build();
                    });
        } catch (XrdRuntimeException e) {
            throw e;
        } catch (Exception e) {
            monitor.warning("%s catalog parse failed".formatted(key), e);
            throw XrdRuntimeException.systemException(DSP_CATALOG_PARSE_FAILED)
                    .origin(ErrorOrigin.DATASPACE)
                    .cause(e)
                    .details(e.getMessage())
                    .build();
        }
    }

    private OfferContext findOffer(String key, Catalog catalog, String assetId, @Nullable String clientId) {
        var dataset = catalog.getDatasets().stream()
                .filter(ds -> assetId.equals(ds.getId()))
                .findFirst()
                .orElseThrow(() -> XrdRuntimeException.systemException(DSP_DATASET_NOT_FOUND)
                        .origin(ErrorOrigin.DATASPACE)
                        .metadataItems(assetId)
                        .build());

        if (!dataset.hasOffers()) {
            throw XrdRuntimeException.systemException(DSP_OFFERS_NOT_FOUND)
                    .origin(ErrorOrigin.DATASPACE)
                    .metadataItems(assetId)
                    .build();
        }

        var offer = selectOffer(dataset.getOffers(), clientId, assetId);

        var transferType = dataset.getDistributions().stream()
                .map(Distribution::getFormat)
                .filter(XRoadTransferType.PULL.wireValue()::equals)
                .findFirst()
                .orElseThrow(() -> XrdRuntimeException.systemException(DSP_PULL_DISTRIBUTION_MISSING)
                        .origin(ErrorOrigin.DATASPACE)
                        .metadataItems(assetId)
                        .build());

        monitor.info("%s offer found: assetId=%s offerId=%s transferType=%s namesClient=%s"
                .formatted(key, assetId, offer.getKey(), transferType,
                        clientId != null && PolicySubjectMatcher.namesClient(offer.getValue(), clientId)));
        return new OfferContext(offer.getKey(), offer.getValue(), dataset, transferType);
    }

    /**
     * The provider publishes one offer per ACL subject. A known caller takes the offer written for its
     * subsystem, else one that applies to its member as a whole (member-level or group subject); an offer
     * written for another subsystem is never negotiated on its behalf. Failing both, a known caller falls
     * back to an unrestricted offer (no permissions at all, as published for builtin/unrestricted-SYSTEM
     * services) since such an offer applies to any caller. An unknown caller takes the first offer.
     */
    private static Map.Entry<String, Policy> selectOffer(Map<String, Policy> offers, @Nullable String clientId,
                                                         String assetId) {
        if (clientId == null) {
            return offers.entrySet().iterator().next();
        }
        var memberId = PolicyContextHelper.parseClientId(clientId).getMemberId().asEncodedId();
        return firstOffer(offers, policy -> PolicySubjectMatcher.namesClient(policy, clientId))
                .or(() -> firstOffer(offers, policy -> appliesToMember(policy, memberId)))
                .or(() -> firstOffer(offers, PolicySubjectMatcher::isUnrestricted))
                .orElseThrow(() -> XrdRuntimeException.systemException(DSP_OFFERS_NOT_FOUND)
                        .origin(ErrorOrigin.DATASPACE)
                        .metadataItems(assetId, clientId)
                        .build());
    }

    private static Optional<Map.Entry<String, Policy>> firstOffer(Map<String, Policy> offers, Predicate<Policy> matches) {
        return offers.entrySet().stream().filter(entry -> matches.test(entry.getValue())).findFirst();
    }

    private static boolean appliesToMember(Policy policy, String encodedMemberId) {
        return PolicySubjectMatcher.namesClient(policy, encodedMemberId) || PolicySubjectMatcher.namesGroup(policy);
    }

    private CompletableFuture<StageOutcome<ContractAgreement>> negotiateContract(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, OfferContext offer, CandidateAttempt attempt, CallContext call) {
        try {
            var timeout = call.stageTimeout(negotiationTimeout);
            var contractRequest = buildContractRequest(request, candidate, offer);
            var negotiationResult = contractNegotiationService.initiateNegotiation(participantContext, contractRequest);
            var negotiationId = negotiationResult
                    .map(Entity::getId)
                    .orElseThrow(exceptionMapper(ContractNegotiation.class, null));
            monitor.info("%s negotiation initiated, awaiting completion: negotiationId=%s".formatted(key, negotiationId));

            var negotiation = call.scope().track(completionPoller.awaitNegotiation(negotiationId, timeout));
            attempt.cancelWaitOnSettle(() -> completionPoller.cancelNegotiation(negotiationId));
            return negotiation
                    .whenComplete((outcome, throwable) -> onNegotiationComplete(key, negotiationId, outcome, throwable))
                    .thenApply(outcome -> call.classifyTimeout(outcome, timeout, negotiationTimeout));
        } catch (Exception e) {
            monitor.warning("%s negotiation initiation failed".formatted(key), e);
            return CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureClassifier.classify(e), e));
        }
    }

    private ContractRequest buildContractRequest(AssetAccessRequest request, AssetAccessRequest.Candidate candidate,
                                                 OfferContext offer) {
        return ContractRequest.Builder.newInstance()
                .protocol(request.protocolOrDefault())
                .counterPartyAddress(candidate.counterPartyAddress())
                .contractOffer(ContractOffer.Builder.newInstance()
                        .id(offer.offerId())
                        .assetId(offer.dataset().getId())
                        .policy(offer.policy().toBuilder()
                                .target(offer.dataset().getId())
                                .assigner(candidate.counterPartyId())
                                .type(PolicyType.OFFER)
                                .build())
                        .build())
                .build();
    }

    private void onNegotiationComplete(String key, String negotiationId, StageOutcome<ContractAgreement> outcome,
                                       Throwable throwable) {
        if (throwable instanceof CancellationException) {
            monitor.info("%s negotiation wait cancelled: negotiationId=%s".formatted(key, negotiationId));
            return;
        }
        if (throwable != null) {
            monitor.warning("%s negotiation failed: negotiationId=%s".formatted(key, negotiationId), throwable);
            return;
        }
        switch (outcome) {
            case StageOutcome.Succeeded(var agreement) ->
                    monitor.info("%s agreement received: agreementId=%s".formatted(key, agreement.getId()));
            case StageOutcome.Failed(_, var failure) ->
                    monitor.warning("%s negotiation failed: negotiationId=%s".formatted(key, negotiationId), failure);
        }
    }

    private CompletableFuture<StageOutcome<DataAddress>> startTransfer(
            String key, ParticipantContext participantContext, ContractAgreement agreement,
            String transferType, String counterPartyAddress, String protocol, CandidateAttempt attempt, CallContext call) {
        Duration timeout;
        try {
            timeout = call.stageTimeout(transferTimeout);
        } catch (XrdRuntimeException e) {
            return CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureKind.OTHER, e));
        }
        var transferRequest = TransferRequest.Builder.newInstance()
                .contractId(agreement.getId())
                .counterPartyAddress(counterPartyAddress)
                .protocol(protocol)
                .transferType(transferType)
                .build();

        ServiceResult<TransferProcess> result;
        try {
            result = transferProcessService.initiateTransfer(participantContext, transferRequest);
        } catch (EdcException e) {
            monitor.warning("%s transfer initiate failed with EDC exception".formatted(key), e);
            return CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureClassifier.classify(e),
                    XrdRuntimeException.systemException(DSP_TRANSFER_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .cause(e)
                            .details(e.getMessage())
                            .build()));
        }

        if (result.failed()) {
            monitor.warning("%s transfer initiate failed: %s".formatted(key, result.getFailureDetail()));
            return CompletableFuture.completedFuture(new StageOutcome.Failed<>(FailureKind.OTHER,
                    XrdRuntimeException.systemException(DSP_TRANSFER_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .details(result.getFailureDetail())
                            .build()));
        }

        var transferProcessId = result.getContent().getId();
        monitor.info("%s transfer initiated, awaiting completion: transferProcessId=%s".formatted(key, transferProcessId));

        var transfer = call.scope().track(completionPoller.awaitTransfer(transferProcessId, timeout));
        attempt.cancelWaitOnSettle(() -> completionPoller.cancelTransfer(transferProcessId));
        return transfer
                .whenComplete((outcome, throwable) -> onTransferComplete(key, transferProcessId, outcome, throwable))
                .thenApply(outcome -> call.classifyTimeout(outcome, timeout, transferTimeout))
                .thenApply(outcome -> refuseMarkedAddress(key, transferProcessId, outcome));
    }

    private StageOutcome<DataAddress> refuseMarkedAddress(String key, String transferProcessId,
                                                          StageOutcome<DataAddress> outcome) {
        if (!(outcome instanceof StageOutcome.Succeeded(var dataAddress))
                || dataAddress == null
                || !Boolean.parseBoolean(String.valueOf(dataAddress.getProperties().get(DspConventions.ACCESS_REFUSED_PROPERTY)))) {
            return outcome;
        }
        monitor.warning("%s provider refused access, the caller's live ACL grants no endpoint: transferProcessId=%s"
                .formatted(key, transferProcessId));
        return new StageOutcome.Failed<>(FailureKind.OTHER, XrdRuntimeException.systemException(DSP_ACCESS_REFUSED)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems(transferProcessId)
                .details("The provider refused access: the caller's ACL grants no endpoint of the service")
                .build());
    }

    private void onTransferComplete(String key, String transferProcessId, StageOutcome<DataAddress> outcome, Throwable throwable) {
        if (throwable instanceof CancellationException) {
            monitor.info("%s transfer wait cancelled: transferProcessId=%s".formatted(key, transferProcessId));
            return;
        }
        if (throwable != null) {
            monitor.warning("%s transfer failed: transferProcessId=%s".formatted(key, transferProcessId), throwable);
            return;
        }
        switch (outcome) {
            case StageOutcome.Succeeded(var dataAddress) -> {
                monitor.info("%s transfer completed: endpointType=%s".formatted(key, dataAddress.getType()));
                monitor.debug("%s transfer completed DataAddress: %s".formatted(key, dataAddress));
            }
            case StageOutcome.Failed(_, var failure) ->
                    monitor.warning("%s transfer failed: transferProcessId=%s".formatted(key, transferProcessId), failure);
        }
    }

    record CallContext(CallBudget budget, AcquisitionScope scope) {
        /** The time a step may take: its cap, or the time left; fails when none is left. */
        Duration stageTimeout(Duration cap) {
            if (budget.isExhausted()) {
                throw budgetSpent();
            }
            return budget.stage(cap);
        }

        /**
         * A step whose limit the budget cut short below its {@code cap} and that then timed out ran out of budget; it
         * is not a transport fault of the candidate. Every other outcome stays as it is.
         */
        <T> StageOutcome<T> classifyTimeout(StageOutcome<T> outcome, Duration granted, Duration cap) {
            if (granted.compareTo(cap) < 0
                    && outcome instanceof StageOutcome.Failed(var failureKind, var failure)
                    && failureKind == FailureKind.TRANSPORT_FAULT
                    && ExceptionUtils.indexOfType(failure, TimeoutException.class) >= 0) {
                return new StageOutcome.Failed<>(FailureKind.OTHER, budgetSpent());
            }
            return outcome;
        }

        XrdRuntimeException budgetSpent() {
            return XrdRuntimeException.systemException(DSP_ACQUISITION_TIMEOUT)
                    .origin(ErrorOrigin.DATASPACE)
                    .metadataItems(budget.total().toMillis())
                    .build();
        }
    }

    private record OfferContext(String offerId, Policy policy, Dataset dataset, String transferType) {
    }
}
