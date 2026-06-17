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
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.listener.TransferCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessStateStore.AgreementContext;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_FETCH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_CATALOG_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PULL_DISTRIBUTION_MISSING;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_TRANSFER_FAILED;

/**
 * Orchestrates the full asset access acquisition flow: catalog fetch, offer selection, contract negotiation,
 * transfer process initiation, and data address resolution — using event-driven callbacks instead of polling.
 *
 * <p>Uses singleton {@link NegotiationCompletionListener} and {@link TransferCompletionListener}
 * registered once at extension startup. O(1) event dispatch via ConcurrentHashMap; dead-letter
 * caches inside each listener close the race between {@code initiate*} returning and {@code register}.
 */
@RequiredArgsConstructor
public class AssetAccessOrchestrator {

    private final AssetAccessStateStore stateStore;
    private final CatalogService catalogService;
    private final ContractNegotiationService contractNegotiationService;
    private final TransferProcessService transferProcessService;
    private final NegotiationCompletionListener negotiationListener;
    private final TransferCompletionListener transferListener;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final Monitor monitor;
    private final Duration negotiationTimeout;
    private final Duration transferTimeout;

    /**
     * Acquires access to an asset, de-duplicating concurrent requests for the same participant+asset+counterparty key.
     */
    public CompletableFuture<ServiceResult<DataAddress>> acquireAssetAccess(ParticipantContext participantContext,
                                                                            AssetAccessRequest request) {
        var key = participantContext.getParticipantContextId() + "::" + request.assetId() + "::" + request.counterPartyId();
        monitor.info("%s acquireAssetAccess entered: counterPartyAddress=%s protocol=%s"
                .formatted(key, request.counterPartyAddress(), request.protocolOrDefault()));
        return stateStore.loadOrStartInFlight(key, () -> buildAcquisitionFuture(key, participantContext, request));
    }

    private CompletableFuture<ServiceResult<DataAddress>> buildAcquisitionFuture(
            String key, ParticipantContext participantContext, AssetAccessRequest request) {
        var existingAgreement = stateStore.getAgreement(key);
        if (existingAgreement != null) {
            monitor.info("%s cached-agreement fast path: agreementId=%s transferType=%s"
                    .formatted(key, existingAgreement.agreement().getId(), existingAgreement.transferType()));
            return transferAndAwaitDataAddress(key, participantContext, existingAgreement.agreement(),
                    existingAgreement.transferType(), request.counterPartyAddress(), request.protocolOrDefault())
                    .thenApply(ServiceResult::success);
        }
        return executeAcquisition(participantContext, request, key);
    }

    private CompletableFuture<ServiceResult<DataAddress>> executeAcquisition(
            ParticipantContext participantContext, AssetAccessRequest request, String registryKey) {
        return fetchCatalog(registryKey, participantContext, request)
                .thenApply(catalog -> findOffer(registryKey, catalog, request.assetId()))
                .thenCompose(offer -> negotiateContract(registryKey, participantContext, request, offer)
                        .thenApply(agreement -> {
                            stateStore.recordAgreement(registryKey, agreement, offer.transferType());
                            return new AgreementContext(agreement, offer.transferType());
                        }))
                .thenCompose(ctx -> transferAndAwaitDataAddress(registryKey, participantContext, ctx.agreement(),
                        ctx.transferType(), request.counterPartyAddress(), request.protocolOrDefault()))
                .thenApply(ServiceResult::success);
    }

