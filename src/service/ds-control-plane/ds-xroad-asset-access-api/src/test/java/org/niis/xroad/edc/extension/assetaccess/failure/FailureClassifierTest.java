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

package org.niis.xroad.edc.extension.assetaccess.failure;

import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FailureClassifierTest {
    private static final String CLIENT_ERROR_TEXT = "Counter Party responded with "
            + "Response{protocol=http/1.1, code=403, message=Forbidden, url=http://provider.test/dsp}. Body: {}.";
    private static final String TRANSPORT_TEXT = EdcHttpClientException.class.getName()
            + ": Failed to connect to provider.test/192.0.2.1:80";
    private static final String SERVER_ERROR_TEXT = EdcHttpClientException.class.getName()
            + ": Server response to [GET, http://provider.test/dsp] was not one of [2xx, 4xx] but was 503: ";
    private static final String LOCAL_TEXT = "Unable to obtain credentials: no credentials";
    private static final String REQUEST_PREFIX = "Failed to request contract to provider: ";
    private static final String VERIFICATION_PREFIX = "Failed to send verification to provider: ";
    private static final String TERMINATION_PREFIX = "Failed to send termination to counter party: ";
    @Test
    void noResponseIsATransportFault() {
        assertThat(FailureClassifier.classify(new EdcHttpClientException("no response", 0, null)))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void noResponseBehindAWrapperIsATransportFault() {
        assertThat(FailureClassifier.classify(new CompletionException(new EdcHttpClientException("no response", 0, null))))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void providerServerErrorIsOther() {
        assertThat(FailureClassifier.classify(new EdcHttpClientException("Server response was 503", 503, "unavailable")))
                .isEqualTo(FailureKind.OTHER);
    }

    @Test
    void localEdcFailureIsOther() {
        assertThat(FailureClassifier.classify(new EdcException("no dispatcher for the protocol"))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void stageTimeoutIsATransportFault() {
        assertThat(FailureClassifier.classify(new TimeoutException())).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void stageTimeoutBehindAWrapperIsATransportFault() {
        assertThat(FailureClassifier.classify(new CompletionException(new TimeoutException())))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void anythingElseIsOther() {
        assertThat(FailureClassifier.classify(new IllegalStateException("boom"))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void clientErrorStatusResultIsAProviderRejection() {
        assertThat(FailureClassifier.classify(StatusResult.failure(ResponseStatus.FATAL_ERROR, "Counter Party responded with 403")))
                .isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void retryableStatusResultIsOther() {
        assertThat(FailureClassifier.classify(StatusResult.failure(ResponseStatus.ERROR_RETRY, "Counter Party responded with 302")))
                .isEqualTo(FailureKind.OTHER);
    }

    @Test
    void negotiationClientErrorTextWithRequestPrefixIsAProviderRejection() {
        assertThat(FailureClassifier.classify(negotiation(REQUEST_PREFIX + CLIENT_ERROR_TEXT, false)))
                .isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void negotiationClientErrorTextWithTerminationPrefixIsAProviderRejection() {
        assertThat(FailureClassifier.classify(negotiation(TERMINATION_PREFIX + CLIENT_ERROR_TEXT, false)))
                .isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void negotiationClientErrorTextWithoutPrefixIsAProviderRejection() {
        assertThat(FailureClassifier.classify(negotiation(CLIENT_ERROR_TEXT, false))).isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void negotiationNoResponseTextWithRequestPrefixIsATransportFault() {
        assertThat(FailureClassifier.classify(negotiation(REQUEST_PREFIX + TRANSPORT_TEXT, false)))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void negotiationNoResponseTextWithoutPrefixIsATransportFault() {
        assertThat(FailureClassifier.classify(negotiation(TRANSPORT_TEXT, false))).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void negotiationServerErrorTextIsOther() {
        assertThat(FailureClassifier.classify(negotiation(REQUEST_PREFIX + SERVER_ERROR_TEXT, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void negotiationServerErrorTextWithALookAlikeBodyIsOther() {
        assertThat(FailureClassifier.classify(negotiation(REQUEST_PREFIX + SERVER_ERROR_TEXT + CLIENT_ERROR_TEXT, false)))
                .isEqualTo(FailureKind.OTHER);
    }

    @Test
    void negotiationClientErrorTextWithATransportLookAlikeBodyIsAProviderRejection() {
        assertThat(FailureClassifier.classify(negotiation(REQUEST_PREFIX + CLIENT_ERROR_TEXT + TRANSPORT_TEXT, false)))
                .isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void negotiationLocalFailureTextIsOther() {
        assertThat(FailureClassifier.classify(negotiation(REQUEST_PREFIX + LOCAL_TEXT, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void negotiationErrorTextDecidesOverReceivedMessagesForAFailedSend() {
        assertThat(FailureClassifier.classify(negotiation(VERIFICATION_PREFIX + TRANSPORT_TEXT, true)))
                .isEqualTo(FailureKind.TRANSPORT_FAULT);
        assertThat(FailureClassifier.classify(negotiation(VERIFICATION_PREFIX + CLIENT_ERROR_TEXT, true)))
                .isEqualTo(FailureKind.PROVIDER_REJECTION);
        assertThat(FailureClassifier.classify(negotiation(VERIFICATION_PREFIX + LOCAL_TEXT, true))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void negotiationWithoutErrorTextAndWithAReceivedMessageIsAProviderRejection() {
        assertThat(FailureClassifier.classify(negotiation(null, true))).isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void negotiationWithoutErrorTextAndWithoutReceivedMessagesIsOther() {
        assertThat(FailureClassifier.classify(negotiation(null, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void transferWithAReceivedMessageIsAProviderRejectionWhateverTheText() {
        assertThat(FailureClassifier.classify(transfer(TRANSPORT_TEXT, true))).isEqualTo(FailureKind.PROVIDER_REJECTION);
        assertThat(FailureClassifier.classify(transfer("provider free text", true))).isEqualTo(FailureKind.PROVIDER_REJECTION);
        assertThat(FailureClassifier.classify(transfer(null, true))).isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void transferClientErrorTextIsAProviderRejection() {
        assertThat(FailureClassifier.classify(transfer(CLIENT_ERROR_TEXT, false))).isEqualTo(FailureKind.PROVIDER_REJECTION);
    }

    @Test
    void transferNoResponseTextIsATransportFault() {
        assertThat(FailureClassifier.classify(transfer(TRANSPORT_TEXT, false))).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    @Test
    void transferServerErrorTextIsOther() {
        assertThat(FailureClassifier.classify(transfer(SERVER_ERROR_TEXT, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void transferServerErrorTextWithALookAlikeBodyIsOther() {
        assertThat(FailureClassifier.classify(transfer(SERVER_ERROR_TEXT + CLIENT_ERROR_TEXT, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void transferLocalFailureTextIsOther() {
        assertThat(FailureClassifier.classify(transfer(LOCAL_TEXT, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void transferWithoutErrorTextAndWithoutReceivedMessagesIsOther() {
        assertThat(FailureClassifier.classify(transfer(null, false))).isEqualTo(FailureKind.OTHER);
    }

    @Test
    void missingProtocolMessagesCountAsNoReceivedMessages() {
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider.test/dsp")
                .protocolMessages(null)
                .state(ContractNegotiationStates.TERMINATED.code())
                .build();
        var transfer = TransferProcess.Builder.newInstance()
                .id("tp-1")
                .protocolMessages(null)
                .state(TransferProcessStates.TERMINATED.code())
                .errorDetail(TRANSPORT_TEXT)
                .build();

        assertThat(FailureClassifier.classify(negotiation)).isEqualTo(FailureKind.OTHER);
        assertThat(FailureClassifier.classify(transfer)).isEqualTo(FailureKind.TRANSPORT_FAULT);
    }

    private static ContractNegotiation negotiation(String errorDetail, boolean receivedMessage) {
        return ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider.test/dsp")
                .protocolMessages(protocolMessages(receivedMessage))
                .state(ContractNegotiationStates.TERMINATED.code())
                .errorDetail(errorDetail)
                .build();
    }

    private static TransferProcess transfer(String errorDetail, boolean receivedMessage) {
        return TransferProcess.Builder.newInstance()
                .id("tp-1")
                .protocolMessages(protocolMessages(receivedMessage))
                .state(TransferProcessStates.TERMINATED.code())
                .errorDetail(errorDetail)
                .build();
    }

    private static ProtocolMessages protocolMessages(boolean receivedMessage) {
        var messages = new ProtocolMessages();
        if (receivedMessage) {
            messages.addReceived("received-1");
        }
        return messages;
    }
}
