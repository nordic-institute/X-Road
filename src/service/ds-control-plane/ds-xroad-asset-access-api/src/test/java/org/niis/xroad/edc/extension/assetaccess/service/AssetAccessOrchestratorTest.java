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
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferRequest;
import org.eclipse.edc.controlplane.DataAddress;
import org.eclipse.edc.http.spi.EdcHttpClientException;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.entity.ProtocolMessages;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureKind;
import org.niis.xroad.edc.extension.assetaccess.failure.StageOutcome;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

    private final Map<String, DataAddress> resolvableAddresses = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(reusableAgreementLookup.find(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(negotiationStore.queryNegotiations(any())).thenAnswer(invocation -> Stream.empty());
        lenient().when(transferProcessStore.findAll(any())).thenAnswer(invocation -> Stream.empty());
        lenient().when(dataAddressStore.resolve(any())).thenAnswer(invocation -> {
            TransferProcess transferProcess = invocation.getArgument(0);
            var address = transferProcess == null ? null : resolvableAddresses.get(transferProcess.getId());
            return address != null ? StoreResult.success(address) : StoreResult.notFound("no data address stored");
        });
        completionPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                new NoopTransactionContext(), ExecutorInstrumentation.noop(), Clock.systemUTC(), monitor, Duration.ofMillis(250));
        orchestrator = new AssetAccessOrchestrator(new AssetAccessStateStore(), reusableAgreementLookup,
                catalogService, contractNegotiationService, transferProcessService, completionPoller,
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
        var assetAccessRequest = requestFor(null);

        var agreement = buildAgreement("agreement-1");
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-1", null))
                .thenReturn(Optional.of(agreement));

        var transferProcess = TransferProcess.Builder.newInstance().id("tp-1").build();
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(ServiceResult.success(transferProcess));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(startedTransfer("tp-1", dataAddress)));
        completionPoller.poll();

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.dataAddress()).isSameAs(dataAddress);

        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);

        var transferRequestCaptor = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService).initiateTransfer(any(), transferRequestCaptor.capture());
        assertThat(transferRequestCaptor.getValue().getTransferType()).isEqualTo(XRoadTransferType.PULL.wireValue());
        assertThat(transferRequestCaptor.getValue().getContractId()).isEqualTo("agreement-1");

        var logCaptor = ArgumentCaptor.forClass(String.class);
        verify(monitor, atLeastOnce()).info(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .anyMatch(message -> message.contains("reusing agreement") && message.contains("agreementId=agreement-1")
                        && message.contains("matchedByClientId=false"));
    }

    @Test
    void reusedAgreementRefusedAtInitiationNegotiatesAfreshAndReturnsTheNewDataAddress() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.badRequest("provider refused agreement-1"))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-2").build()));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-2"))));
        var newAddress = startedAddress("tp-2", "http://provider/new");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThat(future.get(5, TimeUnit.SECONDS).dataAddress()).isSameAs(newAddress);
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(2)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId)
                .containsExactly("agreement-1", "agreement-2");
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
    }

    @Test
    void reusedAgreementTerminatedWhileAwaitedNegotiatesAfreshAndReturnsTheNewDataAddress() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-1").build()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-2").build()));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", "policy no longer holds")));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-2"))));
        var newAddress = startedAddress("tp-2", "http://provider/new");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThat(future.get(5, TimeUnit.SECONDS).dataAddress()).isSameAs(newAddress);
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(2)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId)
                .containsExactly("agreement-1", "agreement-2");
    }

    @Test
    void reusedAgreementTerminatedByProviderRejectionNegotiatesAfresh() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-1").build()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-2").build()));
        var receivedMessages = new ProtocolMessages();
        receivedMessages.addReceived("received-1");
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation -> Stream.of(
                TransferProcess.Builder.newInstance().id("tp-1").state(TransferProcessStates.TERMINATED.code())
                        .protocolMessages(receivedMessages).build()));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-2"))));
        var newAddress = startedAddress("tp-2", "http://provider/new");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThat(future.get(5, TimeUnit.SECONDS).dataAddress()).isSameAs(newAddress);
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(2)).initiateTransfer(any(), any());
    }

    @Test
    void reusedAgreementTerminatedByTransportFaultReturnsTheFailureWithoutRenegotiating() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-1").build()));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", EdcHttpClientException.class.getName() + ": Failed to connect to provider")));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_acquisition_failed"))
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getCause())
                        .isInstanceOf(XrdRuntimeException.class)
                        .satisfies(cause -> assertThat(((XrdRuntimeException) cause).getErrorCode())
                                .isEqualTo("dataspace.dsp_transfer_failed")));
        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void reusedAgreementTransferInitiationFailingWithoutAResponseReturnsTheFailureWithoutRenegotiating() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenThrow(new EdcException(new EdcHttpClientException("no response", 0, null)));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_acquisition_failed"))
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getCause())
                        .isInstanceOf(XrdRuntimeException.class)
                        .satisfies(cause -> assertThat(((XrdRuntimeException) cause).getErrorCode())
                                .isEqualTo("dataspace.dsp_transfer_failed")));
        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void reusedAgreementTransferTimingOutDoesNotRenegotiate() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-1").build()));
        var impatient = new AssetAccessOrchestrator(new AssetAccessStateStore(), reusableAgreementLookup,
                catalogService, contractNegotiationService, transferProcessService, completionPoller,
                jsonLd, transformerRegistry, monitor, Duration.ofSeconds(60), Duration.ZERO);

        var future = impatient.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_acquisition_failed"))
                .satisfies(ex -> assertThat(ex.getCause().getCause()).isInstanceOf(TimeoutException.class));
        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void reusedAgreementTransferStartedButAddressUnresolvableFailsWithoutRenegotiating() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-1").build()));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation -> Stream.of(
                TransferProcess.Builder.newInstance().id("tp-1").state(TransferProcessStates.STARTED.code()).build()));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_dataaddress_invalid"));
        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void failedRenegotiationFailsTheAcquisitionWithTheNegotiationFailureAndDoesNotLoop() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-1").build()));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", "policy no longer holds")));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(terminatedNegotiation("neg-1", "no offer for this client")));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_negotiation_failed"));
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void concurrentAcquisitionsDuringTheFallbackShareOneNegotiation() throws Exception {
        var request = requestFor(null);
        stubReusableAgreement("agreement-1");
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.badRequest("provider refused agreement-1"))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-2").build()));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-2"))));
        startedAddress("tp-2", "http://provider/new");

        var first = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        var second = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(first);

        assertThat(second).isSameAs(first);
        assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull();
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
    }

    private void stubReusableAgreement(String agreementId) {
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-1", null))
                .thenReturn(Optional.of(buildAgreement(agreementId)));
    }

    private DataAddress startedAddress(String transferProcessId, String endpoint) {
        var address = DataAddress.Builder.newInstance().type("HttpData").property("endpoint", endpoint).build();
        when(transferProcessStore.findAll(specWithId(transferProcessId))).thenAnswer(invocation ->
                Stream.of(startedTransfer(transferProcessId, address)));
        return address;
    }

    private void pollUntilDone(CompletableFuture<?> future) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(future).isDone();
        });
    }

    @Test
    void acquireAssetAccessFullHappyPathReturnsResponseFromTransferData() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = requestFor(null);

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
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", agreement)));

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(startedTransfer("tp-1", dataAddress)));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(future).isDone();
        });

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.dataAddress()).isSameAs(dataAddress);

        verify(monitor, atLeastOnce()).info(anyString());
    }

    @Test
    void catalogFetchFiltersByAssetIdWithDefaultLimit() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = requestFor(null);

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
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", agreement)));
        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(startedTransfer("tp-1", dataAddress)));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(future).isDone();
        });
        assertThat(future.get(5, TimeUnit.SECONDS)).isNotNull();

        var querySpecCaptor = ArgumentCaptor.forClass(QuerySpec.class);
        verify(catalogService).requestCatalog(any(), any(), any(), any(), querySpecCaptor.capture());
        var querySpec = querySpecCaptor.getValue();
        assertThat(querySpec.getFilterExpression()).containsExactly(Criterion.criterion("id", "=", "asset-1"));
        assertThat(querySpec.getLimit()).isEqualTo(QuerySpec.none().getLimit());
        assertThat(querySpec.getOffset()).isZero();
    }

    @Test
    void catalogFetchFailureThrowsDspCatalogFetchFailed() {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = requestFor(null);

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
        var assetAccessRequest = requestFor(null);
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

    @ParameterizedTest
    @MethodSource("catalogStageFailedFutures")
    void catalogStageFailedFutureCarriesTheClassifiedKind(Throwable failure, FailureKind expectedKind) throws Exception {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        var outcome = catalogStageOutcome();

        assertThat(outcome).isEqualTo(new StageOutcome.Failed<>(expectedKind, failure));
    }

    static Stream<Arguments> catalogStageFailedFutures() {
        return Stream.of(
                Arguments.of(new EdcHttpClientException("no response", 0, null), FailureKind.TRANSPORT_FAULT),
                Arguments.of(new EdcHttpClientException("Server response was 503", 503, "unavailable"), FailureKind.OTHER),
                Arguments.of(new TimeoutException(), FailureKind.TRANSPORT_FAULT));
    }

    @Test
    void catalogStageProviderClientErrorIsAProviderRejectionCarryingDspCatalogFetchFailed() throws Exception {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        StatusResult.failure(ResponseStatus.FATAL_ERROR, "Counter Party responded with 403")));

        var outcome = catalogStageOutcome();

        assertThat(outcome).isInstanceOfSatisfying(StageOutcome.Failed.class, failed -> {
            assertThat(failed.failureKind()).isEqualTo(FailureKind.PROVIDER_REJECTION);
            assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, xre -> {
                assertThat(xre.getErrorCode()).isEqualTo("dataspace.dsp_catalog_fetch_failed");
                assertThat(xre.getDetails()).contains("403");
            });
        });
    }

    @Test
    void catalogStageSynchronousEdcExceptionIsOther() throws Exception {
        var edcException = new EdcException("upstream failure");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenThrow(edcException);

        var outcome = catalogStageOutcome();

        assertThat(outcome).isInstanceOfSatisfying(StageOutcome.Failed.class, failed -> {
            assertThat(failed.failureKind()).isEqualTo(FailureKind.OTHER);
            assertThat(failed.failure()).isInstanceOfSatisfying(XrdRuntimeException.class, xre -> {
                assertThat(xre.getErrorCode()).isEqualTo("dataspace.dsp_catalog_fetch_failed");
                assertThat(xre.getCause()).isSameAs(edcException);
            });
        });
    }

    @Test
    void catalogNoResponseFailedFutureFailsTheAcquisitionWithTheOriginalEdcHttpClientExceptionAsCause() {
        var noResponse = new EdcHttpClientException("no response", 0, null);
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(noResponse));
        var assetAccessRequest = requestFor(null);

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), assetAccessRequest);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_acquisition_failed"))
                .satisfies(ex -> assertThat(ex.getCause().getCause()).isSameAs(noResponse));
    }

    private StageOutcome<?> catalogStageOutcome() throws Exception {
        var assetAccessRequest = requestFor(null);
        return orchestrator.fetchCatalog("key", buildParticipantContext(), assetAccessRequest,
                assetAccessRequest.candidates().getFirst()).get(5, TimeUnit.SECONDS);
    }

    @Test
    void catalogParseFailureThrowsDspCatalogParseFailed() {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = requestFor(null);

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
        var assetAccessRequest = requestFor(null);

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
        var assetAccessRequest = requestFor(null);

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
        var assetAccessRequest = requestFor(null);

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
        var assetAccessRequest = requestFor(null);

        stubCatalogAndTransformChain("asset-1");

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(ServiceResult.success(negotiation));

        var future = orchestrator.acquireAssetAccess(participantContext, assetAccessRequest);

        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(terminatedNegotiation("neg-1", "Contract negotiation terminated: neg-1")));
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
        var assetAccessRequest = requestFor(null);

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
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", agreement)));
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
        var assetAccessRequest = requestFor(null);

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
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", agreement)));

        var dataAddress = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data").build();
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(startedTransfer("tp-1", dataAddress)));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(future1).isDone();
        });

        future1.get(5, TimeUnit.SECONDS);
    }

    @Test
    void transferTerminatedThrowsDspTransferFailed() throws Exception {
        var participantContext = buildParticipantContext();
        var assetAccessRequest = requestFor(null);

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
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", agreement)));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(invocation ->
                Stream.of(terminatedTransfer("tp-1", null)));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(future).isDone();
        });

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

    @Test
    void providerRejectionFromFirstCandidateFailsAcquisitionWithoutContactingTheNext() {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        StatusResult.failure(ResponseStatus.FATAL_ERROR, "Counter Party responded with 403")));
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_catalog_fetch_failed"));
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
        verify(reusableAgreementLookup, times(1)).find(any(), any(), any(), any(), any());
    }

    @Test
    void providerServerErrorAtCatalogIsReturnedAtOnceWithoutTryingTheNextCandidate() {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new EdcHttpClientException("server error", 503, null)));
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(EdcHttpClientException.class);
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
    }

    @Test
    void transportFaultOnFirstCandidateMovesToTheSecondWhichIsNamedTheWinner() throws Exception {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new EdcHttpClientException("no response", 0, null)));
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-2", null))
                .thenReturn(Optional.of(buildAgreement("agreement-2")));
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(TransferProcess.Builder.newInstance().id("tp-2").build()));
        var address = startedAddress("tp-2", "http://provider-2.example.org/data");
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner()).isEqualTo(new AssetAccessResult.Winner("provider-2", "provider-2.example.org"));
    }

    @Test
    void allCandidatesTransportFailingFailsWithAllCandidatesFailedAndTheTransportCause() {
        var noResponse = new EdcHttpClientException("no response", 0, null);
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(noResponse));
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var cause = (XrdRuntimeException) ex.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed");
                    assertThat(cause.getMessage()).contains("All 2 candidates failed").contains("no response");
                    assertThat(cause.getCause()).isSameAs(noResponse);
                });
        verify(catalogService, times(2)).requestCatalog(any(), any(), any(), any(), any());
    }

    @Test
    void concurrentAcquisitionsForTheSameCandidateSetInDifferentOrderShareOneInFlightEntry() {
        var pending = new CompletableFuture<StatusResult<byte[]>>();
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(pending);

        var first = orchestrator.acquireAssetAccess(buildParticipantContext(),
                requestForCandidates("provider-1", "provider-2"));
        var second = orchestrator.acquireAssetAccess(buildParticipantContext(),
                requestForCandidates("provider-2", "provider-1"));

        assertThat(second).isSameAs(first);
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
    }

    @Test
    void acquisitionsForDifferentForcedTargetsDoNotShareAnInFlightEntry() {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(new CompletableFuture<>());
        var candidates = List.of(new AssetAccessRequest.Candidate("provider-1", "http://provider-1.example.org/dsp"));

        var first = orchestrator.acquireAssetAccess(buildParticipantContext(),
                new AssetAccessRequest("asset-1", candidates, null, null, "DEV:COM:222:SS1"));
        var second = orchestrator.acquireAssetAccess(buildParticipantContext(),
                new AssetAccessRequest("asset-1", candidates, null, null, "DEV:COM:222:SS2"));

        assertThat(second).isNotSameAs(first);
    }

    private static AssetAccessRequest requestForCandidates(String... counterPartyIds) {
        var candidates = Stream.of(counterPartyIds)
                .map(id -> new AssetAccessRequest.Candidate(id, "http://%s.example.org/dsp".formatted(id)))
                .toList();
        return new AssetAccessRequest("asset-1", candidates, null, null, null);
    }

    private static AssetAccessRequest requestFor(String clientId) {
        return new AssetAccessRequest("asset-1",
                List.of(new AssetAccessRequest.Candidate("provider-1", "http://provider/dsp")), null, clientId, null);
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
        resolvableAddresses.put(id, dataAddress);
        return TransferProcess.Builder.newInstance()
                .id(id)
                .state(TransferProcessStates.STARTED.code())
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

    @Test
    void offerNamingTheCallingSubsystemIsPreferredOverOtherOffers() {
        var offers = Map.of(
                "offer-other", clientPolicy("DEV:COM:222:OTHER"),
                "offer-test", clientPolicy("DEV:COM:222:TESTCLIENT"),
                "offer-member", clientPolicy("DEV:COM:222"));

        var chosen = acquireAndCaptureOfferId(offers, "DEV:COM:222:TESTCLIENT");

        assertThat(chosen).isEqualTo("offer-test");
    }

    @Test
    void memberLevelOfferIsTakenWhenNoOfferNamesTheCallingSubsystem() {
        var offers = Map.of(
                "offer-other", clientPolicy("DEV:COM:222:OTHER"),
                "offer-member", clientPolicy("DEV:COM:222"));

        var chosen = acquireAndCaptureOfferId(offers, "DEV:COM:222:TESTCLIENT");

        assertThat(chosen).isEqualTo("offer-member");
    }

    @Test
    void groupOfferIsTakenWhenNoOfferNamesTheCallingSubsystem() {
        var offers = Map.of(
                "offer-other", clientPolicy("DEV:COM:222:OTHER"),
                "offer-group", groupPolicy("DEV:security-server-owners"));

        var chosen = acquireAndCaptureOfferId(offers, "DEV:COM:222:TESTCLIENT");

        assertThat(chosen).isEqualTo("offer-group");
    }

    @Test
    void offersWrittenForOtherSubsystemsOnlyAreNotNegotiated() {
        var offers = Map.of(
                "offer-a", clientPolicy("DEV:COM:222:A"),
                "offer-b", clientPolicy("DEV:COM:222:B"));
        stubCatalog(buildCatalogWithOffers("asset-1", offers));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(),
                requestFor("DEV:COM:222:C"));

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> {
                    var cause = (XrdRuntimeException) e.getCause();
                    assertThat(cause.getErrorCode()).isEqualTo("dataspace.dsp_offers_not_found");
                    assertThat(cause.getErrorCodeMetadata()).contains("asset-1", "DEV:COM:222:C");
                });
        verifyNoInteractions(contractNegotiationService);
    }

    @Test
    void unrestrictedOfferIsTakenWhenClientIdIsSetButNoOfferNamesTheCallerOrItsGroup() {
        var chosen = acquireAndCaptureOfferId(Map.of("offer-unrestricted", unrestrictedPolicy()), "DEV:COM:222:TESTCLIENT");

        assertThat(chosen).isEqualTo("offer-unrestricted");
    }

    @Test
    void withoutAClientIdTheOnlyOfferIsNegotiated() {
        var chosen = acquireAndCaptureOfferId(Map.of("offer-only", clientPolicy("DEV:COM:222:A")), null);

        assertThat(chosen).isEqualTo("offer-only");
    }

    @Test
    void requestsFromDifferentSubsystemsNegotiateSeparately() {
        var offers = Map.of(
                "offer-a", clientPolicy("DEV:COM:222:A"),
                "offer-b", clientPolicy("DEV:COM:222:B"));
        stubCatalog(buildCatalogWithOffers("asset-1", offers));
        stubNegotiationInitiation();
        var participantContext = buildParticipantContext();

        orchestrator.acquireAssetAccess(participantContext,
                requestFor("DEV:COM:222:A"));
        orchestrator.acquireAssetAccess(participantContext,
                requestFor("DEV:COM:222:B"));
        orchestrator.acquireAssetAccess(participantContext,
                requestFor("DEV:COM:222:A"));

        verify(contractNegotiationService, times(2)).initiateNegotiation(any(), any());
    }

    @Test
    void subsystemWithReusableAgreementTransfersOnItWhileTheOtherSubsystemNegotiates() throws Exception {
        var participantContext = buildParticipantContext();
        var clientWithAgreement = "DEV:COM:222:A";
        var clientWithoutAgreement = "DEV:COM:222:B";

        var reusedAgreement = buildAgreement("agreement-a");
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-1", clientWithAgreement))
                .thenReturn(Optional.of(reusedAgreement));

        stubCatalog(buildCatalogWithOffers("asset-1", Map.of("offer-b", clientPolicy(clientWithoutAgreement))));
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(ServiceResult.success(buildNegotiation()));

        var transferProcessA = TransferProcess.Builder.newInstance().id("tp-a").build();
        var transferProcessB = TransferProcess.Builder.newInstance().id("tp-b").build();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.success(transferProcessA))
                .thenReturn(ServiceResult.success(transferProcessB));

        var futureA = orchestrator.acquireAssetAccess(participantContext,
                requestFor(clientWithAgreement));
        var futureB = orchestrator.acquireAssetAccess(participantContext,
                requestFor(clientWithoutAgreement));

        var negotiatedAgreement = buildAgreement("agreement-b");
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", negotiatedAgreement)));

        var dataAddressA = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data-a").build();
        when(transferProcessStore.findAll(specWithId("tp-a"))).thenAnswer(invocation ->
                Stream.of(startedTransfer("tp-a", dataAddressA)));
        var dataAddressB = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data-b").build();
        when(transferProcessStore.findAll(specWithId("tp-b"))).thenAnswer(invocation ->
                Stream.of(startedTransfer("tp-b", dataAddressB)));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(futureA).isDone();
            assertThat(futureB).isDone();
        });

        assertThat(futureA.get(5, TimeUnit.SECONDS).dataAddress()).isSameAs(dataAddressA);
        assertThat(futureB.get(5, TimeUnit.SECONDS).dataAddress()).isSameAs(dataAddressB);

        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        verify(transferProcessService, times(2)).initiateTransfer(any(), any());

        var clientIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(reusableAgreementLookup, times(2))
                .find(eq("participant1"), eq("participant1"), eq("asset-1"), eq("provider-1"), clientIdCaptor.capture());
        assertThat(clientIdCaptor.getAllValues()).containsExactly(clientWithAgreement, clientWithoutAgreement);
    }

    private static QuerySpec specWithId(String id) {
        return argThat(spec -> spec != null && idsOf(spec).contains(id));
    }

    private static Collection<?> idsOf(QuerySpec spec) {
        return spec.getFilterExpression().stream()
                .filter(criterion -> "id".equals(criterion.getOperandLeft()))
                .map(criterion -> (Collection<?>) criterion.getOperandRight())
                .findFirst()
                .orElseThrow();
    }

    private String acquireAndCaptureOfferId(Map<String, Policy> offers, String clientId) {
        stubCatalog(buildCatalogWithOffers("asset-1", offers));
        var requestCaptor = ArgumentCaptor.forClass(ContractRequest.class);
        when(contractNegotiationService.initiateNegotiation(any(), requestCaptor.capture()))
                .thenReturn(ServiceResult.success(buildNegotiation()));

        orchestrator.acquireAssetAccess(buildParticipantContext(),
                requestFor(clientId));

        return requestCaptor.getValue().getContractOffer().getId();
    }

    private void stubNegotiationInitiation() {
        when(contractNegotiationService.initiateNegotiation(any(), any()))
                .thenReturn(ServiceResult.success(buildNegotiation()));
    }

    private void stubCatalog(Catalog catalog) {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));
    }

    private static ContractNegotiation buildNegotiation() {
        return ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
    }

    private static Policy clientPolicy(String encodedClientId) {
        var clientConstraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XRoadPolicyNamespace.XROAD_CLIENT_ID))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(encodedClientId))
                .build();
        var pathConstraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XRoadPolicyNamespace.XROAD_DATAPATH))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression("GET /pets"))
                .build();
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance()
                        .constraint(AndConstraint.Builder.newInstance()
                                .constraints(List.of(clientConstraint, pathConstraint))
                                .build())
                        .build())
                .build();
    }

    private static Policy groupPolicy(String encodedGroupId) {
        var groupConstraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XRoadPolicyNamespace.XROAD_GLOBAL_GROUP))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(encodedGroupId))
                .build();
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance().constraint(groupConstraint).build())
                .build();
    }

    private static Policy unrestrictedPolicy() {
        return Policy.Builder.newInstance().build();
    }

    private static Catalog buildCatalogWithOffers(String assetId, Map<String, Policy> offers) {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance()
                .format(XRoadTransferType.PULL.wireValue()).dataService(dataService).build();
        var dataset = Dataset.Builder.newInstance().id(assetId).distribution(distribution);
        offers.forEach(dataset::offer);
        return Catalog.Builder.newInstance().dataset(dataset.build()).build();
    }
}
