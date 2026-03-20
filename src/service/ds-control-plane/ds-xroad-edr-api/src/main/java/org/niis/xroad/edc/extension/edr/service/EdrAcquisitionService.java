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

package org.niis.xroad.edc.extension.edr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.edc.extension.edr.EdrRequest;
import org.niis.xroad.edc.extension.edr.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.edr.listener.TransferCompletionListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the full EDR acquisition flow: catalog fetch, offer selection, contract negotiation,
 * transfer process initiation, and EDR resolution — using event-driven callbacks instead of polling.
 *
 * <p>Uses singleton {@link NegotiationCompletionListener} and {@link TransferCompletionListener}
 * registered once at extension startup, rather than per-request anonymous listeners. This provides
 * O(1) event dispatch via ConcurrentHashMap and eliminates the TOCTOU race condition.
 */
@RequiredArgsConstructor
public class EdrAcquisitionService {

    private static final long TIMEOUT_SECONDS = 60;

    private final ConcurrentHashMap<String, CompletableFuture<ServiceResult<DataAddress>>> inFlightRequests = new ConcurrentHashMap<>();

    private final CatalogService catalogService;
    private final ContractNegotiationService contractNegotiationService;
    private final TransferProcessService transferProcessService;
    private final EndpointDataReferenceStore edrStore;
    private final NegotiationCompletionListener negotiationListener;
    private final TransferCompletionListener transferListener;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final ObjectMapper objectMapper;

    public CompletableFuture<ServiceResult<DataAddress>> acquireEdr(ParticipantContext participantContext, EdrRequest request) {
        // check if an EDR already exists for this participant + asset + provider
        var existingEdr = findExistingEdr(participantContext.getParticipantContextId(), request.assetId(), request.counterPartyId());
        if (existingEdr != null) {
            return CompletableFuture.completedFuture(ServiceResult.success(existingEdr));
        }

        // deduplicate concurrent requests for the same participant + asset + provider
        var key = participantContext.getParticipantContextId() + "::" + request.assetId() + "::" + request.counterPartyId();
        return inFlightRequests.computeIfAbsent(key, k -> executeAcquisition(participantContext, request)
                .whenComplete((result, throwable) -> inFlightRequests.remove(key)));
    }

    private CompletableFuture<ServiceResult<DataAddress>> executeAcquisition(ParticipantContext participantContext, EdrRequest request) {
        return fetchCatalog(participantContext, request)
                .thenApply(catalog -> findOffer(catalog, request.assetId()))
                .thenCompose(offer -> negotiateContract(participantContext, request, offer)
                        .thenApply(agreement -> new AgreementContext(agreement, offer.transferType())))
                .thenCompose(ctx -> transferAndAwaitEdr(participantContext, ctx.agreement(),
                        ctx.transferType(), request.counterPartyAddress(), request.protocolOrDefault()))
                .thenApply(ServiceResult::success);
    }

    private DataAddress findExistingEdr(String participantId, String assetId, String providerId) {
        var query = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantContextId", "=", participantId))
                .filter(new Criterion("assetId", "=", assetId))
                .filter(new Criterion("providerId", "=", providerId))
                .limit(1)
                .build();

        var result = edrStore.query(query);
        if (result.succeeded() && !result.getContent().isEmpty()) {
            var entry = result.getContent().getFirst();
            var edr = edrStore.resolveByTransferProcess(entry.getTransferProcessId());
            if (edr.succeeded()) {
                return edr.getContent();
            }
        }
        return null;
    }

    private CompletableFuture<Catalog> fetchCatalog(ParticipantContext participantContext, EdrRequest request) {
        return catalogService.requestCatalog(participantContext, request.counterPartyId(), request.counterPartyAddress(),
                        request.protocolOrDefault(), QuerySpec.none())
                .thenApply(result -> {
                    if (result.failed()) {
                        throw new EdcException("Failed to fetch catalog: %s".formatted(result.getFailureDetail()));
                    }
                    return parseCatalog(result.getContent());
                });
    }

