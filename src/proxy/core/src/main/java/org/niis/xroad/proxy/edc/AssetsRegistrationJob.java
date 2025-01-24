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
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.AccessRightPath;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import feign.FeignException;
import jakarta.annotation.PostConstruct;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.connector.controlplane.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.controlplane.api.management.contractdefinition.v3.ContractDefinitionApiV3;
import org.eclipse.edc.connector.controlplane.api.management.policy.transform.JsonObjectFromPolicyDefinitionTransformer;
import org.eclipse.edc.connector.controlplane.api.management.policy.transform.JsonObjectToPolicyDefinitionTransformer;
import org.eclipse.edc.connector.controlplane.api.management.policy.v3.PolicyDefinitionApiV3;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.transform.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.to.JsonObjectToPolicyTransformer;
import org.eclipse.edc.connector.core.agent.NoOpParticipantIdMapper;
import org.eclipse.edc.connector.dataplane.selector.control.api.DataplaneSelectorControlApi;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.transform.TransformerContextImpl;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.niis.xroad.proxy.configuration.ProxyEdcControlPlaneConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance.DATAPLANE_INSTANCE_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.Criterion.criterion;

@Component
@Conditional(ProxyEdcControlPlaneConfig.DataspacesEnabledCondition.class)
@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class AssetsRegistrationJob {
    private static final int FIVE_MINUTES = 5 * 60;

    private static final String XROAD_NAMESPACE = "https://x-road.eu/v0.1/ns/";
    static final String XROAD_JOB_MANAGED_PROPERTY = XROAD_NAMESPACE + "xroadJobManaged";

    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;

    private final DataplaneSelectorControlApi dataplaneSelectorControlApi;
    private final AssetApi assetApi;
    private final PolicyDefinitionApiV3 policyDefinitionApi;
    private final ContractDefinitionApiV3 contractDefinitionApi;
    private final TransformerContext context;

    private final ParticipantIdMapper participantIdMapper = new NoOpParticipantIdMapper();
    private final String providerDataplaneId = "http-provider-dataplane";

    public AssetsRegistrationJob(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                                 DataplaneSelectorControlApi dataplaneSelectorControlApi, AssetApi assetApi,
                                 PolicyDefinitionApiV3 policyDefinitionApi, ContractDefinitionApiV3 contractDefinitionApi) {
        this.globalConfProvider = globalConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.dataplaneSelectorControlApi = dataplaneSelectorControlApi;
        this.assetApi = assetApi;
        this.policyDefinitionApi = policyDefinitionApi;
        this.contractDefinitionApi = contractDefinitionApi;
        this.context = new TransformerContextImpl(registerTransformers());
    }

    private TypeTransformerRegistry registerTransformers() {
        var jsonLdObjectMapper = JacksonJsonLd.createObjectMapper();
        var registry = new TypeTransformerRegistryImpl();
        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());

        registry.register(new JsonValueToGenericTypeTransformer(jsonLdObjectMapper));

        registry.register(new JsonObjectToPolicyTransformer(participantIdMapper));
        registry.register(new JsonObjectFromPolicyTransformer(jsonBuilderFactory, participantIdMapper));

        registry.register(new JsonObjectToPolicyDefinitionTransformer());
        registry.register(new JsonObjectFromPolicyDefinitionTransformer(jsonBuilderFactory, JacksonJsonLd.createObjectMapper()));

        registry.register(new JsonObjectToQuerySpecTransformer());
        registry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));

        registry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));
        registry.register(new JsonObjectFromCriterionTransformer(jsonBuilderFactory, jsonLdObjectMapper));

        return registry;
    }

    @PostConstruct
    @SuppressWarnings("checkstyle:MagicNumber")
    public void afterPropertiesSet() {
        //TODO disabled for now. Initialized by hurl
        //scheduler.schedule(this::registerDataPlane, 5, SECONDS);
    }

    public void registerDataPlane() {
        // todo: recheck
        try {
            log.info("Creating dataplane");
            dataplaneSelectorControlApi.registerDataplane(createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                    .add(TYPE, DATAPLANE_INSTANCE_TYPE)
                    .add(ID, providerDataplaneId)

                    .add(DataPlaneInstance.URL, "%s://%s:%s/control/v1/dataflows"
                            .formatted(SystemProperties.isSslEnabled() ? "https" : "http",
                                    globalConfProvider.getSecurityServerAddress(serverConfProvider.getIdentifier()),
                                    SystemProperties.dataspacesDataPlaneControlListenPort()))
                    .add(DataPlaneInstance.ALLOWED_SOURCE_TYPES, createArrayBuilder()
                            .add("HttpData")
                            .build())
                    .add(DataPlaneInstance.ALLOWED_DEST_TYPES, createArrayBuilder()
                            .add("HttpData")
                            .build())
                    .add(DataPlaneInstance.ALLOWED_TRANSFER_TYPES, createArrayBuilder()
                            .add("XrdHttpData-PULL")
                            .build())
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to create dataplane and its assets", e);
        }
    }

    @Scheduled(initialDelay = FIVE_MINUTES, fixedDelay = FIVE_MINUTES, timeUnit = SECONDS)
    public void registerAssets() throws Exception {
        final JobContext jobContext = fetchAllJobManagedIds();
        process(jobContext);
        deleteNotRelevantObjects(jobContext);
    }

    private JobContext fetchAllJobManagedIds() {
        JobContext jobContext = new JobContext();
        Criterion criterion = criterion("privateProperties.'%s'".formatted(XROAD_JOB_MANAGED_PROPERTY),
                "=",
                Boolean.TRUE.toString());

        assetApi.requestAssetsV3(toJsonObject(QuerySpec.Builder.newInstance()
                        .filter(criterion)
                        .build()))
                .stream()
                .map(x -> (JsonObject) x)
                .map(x -> x.getString(ID))
                .forEach(jobContext.assetIds::add);

        // query policy and contract definitions by property filter is not supported in EDC, using filter.
        Predicate<JsonObject> xRoadManagedObjectFilter = jsonObject -> {
            try {
                return Boolean.TRUE.toString().equals(
                        jsonObject.get("privateProperties").asJsonObject().getString(XROAD_JOB_MANAGED_PROPERTY));
            } catch (Exception e) {
                return false;
            }
        };

        policyDefinitionApi.queryPolicyDefinitionsV3(toJsonObject(QuerySpec.Builder.newInstance()
                        // .filter(criterion)  // todo: edc: Querying Map types is not yet supported
                        .build()))
                .stream()
                .map(x -> (JsonObject) x)
                .filter(xRoadManagedObjectFilter)
                .map(x -> x.getString(ID))
                .forEach(jobContext.policyDefinitionIds::add);

        contractDefinitionApi.queryContractDefinitionsV3(toJsonObject(QuerySpec.Builder.newInstance()
                        // .filter(criterion) // todo: edc: Querying Map types is not yet supported
                        .build()))
                .stream()
                .map(x -> (JsonObject) x)
                .filter(xRoadManagedObjectFilter)
                .map(x -> x.getString(ID))
                .forEach(jobContext.contractDefinitionIds::add);

        return jobContext;
    }

    private void deleteNotRelevantObjects(JobContext jobContext) {
        jobContext.assetIds.forEach(assetId -> {
            try {
                assetApi.removeAssetV3(assetId);
            } catch (Exception e) {
                log.info("Error deleting asset [{}]", assetId, e);
            }
        });

        jobContext.policyDefinitionIds.forEach(policyId -> {
            try {
                policyDefinitionApi.deletePolicyDefinitionV3(policyId);
            } catch (Exception e) {
                log.info("Error deleting policy definition [{}]", policyId, e);
            }
        });

        jobContext.contractDefinitionIds.forEach(contractId -> {
            try {
                contractDefinitionApi.deleteContractDefinitionV3(contractId);
            } catch (Exception e) {
                log.info("Error deleting contract definition [{}]", contractId, e);
            }
        });

    }

    private void process(JobContext jobContext) throws Exception {
        log.info("Processing services");
        for (ClientId.Conf member : serverConfProvider.getMembers()) {
            for (ServiceId.Conf service : serverConfProvider.getAllServices(member)) {
                if (serverConfProvider.getDisabledNotice(service) == null) {
                    // service not disabled
                    String assetId = service.asEncodedId();
                    log.info("Processing service {}", assetId);
                    createOrUpdateAsset(service, assetId, jobContext);
                    createPolicyAndContractDefinition(service, assetId, member, jobContext);
                }
            }
        }
    }

    private void createPolicyAndContractDefinition(ServiceId.Conf service, String assetId, ClientId.Conf member, JobContext jobContext) {
        Map<XRoadId, Set<AccessRightPath>> allowedClients = serverConfProvider.getAllowedClients(member, service.getServiceCode());

        for (XRoadId subjectId : allowedClients.keySet()) {
            var endpointPatterns = allowedClients.get(subjectId);
            if (endpointPatterns.isEmpty()) {
                continue;
            }

            var policyDefinition = EdcPolicyDefinitionBuilder.newPolicyDefinition(assetId, subjectId, endpointPatterns);
            createOrUpdatePolicyDef(policyDefinition.getId(), policyDefinition, jobContext);
            createContractDefinitionForAsset(assetId, policyDefinition.getId(), jobContext);
        }
    }

    private JsonObject toJsonObject(Object object) {
        return context.transform(object, JsonObject.class);
    }

    private void createOrUpdatePolicyDef(String policyDefinitionId, PolicyDefinition policyDefinition, JobContext jobContext) {
        JsonObject policyDefJson = toJsonObject(policyDefinition);
        jobContext.policyDefinitionIds.remove(policyDefinitionId);
        if (policyDefinitionExists(policyDefinitionId)) {
            policyDefinitionApi.updatePolicyDefinitionV3(policyDefinitionId, policyDefJson);
        } else {
            policyDefinitionApi.createPolicyDefinitionV3(policyDefJson);
        }
    }

    private boolean policyDefinitionExists(String policyDefinitionId) {
        try {
            policyDefinitionApi.getPolicyDefinitionV3(policyDefinitionId);
            return true;
        } catch (FeignException.NotFound notFound) {
            return false;
        }
    }

    private void createOrUpdateAsset(ServiceId.Conf service, String assetId, JobContext jobContext) {
        jobContext.assetIds.remove(assetId);
        String serviceBaseUrl = serverConfProvider.getServiceAddress(service);

        Optional<JsonObject> assetOptional = fetchAsset(assetId);
        if (assetOptional.isEmpty()) {
            log.info("Creating new asset for service {}", assetId);
            assetApi.createAssetV3(createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                    .add(TYPE, Asset.EDC_ASSET_TYPE)
                    .add(ID, assetId)
                    .add(Asset.EDC_ASSET_PROPERTIES, createObjectBuilder()
                            .add(Asset.PROPERTY_NAME, "Asset for service %s".formatted(assetId))
                            .add(Asset.PROPERTY_CONTENT_TYPE, MediaType.APPLICATION_JSON)
                            .build())
                    .add(Asset.EDC_ASSET_PRIVATE_PROPERTIES, createObjectBuilder()
                            .add(XROAD_JOB_MANAGED_PROPERTY, Boolean.TRUE.toString())
                            .build())
                    .add(Asset.EDC_ASSET_DATA_ADDRESS, createObjectBuilder()
                            .add("type", "HttpData")
                            .add("proxyPath", Boolean.TRUE.toString())
                            .add("proxyMethod", Boolean.TRUE.toString())
                            .add("proxyBody", Boolean.TRUE.toString())
                            .add("proxyQueryParams", Boolean.TRUE.toString())
                            .add("baseUrl", serviceBaseUrl)
                            //pass custom parameters
                            .add("assetId", assetId)
                            .build())
                    .build());
        } else {
            var savedAssetJson = assetOptional.get();
            String savedBaseUrl = savedAssetJson.getJsonObject("dataAddress").getString("baseUrl");

            if (!serviceBaseUrl.equals(savedBaseUrl)) {
                log.info("Updating existing asset for service {}", assetId);

                var updatedDataAddress = createObjectBuilder(savedAssetJson.getJsonObject("dataAddress"))
                        .remove("baseUrl")
                        .add("baseUrl", serviceBaseUrl)
                        .build();

                var updatedAsset = createObjectBuilder(savedAssetJson)
                        .remove("dataAddress")
                        .add("dataAddress", updatedDataAddress)
                        .build();

                assetApi.updateAssetV3(updatedAsset);
            }
        }
    }

    private void createContractDefinitionForAsset(String assetId, String policyId, JobContext jobContext) {
        var stopWatch = StopWatch.createStarted();
        log.info("Creating contract definition for asset {}..", assetId);
        String contractDefId = "%s-contract-definition".formatted(assetId);
        jobContext.contractDefinitionIds.remove(contractDefId);
        try {
            contractDefinitionApi.getContractDefinitionV3(contractDefId);
        } catch (FeignException.NotFound notFound) {
            contractDefinitionApi.createContractDefinitionV3(createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                    .add(ID, contractDefId)
                    .add(ContractDefinition.CONTRACT_DEFINITION_ACCESSPOLICY_ID, policyId)
                    .add(ContractDefinition.CONTRACT_DEFINITION_CONTRACTPOLICY_ID, policyId)
                    .add(ContractDefinition.CONTRACT_DEFINITION_PRIVATE_PROPERTIES, createObjectBuilder()
                            .add(XROAD_JOB_MANAGED_PROPERTY, Boolean.TRUE.toString())
                            .build())
                    .add(ContractDefinition.CONTRACT_DEFINITION_ASSETS_SELECTOR, createArrayBuilder()
                            .add(createObjectBuilder()
                                    .add(Criterion.CRITERION_OPERAND_LEFT, Asset.PROPERTY_ID)
                                    .add(Criterion.CRITERION_OPERATOR, "=")
                                    .add(Criterion.CRITERION_OPERAND_RIGHT, assetId)))
                    .build());
        }
        log.info("Contract definition for asset {} created in {} ms", assetId, stopWatch.getTime());
    }

    private Optional<JsonObject> fetchAsset(String assetId) {
        try {
            return Optional.of(assetApi.getAssetV3(assetId));
        } catch (FeignException.NotFound notFound) {
            return Optional.empty();
        }
    }

    private final class JobContext {
        private final Set<String> assetIds = new HashSet<>();
        private final Set<String> policyDefinitionIds = new HashSet<>();
        private final Set<String> contractDefinitionIds = new HashSet<>();
    }

}
