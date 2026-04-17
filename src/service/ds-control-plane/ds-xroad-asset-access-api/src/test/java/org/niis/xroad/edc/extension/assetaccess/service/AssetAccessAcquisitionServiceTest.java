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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.catalog.spi.Catalog;
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessStartedData;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.listener.TransferCompletionListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessAcquisitionServiceTest {

    @Mock
    CatalogService catalogService;
    @Mock
    ContractNegotiationService contractNegotiationService;
    @Mock
    TransferProcessService transferProcessService;
    @Mock
    JsonLd jsonLd;
    @Mock
    TypeTransformerRegistry transformerRegistry;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    Monitor monitor;

    NegotiationCompletionListener negotiationListener;
    TransferCompletionListener transferListener;

    AssetAccessAcquisitionService service;

    @BeforeEach
    void setUp() {
        negotiationListener = new NegotiationCompletionListener();
        transferListener = new TransferCompletionListener();
        service = new AssetAccessAcquisitionService(catalogService, contractNegotiationService,
                transferProcessService, negotiationListener, transferListener,
                jsonLd, transformerRegistry, objectMapper, monitor);
    }

    @Test
    void acquireAssetAccessWithExistingAgreementSkipsCatalogAndNegotiation() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        // --- First call: full flow (catalog → negotiate → transfer) ---
        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        var transferProcess1 = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess1));

        var future1 = service.acquireAssetAccess(participantContext, assetAccessRequest);

        // Drive negotiation to finalized
        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        // Drive transfer to started
        var dataAddress1 = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData1 = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(dataAddress1).build();
        transferListener.started(TransferProcess.Builder.newInstance().id("tp-1").build(), startedData1);

        var result1 = future1.get(5, TimeUnit.SECONDS);
        assertThat(result1.succeeded()).isTrue();

        // --- Second call: agreement registry hit — skips catalog + negotiation ---
        var transferProcess2 = TransferProcess.Builder.newInstance().id("tp-2").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess2));

        var future2 = service.acquireAssetAccess(participantContext, assetAccessRequest);

        // Drive only the transfer listener for the second call
        var dataAddress2 = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data-refreshed").build();
        var startedData2 = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(dataAddress2).build();
        transferListener.started(TransferProcess.Builder.newInstance().id("tp-2").build(), startedData2);

        var result2 = future2.get(5, TimeUnit.SECONDS);
        assertThat(result2.succeeded()).isTrue();
        assertThat(result2.getContent()).isSameAs(dataAddress2);

        // Verify: catalog and negotiation called only once, transfer called twice
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(2)).initiateTransfer(any(), any());
    }

    @Test
    void acquireAssetAccessFullHappyPathReturnsResponseFromTransferListener() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(dataAddress).build();
        transferListener.started(TransferProcess.Builder.newInstance().id("tp-1").build(), startedData);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isSameAs(dataAddress);

        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void acquireAssetAccessDistributionWithMismatchedFormatRejected() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        // "JSON-PULL" matches the old .contains("PULL") predicate but MUST NOT match "Xrd-PULL".equals(f).
        var catalog = buildCatalog("asset-1", "offer-1", "JSON-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class))).thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class)
                .hasMessageContaining("No PULL distribution found");
    }

    @Test
    void acquireAssetAccessCatalogFetchFailurePropagatesAsEdcException() {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        StatusResult.failure(ResponseStatus.FATAL_ERROR, "catalog unavailable")));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
    }

    @Test
    void acquireAssetAccessNoMatchingOfferPropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("other-asset");

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
    }

    @Test
    void acquireAssetAccessNegotiationTerminatedPropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        var terminatedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        negotiationListener.terminated(terminatedNegotiation);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
        verifyNoInteractions(transferProcessService);
    }

    @Test
    void acquireAssetAccessTransferInitiationFailurePropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.unexpected("transfer failed"));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
    }

    @Test
    void acquireAssetAccessListenerCleanupAfterSuccess() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(dataAddress).build();
        transferListener.started(TransferProcess.Builder.newInstance().id("tp-1").build(), startedData);

        future.get(5, TimeUnit.SECONDS);

        assertThat(negotiationListener.activeWaiters()).isZero();
        assertThat(transferListener.activeWaiters()).isZero();
    }

    @Test
    void acquireAssetAccessListenerCleanupAfterTransferFailure() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        transferListener.terminated(TransferProcess.Builder.newInstance().id("tp-1").build());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);

        assertThat(negotiationListener.activeWaiters()).isZero();
        assertThat(transferListener.activeWaiters()).isZero();
    }

    @Test
    void acquireAssetAccessConcurrentIdenticalRequestsReturnSameFuture() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        var future1 = service.acquireAssetAccess(participantContext, assetAccessRequest);
        var future2 = service.acquireAssetAccess(participantContext, assetAccessRequest);

        // Both must return the same CompletableFuture instance (deduplication)
        assertThat(future1).isSameAs(future2);

        // Drive to completion to avoid Mockito strict stub failures
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(dataAddress).build();
        transferListener.started(TransferProcess.Builder.newInstance().id("tp-1").build(), startedData);

        future1.get(5, TimeUnit.SECONDS);
    }

    @Test
    void acquireAssetAccessTransferTerminatedPropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = service.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        transferListener.terminated(TransferProcess.Builder.newInstance().id("tp-1").build());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
    }

    private ParticipantContext buildParticipantContext() {
        return ParticipantContext.Builder.newInstance()
                .participantContextId("participant1")
                .identity("participant1")
                .build();
    }

    private ContractAgreement buildAgreement(String agreementId) {
        return ContractAgreement.Builder.newInstance()
                .id(agreementId)
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
    }

    private void stubCatalogAndTransformChain(String assetId) throws Exception {
        var catalog = buildCatalog(assetId, "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));
    }

    private Catalog buildCatalog(String assetId, String offerId, String transferType) {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().format(transferType).dataService(dataService).build();
        var dataset = Dataset.Builder.newInstance()
                .id(assetId)
                .offer(offerId, Policy.Builder.newInstance().build())
                .distribution(distribution)
                .build();
        return Catalog.Builder.newInstance().dataset(dataset).build();
    }
}