    private Catalog parseCatalog(byte[] catalogBytes) {
        try {
            var json = new String(catalogBytes);
            var catalogJsonObject = objectMapper.readValue(json, JsonObject.class);
            return jsonLd.expand(catalogJsonObject)
                    .compose(expanded -> transformerRegistry.transform(expanded, Catalog.class))
                    .orElseThrow(failure -> new EdcException("Failed to parse catalog: %s".formatted(failure.getFailureDetail())));
        } catch (Exception e) {
            throw new EdcException("Error parsing catalog response", e);
        }
    }

    private OfferContext findOffer(Catalog catalog, String assetId) {
        var dataset = catalog.getDatasets().stream()
                .filter(ds -> assetId.equals(ds.getId()))
                .findFirst()
                .orElseThrow(() -> new EdcException("No dataset found for asset ID: %s".formatted(assetId)));

        if (!dataset.hasOffers()) {
            throw new EdcException("No offers found for asset ID: %s".formatted(assetId));
        }

        var firstOffer = dataset.getOffers().entrySet().iterator().next();

        // find a PULL distribution from the dataset
        var transferType = dataset.getDistributions().stream()
                .map(Distribution::getFormat)
                .filter(f -> f != null && f.contains("PULL")) //"Xrd-PULL".equals(f)
                .findFirst()
                .orElseThrow(() -> new EdcException("No PULL distribution found for asset ID: %s".formatted(assetId)));

        return new OfferContext(firstOffer.getKey(), firstOffer.getValue(), dataset, transferType);
    }

    /**
     * Initiates contract negotiation and registers on the shared listener to await the terminal event.
     * The negotiation ID is obtained first, then registered with the listener — the dead-letter map
     * in the listener handles the sub-microsecond window between initiation returning and registration.
     */
    private CompletableFuture<ContractAgreement> negotiateContract(ParticipantContext participantContext,
                                                                   EdrRequest request, OfferContext offer) {
        try {
            var contractRequest = ContractRequest.Builder.newInstance()
                    .protocol(request.protocolOrDefault())
                    .counterPartyAddress(request.counterPartyAddress())
                    .contractOffer(ContractOffer.Builder.newInstance()
                            .id(offer.offerId())
                            .assetId(offer.dataset().getId())
                            .policy(offer.policy().toBuilder()
                                    .target(offer.dataset().getId())
                                    .assigner(request.counterPartyId())
                                    .type(PolicyType.OFFER)
                                    .build())
                            .build())
                    .build();

            var negotiationResult = contractNegotiationService.initiateNegotiation(participantContext, contractRequest);
            if (negotiationResult.failed()) {
                return CompletableFuture.failedFuture(
                        new EdcException("Could not initiate contract negotiation: %s".formatted(negotiationResult.getFailureDetail())));
            }
            var negotiationId = negotiationResult.getContent().getId();

            var future = new CompletableFuture<ContractAgreement>();
            negotiationListener.register(negotiationId, future);

            return future
                    .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((result, throwable) -> negotiationListener.deregister(negotiationId));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Initiates the transfer process and registers on the shared listener to await the started event.
     * The transfer process ID is obtained first, then registered with the listener — the dead-letter map
     * in the listener handles the sub-microsecond window between initiation returning and registration.
     */
    private CompletableFuture<DataAddress> transferAndAwaitEdr(ParticipantContext participantContext, ContractAgreement agreement,
                                                               String transferType, String counterPartyAddress, String protocol) {
        var transferRequest = TransferRequest.Builder.newInstance()
                .contractId(agreement.getId())
                .counterPartyAddress(counterPartyAddress)
                .protocol(protocol)
                .transferType(transferType)
                .build();

        var result = transferProcessService.initiateTransfer(participantContext, transferRequest);
        if (result.failed()) {
            return CompletableFuture.failedFuture(
                    new EdcException("Could not start transfer process: %s".formatted(result.getFailureDetail())));
        }

        var transferProcessId = result.getContent().getId();
        var future = new CompletableFuture<DataAddress>();
        transferListener.register(transferProcessId, future);

        return future
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((r, throwable) -> transferListener.deregister(transferProcessId));
    }

    private record OfferContext(String offerId, Policy policy, Dataset dataset, String transferType) {
    }

    private record AgreementContext(ContractAgreement agreement, String transferType) {
    }
}
