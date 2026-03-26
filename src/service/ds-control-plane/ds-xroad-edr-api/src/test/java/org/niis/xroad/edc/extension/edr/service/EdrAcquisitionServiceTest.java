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

package org.niis.xroad.edc.extension.edr.service;

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
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.edr.EdrRequest;
import org.niis.xroad.edc.extension.edr.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.edr.listener.TransferCompletionListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdrAcquisitionServiceTest {

    @Mock
    CatalogService catalogService;
    @Mock
    ContractNegotiationService contractNegotiationService;
    @Mock
    TransferProcessService transferProcessService;
    @Mock
    EndpointDataReferenceStore edrStore;
    @Mock
    JsonLd jsonLd;
    @Mock
    TypeTransformerRegistry transformerRegistry;
    @Mock
    ObjectMapper objectMapper;

    NegotiationCompletionListener negotiationListener;
    TransferCompletionListener transferListener;

    EdrAcquisitionService service;

    @BeforeEach
    void setUp() {
        negotiationListener = new NegotiationCompletionListener();
        transferListener = new TransferCompletionListener();
        service = new EdrAcquisitionService(catalogService, contractNegotiationService,
                transferProcessService, edrStore, negotiationListener, transferListener,
                jsonLd, transformerRegistry, objectMapper);
    }

    @Test
    void acquireEdrReturnsCachedEdrWithoutCallingCatalogService() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);
        var cachedAddress = DataAddress.Builder.newInstance().type("HttpData").build();
        var entry = EndpointDataReferenceEntry.Builder.newInstance()
                .participantContextId("participant1")
                .assetId("asset-1")
                .providerId("provider-1")
                .agreementId("agreement-1")
                .transferProcessId("tp-1")
                .build();

        when(edrStore.query(any(QuerySpec.class)))
                .thenReturn(StoreResult.success(List.of(entry)));
        when(edrStore.resolveByTransferProcess("tp-1"))
                .thenReturn(StoreResult.success(cachedAddress));

        var result = service.acquireEdr(participantContext, edrRequest).get(5, TimeUnit.SECONDS);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isSameAs(cachedAddress);
        verifyNoInteractions(catalogService);
    }

    @Test
    void acquireEdrFullHappyPathReturnsEdrFromTransferListener() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        // Cache miss
        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        // Stub catalog fetch
        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

        // Stub negotiation
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        // Stub transfer
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        // Launch async (blocks internally waiting for listeners to fire)
        var future = service.acquireEdr(participantContext, edrRequest);

        // Fire negotiation finalized event directly on the shared listener
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        // Fire transfer started event directly on the shared listener
        var edrAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(edrAddress).build();
        var startedProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        transferListener.started(startedProcess, startedData);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isSameAs(edrAddress);
    }

    @Test
    void acquireEdrCatalogFetchFailurePropagatesAsEdcException() {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        StatusResult.failure(ResponseStatus.FATAL_ERROR, "catalog unavailable")));

        var future = service.acquireEdr(participantContext, edrRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
    }

    @Test
    void acquireEdrNoMatchingOfferPropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        var catalogNoMatch = buildCatalog("other-asset", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalogNoMatch));

        var future = service.acquireEdr(participantContext, edrRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);
    }

    @Test
    void acquireEdrNegotiationTerminatedPropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        var future = service.acquireEdr(participantContext, edrRequest);

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
    void acquireEdrTransferInitiationFailurePropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.unexpected("transfer failed"));

        var future = service.acquireEdr(participantContext, edrRequest);

        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
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
    void acquireEdrListenerCleanupAfterSuccess() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        // Cache miss
        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        // Stub catalog fetch
        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

        // Stub negotiation
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        // Stub transfer
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = service.acquireEdr(participantContext, edrRequest);

        // Drive negotiation to finalized
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        // Drive transfer to started
        var edrAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(edrAddress).build();
        var startedProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        transferListener.started(startedProcess, startedData);

        // Wait for future to complete
        future.get(5, TimeUnit.SECONDS);

        // Verify both listeners cleaned up after success (whenComplete removes from registry)
        assertThat(negotiationListener.activeWaiters()).isZero();
        assertThat(transferListener.activeWaiters()).isZero();
    }

    @Test
    void acquireEdrListenerCleanupAfterTransferFailure() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

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

        var future = service.acquireEdr(participantContext, edrRequest);

        // Drive negotiation to finalized
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        // Drive transfer to terminated (failure path)
        var terminatedProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        transferListener.terminated(terminatedProcess);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcException.class);

        // Verify both listeners cleaned up after transfer failure
        assertThat(negotiationListener.activeWaiters()).isZero();
        assertThat(transferListener.activeWaiters()).isZero();
    }

    @Test
    void acquireEdrConcurrentIdenticalRequestsReturnSameFuture() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        // Cache miss for both calls
        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        // Stub catalog fetch
        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

        // Stub negotiation
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(negotiation);

        // Call acquireEdr twice with identical args before any listener fires
        var future1 = service.acquireEdr(participantContext, edrRequest);
        var future2 = service.acquireEdr(participantContext, edrRequest);

        // Both must return the same CompletableFuture instance (deduplication)
        assertThat(future1).isSameAs(future2);

        // Drive to completion to avoid Mockito strict stub failures
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        var edrAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        var startedData = TransferProcessStartedData.Builder.newInstance()
                .dataAddress(edrAddress).build();
        var startedProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        transferListener.started(startedProcess, startedData);

        future1.get(5, TimeUnit.SECONDS);
    }

    @Test
    void acquireEdrTransferTerminatedPropagatesAsEdcException() throws Exception {
        var participantContext = buildParticipantContext();
        var edrRequest = new EdrRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(edrStore.query(any())).thenReturn(StoreResult.success(List.of()));

        var catalog = buildCatalog("asset-1", "offer-1", "Xrd-PULL");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success(new byte[0])));
        when(objectMapper.readValue(any(String.class), eq(JsonObject.class)))
                .thenReturn(mock(JsonObject.class));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));

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

        var future = service.acquireEdr(participantContext, edrRequest);

        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-1")
                .providerId("provider-1")
                .consumerId("participant1")
                .contractSigningDate(System.currentTimeMillis())
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
        var finalizedNegotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("dataspace-protocol-http:2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .contractAgreement(agreement)
                .build();
        negotiationListener.finalized(finalizedNegotiation);

        var terminatedProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        transferListener.terminated(terminatedProcess);

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
