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
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.controlplane.DataAddress;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.protocol.assetaccess.ProviderFailureClassifier;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives EDC's own provider-side transfer request handler into each answer a consumer can receive and
 * feeds that answer, in the sentence the consumer's dispatcher builds around it, to
 * {@link ProviderFailureClassifier}. A reworded EDC message turns this test red.
 */
class ProviderRefusalCanaryTest {

    private static final String TRANSFER_TYPE = "transferType";

    private final ContractNegotiationStore negotiationStore = mock();
    private final ContractValidationService validationService = mock();
    private final ProtocolTokenValidator protocolTokenValidator = mock();
    private final DataFlowController dataFlowController = mock();
    private final ParticipantContext participantContext = ParticipantContext.Builder.newInstance()
            .id("participantContextId")
            .identity("participantId")
            .build();
    private final TokenRepresentation token = TokenRepresentation.Builder.newInstance().token("token").build();

    private TransferProcessProtocolServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransferProcessProtocolServiceImpl(mock(TransferProcessStore.class), new NoopTransactionContext(),
                negotiationStore, validationService, protocolTokenValidator, mock(TransferProcessObservable.class),
                mock(Monitor.class), dataFlowController, mock(DataAddressStore.class),
                mock(TransferProcessProviderFactory.class));
    }

    @Test
    void anUnknownAgreementIsAnAnswerTheRecogniserTakesForARefusal() {
        when(negotiationStore.queryAgreements(any())).thenAnswer(invocation -> Stream.empty());

        var failure = requestTransfer();

        assertThat(failure.getReason()).isEqualTo(ServiceFailure.Reason.NOT_FOUND);
        assertThat(failure.getFailureDetail().toLowerCase())
                .contains(ProviderFailureClassifier.AGREEMENT_NOT_VALID);
        assertThat(ProviderFailureClassifier.isRefusal(consumerSideDetail(404, failure))).isTrue();
    }

    @Test
    void anAgreementThatIsNoLongerValidIsAnAnswerTheRecogniserTakesForARefusal() {
        var participantAgent = mock(ParticipantAgent.class);
        when(protocolTokenValidator.verify(eq(participantContext), eq(token), any(), any(), any()))
                .thenReturn(ServiceResult.success(participantAgent));
        when(validationService.validateRequest(any(), isA(ContractAgreement.class))).thenReturn(Result.success());
        when(negotiationStore.queryAgreements(any())).thenAnswer(invocation -> Stream.of(agreement()));
        when(dataFlowController.transferTypesFor(anyString(), anyString())).thenReturn(Set.of(TRANSFER_TYPE));
        when(validationService.validateAgreement(any(ParticipantAgent.class), any()))
                .thenReturn(Result.failure("agreement expired"));

        var failure = requestTransfer();

        assertThat(failure.getReason()).isEqualTo(ServiceFailure.Reason.CONFLICT);
        assertThat(ProviderFailureClassifier.isRefusal(consumerSideDetail(409, failure))).isTrue();
    }

    @Test
    void aTokenTheProviderCannotVerifyIsNotARefusal() {
        when(protocolTokenValidator.verify(eq(participantContext), eq(token), any(), any(), any()))
                .thenReturn(ServiceResult.unauthorized("bad token"));
        when(negotiationStore.queryAgreements(any())).thenAnswer(invocation -> Stream.of(agreement()));

        var failure = requestTransfer();

        assertThat(failure.getReason()).isEqualTo(ServiceFailure.Reason.NOT_FOUND);
        assertThat(ProviderFailureClassifier.isRefusal(consumerSideDetail(404, failure))).isFalse();
    }

    private ServiceFailure requestTransfer() {
        var message = TransferRequestMessage.Builder.newInstance()
                .consumerPid("consumerPid")
                .processId("consumerPid")
                .contractId("agreementId")
                .protocol("protocol")
                .callbackAddress("http://any")
                .transferType(TRANSFER_TYPE)
                .dataAddress(DataAddress.Builder.newInstance().type("any").build())
                .build();
        var result = service.notifyRequested(participantContext, message, token);
        assertThat(result.failed()).isTrue();
        return result.getFailure();
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

    private static String consumerSideDetail(int httpStatus, ServiceFailure failure) {
        return "Counter Party responded with Response{protocol=h2, code=%d, message=, url=https://provider.example/dsp}. Body: %s."
                .formatted(httpStatus, failure.getFailureDetail());
    }
}
