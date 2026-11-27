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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.validation.ContractValidationService;
import org.eclipse.edc.connector.controlplane.services.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.connector.controlplane.services.transferprocess.TransferProcessProtocolServiceImpl;
import org.eclipse.edc.connector.controlplane.services.transferprocess.TransferProcessProviderFactory;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferError;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.controlplane.DataAddress;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.protocol.dsp.http.message.DspRequestHandlerImpl;
import org.eclipse.edc.protocol.dsp.http.spi.message.PostDspRequest;
import org.eclipse.edc.protocol.dsp.spi.transform.DspProtocolTypeTransformerRegistry;
import org.eclipse.edc.protocol.dsp.transferprocess.transform.type.from.JsonObjectFromTransferErrorTransformer;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_NAMESPACE_V_2025_1;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.extension.assetaccess.agreement.DspProtocolCanarySupport.acceptingValidators;
import static org.niis.xroad.edc.extension.assetaccess.agreement.DspProtocolCanarySupport.consumerDetail;
import static org.niis.xroad.edc.extension.assetaccess.agreement.DspProtocolCanarySupport.dispatcher;

/**
 * Drives EDC's own provider-side transfer request chain into each answer a consumer can receive: the transfer
 * service, the DSP request handler that maps the failure to an HTTP status and error body, a real HTTP answer and the
 * consumer's DSP dispatcher that builds the failure detail. {@link ProviderFailureClassifier} then judges that detail.
 * A changed status mapping or a reworded message turns this test red.
 */
class ProviderRefusalCanaryTest {

    private static final String TRANSFER_TYPE = "transferType";
    private static final String PROTOCOL = "dataspace-protocol-http:2025-1";

    private final ContractNegotiationStore negotiationStore = mock();
    private final ContractValidationService validationService = mock();
    private final ProtocolTokenValidator protocolTokenValidator = mock();
    private final DataFlowController dataFlowController = mock();
    private final ParticipantContext participantContext = ParticipantContext.Builder.newInstance()
            .id("participantContextId")
            .identity("participantId")
            .build();
    private final TransferRequestMessage requestMessage = TransferRequestMessage.Builder.newInstance()
            .consumerPid("consumerPid")
            .processId("consumerPid")
            .contractId("agreementId")
            .protocol(PROTOCOL)
            .callbackAddress("http://any")
            .transferType(TRANSFER_TYPE)
            .dataAddress(DataAddress.Builder.newInstance().type("any").build())
            .build();

