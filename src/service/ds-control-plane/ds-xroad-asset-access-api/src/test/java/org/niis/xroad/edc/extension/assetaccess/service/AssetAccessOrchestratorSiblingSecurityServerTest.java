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

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;

class AssetAccessOrchestratorSiblingSecurityServerTest extends AssetAccessOrchestratorTestSupport {

    private static final String PETSTORE_ASSET_ID = "DEV:COM:222:TESTSERVICE:petstore";
    private static final String TESTCLIENT_ID = "DEV:COM:222:TESTCLIENT";
    private static final AssetAccessRequest.Candidate SECURITY_SERVER =
            new AssetAccessRequest.Candidate("ss0.example.org", "http://ss0.example.org/dsp");
    private static final AssetAccessRequest.Candidate SIBLING_SECURITY_SERVER =
            new AssetAccessRequest.Candidate("ss1.example.org", "http://ss1.example.org/dsp");

    @Test
    void transportFaultThenNoOfferOnTheSiblingSecurityServerFailsAsAccessDeniedAndMarksOnlyTheFirstServer() {
        stubTransportFaultAtCatalog(SECURITY_SERVER.counterPartyId());
        stubCatalogAt(SIBLING_SECURITY_SERVER.counterPartyId(), buildCatalogWithNoOffers(PETSTORE_ASSET_ID));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), petstoreRequestForTestClient(), defaultBudget());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(ClientFacingErrorPolicy
                        .sanitizeForConsumer((XrdRuntimeException) ex.getCause()).isCausedBy(ACCESS_DENIED)).isTrue());
        verify(catalogService, times(1)).requestCatalog(any(), eq(SIBLING_SECURITY_SERVER.counterPartyId()), any(), any(), any());
        verifyNoInteractions(contractNegotiationService, transferProcessService);
        assertThat(cooldownTracker.isCoolingDown(SECURITY_SERVER.counterPartyAddress())).isTrue();
        assertThat(cooldownTracker.isCoolingDown(SIBLING_SECURITY_SERVER.counterPartyAddress())).isFalse();
    }

    @Test
    void transportFaultThenAnAuthorisedSiblingSecurityServerWinsWithItsOwnAddressAndAgreement() throws Exception {
        when(reusableAgreementLookup.find("participant1", "participant1", PETSTORE_ASSET_ID,
                SECURITY_SERVER.counterPartyId(), TESTCLIENT_ID))
                .thenReturn(Optional.of(buildAgreement("agreement-security-server", SECURITY_SERVER.counterPartyId(), 1_000L)));
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(successfulTransferInitiation("tp-security-server"))
                .thenReturn(successfulTransferInitiation("tp-sibling-security-server"));
        when(transferProcessStore.findAll(specWithId("tp-security-server")))
                .thenAnswer(_ -> Stream.of(transportFaultTransfer("tp-security-server")));
        var address = startedAddress("tp-sibling-security-server", "http://ss1.example.org/data");
        stubCatalogAt(SIBLING_SECURITY_SERVER.counterPartyId(), buildCatalogWithOffers(PETSTORE_ASSET_ID,
                Map.of("offer-testclient", clientPolicy(TESTCLIENT_ID))));
        var contractRequests = ArgumentCaptor.forClass(ContractRequest.class);
        when(contractNegotiationService.initiateNegotiation(any(), contractRequests.capture()))
                .thenReturn(ServiceResult.success(buildNegotiation()));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ -> Stream.of(finalizedNegotiation("neg-1",
                buildAgreement("agreement-sibling-security-server", SIBLING_SECURITY_SERVER.counterPartyId(), 2_000L))));

        var result = acquireAndAwait(petstoreRequestForTestClient());

        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner()).isEqualTo(new AssetAccessResult.Winner(
                SIBLING_SECURITY_SERVER.counterPartyId(), SIBLING_SECURITY_SERVER.counterPartyAddress()));
        assertThat(contractRequests.getAllValues()).extracting(ContractRequest::getCounterPartyAddress)
                .containsExactly(SIBLING_SECURITY_SERVER.counterPartyAddress());
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(2)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId, TransferRequest::getCounterPartyAddress)
                .containsExactly(
                        tuple("agreement-security-server", SECURITY_SERVER.counterPartyAddress()),
                        tuple("agreement-sibling-security-server", SIBLING_SECURITY_SERVER.counterPartyAddress()));
        assertThat(cooldownTracker.isCoolingDown(SECURITY_SERVER.counterPartyAddress())).isTrue();
        assertThat(cooldownTracker.isCoolingDown(SIBLING_SECURITY_SERVER.counterPartyAddress())).isFalse();
    }

    private static AssetAccessRequest petstoreRequestForTestClient() {
        return new AssetAccessRequest(PETSTORE_ASSET_ID, List.of(SECURITY_SERVER, SIBLING_SECURITY_SERVER), null,
                TESTCLIENT_ID, null);
    }

    private void stubTransportFaultAtCatalog(String counterPartyId) {
        when(catalogService.requestCatalog(any(), eq(counterPartyId), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new EdcHttpClientException("no response", 0, null)));
    }

    private void stubCatalogAt(String counterPartyId, Catalog catalog) {
        when(catalogService.requestCatalog(any(), eq(counterPartyId), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));
    }
}
