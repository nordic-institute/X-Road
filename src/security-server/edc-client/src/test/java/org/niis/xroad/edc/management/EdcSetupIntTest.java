package org.niis.xroad.edc.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.catalog.spi.CatalogRequest;
import org.eclipse.edc.connector.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.api.management.configuration.transform.JsonObjectFromContractAgreementTransformer;
import org.eclipse.edc.connector.api.management.configuration.transform.ManagementApiTypeTransformerRegistryImpl;
import org.eclipse.edc.connector.api.management.contractdefinition.ContractDefinitionApi;
import org.eclipse.edc.connector.api.management.contractdefinition.transform.JsonObjectFromContractDefinitionTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.ContractNegotiationApi;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectToContractRequestTransformer;
import org.eclipse.edc.connector.api.management.policy.PolicyDefinitionApi;
import org.eclipse.edc.connector.api.management.policy.transform.JsonObjectFromPolicyDefinitionTransformer;
import org.eclipse.edc.connector.api.management.transferprocess.TransferProcessApi;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.dataplane.selector.api.v2.DataplaneSelectorApi;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.transformer.JsonObjectFromDataPlaneInstanceTransformer;
import org.eclipse.edc.connector.policy.spi.PolicyDefinition;
import org.eclipse.edc.core.transform.TransformerContextImpl;
import org.eclipse.edc.core.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromAssetTransformer;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromCatalogTransformer;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromDataAddressTransformer;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.core.transform.transformer.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.to.JsonObjectToDataAddressTransformer;
import org.eclipse.edc.core.transform.transformer.to.JsonObjectToPolicyTransformer;
import org.eclipse.edc.core.transform.transformer.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyType;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.management.client.FeignCatalogApi;
import org.niis.xroad.edc.management.client.configuration.EdcManagementApiFactory;
import org.niis.xroad.edc.management.client.ext.JsonObjectFromCatalogRequestTransformer;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static jakarta.json.Json.createObjectBuilder;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.given;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_TYPE;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_ASSET_ID;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_CONNECTOR_ID;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_CONTRACT_ID;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_COUNTER_PARTY_ADDRESS;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_DATA_DESTINATION;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_PROTOCOL;
import static org.eclipse.edc.connector.transfer.spi.types.TransferRequest.TRANSFER_REQUEST_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.CoreConstants.EDC_PREFIX;
import static org.eclipse.edc.spi.types.domain.DataAddress.SIMPLE_TYPE;
import static org.eclipse.edc.spi.types.domain.asset.Asset.EDC_ASSET_DATA_ADDRESS;
import static org.eclipse.edc.spi.types.domain.asset.Asset.EDC_ASSET_PROPERTIES;
import static org.eclipse.edc.spi.types.domain.asset.Asset.EDC_ASSET_TYPE;
import static org.eclipse.edc.spi.types.domain.asset.Asset.PROPERTY_CONTENT_TYPE;
import static org.eclipse.edc.spi.types.domain.asset.Asset.PROPERTY_NAME;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
class EdcSetupIntTest {
    private final ObjectMapper objectMapper = JacksonJsonLd.createObjectMapper();

    private final EdcManagementApiFactory providerApiFactory = new EdcManagementApiFactory("http://localhost:19193");
    private final EdcManagementApiFactory consumerApiFactory = new EdcManagementApiFactory("http://localhost:29193");

    private final DataplaneSelectorApi dataplaneSelectorApi = providerApiFactory.dataplaneSelectorApi();
    private final AssetApi assetApi = providerApiFactory.assetsApi();
    private final PolicyDefinitionApi policyDefinitionApi = providerApiFactory.policyDefinitionApi();
    private final ContractDefinitionApi contractDefinitionApi = providerApiFactory.contractDefinitionApi();
    private final ContractNegotiationApi consumerContractNegotiationApi = consumerApiFactory.contractNegotiationApi();
    private final FeignCatalogApi consumerCatalogApi = consumerApiFactory.catalogApi();
    private final TransferProcessApi consumerTransferProcessApi = consumerApiFactory.transferProcessApi();

    private JsonLd jsonLd;
    private TransformerContextImpl context;

    private static Process consumerProcess;
    private static Process providerProcess;

    @BeforeEach
    void setUp() throws InterruptedException {
        var objectMapper = JacksonJsonLd.createObjectMapper();
        var transformerRegistry = new TypeTransformerRegistryImpl();
        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());
        var registry = new ManagementApiTypeTransformerRegistryImpl(transformerRegistry);

        registry.register(new JsonObjectFromContractAgreementTransformer(jsonBuilderFactory));
        registry.register(new JsonObjectToDataAddressTransformer());
        registry.register(new JsonObjectFromDataAddressTransformer(jsonBuilderFactory));
        registry.register(new JsonValueToGenericTypeTransformer(objectMapper));
        registry.register(new JsonObjectFromDataPlaneInstanceTransformer(jsonBuilderFactory, objectMapper));
        registry.register(new JsonObjectFromAssetTransformer(jsonBuilderFactory, objectMapper));
        registry.register(new JsonObjectFromPolicyDefinitionTransformer(jsonBuilderFactory, objectMapper));