    private DspRequestHandlerImpl requestHandler;
    private TransferProcessProtocolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransferProcessProtocolServiceImpl(mock(TransferProcessStore.class), new NoopTransactionContext(),
                negotiationStore, validationService, protocolTokenValidator, mock(TransferProcessObservable.class),
                mock(Monitor.class), dataFlowController, mock(DataAddressStore.class),
                mock(TransferProcessProviderFactory.class));
        requestHandler = new DspRequestHandlerImpl(mock(Monitor.class), acceptingValidators(), protocolTransformers());
    }

    @Test
    void anUnknownAgreementIsAnAnswerTheRecogniserTakesForARefusal() throws Exception {
        when(negotiationStore.queryAgreements(any())).thenAnswer(invocation -> Stream.empty());

        var response = providerAnswer();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(ProviderFailureClassifier.classify(
                consumerDetail(response, dispatcher(TransferRequestMessage.class, "/transfers/request"), this::transferRequestFor),
                ProviderFailureClassifier.Stage.TRANSFER))
                .isEqualTo(ProviderFailureClassifier.Classification.AGREEMENT_REFUSAL);
    }

    @Test
    void anAgreementThatIsNoLongerValidIsAnAnswerTheRecogniserTakesForARefusal() throws Exception {
        givenVerifiedRequestForStoredAgreement();
        when(validationService.validateAgreement(any(ParticipantAgent.class), any()))
                .thenReturn(Result.failure("agreement expired"));

        var response = providerAnswer();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(ProviderFailureClassifier.classify(
                consumerDetail(response, dispatcher(TransferRequestMessage.class, "/transfers/request"), this::transferRequestFor),
                ProviderFailureClassifier.Stage.TRANSFER))
                .isEqualTo(ProviderFailureClassifier.Classification.AGREEMENT_REFUSAL);
    }

    @Test
    void aTransferTypeTheProviderDoesNotSupportIsNotARefusal() throws Exception {
        givenVerifiedRequestForStoredAgreement();
        when(dataFlowController.transferTypesFor(anyString(), anyString())).thenReturn(Set.of("otherTransferType"));

        var response = providerAnswer();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(ProviderFailureClassifier.classify(
                consumerDetail(response, dispatcher(TransferRequestMessage.class, "/transfers/request"), this::transferRequestFor),
                ProviderFailureClassifier.Stage.TRANSFER))
                .isEqualTo(ProviderFailureClassifier.Classification.NO_REFUSAL);
    }

    @Test
    void aTokenTheProviderCannotVerifyIsAnIdentityRejection() throws Exception {
        when(protocolTokenValidator.verify(eq(participantContext), any(), any(), any()))
                .thenReturn(ServiceResult.unauthorized("bad token"));
        when(negotiationStore.queryAgreements(any())).thenAnswer(invocation -> Stream.of(agreement()));

        var response = providerAnswer();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(ProviderFailureClassifier.classify(
                consumerDetail(response, dispatcher(TransferRequestMessage.class, "/transfers/request"), this::transferRequestFor),
                ProviderFailureClassifier.Stage.TRANSFER))
                .isEqualTo(ProviderFailureClassifier.Classification.IDENTITY_REJECTION);
    }

    private void givenVerifiedRequestForStoredAgreement() {
        when(protocolTokenValidator.verify(eq(participantContext), any(), any(), any()))
                .thenReturn(ServiceResult.success(mock(ParticipantAgent.class)));
        when(validationService.validateRequest(any(), isA(ContractAgreement.class))).thenReturn(Result.success());
        when(negotiationStore.queryAgreements(any())).thenAnswer(invocation -> Stream.of(agreement()));
        when(dataFlowController.transferTypesFor(anyString(), anyString())).thenReturn(Set.of(TRANSFER_TYPE));
    }

    private Response providerAnswer() {
        var request = PostDspRequest.Builder.newInstance(TransferRequestMessage.class, TransferProcess.class, TransferError.class)
                .message(Json.createObjectBuilder().build())
                .token("token")
                .expectedMessageType("TransferRequestMessage")
                .serviceCall(service::notifyRequested)
                .errorProvider(TransferError.Builder::newInstance)
                .protocol(PROTOCOL)
                .participantContextProvider(() -> ServiceResult.success(participantContext))
                .build();
        return requestHandler.createResource(request);
    }

    private TransferRequestMessage transferRequestFor(String counterPartyAddress) {
        return TransferRequestMessage.Builder.newInstance()
                .consumerPid("consumerPid")
                .processId("consumerPid")
                .contractId("agreementId")
                .protocol(PROTOCOL)
                .callbackAddress("http://any")
                .counterPartyAddress(counterPartyAddress)
                .transferType(TRANSFER_TYPE)
                .build();
    }

    private DspProtocolTypeTransformerRegistry protocolTransformers() {
        var errorTransformer = new JsonObjectFromTransferErrorTransformer(
                Json.createBuilderFactory(Map.of()), DSP_NAMESPACE_V_2025_1);
        var transformers = mock(TypeTransformerRegistry.class);
        when(transformers.transform(any(JsonObject.class), eq(TransferRequestMessage.class)))
                .thenReturn(Result.success(requestMessage));
        when(transformers.transform(any(TransferError.class), eq(JsonObject.class)))
                .thenAnswer(invocation ->
                        Result.success(errorTransformer.transform(invocation.getArgument(0), mock(TransformerContext.class))));
        var registry = mock(DspProtocolTypeTransformerRegistry.class);
        when(registry.forProtocol(PROTOCOL)).thenReturn(Result.success(transformers));
        return registry;
    }

    private ContractAgreement agreement() {
        return ContractAgreement.Builder.newInstance()
                .id("agreementId")
                .providerId("provider")
                .consumerId("consumer")
                .assetId("assetId")
                .policy(Policy.Builder.newInstance().build())
                .participantContextId(participantContext.getId())
                .build();
    }
}
