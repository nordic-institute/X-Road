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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.entity.Entity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.listener.TransferCompletionListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

/**
 * Orchestrates the full asset access acquisition flow: catalog fetch, offer selection, contract negotiation,
 * transfer process initiation, and data address resolution — using event-driven callbacks instead of polling.
 *
 * <p>Uses singleton {@link NegotiationCompletionListener} and {@link TransferCompletionListener}
 * registered once at extension startup, rather than per-request anonymous listeners. This provides
 * O(1) event dispatch via ConcurrentHashMap and eliminates the TOCTOU race condition.
 */
@RequiredArgsConstructor
public class AssetAccessAcquisitionService {

    private static final long TIMEOUT_SECONDS = 60;

    private final ConcurrentHashMap<String, CompletableFuture<ServiceResult<DataAddress>>> inFlightRequests = new ConcurrentHashMap<>();

    private final CatalogService catalogService;
    private final ContractNegotiationService contractNegotiationService;
    private final TransferProcessService transferProcessService;
    private final NegotiationCompletionListener negotiationListener;
    private final TransferCompletionListener transferListener;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final ObjectMapper objectMapper;
    private final Monitor monitor;

    private final ConcurrentHashMap<String, AgreementContext> agreementRegistry = new ConcurrentHashMap<>();

    public CompletableFuture<ServiceResult<DataAddress>> acquireAssetAccess(ParticipantContext participantContext,
                                                                            AssetAccessRequest request) {
        var key = participantContext.getParticipantContextId() + "::" + request.assetId() + "::" + request.counterPartyId();
        monitor.info("%s acquireAssetAccess entered: counterPartyAddress=%s protocol=%s"
                .formatted(key, request.counterPartyAddress(), request.protocolOrDefault()));
        return inFlightRequests.computeIfAbsent(key, k -> {
            var existingAgreement = agreementRegistry.get(key);
            CompletableFuture<ServiceResult<DataAddress>> future;
            if (existingAgreement != null) {
                monitor.info("%s cached-agreement fast path: agreementId=%s transferType=%s"
                        .formatted(key, existingAgreement.agreement().getId(), existingAgreement.transferType()));
                future = transferAndAwaitDataAddress(key, participantContext, existingAgreement.agreement(),
                        existingAgreement.transferType(), request.counterPartyAddress(), request.protocolOrDefault())
                        .thenApply(ServiceResult::success);
            } else {
                future = executeAcquisition(participantContext, request, key);
            }
            return future.whenComplete((result, throwable) -> inFlightRequests.remove(key));
        });
    }

    private CompletableFuture<ServiceResult<DataAddress>> executeAcquisition(ParticipantContext participantContext,
                                                                             AssetAccessRequest request, String registryKey) {
        return fetchCatalog(registryKey, participantContext, request)
                .thenApply(catalog -> findOffer(registryKey, catalog, request.assetId()))
                .thenCompose(offer -> negotiateContract(registryKey, participantContext, request, offer)
                        .thenApply(agreement -> {
                            var ctx = new AgreementContext(agreement, offer.transferType());
                            agreementRegistry.put(registryKey, ctx);
                            return ctx;
                        }))
                .thenCompose(ctx -> transferAndAwaitDataAddress(registryKey, participantContext, ctx.agreement(),
                        ctx.transferType(), request.counterPartyAddress(), request.protocolOrDefault()))
                .thenApply(ServiceResult::success);
    }

    private CompletableFuture<Catalog> fetchCatalog(String key, ParticipantContext participantContext, AssetAccessRequest request) {
        monitor.info("%s catalog fetch started".formatted(key));
        return catalogService.requestCatalog(participantContext, request.counterPartyId(), request.counterPartyAddress(),
                        request.protocolOrDefault(), QuerySpec.none())
                .thenApply(result -> {
                    if (result.failed()) {
                        monitor.warning("%s catalog fetch failed: %s".formatted(key, result.getFailureDetail()));
                        throw new EdcException("Failed to fetch catalog: %s".formatted(result.getFailureDetail()));
                    }
                    var catalog = parseCatalog(key, result.getContent());
                    monitor.debug("%s catalog fetch succeeded: datasets=%d"
                            .formatted(key, catalog.getDatasets().size()));
                    return catalog;
                });
    }