        registry.register(new JsonObjectToPolicyTransformer());
        registry.register(new JsonObjectFromPolicyTransformer(jsonBuilderFactory));
        registry.register(new JsonObjectFromContractDefinitionTransformer(jsonBuilderFactory, objectMapper));
        registry.register(new JsonObjectFromCatalogTransformer(jsonBuilderFactory, objectMapper));
        registry.register(new JsonObjectFromCatalogRequestTransformer(jsonBuilderFactory, objectMapper));
        registry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));

        registry.register(new JsonObjectToContractRequestTransformer());
        context = new TransformerContextImpl(registry);

        jsonLd = new TitaniumJsonLd(new ConsoleMonitor());
        jsonLd.registerNamespace(EDC_PREFIX, EDC_NAMESPACE);
        jsonLd.registerNamespace(DCAT_PREFIX, DCAT_SCHEMA);
        jsonLd.registerNamespace(DCT_PREFIX, DCT_SCHEMA);
        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA);
        jsonLd.registerNamespace(DSPACE_PREFIX, DSPACE_SCHEMA);

        startEdcConsumer();
        startEdcProvider();
    }

    @AfterAll
    static void shutdown() {
        providerProcess.descendants().forEach(ProcessHandle::destroy);
        consumerProcess.descendants().forEach(ProcessHandle::destroy);
        providerProcess.destroy();
        consumerProcess.destroy();
    }

    @Test
    void shouldSetupEdcConnection() {

        try {
            //create dataplane in provider
            var dataPlaneAddResult = dataplaneSelectorApi.addEntry(transform(
                    DataPlaneInstance.Builder.newInstance()
                            .id("http-pull-provider-dataplane")
                            .url("http://localhost:19192/control/transfer")
                            .allowedSourceTypes(Set.of("HttpData"))
                            .allowedDestTypes(Set.of("HttpData", "HttpProxy"))
                            .property("https://w3id.org/edc/v0.0.1/ns/publicApiUrl", "http://localhost:19291/public/")
                            .build()
            ));
            log.info("======== dataPlaneAddResult: {}", dataPlaneAddResult);

            //create asset

            var assetCreateResult = assetApi.createAsset(createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder()
                            .add(VOCAB, EDC_NAMESPACE))
                    .add(TYPE, EDC_ASSET_TYPE)
                    .add(ID, "asset-id")
                    .add(EDC_ASSET_PROPERTIES, createObjectBuilder()
                            .add(PROPERTY_NAME, "Test asset")
                            .add(PROPERTY_CONTENT_TYPE, MediaType.APPLICATION_JSON)
                            .build())
                    .add(EDC_ASSET_DATA_ADDRESS, createObjectBuilder()
                            .add("type", "HttpData")
                            .add("name", "Test asset")
                            .add("proxyPath", Boolean.TRUE.toString())
                            .add("baseUrl", "https://jsonplaceholder.typicode.com/users") //target internal IS
                            .build())
                    .build());
            log.info("======== CreateAsset: {}", assetCreateResult);
            var getAssetResult = assetApi.getAsset("asset-id");
            log.info("======== getAssetResult: {}", getAssetResult);

            //create policy
            var createPolicyDefinitionResult = policyDefinitionApi.createPolicyDefinition(transform(
                    PolicyDefinition.Builder.newInstance()
                            .id("policy-id")
                            .policy(Policy.Builder.newInstance()
                                    .type(PolicyType.SET)
                                    .build())
                            .build()
            ));
            log.info("======== createPolicyDefinitionResult: {}", createPolicyDefinitionResult);

            //create contract definition
            var createContractDefinitionResult = contractDefinitionApi.createContractDefinition(transform(
                    ContractDefinition.Builder.newInstance()
                            .accessPolicyId("policy-id")
                            .contractPolicyId("policy-id")
                            .build()
            ));
            log.info("======== createContractDefinitionResult: {}", createContractDefinitionResult);

            //fetch catalog
            var requestCatalogResult = consumerCatalogApi.requestCatalogExt(transform(
                    CatalogRequest.Builder.newInstance()
                            .counterPartyAddress("http://localhost:19194/protocol")
                            .protocol("dataspace-protocol-http")
                            .build()
            ));

            log.info("======== requestCatalogResult: {}", requestCatalogResult);
            var policy = requestCatalogResult.get("dcat:dataset")
                    .asJsonObject().get("odrl:hasPolicy").asJsonObject();

            //initiateContractNegotiation
            var alt = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder()
                            .add(VOCAB, EDC_NAMESPACE)
                            .add(ODRL_PREFIX, ODRL_SCHEMA))
                    .add(TYPE, CONTRACT_REQUEST_TYPE)
                    .add("providerId", "providerId")
                    .add("counterPartyAddress", "http://localhost:19194/protocol")
                    .add("protocol", "dataspace-protocol-http")
                    .add("policy", policy)
                    .build();
            log.info("Request: {}", alt.toString());
