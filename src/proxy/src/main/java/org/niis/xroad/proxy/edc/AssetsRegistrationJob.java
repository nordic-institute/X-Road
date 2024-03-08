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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.annotation.PostConstruct;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.api.management.configuration.transform.ManagementApiTypeTransformerRegistryImpl;
import org.eclipse.edc.connector.api.management.contractdefinition.ContractDefinitionApi;
import org.eclipse.edc.connector.api.management.policy.PolicyDefinitionApi;
import org.eclipse.edc.connector.api.management.policy.transform.JsonObjectFromPolicyDefinitionTransformer;
import org.eclipse.edc.connector.api.management.policy.transform.JsonObjectToPolicyDefinitionTransformer;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.dataplane.selector.api.v2.DataplaneSelectorApi;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.policy.spi.PolicyDefinition;
import org.eclipse.edc.core.transform.TransformerContextImpl;
import org.eclipse.edc.core.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.to.JsonObjectToPolicyTransformer;
import org.eclipse.edc.core.transform.transformer.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.domain.asset.Asset;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.niis.xroad.proxy.configuration.ProxyEdcConfig;
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
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.Criterion.criterion;

@Component
@Conditional(ProxyEdcConfig.DataspacesEnabledCondition.class)
@Slf4j
public class AssetsRegistrationJob {

    private static final String XROAD_NAMESPACE = "https://x-road.eu/v0.1/ns/";
    private static final String XROAD_JOB_MANAGED_PROPERTY = XROAD_NAMESPACE + "xroadJobManaged";

    private static final String XROAD_CLIENT_ID_CONSTRAINT = "xroad:clientId";
    private static final String XROAD_DATAPATH_CONSTRAINT = "xroad:datapath";
    private static final String XROAD_GLOBALGROUP_CONSTRAINT = "xroad:globalGroupMember";

    private final DataplaneSelectorApi dataplaneSelectorApi;
    private final AssetApi assetApi;
    private final PolicyDefinitionApi policyDefinitionApi;
    private final ContractDefinitionApi contractDefinitionApi;
    private final TransformerContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String providerDataplaneId = "http-provider-dataplane";

    public AssetsRegistrationJob(DataplaneSelectorApi dataplaneSelectorApi, AssetApi assetApi,
                                 PolicyDefinitionApi policyDefinitionApi, ContractDefinitionApi contractDefinitionApi) {
        this.dataplaneSelectorApi = dataplaneSelectorApi;
        this.assetApi = assetApi;
        this.policyDefinitionApi = policyDefinitionApi;
        this.contractDefinitionApi = contractDefinitionApi;
        this.context = new TransformerContextImpl(registerTransformers());
    }

    private TypeTransformerRegistry registerTransformers() {
        var jsonLdObjectMapper = JacksonJsonLd.createObjectMapper();
        var registry = new ManagementApiTypeTransformerRegistryImpl(new TypeTransformerRegistryImpl());
        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());

        registry.register(new JsonValueToGenericTypeTransformer(jsonLdObjectMapper));

        registry.register(new JsonObjectToPolicyTransformer());
        registry.register(new JsonObjectFromPolicyTransformer(jsonBuilderFactory));

        registry.register(new JsonObjectToPolicyDefinitionTransformer());
        registry.register(new JsonObjectFromPolicyDefinitionTransformer(jsonBuilderFactory, JacksonJsonLd.createObjectMapper()));