    private Catalog parseCatalog(String key, byte[] catalogBytes) {
        try {
            var json = new String(catalogBytes);
            var catalogJsonObject = objectMapper.readValue(json, JsonObject.class);
            return jsonLd.expand(catalogJsonObject)
                    .compose(expanded -> transformerRegistry.transform(expanded, Catalog.class))
                    .orElseThrow(failure -> {
                        monitor.warning("%s catalog parse failed: %s".formatted(key, failure.getFailureDetail()));
                        return new EdcException("Failed to parse catalog: %s".formatted(failure.getFailureDetail()));
                    });
        } catch (Exception e) {
            monitor.warning("%s catalog parse failed".formatted(key), e);
            throw new EdcException("Error parsing catalog response", e);
        }
    }

    private OfferContext findOffer(String key, Catalog catalog, String assetId) {
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
                .filter(f -> "Xrd-PULL".equals(f))
                .findFirst()
                .orElseThrow(() -> new EdcException("No PULL distribution found for asset ID: %s".formatted(assetId)));

        monitor.info("%s offer found: assetId=%s offerId=%s transferType=%s"
                .formatted(key, assetId, firstOffer.getKey(), transferType));
        return new OfferContext(firstOffer.getKey(), firstOffer.getValue(), dataset, transferType);
    }

    /**
     * Initiates contract negotiation and registers on the shared listener to await the terminal event.
     * The negotiation ID is obtained first, then registered with the listener — the dead-letter map
     * in the listener handles the sub-microsecond window between initiation returning and registration.
     */
    private CompletableFuture<ContractAgreement> negotiateContract(String key, ParticipantContext participantContext,
                                                                   AssetAccessRequest request, OfferContext offer) {
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
            var negotiationId = negotiationResult
                    .map(Entity::getId)
                    .orElseThrow(exceptionMapper(ContractNegotiation.class, null));
            monitor.info("%s negotiation initiated: negotiationId=%s".formatted(key, negotiationId));

            var future = new CompletableFuture<ContractAgreement>();
            negotiationListener.register(negotiationId, future);

            return future
                    .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((result, throwable) -> {
                        negotiationListener.deregister(negotiationId);
                        if (throwable != null) {
                                monitor.warning("%s negotiation failed: negotiationId=%s"
                                    .formatted(key, negotiationId), throwable);
                        } else if (result != null) {
                                monitor.info("%s agreement received: agreementId=%s"
                                    .formatted(key, result.getId()));
                        }
                    });
        } catch (Exception e) {
            monitor.warning("%s negotiation initiation failed".formatted(key), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Initiates the transfer process and registers on the shared listener to await the started event.
     * The transfer process ID is obtained first, then registered with the listener — the dead-letter map
     * in the listener handles the sub-microsecond window between initiation returning and registration.
     */
    private CompletableFuture<DataAddress> transferAndAwaitDataAddress(String key, ParticipantContext participantContext,
                                                               ContractAgreement agreement,
                                                               String transferType, String counterPartyAddress, String protocol) {
        var transferRequest = TransferRequest.Builder.newInstance()
                .contractId(agreement.getId())
                .counterPartyAddress(counterPartyAddress)
                .protocol(protocol)
                .transferType(transferType)
                .build();

        var result = transferProcessService.initiateTransfer(participantContext, transferRequest);
        if (result.failed()) {
            monitor.warning("%s transfer initiate failed: %s".formatted(key, result.getFailureDetail()));
            return CompletableFuture.failedFuture(
                    new EdcException("Could not start transfer process: %s".formatted(result.getFailureDetail())));
        }

        var transferProcessId = result.getContent().getId();
        monitor.info("%s transfer initiated: transferProcessId=%s".formatted(key, transferProcessId));
        var future = new CompletableFuture<DataAddress>();
        transferListener.register(transferProcessId, future);

        return future
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((dataAddress, throwable) -> {
                    transferListener.deregister(transferProcessId);
                    if (throwable != null) {
                        monitor.warning("%s transfer failed: transferProcessId=%s"
                                .formatted(key, transferProcessId), throwable);
                    } else if (dataAddress != null) {
                        // Log endpoint type only at info level; full DataAddress may carry a bearer token.
                        monitor.info("%s transfer completed: endpointType=%s"
                                .formatted(key, dataAddress.getType()));
                        monitor.debug("%s transfer completed DataAddress: %s".formatted(key, dataAddress));
                    }
                });
    }

    private record OfferContext(String offerId, Policy policy, Dataset dataset, String transferType) {
    }

    private record AgreementContext(ContractAgreement agreement, String transferType) {
    }
}
