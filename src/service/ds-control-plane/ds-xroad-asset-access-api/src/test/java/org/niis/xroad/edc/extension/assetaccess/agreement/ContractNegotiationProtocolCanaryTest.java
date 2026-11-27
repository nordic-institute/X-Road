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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import com.sun.net.httpserver.HttpServer;
import dev.failsafe.RetryPolicy;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.eclipse.edc.connector.controlplane.contract.spi.ContractOfferId;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.ConsumerOfferResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationError;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractOffer;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ValidatableConsumerOffer;
import org.eclipse.edc.connector.controlplane.services.contractnegotiation.ContractNegotiationProtocolServiceImpl;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.context.request.spi.RequestScopeExtractorRegistry;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.dsp.http.dispatcher.DspHttpRemoteMessageDispatcherImpl;
import org.eclipse.edc.protocol.dsp.http.message.DspRequestHandlerImpl;
import org.eclipse.edc.protocol.dsp.http.spi.message.PostDspRequest;
import org.eclipse.edc.protocol.dsp.negotiation.transform.from.JsonObjectFromContractNegotiationErrorTransformer;
import org.eclipse.edc.protocol.dsp.spi.transform.DspProtocolTypeTransformerRegistry;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.telemetry.Telemetry;
import org.eclipse.edc.token.spi.TokenDecorator;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives EDC's own provider-side contract negotiation chain into the answer a consumer receives for a token it
 * cannot verify: the negotiation protocol service, the DSP request handler that maps the failure to an HTTP status
 * and error body, a real HTTP answer and the consumer's DSP dispatcher that builds the failure detail.
 * {@link ProviderFailureClassifier} then judges that detail, read the way
 * {@link org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller} reads it: prefixed with EDC's
 * own negotiation send-failure wording. A changed status mapping or a reworded message turns this test red.
 */
class ContractNegotiationProtocolCanaryTest {

    private static final String PROTOCOL = "dataspace-protocol-http:2025-1";
    private static final String NEGOTIATION_FAILURE_PREFIX = "Failed to request contract to provider: ";
    private static final String OFFER_ID = "offer-1";

    private final ConsumerOfferResolver consumerOfferResolver = mock();
    private final ProtocolTokenValidator protocolTokenValidator = mock();
    private final ParticipantContext participantContext = ParticipantContext.Builder.newInstance()
            .id("participantContextId")
            .identity("participantId")
            .build();
    private final ContractRequestMessage requestMessage = ContractRequestMessage.Builder.newInstance()
            .protocol(PROTOCOL)
            .contractOffer(ContractOffer.Builder.newInstance()
                    .id(OFFER_ID)
                    .assetId("asset-1")
                    .policy(Policy.Builder.newInstance().build())
                    .build())
            .build();

