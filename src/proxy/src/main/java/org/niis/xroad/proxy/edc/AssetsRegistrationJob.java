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

package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import feign.FeignException;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.api.management.contractdefinition.ContractDefinitionApi;
import org.eclipse.edc.connector.api.management.policy.PolicyDefinitionApi;
import org.eclipse.edc.connector.dataplane.selector.api.v2.DataplaneSelectorApi;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.types.domain.asset.Asset;
import org.niis.xroad.proxy.configuration.ProxyEdcConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition.CONTRACT_DEFINITION_ACCESSPOLICY_ID;
import static org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition.CONTRACT_DEFINITION_ASSETS_SELECTOR;
import static org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition.CONTRACT_DEFINITION_CONTRACTPOLICY_ID;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_DEST_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.ALLOWED_SOURCE_TYPES;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_TYPE;
import static org.eclipse.edc.connector.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_POLICY;
import static org.eclipse.edc.connector.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_OPERAND_LEFT;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_OPERAND_RIGHT;
import static org.eclipse.edc.spi.query.Criterion.CRITERION_OPERATOR;
import static org.eclipse.edc.spi.types.domain.asset.Asset.EDC_ASSET_DATA_ADDRESS;
import static org.eclipse.edc.spi.types.domain.asset.Asset.EDC_ASSET_PROPERTIES;
import static org.eclipse.edc.spi.types.domain.asset.Asset.EDC_ASSET_TYPE;
import static org.eclipse.edc.spi.types.domain.asset.Asset.PROPERTY_CONTENT_TYPE;
import static org.eclipse.edc.spi.types.domain.asset.Asset.PROPERTY_NAME;

@RequiredArgsConstructor
@Component
@Conditional(ProxyEdcConfig.DataspacesEnabledCondition.class)
@Slf4j
public class AssetsRegistrationJob {

    private final DataplaneSelectorApi dataplaneSelectorApi;
    private final AssetApi assetApi;
    private final PolicyDefinitionApi policyDefinitionApi;
    private final ContractDefinitionApi contractDefinitionApi;

    private final String providerDataplaneId = "http-provider-dataplane";
    private final String allAllowedPolicyId = "allow-all-policy";

    @PostConstruct
    public void registerDataPlane() {
        // todo: recheck
        log.info("Creating dataplane");
        dataplaneSelectorApi.addEntry(createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(TYPE, DATAPLANE_INSTANCE_TYPE)
                .add(ID, providerDataplaneId)

                .add(DataPlaneInstance.URL, "http://%s:%s/control/transfer"
                        .formatted(GlobalConf.getSecurityServerAddress(ServerConf.getIdentifier()),
                                SystemProperties.dataspacesControlListenPort()))
                .add(ALLOWED_SOURCE_TYPES, createArrayBuilder()
                        .add("HttpData")
                        .build())
                .add(ALLOWED_DEST_TYPES, createArrayBuilder()
                        .add("HttpData")
                        .add("HttpProxy")
                        .build())
                .add(DataPlaneInstance.PROPERTIES, createObjectBuilder()
                        .add("https://w3id.org/edc/v0.0.1/ns/publicApiUrl", "http://%s:%s/xroad/public/"
                                .formatted(GlobalConf.getSecurityServerAddress(ServerConf.getIdentifier()),
                                        SystemProperties.dataspacesPublicListenPort()))
                        .build())
                .build()
        );

    }

    @Scheduled(initialDelay = 1, fixedDelay = 5 * 60, timeUnit = SECONDS) //every 5 minutes;
    public void registerAssets() throws Exception {
        createAllowAllPolicy();
        createAssets();
    }

    private void createAllowAllPolicy() {
        // todo: all allowed policy for poc
        try {
            policyDefinitionApi.getPolicyDefinition(allAllowedPolicyId);
            log.info("Policy definition {} exists.", allAllowedPolicyId);
        } catch (FeignException.NotFound notFound) {
            // policy does not exist, create new
            log.info("Creating new policy definition {}", allAllowedPolicyId);
            var createPolicyDefitinionResponse = policyDefinitionApi.createPolicyDefinition(createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                    .add(TYPE, EDC_POLICY_DEFINITION_TYPE)
                    .add(ID, allAllowedPolicyId)
                    .add(EDC_POLICY_DEFINITION_POLICY, createObjectBuilder()
                            .add("type", PolicyType.SET.getType()))
                    .build());
            log.info("Policy definition {} created. Api response: {}", allAllowedPolicyId, createPolicyDefitinionResponse);
        }
    }

    private void createAssets() throws Exception {
        log.info("Creating assets");
        for (ClientId.Conf member : ServerConf.getMembers()) {
            for (ServiceId.Conf service : ServerConf.getAllServices(member)) {
                String assetId = service.asEncodedId();
                log.info("Processing service {}", assetId);
                if (!assetExists(assetId)) {
                    createAsset(service, assetId);
                    createContractDefinitionForAsset(assetId);
                }
            }
        }
    }

    private void createAsset(ServiceId.Conf service, String assetId) {
        log.info("Creating asset for service {}", assetId);
        assetApi.createAsset(createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(TYPE, EDC_ASSET_TYPE)
                .add(ID, assetId)
                .add(EDC_ASSET_PROPERTIES, createObjectBuilder()
                        .add(PROPERTY_NAME, "Asset for service %s".formatted(assetId))
                        .add(PROPERTY_CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .build())
                .add(EDC_ASSET_DATA_ADDRESS, createObjectBuilder()
                        .add("type", "HttpData")
                        .add("proxyPath", Boolean.TRUE.toString())
                        .add("proxyMethod", Boolean.TRUE.toString())
                        .add("proxyBody", Boolean.TRUE.toString())
                        .add("proxyQueryParams", Boolean.TRUE.toString())
                        .add("baseUrl", ServerConf.getServiceAddress(service))
                        //pass custom parameters
                        .add("assetId", assetId)
                        .build())
                .build());
    }

    private void createContractDefinitionForAsset(String assetId) {
        log.info("Creating contract definition for asset {}", assetId);
        contractDefinitionApi.createContractDefinition(createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(ID, "%s-contract-definition".formatted(assetId))
                .add(CONTRACT_DEFINITION_ACCESSPOLICY_ID, allAllowedPolicyId)
                .add(CONTRACT_DEFINITION_CONTRACTPOLICY_ID, allAllowedPolicyId)
                .add(CONTRACT_DEFINITION_ASSETS_SELECTOR, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(CRITERION_OPERAND_LEFT, Asset.PROPERTY_ID)
                                .add(CRITERION_OPERATOR, "=")
                                .add(CRITERION_OPERAND_RIGHT, assetId)))
                .build());
    }

    private boolean assetExists(String assetId) {
        try {
            assetApi.getAsset(assetId);
            return true;
        } catch (FeignException.NotFound notFound) {
            return false;
        }
    }

}