//            log.info("Updated Req {}", jsonLd.compact(alt).getContent());
            var initiateContractNegotiationResult = consumerContractNegotiationApi.initiateContractNegotiation(alt);

            log.info("======== initiateContractNegotiation: {}", initiateContractNegotiationResult);

            //get status
            final AtomicReference<String> contractAgreementId = new AtomicReference<>(null);

            given()
                    .pollDelay(5, TimeUnit.SECONDS)
                    .pollInterval(5, TimeUnit.SECONDS)
                    .pollInSameThread()
                    .conditionEvaluationListener(condition -> {
                        if (!condition.isSatisfied()) {
                            var getNegotiationResponse = consumerContractNegotiationApi.getNegotiation(initiateContractNegotiationResult.getString(ID));
                            log.info("======== getNegotiation: {}", getNegotiationResponse);
                            if ("VERIFIED".equalsIgnoreCase(getNegotiationResponse.getString("state"))) {
                                contractAgreementId.set(getNegotiationResponse.getString("contractAgreementId"));
                            }
                        }
                    })
                    .atMost(15, TimeUnit.SECONDS)
                    .await()
                    .untilAtomic(contractAgreementId, notNullValue());

            // start transfer
            var initTransferRequest = createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder()
                            .add(VOCAB, EDC_NAMESPACE))
                    .add(TYPE, TRANSFER_REQUEST_TYPE)//TransferRequestDto
                    .add(TRANSFER_REQUEST_CONNECTOR_ID, "provider")
                    .add(TRANSFER_REQUEST_COUNTER_PARTY_ADDRESS, "http://localhost:19194/protocol")
                    .add(TRANSFER_REQUEST_CONTRACT_ID, contractAgreementId.get())
                    .add(TRANSFER_REQUEST_ASSET_ID, "asset-id")
                    .add(TRANSFER_REQUEST_PROTOCOL, "dataspace-protocol-http")
                    .add(TRANSFER_REQUEST_DATA_DESTINATION, createObjectBuilder().add(SIMPLE_TYPE, "HttpProxy").build())
                    .build();
            var initiateTransferProcessResponse = consumerTransferProcessApi.initiateTransferProcess(initTransferRequest);
            log.info("======== initiateTransferProcess: {}", initiateTransferProcessResponse);
            final String transferId = initiateTransferProcessResponse.getString(ID);

            given()
                    .pollDelay(1, TimeUnit.SECONDS)
                    .pollInterval(1, TimeUnit.SECONDS)
                    .pollInSameThread()
                    .atMost(120, TimeUnit.SECONDS)
                    .await()
                    .until(() -> {
                        var response = consumerTransferProcessApi.getTransferProcess(transferId);
                        log.info("======== getTransferProcess: {}", response);
                        var status = response.getString("state");

                        return "FINISHED".equalsIgnoreCase(status);
                    });
        } catch (FeignException feignException) {
            log.error("FeignException. Response: {}", feignException.contentUTF8(), feignException);
        }
    }

    @Deprecated
    @SneakyThrows
    private JsonObject transform(Object content) {
        var jsonObject = jsonLd.compact(context.transform(content, JsonObject.class)).getContent();

        return jsonObject;
    }


    private static void startEdcConsumer() throws InterruptedException {

        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("../edc/run-consumer.sh");
                pb.directory(new File("../edc/"));
                consumerProcess = pb.start();
                // Redirect output and error streams to SLF4J
                var logger = LoggerFactory.getLogger("EDC-CONSUMER");
                StreamGobbler outputGobbler = new StreamGobbler(consumerProcess.getInputStream(), logger::info);
                StreamGobbler errorGobbler = new StreamGobbler(consumerProcess.getErrorStream(), logger::error);

                // Start gobbling the streams
                outputGobbler.start();
                errorGobbler.start();

            } catch (Exception e) {
                log.error("Error", e);
            }
        });

        t.start();
        MILLISECONDS.sleep(1000);
    }

    private static void startEdcProvider() throws InterruptedException {

        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("../edc/run-provider.sh");
                pb.directory(new File("../edc/"));

                providerProcess = pb.start();
                // Redirect output and error streams to SLF4J
                var logger = LoggerFactory.getLogger("EDC-PROVIDER");
                StreamGobbler outputGobbler = new StreamGobbler(consumerProcess.getInputStream(), logger::info);
                StreamGobbler errorGobbler = new StreamGobbler(consumerProcess.getErrorStream(), logger::error);

                // Start gobbling the streams
                outputGobbler.start();
                errorGobbler.start();

            } catch (Exception e) {
                log.error("Error", e);
            }
        });

        t.start();
        MILLISECONDS.sleep(3000);
    }

    private static class StreamGobbler extends Thread {
        private final InputStream inputStream;
        private final Consumer<String> consumeInputLine;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumeInputLine) {
            this.inputStream = inputStream;
            this.consumeInputLine = consumeInputLine;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumeInputLine.accept(line);
                }
            } catch (IOException e) {
                //do nothing
            }
        }
    }
}
