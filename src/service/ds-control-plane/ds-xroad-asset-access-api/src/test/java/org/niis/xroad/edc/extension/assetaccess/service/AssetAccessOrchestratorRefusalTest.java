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

package org.niis.xroad.edc.extension.assetaccess.service;

import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.spi.EdcException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssetAccessOrchestratorRefusalTest extends AssetAccessOrchestratorTestSupport {

    static Stream<String> failuresThatAreNotRefusals() {
        return Stream.of(
                providerAnswer(404, "Not found"),
                providerAnswer(400, "TransferType is not supported"),
                providerAnswer(401, "agreement token rejected"),
                providerAnswer(500, "agreement not found or not valid"),
                "provider terminated");
    }

    @ParameterizedTest
    @MethodSource("failuresThatAreNotRefusals")
    void aFailureThatIsNotARefusalEvictsNothingAndDoesNotRenegotiate(String detail) {
        reuseNote.remember(noteKey(GROUP_CLIENT_A), "agreement-1");
        stubFindById(buildAgreement("agreement-1"));
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", detail)));

        assertTransferFailed(acquireExpectingFailure(GROUP_CLIENT_A));

        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-1");
    }

    @Test
    void aProviderRefusalRemovesTheEntryAndRenegotiatesExactlyOnce() throws Exception {
        stubRenegotiationAfterReuse();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", providerAnswer(404, "agreement not found or not valid"))));
        startedAddress("tp-2", "http://provider/data-tp-2");

        var result = acquire(GROUP_CLIENT_A);

        assertThat(result.dataAddress().getStringProperty("endpoint")).isEqualTo("http://provider/data-tp-2");
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-2");
    }

    @Test
    void providerOutageThenRecoveryKeepsTheAgreementAndTransfersAgainOnIt() throws Exception {
        reuseNote.remember(noteKey(GROUP_CLIENT_A), "agreement-1");
        stubFindById(buildAgreement("agreement-1"));
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenThrow(new EdcException(new EdcHttpClientException("no response", 0, null)))
                .thenReturn(successfulTransferInitiation("tp-2"));
        startedAddress("tp-2", "http://provider/data-tp-2");

        var outage = (XrdRuntimeException) acquireExpectingFailure(GROUP_CLIENT_A);

        assertThat(outage.getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed");
        assertTransferFailed(outage.getCause());
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-1");

        var recovered = acquire(GROUP_CLIENT_A);

        assertThat(recovered.dataAddress().getStringProperty("endpoint")).isEqualTo("http://provider/data-tp-2");
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(2)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId)
                .containsExactly("agreement-1", "agreement-1");
        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
    }

    @Test
    void aRefusedRenegotiatedAgreementLeavesNoEntryAndDoesNotLoop() {
        stubRenegotiationAfterReuse();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", PROVIDER_CONFLICT)));
        when(transferProcessStore.findAll(specWithId("tp-2"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-2", PROVIDER_CONFLICT)));

        assertTransferFailed(acquireExpectingFailure(GROUP_CLIENT_A));

        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(2)).initiateTransfer(any(), any());
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).isEmpty();
    }

    @Test
    void aRefusedDataAddressOnAReusedAgreementDropsTheEntryAndRenegotiatesOnce() throws Exception {
        stubRenegotiationAfterReuse();
        refusedAddress("tp-1");
        startedAddress("tp-2", "http://provider/data-tp-2");

        var result = acquire(GROUP_CLIENT_A);

        assertThat(result.dataAddress().getStringProperty("endpoint")).isEqualTo("http://provider/data-tp-2");
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-2");
    }

    @Test
    void aRefusedDataAddressAfterAFreshNegotiationFailsAtOnceAndNotesNothing() {
        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-group", groupPolicy("DEV:COM:222:owner;group"))));
        stubNegotiatedAgreements("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        refusedAddress("tp-1");

        assertAccessRefused(acquireExpectingFailure(GROUP_CLIENT_A));

        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).isEmpty();
    }

    @Test
    void aRefusedDataAddressAfterTheRenegotiationFailsTheAcquisitionWithoutLoopingOrNoting() {
        stubRenegotiationAfterReuse();
        refusedAddress("tp-1");
        refusedAddress("tp-2");

        assertAccessRefused(acquireExpectingFailure(GROUP_CLIENT_A));

        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(2)).initiateTransfer(any(), any());
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).isEmpty();
    }

    private void stubRenegotiationAfterReuse() {
        reuseNote.remember(noteKey(GROUP_CLIENT_A), "agreement-1");
        stubFindById(buildAgreement("agreement-1"));
        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-group", groupPolicy("DEV:COM:222:owner;group"))));
        stubNegotiatedAgreements("agreement-2");
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(successfulTransferInitiation("tp-1"))
                .thenReturn(successfulTransferInitiation("tp-2"));
    }

    private static void assertTransferFailed(Throwable failure) {
        assertThat(((XrdRuntimeException) failure).getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
    }

    private static void assertAccessRefused(Throwable failure) {
        assertThat(((XrdRuntimeException) failure).getErrorCode()).isEqualTo("dataspace.dsp_access_refused");
    }
}