        registry.register(new JsonObjectToQuerySpecTransformer());
        registry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));

        registry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));
        registry.register(new JsonObjectFromCriterionTransformer(jsonBuilderFactory, jsonLdObjectMapper));

        return registry;
    }

    @PostConstruct
    public void registerDataPlane() {
        log.info("Creating dataplane");
        // creates or updates the dataplane
        dataplaneSelectorApi.addEntry(createObjectBuilder()
                .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                .add(TYPE, DataPlaneInstance.DATAPLANE_INSTANCE_TYPE)
                .add(ID, providerDataplaneId)

                .add(DataPlaneInstance.URL, "http://%s:%s/control/transfer"
                        .formatted(GlobalConf.getSecurityServerAddress(ServerConf.getIdentifier()),
                                SystemProperties.dataspacesControlListenPort()))
                .add(DataPlaneInstance.ALLOWED_SOURCE_TYPES, createArrayBuilder()
                        .add("HttpData")
                        .build())
                .add(DataPlaneInstance.ALLOWED_DEST_TYPES, createArrayBuilder()
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
        final JobContext jobContext = fetchAllJobManagedIds();
        process(jobContext);
        deleteNotRelevantObjects(jobContext);
    }


    private JobContext fetchAllJobManagedIds() {
        JobContext jobContext = new JobContext();
        Criterion criterion = criterion("privateProperties.'%s'".formatted(XROAD_JOB_MANAGED_PROPERTY),
                "=",
                Boolean.TRUE.toString());

        assetApi.requestAssets(toJsonObject(QuerySpec.Builder.newInstance()
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

        policyDefinitionApi.queryPolicyDefinitions(toJsonObject(QuerySpec.Builder.newInstance()
                        // .filter(criterion)  // todo: edc: Querying Map types is not yet supported
                        .build()))
                .stream()
                .map(x -> (JsonObject) x)
                .filter(xRoadManagedObjectFilter)
                .map(x -> x.getString(ID))
                .forEach(jobContext.policyDefinitionIds::add);

        contractDefinitionApi.queryAllContractDefinitions(toJsonObject(QuerySpec.Builder.newInstance()
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
                assetApi.removeAsset(assetId);
            } catch (Exception e) {
                log.info("Error deleting asset [{}]", assetId, e);
            }
        });

        jobContext.policyDefinitionIds.forEach(policyId -> {
            try {
                policyDefinitionApi.deletePolicyDefinition(policyId);
            } catch (Exception e) {
                log.info("Error deleting policy definition [{}]", policyId, e);
            }
        });

        jobContext.contractDefinitionIds.forEach(contractId -> {
            try {
                contractDefinitionApi.deleteContractDefinition(contractId);
            } catch (Exception e) {
                log.info("Error deleting contract definition [{}]", contractId, e);
            }
        });

    }

    private void process(JobContext jobContext) throws Exception {
        log.info("Processing services");
        for (ClientId.Conf member : ServerConf.getMembers()) {
            for (ServiceId.Conf service : ServerConf.getAllServices(member)) {
                if (ServerConf.getDisabledNotice(service) == null) {
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
        Map<XRoadId, Set<String>> allowedClients = ServerConf.getAllowedClients(member, service.getServiceCode());

        for (XRoadId subjectId : allowedClients.keySet()) {
            Set<String> endpointPatterns = allowedClients.get(subjectId);
            if (!endpointPatterns.isEmpty()) {
                AtomicConstraint clientConstraint;
                if (subjectId instanceof GlobalGroupId) {
                    clientConstraint = AtomicConstraint.Builder.newInstance()
                            .leftExpression(new LiteralExpression(XROAD_GLOBALGROUP_CONSTRAINT))
                            .operator(Operator.EQ)
                            .rightExpression(new LiteralExpression(subjectId.asEncodedId()))
                            .build();
                } else if (subjectId instanceof LocalGroupId) {
                    // todo: implement. not yet supported.
                    continue;
                } else {
                    // single client id
                    clientConstraint = AtomicConstraint.Builder.newInstance()
                            .leftExpression(new LiteralExpression(XROAD_CLIENT_ID_CONSTRAINT))
                            .operator(Operator.EQ)
                            .rightExpression(new LiteralExpression(subjectId.asEncodedId()))
                            .build();
                }

                AtomicConstraint datapathConstraint = AtomicConstraint.Builder.newInstance()
                        .leftExpression(new LiteralExpression(XROAD_DATAPATH_CONSTRAINT))
                        .operator(Operator.IS_ANY_OF)
                        .rightExpression(new LiteralExpression(toJsonArrayString(endpointPatterns)))
                        .build();

                String policyDefinitionId = "%s:%s-policyDef".formatted(assetId, subjectId.asEncodedId());

                PolicyDefinition policyDefinition = PolicyDefinition.Builder.newInstance()
                        .id(policyDefinitionId)
                        .policy(Policy.Builder.newInstance()
                                .type(PolicyType.SET)
                                .permission(Permission.Builder.newInstance()
                                        .action(Action.Builder.newInstance().type("http://www.w3.org/ns/odrl/2/use").build())
                                        .constraint(AndConstraint.Builder.newInstance()
                                                .constraint(clientConstraint)
                                                .constraint(datapathConstraint)
                                                .build())
                                        .build())
                                .build())
                        .privateProperties(Map.of(
                                XROAD_JOB_MANAGED_PROPERTY, Boolean.TRUE.toString()))
                        .build();

                createOrUpdatePolicyDef(policyDefinitionId, policyDefinition, jobContext);
                createContractDefinitionForAsset(assetId, policyDefinitionId, jobContext);
            }
        }
    }

    @SneakyThrows
    private String toJsonArrayString(Set<String> endpointPatterns) {
        return objectMapper.writeValueAsString(endpointPatterns);
    }

    private JsonObject toJsonObject(Object object) {
        return context.transform(object, JsonObject.class);
    }

    private void createOrUpdatePolicyDef(String policyDefinitionId, PolicyDefinition policyDefinition, JobContext jobContext) {
        JsonObject policyDefJson = toJsonObject(policyDefinition);
        jobContext.policyDefinitionIds.remove(policyDefinitionId);
        if (policyDefinitionExists(policyDefinitionId)) {
            policyDefinitionApi.updatePolicyDefinition(policyDefinitionId, policyDefJson);
        } else {
            policyDefinitionApi.createPolicyDefinition(policyDefJson);
        }
    }

    private boolean policyDefinitionExists(String policyDefinitionId) {
        try {
            policyDefinitionApi.getPolicyDefinition(policyDefinitionId);
            return true;
        } catch (FeignException.NotFound notFound) {
            return false;
        }
    }

    private void createOrUpdateAsset(ServiceId.Conf service, String assetId, JobContext jobContext) {
        jobContext.assetIds.remove(assetId);
        String serviceBaseUrl = ServerConf.getServiceAddress(service);

        Optional<JsonObject> assetOptional = fetchAsset(assetId);
        if (assetOptional.isEmpty()) {
            log.info("Creating new asset for service {}", assetId);
            assetApi.createAsset(createObjectBuilder()
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

                assetApi.updateAsset(updatedAsset);
            }
        }
    }

    private void createContractDefinitionForAsset(String assetId, String policyId, JobContext jobContext) {
        String contractDefId = "%s-contract-def".formatted(policyId);
        jobContext.contractDefinitionIds.remove(contractDefId);
        try {
            contractDefinitionApi.getContractDefinition(contractDefId);
        } catch (FeignException.NotFound notFound) {
            log.info("Creating contract definition for asset {}", assetId);
            contractDefinitionApi.createContractDefinition(createObjectBuilder()
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
    }

    private Optional<JsonObject> fetchAsset(String assetId) {
        try {
            return Optional.of(assetApi.getAsset(assetId));
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
