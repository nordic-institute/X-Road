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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.eclipse.edc.catalog.spi.Catalog;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.spi.catalog.CatalogService;
import org.eclipse.edc.connector.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.types.domain.asset.Asset;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.eclipse.edc.spi.types.domain.edr.EndpointDataReference;
import org.eclipse.edc.spi.types.domain.offer.ContractOffer;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.eclipse.edc.spi.query.Criterion.criterion;

@RequiredArgsConstructor
public class AssetAuthorizationManager {

    private static final long TIMEOUT_SECONDS = 60; // todo: parameterize?

    private final CatalogService catalogService;
    private final ContractNegotiationService contractNegotiationService;
    private final TypeTransformerRegistry transformerRegistry;
    private final AuthorizedAssetRegistry authorizedAssetRegistry;
    private final AssetInProgressRegistry inProgressRegistry;

    private final TitaniumJsonLd jsonLd = new TitaniumJsonLd(new ConsoleMonitor());
    private final CallbackAddress negotiationCallback = CallbackAddress.Builder.newInstance()
            .uri("local://x-road")
            .events(Set.of("contract.negotiation.finalized"))
            .build();

    public EndpointDataReference getOrRequestAssetAccess(NegotiateAssetRequestDto requestDto) {

        return authorizedAssetRegistry.getAssetInfo(requestDto.getClientId(), requestDto.getAssetId())
                .orElseGet(() -> requestAccess(requestDto));
    }

    public EndpointDataReference requestAccess(NegotiateAssetRequestDto requestDto) {

        var clientId = requestDto.getClientId();
        var serviceId = requestDto.getAssetId();
        var counterPartyId = requestDto.getCounterPartyId();
        var providerServerAddress = requestDto.getCounterPartyAddress();

        var catalog = getCatalog(serviceId, counterPartyId, providerServerAddress);
        var contractOffer = createContractOffer(serviceId, catalog);

        String negotiationId = initiateNegotiation(providerServerAddress, contractOffer);

        CompletableFuture<Void> future = inProgressRegistry.register(negotiationId, clientId, serviceId);

        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return authorizedAssetRegistry.getAssetInfo(clientId, serviceId)
                    .orElseThrow(() -> new EdcException("Failed to get EndpointDataReference for asset"));
        } catch (Exception e) {
            throw new EdcException("Failed to get asset info", e);
        }
    }

    @SneakyThrows
    private Catalog getCatalog(String assetId, String counterPartyId, String counterPartyAddress) {
        CompletableFuture<StatusResult<byte[]>> result = catalogService.requestCatalog(counterPartyId,
                counterPartyAddress,
                "dataspace-protocol-http", assetIdQuery(assetId));

        byte[] content = result.get().getContent();
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(content))) {
            JsonObject jsonObject = jsonLd.expand(reader.readObject()).getContent();
            return transformerRegistry.transform(jsonObject, Catalog.class)
                    .orElseThrow(e -> new EdcException("Failed to transform catalog: " + e.getFailureDetail()));
        }
    }

    private ContractOffer createContractOffer(String assetId, Catalog catalog) {
        if (catalog.getDatasets() == null) {
            throw new EdcException("Failed to get datasets from catalog");
        }
        return catalog.getDatasets().stream()
                .filter(ds -> ds.getId().equals(assetId))
                .findFirst()
                .map(d -> d.getOffers().entrySet().stream().findFirst())
                .flatMap(entry -> entry)
                .map(entry -> ContractOffer.Builder.newInstance()
                        .id(entry.getKey())
                        .policy(entry.getValue())
                        .assetId(assetId)
                        .build())
                .orElseThrow(() -> new EdcException("Failed to get contract offer"));
    }

    private QuerySpec assetIdQuery(String assetId) {
        return QuerySpec.Builder.newInstance()
                .filter(criterion(Asset.PROPERTY_ID, "=", assetId))
                .build();
    }

    private String initiateNegotiation(String counterPartyAddress, ContractOffer contractOffer) {
        ContractRequest contractRequest = ContractRequest.Builder.newInstance()
                .protocol("dataspace-protocol-http")
                .counterPartyAddress(counterPartyAddress)
                .contractOffer(contractOffer)
                .callbackAddresses(List.of(negotiationCallback))
                .build();

        ContractNegotiation contractNegotiation = contractNegotiationService.initiateNegotiation(contractRequest);
        return contractNegotiation.getId();
    }

}
