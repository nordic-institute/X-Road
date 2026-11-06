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
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssetAccessOrchestratorReuseNoteTest extends AssetAccessOrchestratorTestSupport {

    @Test
    void anAgreementTheStoreNoLongerHoldsIsForgottenAndTheLookupTakesOver() throws Exception {
        reuseNote.remember(noteKey(GROUP_CLIENT_A), "agreement-gone");
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-1", GROUP_CLIENT_A))
                .thenReturn(Optional.of(buildAgreement("agreement-found")));
        stubStartedTransfers("tp-1");

        acquire(GROUP_CLIENT_A);

        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getValue().getContractId()).isEqualTo("agreement-found");
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-found");
        verifyNoInteractions(contractNegotiationService);
    }

    @Test
    void anUnrestrictedServiceOnlyAgreementIsNotedPerCallingSubsystem() throws Exception {
        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-open", unrestrictedPolicy())));
        stubNegotiatedAgreements("agreement-open-a", "agreement-open-b");
        stubStartedTransfers("tp-1", "tp-2");

        acquire(GROUP_CLIENT_A);
        acquire(GROUP_CLIENT_B);

        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-open-a");
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_B))).contains("agreement-open-b");
    }

    @Test
    void groupGrantedSubsystemReusesItsAgreementOnTheSecondAcquisition() throws Exception {
        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-group", groupPolicy("DEV:COM:222:owner;group"))));
        stubNegotiatedAgreements("agreement-g");
        stubFindById(buildAgreement("agreement-g"));
        stubStartedTransfers("tp-1", "tp-2");

        var first = acquire(GROUP_CLIENT_A);
        var second = acquire(GROUP_CLIENT_A);

        assertThat(first.dataAddress().getStringProperty("endpoint")).isEqualTo("http://provider/data-tp-1");
        assertThat(second.dataAddress().getStringProperty("endpoint")).isEqualTo("http://provider/data-tp-2");
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(2)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId)
                .containsExactly("agreement-g", "agreement-g");
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-g");
    }

    @Test
    void twoSubsystemsOfOneMemberInTheSameGroupGetSeparateEntries() throws Exception {
        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-group", groupPolicy("DEV:COM:222:owner;group"))));
        stubNegotiatedAgreements("agreement-a", "agreement-b");
        stubFindById(buildAgreement("agreement-a"));
        stubFindById(buildAgreement("agreement-b"));
        stubStartedTransfers("tp-1", "tp-2", "tp-3", "tp-4");

        acquire(GROUP_CLIENT_A);
        acquire(GROUP_CLIENT_B);
        acquire(GROUP_CLIENT_A);
        acquire(GROUP_CLIENT_B);

        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-a");
        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_B))).contains("agreement-b");
        verify(contractNegotiationService, times(2)).initiateNegotiation(any(), any());
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(4)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId)
                .containsExactly("agreement-a", "agreement-b", "agreement-a", "agreement-b");
    }

    @Test
    void refusalOfAnAgreementTheNoteDoesNotHoldLeavesTheNewerEntryInPlace() {
        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-a", clientPolicy(GROUP_CLIENT_A))));
        stubNegotiatedAgreements("agreement-2");
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-1", GROUP_CLIENT_A))
                .thenReturn(Optional.of(buildAgreement("agreement-old")));
        var calls = new AtomicInteger();
        when(transferProcessService.initiateTransfer(any(), any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() > 0) {
                return ServiceResult.badRequest("renegotiated transfer rejected locally");
            }
            reuseNote.remember(noteKey(GROUP_CLIENT_A), "agreement-newer");
            return ServiceResult.conflict(PROVIDER_CONFLICT);
        });

        acquireExpectingFailure(GROUP_CLIENT_A);

        assertThat(reuseNote.find(noteKey(GROUP_CLIENT_A))).contains("agreement-newer");
    }
}