    private CompletableFuture<Catalog> fetchCatalog(String key, ParticipantContext participantContext, AssetAccessRequest request) {
        monitor.info("%s catalog fetch started".formatted(key));
        try {
            return catalogService.requestCatalog(participantContext, request.counterPartyId(), request.counterPartyAddress(),
                            request.protocolOrDefault(), QuerySpec.none())
                    .thenApply(result -> {
                        if (result.failed()) {
                            monitor.warning("%s catalog fetch failed: %s".formatted(key, result.getFailureDetail()));
                            throw XrdRuntimeException.systemException(DSP_CATALOG_FETCH_FAILED)
                                    .origin(ErrorOrigin.DATASPACE)
                                    .details(result.getFailureDetail())
                                    .build();
                        }
                        var catalog = parseCatalog(key, result.getContent());
                        monitor.debug("%s catalog fetch succeeded: datasets=%d"
                                .formatted(key, catalog.getDatasets().size()));
                        return catalog;
                    });
        } catch (EdcException e) {
            monitor.warning("%s catalog fetch failed with EDC exception".formatted(key), e);
            return CompletableFuture.failedFuture(
                    XrdRuntimeException.systemException(DSP_CATALOG_FETCH_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .cause(e)
                            .details(e.getMessage())
                            .build());
        }
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

    private OfferContext findOffer(String key, Catalog catalog, String assetId) {
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

        var firstOffer = dataset.getOffers().entrySet().iterator().next();

        var transferType = dataset.getDistributions().stream()
                .map(Distribution::getFormat)
                .filter(XRoadTransferType.PULL.wireValue()::equals)
                .findFirst()
                .orElseThrow(() -> XrdRuntimeException.systemException(DSP_PULL_DISTRIBUTION_MISSING)
                        .origin(ErrorOrigin.DATASPACE)
                        .metadataItems(assetId)
                        .build());

        monitor.info("%s offer found: assetId=%s offerId=%s transferType=%s"
                .formatted(key, assetId, firstOffer.getKey(), transferType));
        return new OfferContext(firstOffer.getKey(), firstOffer.getValue(), dataset, transferType);
    }

    private CompletableFuture<ContractAgreement> negotiateContract(String key, ParticipantContext participantContext,
                                                                   AssetAccessRequest request, OfferContext offer) {
        try {
            var contractRequest = buildContractRequest(request, offer);
            var negotiationResult = contractNegotiationService.initiateNegotiation(participantContext, contractRequest);
            var negotiationId = negotiationResult
                    .map(Entity::getId)
                    .orElseThrow(exceptionMapper(ContractNegotiation.class, null));
            monitor.info("%s negotiation initiated: negotiationId=%s".formatted(key, negotiationId));

            var future = new CompletableFuture<ContractAgreement>();
            negotiationListener.register(negotiationId, future);

            return future
                    .orTimeout(negotiationTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((result, throwable) -> onNegotiationComplete(key, negotiationId, result, throwable));
        } catch (Exception e) {
            monitor.warning("%s negotiation initiation failed".formatted(key), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private ContractRequest buildContractRequest(AssetAccessRequest request, OfferContext offer) {
        return ContractRequest.Builder.newInstance()
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
    }

    private void onNegotiationComplete(String key, String negotiationId, ContractAgreement result, Throwable throwable) {
        negotiationListener.deregister(negotiationId);
        if (throwable != null) {
            monitor.warning("%s negotiation failed: negotiationId=%s".formatted(key, negotiationId), throwable);
        } else if (result != null) {
            monitor.info("%s agreement received: agreementId=%s".formatted(key, result.getId()));
        }
    }

    private CompletableFuture<DataAddress> transferAndAwaitDataAddress(
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
            return CompletableFuture.failedFuture(
                    XrdRuntimeException.systemException(DSP_TRANSFER_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .cause(e)
                            .details(e.getMessage())
                            .build());
        }

        if (result.failed()) {
            monitor.warning("%s transfer initiate failed: %s".formatted(key, result.getFailureDetail()));
            return CompletableFuture.failedFuture(
                    XrdRuntimeException.systemException(DSP_TRANSFER_FAILED)
                            .origin(ErrorOrigin.DATASPACE)
                            .details(result.getFailureDetail())
                            .build());
        }

        var transferProcessId = result.getContent().getId();
        monitor.info("%s transfer initiated: transferProcessId=%s".formatted(key, transferProcessId));
        var future = new CompletableFuture<DataAddress>();
        transferListener.register(transferProcessId, future);

        return future
                .orTimeout(transferTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((dataAddress, throwable) -> onTransferComplete(key, transferProcessId, dataAddress, throwable));
    }

    private void onTransferComplete(String key, String transferProcessId, DataAddress dataAddress, Throwable throwable) {
        transferListener.deregister(transferProcessId);
        if (throwable != null) {
            monitor.warning("%s transfer failed: transferProcessId=%s".formatted(key, transferProcessId), throwable);
        } else if (dataAddress != null) {
            monitor.info("%s transfer completed: endpointType=%s".formatted(key, dataAddress.getType()));
            monitor.debug("%s transfer completed DataAddress: %s".formatted(key, dataAddress));
        }
    }

    private record OfferContext(String offerId, Policy policy, Dataset dataset, String transferType) {
    }
}
