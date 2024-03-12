/*
 * The MIT License
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
package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.conf.globalconf.ServerAddressInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.api.management.contractnegotiation.ContractNegotiationApi;
import org.eclipse.edc.connector.api.management.transferprocess.TransferProcessApi;
import org.eclipse.edc.spi.types.domain.asset.Asset;
import org.niis.xroad.edc.management.client.FeignCatalogApi;
import org.niis.xroad.proxy.configuration.ProxyEdcConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.eclipse.edc.catalog.spi.CatalogRequest.CATALOG_REQUEST_COUNTER_PARTY_ADDRESS;
import static org.eclipse.edc.catalog.spi.CatalogRequest.CATALOG_REQUEST_PROTOCOL;
import static org.eclipse.edc.catalog.spi.CatalogRequest.CATALOG_REQUEST_QUERY_SPEC;
import static org.eclipse.edc.catalog.spi.CatalogRequest.CATALOG_REQUEST_TYPE;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_TYPE;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.POLICY;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.PROTOCOL;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_ASSET_ID;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_CONTRACT_ID;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_COUNTER_PARTY_ADDRESS;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_DATA_DESTINATION;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_PROTOCOL;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_OPERAND_LEFT;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_OPERAND_RIGHT;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_OPERATOR;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_TYPE;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_FILTER_EXPRESSION;
import static org.eclipse.edc.spi.query.QuerySpec.EDC_QUERY_SPEC_TYPE;
import static org.eclipse.edc.spi.types.domain.DataAddress.SIMPLE_TYPE;

@Slf4j
@Component
@Conditional(ProxyEdcConfig.DataspacesEnabledCondition.class)
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:MagicNumber")
public class AssetAuthorizationManager {
    private final FeignCatalogApi catalogApi;
    private final ContractNegotiationApi contractNegotiationApi;
    private final TransferProcessApi transferProcessApi;

    private final AssetTransferRegistry assetTransferRegistry;
    private final AuthorizedAssetRegistry authorizedAssetRegistry;

    public AuthorizedAssetRegistry.GrantedAssetInfo getOrRequestAssetAccess(ClientId senderId,
                                                                            ServerAddressInfo providerServerAddress,
                                                                            ServiceId providerServiceId) {
        return authorizedAssetRegistry.getAssetInfo(senderId.asEncodedId(), providerServiceId.asEncodedId())
                .orElseGet(() -> requestAccess(senderId, providerServerAddress, providerServiceId));
    }

    public AuthorizedAssetRegistry.GrantedAssetInfo requestAccess(ClientId senderId,
                                                                  ServerAddressInfo providerServerAddress,
                                                                  ServiceId providerServiceId) {
        var policy = fetchPolicy(providerServerAddress, providerServiceId);
        var contractNegotiationId = initiateContractNegotiation(policy, providerServerAddress);

        var contractAgreementId = getContractAgreementId(contractNegotiationId);

        var transferId = initiateTransfer(contractAgreementId, providerServerAddress, providerServiceId);
        assetTransferRegistry.registerAssetInTransfer(transferId, senderId.asEncodedId(), providerServiceId.asEncodedId());

        return pollTransferCompletion(senderId, providerServiceId);
    }

    private JsonObject fetchPolicy(ServerAddressInfo targetServerInfo, ServiceId serviceId) {
        var catalogFetchRequest = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder()
                        .add(VOCAB, EDC_NAMESPACE)
                        .add(ODRL_PREFIX, ODRL_SCHEMA))
                .add(TYPE, CATALOG_REQUEST_TYPE)
                .add(CATALOG_REQUEST_COUNTER_PARTY_ADDRESS, targetServerInfo.getProtocolUrl())
                .add(CATALOG_REQUEST_PROTOCOL, "dataspace-protocol-http")
                .add(CATALOG_REQUEST_QUERY_SPEC, createArrayBuilder(List.of(createObjectBuilder()
                        .add(TYPE, EDC_QUERY_SPEC_TYPE)
                        .add(EDC_QUERY_SPEC_FILTER_EXPRESSION, createObjectBuilder()
                                .add(TYPE, CRITERION_TYPE)
                                .add(CRITERION_OPERAND_LEFT, Asset.PROPERTY_ID)
                                .add(CRITERION_OPERAND_RIGHT, serviceId.asEncodedId())
                                .add(CRITERION_OPERATOR, "=")))))
                .build();
        var requestCatalogResult = catalogApi.requestCatalogExt(catalogFetchRequest);

        log.info("EDC: Result for request catalog: {}", requestCatalogResult);

        //TODO xroad8 make a safer selection, it changes from object to array if dataset is empty..
        return requestCatalogResult.get("dcat:dataset").asJsonObject().get("odrl:hasPolicy").asJsonObject();
    }

    private String initiateContractNegotiation(JsonObject policy, ServerAddressInfo targetServerInfo) {
        var request = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder()
                        .add(VOCAB, EDC_NAMESPACE)
                        .add(ODRL_PREFIX, ODRL_SCHEMA))
                .add(TYPE, CONTRACT_REQUEST_TYPE)
                .add(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS, targetServerInfo.getProtocolUrl())
                .add(PROTOCOL, "dataspace-protocol-http")
                .add(POLICY, policy)
                .build();

        var initiateContractNegotiationResult = contractNegotiationApi.initiateContractNegotiation(request);
        return initiateContractNegotiationResult.getString(ID);
    }

    @SneakyThrows
    private String getContractAgreementId(String contractNegotiationId) {
        int pollCounter = 0;

        while (pollCounter++ <= 600) {
            var getNegotiationResponse = contractNegotiationApi.getNegotiation(contractNegotiationId);
            log.info("======== getNegotiation: {}", getNegotiationResponse);
            var status = getNegotiationResponse.getString("state");
            if ("VERIFIED".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status) || "FINALIZED".equalsIgnoreCase(status)) {
                return getNegotiationResponse.getString("contractAgreementId");
            }

            Thread.sleep(100L);
        }
        throw new RuntimeException("Failed fetch contractId");
    }

    private String initiateTransfer(String contractAgreementId, ServerAddressInfo targetServerInfo, ServiceId serviceId) {
        var initTransferRequest = createObjectBuilder()
                .add(CONTEXT, createObjectBuilder()
                        .add(VOCAB, EDC_NAMESPACE))
                .add(TYPE, TRANSFER_REQUEST_TYPE)
//                .add(TRANSFER_REQUEST_CONNECTOR_ID, "provider")
                .add(TRANSFER_REQUEST_COUNTER_PARTY_ADDRESS, targetServerInfo.getProtocolUrl())
                .add(TRANSFER_REQUEST_CONTRACT_ID, contractAgreementId)
                .add(TRANSFER_REQUEST_ASSET_ID, serviceId.asEncodedId())
                .add(TRANSFER_REQUEST_PROTOCOL, "dataspace-protocol-http")
                .add(TRANSFER_REQUEST_DATA_DESTINATION, createObjectBuilder().add(SIMPLE_TYPE, "HttpProxy").build())
                .build();

        var initiateTransferProcessResponse = transferProcessApi.initiateTransferProcess(initTransferRequest);
        log.info("======== initiateTransferProcess: {}", initiateTransferProcessResponse);
        return initiateTransferProcessResponse.getString(ID);
    }

    @SneakyThrows
    private void pollTransferCompletion(String transferId) {
        int pollCounter = 0;

        while (pollCounter++ <= 600) {
            var transferProcess = transferProcessApi.getTransferProcess(transferId);
            log.info("======== getTransferProcess: {}", transferProcess);
            var status = transferProcess.getString("state");
            if ("FINISHED".equalsIgnoreCase(status) || "STARTED".equalsIgnoreCase(status)) {
                return;
            }

            Thread.sleep(100L);
        }
        throw new RuntimeException("Download failed");
    }

    @SneakyThrows
    private AuthorizedAssetRegistry.GrantedAssetInfo pollTransferCompletion(ClientId senderId,
                                                                            ServiceId providerServiceId) {
        int pollCounter = 0;

        while (pollCounter++ <= 600) {
            var grantedAssetOpt = authorizedAssetRegistry.getAssetInfo(senderId.asEncodedId(), providerServiceId.asEncodedId());
            if (grantedAssetOpt.isPresent()) {
                return grantedAssetOpt.get();
            }

            Thread.sleep(50L);
        }
        throw new RuntimeException("Download failed");
    }
}
