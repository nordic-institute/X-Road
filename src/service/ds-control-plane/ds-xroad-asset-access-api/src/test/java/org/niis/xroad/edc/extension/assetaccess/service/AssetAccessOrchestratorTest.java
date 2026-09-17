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
import org.eclipse.edc.connector.controlplane.catalog.spi.DataService;
import org.eclipse.edc.connector.controlplane.catalog.spi.Dataset;
import org.eclipse.edc.connector.controlplane.catalog.spi.Distribution;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessOrchestratorTest {

    @Mock
    CatalogService catalogService;
    @Mock
    ContractNegotiationService contractNegotiationService;
    @Mock
    TransferProcessService transferProcessService;
    @Mock
    ContractNegotiationStore negotiationStore;
    @Mock
    TransferProcessStore transferProcessStore;
    @Mock
    DataAddressStore dataAddressStore;
    @Mock
    ReusableAgreementLookup reusableAgreementLookup;
    @Mock
    JsonLd jsonLd;
    @Mock
    TypeTransformerRegistry transformerRegistry;
    @Mock
    Monitor monitor;

    AssetAccessCompletionPoller completionPoller;

    AssetAccessOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(reusableAgreementLookup.find(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(dataAddressStore.resolve(any())).thenAnswer(invocation -> {
            TransferProcess transferProcess = invocation.getArgument(0);
            return transferProcess == null ? null : StoreResult.success(transferProcess.getContentDataAddress());
        });
        completionPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                new NoopTransactionContext(), ExecutorInstrumentation.noop(), Clock.systemUTC(), monitor, Duration.ofMillis(250));
        orchestrator = new AssetAccessOrchestrator(new AssetAccessStateStore(), reusableAgreementLookup, catalogService,
                contractNegotiationService, transferProcessService, completionPoller,
                jsonLd, transformerRegistry, monitor,
                Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        completionPoller.stop();
    }

    @Test
    void acquireAssetAccessReuseHitSkipsCatalogAndNegotiationAndUsesPullTransferType() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        var agreement = buildAgreement("agreement-1");
        when(reusableAgreementLookup.find("participant1", "asset-1", "provider-1")).thenReturn(Optional.of(agreement));

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(ServiceResult.success(transferProcess));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findById("tp-1")).thenReturn(startedTransfer("tp-1", dataAddress));
        completionPoller.poll();

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isSameAs(dataAddress);

        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);

        var transferRequestCaptor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService).initiateTransfer(any(), transferRequestCaptor.capture());
        assertThat(transferRequestCaptor.getValue().getTransferType()).isEqualTo(XRoadTransferType.PULL.wireValue());
        assertThat(transferRequestCaptor.getValue().getContractId()).isEqualTo("agreement-1");
    }

    @Test
    void acquireAssetAccessReusedAgreementTerminatedTransferFailsWithoutRenegotiation() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        var agreement = buildAgreement("agreement-1");
        when(reusableAgreementLookup.find("participant1", "asset-1", "provider-1")).thenReturn(Optional.of(agreement));

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(ServiceResult.success(transferProcess));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        when(transferProcessStore.findById("tp-1")).thenReturn(terminatedTransfer("tp-1", "provider rejected reused agreement"));
        completionPoller.poll();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                });

        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void acquireAssetAccessFullHappyPathReturnsResponseFromTransferData() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any()))
                .thenReturn(ServiceResult.success(negotiation));

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        when(negotiationStore.findById("neg-1")).thenReturn(finalizedNegotiation("neg-1", agreement));
        completionPoller.poll();

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findById("tp-1")).thenReturn(startedTransfer("tp-1", dataAddress));
        completionPoller.poll();

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).isSameAs(dataAddress);

        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void catalogFetchFailureThrowsDspCatalogFetchFailed() {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        StatusResult.failure(ResponseStatus.FATAL_ERROR, "catalog unavailable")));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_catalog_fetch_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                });
    }

    @Test
    void catalogFetchEdcExceptionThrowsDspCatalogFetchFailed() {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);
        var edcException = new EdcException("upstream failure");

        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenThrow(edcException);

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_catalog_fetch_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getCause()).isSameAs(edcException);
                });
    }

    @Test
    void catalogParseFailureThrowsDspCatalogParseFailed() {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.failure("bad catalog json"));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_catalog_parse_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                });
    }

    @Test
    void datasetNotFoundThrowsDspDatasetNotFound() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("other-asset");

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_dataset_not_found");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("asset-1");
                });
    }

    @Test
    void offersNotFoundThrowsDspOffersNotFound() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        var catalog = buildCatalogWithNoOffers("asset-1");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_offers_not_found");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("asset-1");
                });
    }

    @Test
    void pullDistributionMissingThrowsDspPullDistributionMissing() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        var catalog = buildCatalog("asset-1", "offer-1", "JSON-LD");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_pull_distribution_missing");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("asset-1");
                });
    }

    @Test
    void negotiationTerminatedThrowsDspNegotiationFailed() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(ServiceResult.success(negotiation));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        when(negotiationStore.findById("neg-1"))
                .thenReturn(terminatedNegotiation("neg-1", "Contract negotiation terminated: neg-1"));
        completionPoller.poll();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_negotiation_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("neg-1");
                });
        verifyNoInteractions(transferProcessService);
    }

    @Test
    void transferInitiationFailureThrowsDspTransferFailed() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any()))
                .thenReturn(ServiceResult.success(negotiation));
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.unexpected("transfer failed"));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        when(negotiationStore.findById("neg-1")).thenReturn(finalizedNegotiation("neg-1", agreement));
        completionPoller.poll();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                });
    }

    @Test
    void acquireAssetAccessConcurrentIdenticalRequestsReturnSameFuture() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any()))
                .thenReturn(ServiceResult.success(negotiation));

        var future1 = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);
        var future2 = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        assertThat(future1).isSameAs(future2);

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var agreement = buildAgreement("agreement-1");
        when(negotiationStore.findById("neg-1")).thenReturn(finalizedNegotiation("neg-1", agreement));
        completionPoller.poll();

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findById("tp-1")).thenReturn(startedTransfer("tp-1", dataAddress));
        completionPoller.poll();

        future1.get(5, TimeUnit.SECONDS);
    }

    @Test
    void transferTerminatedThrowsDspTransferFailed() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = new AssetAccessRequest("asset-1", "provider-1", "http://provider/dsp", null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any()))
                .thenReturn(ServiceResult.success(negotiation));
        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcess));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        var agreement = buildAgreement("agreement-1");
        when(negotiationStore.findById("neg-1")).thenReturn(finalizedNegotiation("neg-1", agreement));
        completionPoller.poll();

        when(transferProcessStore.findById("tp-1")).thenReturn(terminatedTransfer("tp-1", null));
        completionPoller.poll();

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_transfer_failed");
                    assertThat(cause.getOrigin()).isEqualTo(ErrorOrigin.DATASPACE);
                    assertThat(cause.getErrorCodeMetadata()).contains("tp-1");
                });
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

    private ContractNegotiation finalizedNegotiation(String id, ContractAgreement agreement) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .state(ContractNegotiationStates.FINALIZED.code())
                .contractAgreement(agreement)
                .build();
    }

    private ContractNegotiation terminatedNegotiation(String id, String errorDetail) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .state(ContractNegotiationStates.TERMINATED.code())
                .errorDetail(errorDetail)
                .build();
    }

    private TransferProcess startedTransfer(String id, DataAddress dataAddress) {
        return TransferProcess.Builder.newInstance()
                .id(id)
                .state(TransferProcessStates.STARTED.code())
                .contentDataAddress(dataAddress)
                .build();
    }

    private TransferProcess terminatedTransfer(String id, String errorDetail) {
        return TransferProcess.Builder.newInstance()
                .id(id)
                .state(TransferProcessStates.TERMINATED.code())
                .errorDetail(errorDetail)
                .build();
    }

    private void stubCatalogAndTransformChain(String assetId) {
        var catalog = buildCatalog(assetId, "offer-1", XRoadTransferType.PULL.wireValue());
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
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

    private Catalog buildCatalogWithNoOffers(String assetId) {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().format(XRoadTransferType.PULL.wireValue()).dataService(dataService).build();
        var dataset = Dataset.Builder.newInstance()
                .id(assetId)
                .distribution(distribution)
                .build();
        return Catalog.Builder.newInstance().dataset(dataset).build();
    }
}