    private DspRequestHandlerImpl requestHandler;
    private ContractNegotiationProtocolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContractNegotiationProtocolServiceImpl(mock(ContractNegotiationStore.class), new NoopTransactionContext(),
                mock(ContractValidationService.class), consumerOfferResolver, protocolTokenValidator,
                mock(ContractNegotiationObservable.class), mock(Monitor.class), mock(Telemetry.class));
        requestHandler = new DspRequestHandlerImpl(mock(Monitor.class), acceptingValidators(), protocolTransformers());
    }

    @Test
    void aTokenTheProviderCannotVerifyIsAnIdentityRejection() throws Exception {
        when(consumerOfferResolver.resolveOffer(participantContext.getId(), OFFER_ID))
                .thenReturn(ServiceResult.success(validatableOffer()));
        when(protocolTokenValidator.verify(eq(participantContext), any(), any(), any()))
                .thenReturn(ServiceResult.unauthorized("bad token"));

        var response = providerAnswer();

        assertThat(response.getStatus()).isEqualTo(404);
        var errorDetail = NEGOTIATION_FAILURE_PREFIX + consumerDetail(response);
        assertThat(errorDetail).doesNotContainIgnoringCase(ProviderFailureClassifier.AGREEMENT_NOT_VALID);
        assertThat(ProviderFailureClassifier.classify(errorDetail, ProviderFailureClassifier.Stage.NEGOTIATION))
                .isEqualTo(ProviderFailureClassifier.Classification.IDENTITY_REJECTION);
    }

    private ValidatableConsumerOffer validatableOffer() {
        var contractDefinition = ContractDefinition.Builder.newInstance()
                .id("definition-1")
                .accessPolicyId("access-policy-1")
                .contractPolicyId("contract-policy-1")
                .participantContextId(participantContext.getId())
                .build();
        return ValidatableConsumerOffer.Builder.newInstance()
                .offerId(ContractOfferId.create("definition-1", "asset-1"))
                .contractDefinition(contractDefinition)
                .accessPolicy(Policy.Builder.newInstance().build())
                .contractPolicy(Policy.Builder.newInstance().build())
                .build();
    }

    private Response providerAnswer() {
        var request = PostDspRequest.Builder.newInstance(
                        ContractRequestMessage.class, ContractNegotiation.class, ContractNegotiationError.class)
                .message(Json.createObjectBuilder().build())
                .token("token")
                .expectedMessageType("ContractRequestMessage")
                .serviceCall(service::notifyRequested)
                .errorProvider(ContractNegotiationError.Builder::newInstance)
                .protocol(PROTOCOL)
                .participantContextProvider(() -> ServiceResult.success(participantContext))
                .build();
        return requestHandler.createResource(request);
    }

    private String consumerDetail(Response providerResponse) throws Exception {
        var body = providerResponse.getEntity().toString().getBytes(StandardCharsets.UTF_8);
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(providerResponse.getStatus(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var message = ContractRequestMessage.Builder.newInstance()
                    .protocol(PROTOCOL)
                    .counterPartyAddress("http://localhost:" + server.getAddress().getPort() + "/dsp")
                    .contractOffer(ContractOffer.Builder.newInstance()
                            .id(OFFER_ID)
                            .assetId("asset-1")
                            .policy(Policy.Builder.newInstance().build())
                            .build())
                    .build();
            var result = dispatcher().dispatch("participant-1", Object.class, message).get(10, TimeUnit.SECONDS);
            assertThat(result.failed()).isTrue();
            return result.getFailureDetail();
        } finally {
            server.stop(0);
        }
    }

    private static DspHttpRemoteMessageDispatcherImpl dispatcher() {
        var okHttpClient = new OkHttpClient.Builder().readTimeout(Duration.ofSeconds(5)).build();
        var retryPolicy = RetryPolicy.<okhttp3.Response>builder().withMaxRetries(0).build();
        var httpClient = new EdcHttpClientImpl(okHttpClient, retryPolicy, mock(Monitor.class));
        var identityService = mock(IdentityService.class);
        when(identityService.obtainClientCredentials(anyString(), any()))
                .thenReturn(Result.success(TokenRepresentation.Builder.newInstance().token("token").build()));
        var tokenDecorator = mock(TokenDecorator.class);
        when(tokenDecorator.decorate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var scopeExtractors = mock(RequestScopeExtractorRegistry.class);
        when(scopeExtractors.extractScopes(any(), any())).thenReturn(Result.success(Set.of()));
        var dispatcher = new DspHttpRemoteMessageDispatcherImpl(httpClient, identityService, tokenDecorator,
                scopeExtractors, _ -> Result.success("provider-1"));
        dispatcher.registerMessage(ContractRequestMessage.class,
                message -> new Request.Builder().url(message.getCounterPartyAddress() + "/negotiations/request")
                        .post(new FormBody.Builder().build()).build(),
                (_, _) -> new Object());
        return dispatcher;
    }

    private static JsonObjectValidatorRegistry acceptingValidators() {
        var validators = mock(JsonObjectValidatorRegistry.class);
        when(validators.validate(any(), any())).thenReturn(ValidationResult.success());
        return validators;
    }

    private DspProtocolTypeTransformerRegistry protocolTransformers() {
        var errorTransformer = new JsonObjectFromContractNegotiationErrorTransformer(
                Json.createBuilderFactory(Map.of()), DSP_NAMESPACE_V_2025_1);
        var transformers = mock(TypeTransformerRegistry.class);
        when(transformers.transform(any(JsonObject.class), eq(ContractRequestMessage.class)))
                .thenReturn(Result.success(requestMessage));
        when(transformers.transform(any(ContractNegotiationError.class), eq(JsonObject.class)))
                .thenAnswer(invocation ->
                        Result.success(errorTransformer.transform(invocation.getArgument(0), mock(TransformerContext.class))));
        var registry = mock(DspProtocolTypeTransformerRegistry.class);
        when(registry.forProtocol(PROTOCOL)).thenReturn(Result.success(transformers));
        return registry;
    }
}
