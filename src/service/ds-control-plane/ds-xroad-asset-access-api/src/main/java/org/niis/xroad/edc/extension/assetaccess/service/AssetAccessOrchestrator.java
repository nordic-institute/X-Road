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
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureClassifier;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureKind;
import org.niis.xroad.edc.extension.assetaccess.failure.StageOutcome;
import org.niis.xroad.edc.extension.assetaccess.policy.PolicySubjectMatcher;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace;
import org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
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
 * <p>Every acquisition starts with a {@link ReusableAgreementLookup} against the shared agreement store, scoped
 * to agreements this participant context negotiated as consumer; a hit skips the catalog fetch and negotiation
 * entirely and transfers with the existing agreement. When that transfer is refused at initiation or terminated
 * while awaited, the same acquisition falls back to a full negotiation, whose newer agreement wins reuse from
 * then on. A transport fault or a timeout waiting for the transfer to start is not a refusal and fails the acquisition.
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
    private final CatalogService catalogService;
    private final ContractNegotiationService contractNegotiationService;
    private final TransferProcessService transferProcessService;
    private final AssetAccessCompletionPoller completionPoller;
    private final CooldownTracker cooldownTracker;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final Monitor monitor;
    private final Duration negotiationTimeout;
    private final Duration transferTimeout;
    private final Duration candidateTimeout;

    /**
     * Acquires access to an asset from the first candidate that answers, de-duplicating concurrent requests
     * for the same participant+asset+candidate set(+forced target)(+client) key. Candidates are tried in the
     * order given, skipping those in cooldown unless every candidate is. A transport fault puts the candidate in
     * cooldown and moves on to the next; any other failure is returned at once, so no other candidate is
     * contacted after a provider answered.
     */
    public CompletableFuture<AssetAccessResult> acquireAssetAccess(ParticipantContext participantContext,
                                                                   AssetAccessRequest request) {
        var key = registryKey(participantContext, request);
        monitor.info("%s acquireAssetAccess entered: candidates=%d protocol=%s"
                .formatted(key, request.candidates().size(), request.protocolOrDefault()));
        return stateStore.loadOrStartInFlight(key,
                () -> acquireFromCandidate(key, participantContext, request,
                        cooldownTracker.selectable(request.candidates(), AssetAccessRequest.Candidate::counterPartyAddress),
                        0, new ArrayList<>()));
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

    private CompletableFuture<AssetAccessResult> acquireFromCandidate(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            List<AssetAccessRequest.Candidate> candidates, int index, List<Throwable> transportFailures) {
        if (index >= candidates.size()) {
            return CompletableFuture.failedFuture(allCandidatesFailed(key, candidates.size(), transportFailures));
        }
        var candidate = candidates.get(index);
        return attemptCandidate(key, participantContext, request, candidate)
                .thenCompose(outcome -> switch (outcome) {
                    case StageOutcome.Succeeded(var dataAddress) -> CompletableFuture.completedFuture(
                            new AssetAccessResult(dataAddress, winnerOf(candidate)));
                    case StageOutcome.Failed(var failureKind, var failure) when failureKind == FailureKind.TRANSPORT_FAULT -> {
                        monitor.warning("%s candidate unreachable, trying next: counterPartyId=%s counterPartyAddress=%s"
                                .formatted(key, candidate.counterPartyId(), candidate.counterPartyAddress()), failure);
                        cooldownTracker.markFailed(candidate.counterPartyAddress());
                        transportFailures.add(unwrap(failure));
                        yield acquireFromCandidate(key, participantContext, request, candidates, index + 1, transportFailures);
                    }
                    case StageOutcome.Failed(_, var failure) -> CompletableFuture.<AssetAccessResult>failedFuture(unwrap(failure));
                });
    }

    private XrdRuntimeException allCandidatesFailed(String key, int candidateCount, List<Throwable> transportFailures) {
        var message = "All %d candidates failed".formatted(candidateCount);
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
        var host = URI.create(candidate.counterPartyAddress()).getHost();
        return new AssetAccessResult.Winner(candidate.counterPartyId(), host != null ? host : candidate.counterPartyAddress());
    }

    private CompletableFuture<StageOutcome<DataAddress>> attemptCandidate(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate) {
        var reusableAgreement = reusableAgreementLookup.find(
                participantContext.getParticipantContextId(), participantContext.getIdentity(),
                request.assetId(), candidate.counterPartyId(), request.clientId());
        CompletableFuture<StageOutcome<DataAddress>> attempt;
        if (reusableAgreement.isPresent()) {
            attempt = transferUnderReusedAgreement(key, participantContext, request, candidate, reusableAgreement.get());
        } else {
            monitor.info("%s no reusable agreement found, negotiating".formatted(key));
            attempt = negotiateAndTransfer(key, participantContext, request, candidate);
        }
        var classified = attempt.exceptionally(
                failure -> new StageOutcome.Failed<>(FailureClassifier.classify(failure), unwrap(failure)));
        return abandonAtCandidateLimit(candidate, classified);
    }

    private CompletableFuture<StageOutcome<DataAddress>> abandonAtCandidateLimit(
            AssetAccessRequest.Candidate candidate, CompletableFuture<StageOutcome<DataAddress>> attempt) {
        var deadline = completionPoller.awaitDeadline(candidateTimeout);
        var abandoned = deadline.thenApply(outcome -> switch (outcome) {
            case StageOutcome.Failed(var failureKind, var failure) when failureKind != FailureKind.TRANSPORT_FAULT ->
                    new StageOutcome.Failed<DataAddress>(failureKind, failure);
            default -> candidateAbandoned(candidate);
        });
        return attempt.applyToEither(abandoned, Function.identity())
                .whenComplete((_, _) -> deadline.cancel(false));
    }

    private StageOutcome<DataAddress> candidateAbandoned(AssetAccessRequest.Candidate candidate) {
        var timeout = new TimeoutException("Candidate %s abandoned after %s"
                .formatted(candidate.counterPartyAddress(), candidateTimeout));
        return new StageOutcome.Failed<>(FailureClassifier.classify(timeout), timeout);
    }

    private CompletableFuture<StageOutcome<DataAddress>> transferUnderReusedAgreement(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, ContractAgreement agreement) {
        monitor.info("%s reusing agreement: agreementId=%s matchedByClientId=%s"
                .formatted(key, agreement.getId(), request.clientId() != null));
        return startTransfer(key, participantContext, agreement, XRoadTransferType.PULL.wireValue(),
                candidate.counterPartyAddress(), request.protocolOrDefault())
                .thenCompose(outcome -> switch (outcome) {
                    case StageOutcome.Succeeded<DataAddress> _ -> CompletableFuture.completedFuture(outcome);
                    case StageOutcome.Failed(var failureKind, var failure) -> isTransferRefusal(failureKind, failure)
                            ? negotiateAfterRefusal(key, participantContext, request, candidate, agreement, failure)
                            : CompletableFuture.completedFuture(outcome);
                });
    }

    private CompletableFuture<StageOutcome<DataAddress>> negotiateAfterRefusal(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, ContractAgreement refusedAgreement, Throwable refusal) {
        monitor.warning("%s reused agreement refused, negotiating afresh: agreementId=%s reason=%s"
                .formatted(key, refusedAgreement.getId(), unwrap(refusal).getMessage()));
        return negotiateAndTransfer(key, participantContext, request, candidate);
    }

    private static boolean isTransferRefusal(FailureKind failureKind, Throwable failure) {
        return failureKind != FailureKind.TRANSPORT_FAULT
                && unwrap(failure) instanceof XrdRuntimeException xrdException && xrdException.isCausedBy(DSP_TRANSFER_FAILED);
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
            AssetAccessRequest.Candidate candidate) {
        return andThen(fetchCatalog(key, participantContext, request, candidate),
                catalog -> acquireFromCatalog(key, participantContext, request, candidate, catalog));
    }

    private CompletableFuture<StageOutcome<DataAddress>> acquireFromCatalog(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, Catalog catalog) {
        var offer = findOffer(key, catalog, request.assetId(), request.clientId());
        return andThen(negotiateContract(key, participantContext, request, candidate, offer),
                agreement -> startTransfer(key, participantContext, agreement, offer.transferType(),
                        candidate.counterPartyAddress(), request.protocolOrDefault()));
    }

    private static <T, R> CompletableFuture<StageOutcome<R>> andThen(
            CompletableFuture<StageOutcome<T>> stage, Function<T, CompletableFuture<StageOutcome<R>>> next) {
        return stage.thenCompose(outcome -> switch (outcome) {
            case StageOutcome.Succeeded<T> succeeded -> next.apply(succeeded.value());
            case StageOutcome.Failed<T> failed ->
                    CompletableFuture.completedFuture(new StageOutcome.Failed<R>(failed.failureKind(), failed.failure()));
        });
    }

    private static QuerySpec datasetQuery(String assetId) {
        return QuerySpec.Builder.newInstance()
                .filter(Criterion.criterion("id", "=", assetId))
                .build();
    }

    CompletableFuture<StageOutcome<Catalog>> fetchCatalog(String key, ParticipantContext participantContext,
                                                          AssetAccessRequest request, AssetAccessRequest.Candidate candidate) {
        monitor.info("%s catalog fetch started".formatted(key));
        try {
            return catalogService.requestCatalog(participantContext, candidate.counterPartyId(), candidate.counterPartyAddress(),
                            request.protocolOrDefault(), datasetQuery(request.assetId()))
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

    private StageOutcome<Catalog> catalogFailed(String key, Throwable failure) {
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
        return PolicySubjectMatcher.namesClient(policy, encodedMemberId)
                || PolicySubjectMatcher.anyConstraint(policy, atomic ->
                        PolicySubjectMatcher.isLiteral(atomic.getLeftExpression(), XRoadPolicyNamespace.XROAD_LOCAL_GROUP)
                                || PolicySubjectMatcher.isLiteral(atomic.getLeftExpression(), XRoadPolicyNamespace.XROAD_GLOBAL_GROUP));
    }

    private CompletableFuture<StageOutcome<ContractAgreement>> negotiateContract(
            String key, ParticipantContext participantContext, AssetAccessRequest request,
            AssetAccessRequest.Candidate candidate, OfferContext offer) {
        try {
            var contractRequest = buildContractRequest(request, candidate, offer);
            var negotiationResult = contractNegotiationService.initiateNegotiation(participantContext, contractRequest);
            var negotiationId = negotiationResult
                    .map(Entity::getId)
                    .orElseThrow(exceptionMapper(ContractNegotiation.class, null));
            monitor.info("%s negotiation initiated, awaiting completion: negotiationId=%s".formatted(key, negotiationId));

            return completionPoller.awaitNegotiation(negotiationId, negotiationTimeout)
                    .whenComplete((outcome, throwable) -> onNegotiationComplete(key, negotiationId, outcome, throwable));
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
            String transferType, String counterPartyAddress, String protocol) {
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

        return completionPoller.awaitTransfer(transferProcessId, transferTimeout)
                .whenComplete((outcome, throwable) -> onTransferComplete(key, transferProcessId, outcome, throwable));
    }

    private void onTransferComplete(String key, String transferProcessId, StageOutcome<DataAddress> outcome, Throwable throwable) {
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

    private record OfferContext(String offerId, Policy policy, Dataset dataset, String transferType) {
    }
}
