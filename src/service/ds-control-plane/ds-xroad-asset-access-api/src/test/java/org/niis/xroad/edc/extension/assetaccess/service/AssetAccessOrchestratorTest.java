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

import com.google.common.base.Ticker;
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
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;
import org.niis.xroad.edc.extension.assetaccess.failure.FailureKind;
import org.niis.xroad.edc.extension.assetaccess.failure.StageOutcome;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.extension.assetaccess.probe.CandidateHandshakeRace;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessOrchestratorTest {

    private static final Duration COOLDOWN_PERIOD = Duration.ofSeconds(180);
    private static final Duration CANDIDATE_TIMEOUT = Duration.ofSeconds(20);

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
    CandidateHandshakeRace candidateHandshakeRace;
    @Mock
    JsonLd jsonLd;
    @Mock
    TypeTransformerRegistry transformerRegistry;
    @Mock
    Monitor monitor;

    AssetAccessCompletionPoller completionPoller;

    AssetAccessOrchestrator orchestrator;

    private final FakeTicker ticker = new FakeTicker();
    private final MutableClock clock = new MutableClock();
    private final CooldownTracker cooldownTracker = new CooldownTracker(COOLDOWN_PERIOD, ticker);

    private final Map<String, DataAddress> resolvableAddresses = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(reusableAgreementLookup.find(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(candidateHandshakeRace.order(any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));
        lenient().when(negotiationStore.queryNegotiations(any())).thenAnswer(invocation -> Stream.empty());
        lenient().when(transferProcessStore.findAll(any())).thenAnswer(invocation -> Stream.empty());
        lenient().when(dataAddressStore.resolve(any())).thenAnswer(invocation -> {
            TransferProcess transferProcess = invocation.getArgument(0);
            var address = transferProcess == null ? null : resolvableAddresses.get(transferProcess.getId());
            return address != null ? StoreResult.success(address) : StoreResult.notFound("no data address stored");
        });
        completionPoller = new AssetAccessCompletionPoller(negotiationStore, transferProcessStore, dataAddressStore,
                new NoopTransactionContext(), ExecutorInstrumentation.noop(), clock, monitor, Duration.ofMillis(250));
        orchestrator = new AssetAccessOrchestrator(new AssetAccessStateStore(), reusableAgreementLookup,
                catalogService, contractNegotiationService, transferProcessService, completionPoller, cooldownTracker,
                candidateHandshakeRace,
                jsonLd, transformerRegistry, monitor,
                Duration.ofSeconds(60), Duration.ofSeconds(60), CANDIDATE_TIMEOUT);
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
                Stream.of(transportFaultTransfer("tp-1")));

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
                catalogService, contractNegotiationService, transferProcessService, completionPoller, cooldownTracker,
                candidateHandshakeRace,
                jsonLd, transformerRegistry, monitor, Duration.ofSeconds(60), Duration.ZERO, CANDIDATE_TIMEOUT);

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

    private void finishDispatchedCompletions() {
        completionPoller.stop();
    }

    private AssetAccessOrchestrator orchestratorKeepingAbandonedWaits() {
        var poller = spy(completionPoller);
        lenient().doNothing().when(poller).cancelNegotiation(any());
        lenient().doNothing().when(poller).cancelTransfer(any());
        return new AssetAccessOrchestrator(new AssetAccessStateStore(), reusableAgreementLookup,
                catalogService, contractNegotiationService, transferProcessService, poller, cooldownTracker,
                candidateHandshakeRace,
                jsonLd, transformerRegistry, monitor,
                Duration.ofSeconds(60), Duration.ofSeconds(60), CANDIDATE_TIMEOUT);
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
        var address = stubNegotiatedAccessViaCatalogOf("provider-2");
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
    void transportFaultMarksTheServerSoTheNextAcquisitionSkipsItAndUsesTheSibling() throws Exception {
        stubTransportFaultAtCatalog("provider-1");
        var address = stubNegotiatedAccessViaCatalogOf("provider-2");
        var request = requestForCandidates("provider-1", "provider-2");

        var first = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(first);
        first.get(5, TimeUnit.SECONDS);

        var second = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(second);

        var result = second.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-2");

        verify(catalogService, times(1)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
    }

    @Test
    void serverIsACandidateAgainOnceTheCooldownPeriodHasExpired() throws Exception {
        stubTransportFaultAtCatalog("provider-1");
        stubNegotiatedAccessViaCatalogOf("provider-2");
        var request = requestForCandidates("provider-1", "provider-2");

        var first = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(first);
        first.get(5, TimeUnit.SECONDS);

        ticker.advance(COOLDOWN_PERIOD);

        var second = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(second);
        second.get(5, TimeUnit.SECONDS);

        verify(catalogService, times(2)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
    }

    @Test
    void everyCandidateInCooldownAreAllTriedOnTheNextAcquisition() {
        var noResponse = new EdcHttpClientException("no response", 0, null);
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(noResponse));
        var request = requestForCandidates("provider-1", "provider-2");

        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        verify(catalogService, times(2)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
        verify(catalogService, times(2)).requestCatalog(any(), eq("provider-2"), any(), any(), any());
    }

    @Test
    void loneCandidateIsMarkedOnATransportFaultAndStillUsedOnTheNextCall() {
        stubTransportFaultAtCatalog("provider-1");
        var request = requestForCandidates("provider-1");

        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isTrue();

        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        verify(catalogService, times(2)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
        verifyNoInteractions(candidateHandshakeRace);
    }

    @Test
    void transportFaultForOneMembersContextPutsTheServerInCooldownForAnotherMembersAcquisition() throws Exception {
        stubTransportFaultAtCatalog("provider-1-for-222");
        stubNegotiatedAccessViaCatalogOf("provider-2-for-333");
        var firstMember = requestForMemberContext("222", "provider-1");
        var secondMember = requestForMemberContext("333", "provider-1", "provider-2");

        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), firstMember).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        var second = orchestrator.acquireAssetAccess(buildParticipantContext(), secondMember);
        pollUntilDone(second);

        assertThat(second.get(5, TimeUnit.SECONDS).winner().counterPartyId()).isEqualTo("provider-2-for-333");
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-1-for-333"), any(), any(), any());
    }

    @Test
    void providerRejectionDoesNotMarkTheServer() {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        StatusResult.failure(ResponseStatus.FATAL_ERROR, "Counter Party responded with 403")));
        var request = requestForCandidates("provider-1", "provider-2");

        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isFalse();

        verify(catalogService, times(2)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-2"), any(), any(), any());
    }

    @Test
    void providerServerErrorDoesNotMarkTheServer() {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new EdcHttpClientException("server error", 503, null)));
        var request = requestForCandidates("provider-1", "provider-2");

        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> orchestrator.acquireAssetAccess(buildParticipantContext(), request).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isFalse();

        verify(catalogService, times(2)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-2"), any(), any(), any());
    }

    @Test
    void candidateHangingPastTheLimitIsAbandonedMarkedAndTheNextCandidateWins() throws Exception {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(new CompletableFuture<>());
        var address = stubNegotiatedAccessViaCatalogOf("provider-2");
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-2");
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isTrue();
        assertThat(cooldownTracker.isCoolingDown("http://provider-2.example.org/dsp")).isFalse();
    }

    @Test
    void candidateAnsweringWithinTheLimitIsNotAbandoned() throws Exception {
        stubAgreementAt("provider-1", "agreement-for-provider-1", 1_000L);
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-2"));
        var address = startedAddress("tp-2", "http://provider-1.example.org/data");
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        clock.advance(CANDIDATE_TIMEOUT.minusSeconds(1));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isFalse();
    }

    @Test
    void loneCandidateHangingPastTheLimitFailsAcquisitionWithATimeoutCause() {
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any()))
                .thenReturn(new CompletableFuture<>());
        var request = requestForCandidates("provider-1");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_acquisition_failed"))
                .satisfies(ex -> assertThat(ex.getCause().getCause())
                        .isInstanceOf(TimeoutException.class)
                        .hasMessage("Candidate http://provider-1.example.org/dsp abandoned after PT20S"));
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isTrue();
    }

    @Test
    void abandonedCandidateNegotiationWaitIsRemovedFromThePoller() {
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1"));
        verify(contractNegotiationService).initiateNegotiation(any(), any());
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);
        clearInvocations(negotiationStore, transferProcessStore);
        completionPoller.poll();

        verifyNoInteractions(negotiationStore, transferProcessStore);
    }

    @Test
    void abandonedCandidateTransferWaitIsRemovedFromThePoller() {
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1"));
        verify(transferProcessService).initiateTransfer(any(), any());
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);
        clearInvocations(negotiationStore, transferProcessStore);
        completionPoller.poll();

        verifyNoInteractions(negotiationStore, transferProcessStore);
    }

    @Test
    void negotiationFinalizingAfterItsCandidateWasAbandonedStartsNoTransfer() {
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        var keepingWaits = orchestratorKeepingAbandonedWaits();

        var future = keepingWaits.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1"));
        verify(contractNegotiationService).initiateNegotiation(any(), any());
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-late"))));
        clearInvocations(negotiationStore);
        completionPoller.poll();
        finishDispatchedCompletions();

        verify(negotiationStore).queryNegotiations(specWithId("neg-1"));
        verifyNoInteractions(transferProcessService);
    }

    @Test
    void renegotiationAfterRefusedReuseFinalizingAfterTheCandidateWasAbandonedStartsNoTransfer() {
        stubReusableAgreement("agreement-1");
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(ServiceResult.badRequest("provider refused agreement-1"));
        var keepingWaits = orchestratorKeepingAbandonedWaits();

        var future = keepingWaits.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1"));
        verify(contractNegotiationService).initiateNegotiation(any(), any());
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-late"))));
        clearInvocations(negotiationStore);
        completionPoller.poll();
        finishDispatchedCompletions();

        verify(negotiationStore).queryNegotiations(specWithId("neg-1"));
        verify(transferProcessService, times(1)).initiateTransfer(any(), any());
    }

    @Test
    void reuseTransferRefusedAfterTheCandidateWasAbandonedStartsNoRenegotiation() {
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        var keepingWaits = orchestratorKeepingAbandonedWaits();

        var future = keepingWaits.acquireAssetAccess(buildParticipantContext(), requestFor(null));
        verify(transferProcessService).initiateTransfer(any(), any());
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(_ ->
                Stream.of(terminatedTransfer("tp-1", "policy no longer holds")));
        completionPoller.poll();
        finishDispatchedCompletions();

        verifyNoInteractions(catalogService, contractNegotiationService);
    }

    @Test
    void catalogAnsweringAfterTheCandidateWasAbandonedStartsNoNegotiation() {
        var catalogResponse = new CompletableFuture<StatusResult<byte[]>>();
        when(catalogService.requestCatalog(any(), eq("provider-1"), any(), any(), any())).thenReturn(catalogResponse);
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(buildCatalog("asset-1", "offer-1", XRoadTransferType.PULL.wireValue())));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1"));
        clock.advance(CANDIDATE_TIMEOUT);
        pollUntilDone(future);
        catalogResponse.complete(StatusResult.success("{}".getBytes()));

        verify(transformerRegistry).transform(any(), eq(Catalog.class));
        verifyNoInteractions(contractNegotiationService);
    }

    @Test
    void acquisitionAfterThePollerStoppedFailsWithoutAbandoningOrMarkingCandidates() {
        lenient().when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(new CompletableFuture<>());
        completionPoller.stop();
        var request = requestForCandidates("provider-1", "provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("Asset access completion poller stopped");
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-2"), any(), any(), any());
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isFalse();
    }

    @Test
    void candidatesAreTriedInTheOrderTheHandshakeRaceReturns() throws Exception {
        var request = requestForCandidates("provider-1", "provider-2");
        var reversed = List.of(request.candidates().get(1), request.candidates().get(0));
        when(candidateHandshakeRace.order(any())).thenReturn(CompletableFuture.completedFuture(reversed));
        stubNegotiatedAccessViaCatalogOf("provider-2");
        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);
        assertThat(future.get(5, TimeUnit.SECONDS).winner().counterPartyId()).isEqualTo("provider-2");
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
    }

    @Test
    void onlyCandidatesNotInCooldownAreProbed() {
        cooldownTracker.markFailed("http://provider-1.example.org/dsp");
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(new CompletableFuture<>());
        var request = requestForCandidates("provider-1", "provider-2", "provider-3");
        orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        verify(candidateHandshakeRace).order(List.of(request.candidates().get(1), request.candidates().get(2)));
    }

    @Test
    void aSingleCandidateIsTriedAtOnceWithoutProbing() throws Exception {
        var address = stubNegotiatedAccessViaCatalogOf("provider-1");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1"));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
        verifyNoInteractions(candidateHandshakeRace);
    }

    @Test
    void theOnlyCandidateLeftNotInCooldownIsTriedWithoutProbing() throws Exception {
        cooldownTracker.markFailed("http://provider-1.example.org/dsp");
        stubNegotiatedAccessViaCatalogOf("provider-2");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        assertThat(future.get(5, TimeUnit.SECONDS).winner().counterPartyId()).isEqualTo("provider-2");
        verifyNoInteractions(candidateHandshakeRace);
    }

    @Test
    void newestAgreementAmongCandidatesIsReusedWithoutProbingOrNegotiating() throws Exception {
        stubAgreementAt("provider-1", "agreement-old", 1_000L);
        stubAgreementAt("provider-2", "agreement-new", 2_000L);
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-2"));
        var address = startedAddress("tp-2", "http://provider-2.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-2");
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(1)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getValue().getContractId()).isEqualTo("agreement-new");
        verify(reusableAgreementLookup, times(2)).find(any(), any(), any(), any(), any());
        verifyNoInteractions(catalogService, contractNegotiationService, candidateHandshakeRace);
    }

    @Test
    void agreementsWithTheSameSigningDateAreTriedInRequestOrder() throws Exception {
        stubAgreementAt("provider-1", "agreement-first", 1_000L);
        stubAgreementAt("provider-2", "agreement-second", 1_000L);
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        var address = startedAddress("tp-1", "http://provider-1.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(1)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getValue().getContractId()).isEqualTo("agreement-first");
    }

    @Test
    void candidateInCooldownIsSkippedForReuseEvenWithTheNewestAgreement() throws Exception {
        cooldownTracker.markFailed("http://provider-2.example.org/dsp");
        stubAgreementAt("provider-1", "agreement-old", 1_000L);
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        var address = startedAddress("tp-1", "http://provider-1.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(1)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getValue().getContractId()).isEqualTo("agreement-old");
        verify(reusableAgreementLookup, never()).find(any(), any(), any(), eq("provider-2"), any());
    }

    @Test
    void transportFaultOnReuseMarksTheServerAndTriesTheNextCandidateHoldingAnAgreement() throws Exception {
        stubAgreementAt("provider-1", "agreement-old", 1_000L);
        stubAgreementAt("provider-2", "agreement-new", 2_000L);
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(successfulTransferInitiation("tp-2"))
                .thenReturn(successfulTransferInitiation("tp-1"));
        when(transferProcessStore.findAll(specWithId("tp-2"))).thenAnswer(_ -> Stream.of(transportFaultTransfer("tp-2")));
        var address = startedAddress("tp-1", "http://provider-1.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
        assertThat(cooldownTracker.isCoolingDown("http://provider-2.example.org/dsp")).isTrue();
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isFalse();
        verifyNoInteractions(catalogService, contractNegotiationService);
    }

    @Test
    void transportFaultOnTheOnlyReusedAgreementFallsBackToNegotiatingWithTheCandidateHoldingNone() throws Exception {
        stubAgreementAt("provider-2", "agreement-new", 2_000L);
        var request = requestForCandidates("provider-1", "provider-2");
        when(transferProcessStore.findAll(specWithId("tp-2"))).thenAnswer(_ -> Stream.of(transportFaultTransfer("tp-2")));
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-fresh"))));
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(successfulTransferInitiation("tp-2"))
                .thenReturn(successfulTransferInitiation("tp-3"));
        var address = startedAddress("tp-3", "http://provider-1.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request);
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-1");
        assertThat(cooldownTracker.isCoolingDown("http://provider-2.example.org/dsp")).isTrue();
        verifyNoInteractions(candidateHandshakeRace);
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-2"), any(), any(), any());
    }

    @Test
    void transportFaultOnEveryReusedAgreementFailsAcquisitionWithoutNegotiating() {
        stubAgreementAt("provider-1", "agreement-old", 1_000L);
        stubAgreementAt("provider-2", "agreement-new", 2_000L);
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(successfulTransferInitiation("tp-2"))
                .thenReturn(successfulTransferInitiation("tp-1"));
        when(transferProcessStore.findAll(specWithId("tp-2"))).thenAnswer(_ -> Stream.of(transportFaultTransfer("tp-2")));
        when(transferProcessStore.findAll(specWithId("tp-1"))).thenAnswer(_ -> Stream.of(transportFaultTransfer("tp-1")));

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex.getCause()).getErrorCode())
                        .isEqualTo("dataspace.dsp_acquisition_failed"))
                .satisfies(ex -> assertThat(ex.getCause()).hasMessageContaining("All 2 candidates failed"));
        assertThat(cooldownTracker.isCoolingDown("http://provider-1.example.org/dsp")).isTrue();
        assertThat(cooldownTracker.isCoolingDown("http://provider-2.example.org/dsp")).isTrue();
        verifyNoInteractions(catalogService, contractNegotiationService);
    }

    @Test
    void refusalOnReuseRenegotiatesOnTheSameServerWithoutMarkingItOrTryingTheNext() throws Exception {
        stubAgreementAt("provider-1", "agreement-old", 1_000L);
        stubAgreementAt("provider-2", "agreement-new", 2_000L);
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any()))
                .thenReturn(ServiceResult.badRequest("provider refused agreement-new"))
                .thenReturn(successfulTransferInitiation("tp-2"));
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-fresh"))));
        var address = startedAddress("tp-2", "http://provider-2.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestForCandidates("provider-1", "provider-2"));
        pollUntilDone(future);

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner().counterPartyId()).isEqualTo("provider-2");
        assertThat(cooldownTracker.isCoolingDown("http://provider-2.example.org/dsp")).isFalse();
        var transferRequests = ArgumentCaptor.forClass(TransferRequest.class);
        verify(transferProcessService, times(2)).initiateTransfer(any(), transferRequests.capture());
        assertThat(transferRequests.getAllValues()).extracting(TransferRequest::getContractId)
                .containsExactly("agreement-new", "agreement-fresh");
        verify(catalogService, times(1)).requestCatalog(any(), eq("provider-2"), any(), any(), any());
        verify(catalogService, times(0)).requestCatalog(any(), eq("provider-1"), any(), any(), any());
    }

    private void stubAgreementAt(String counterPartyId, String agreementId, long signingDate) {
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", counterPartyId, null))
                .thenReturn(Optional.of(buildAgreement(agreementId, counterPartyId, signingDate)));
    }

    private DataAddress stubNegotiatedAccessViaCatalogOf(String counterPartyId) {
        var catalog = buildCatalog("asset-1", "offer-1", XRoadTransferType.PULL.wireValue());
        when(catalogService.requestCatalog(any(), eq(counterPartyId), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));
        stubNegotiationInitiation();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-negotiated"))));
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-2"));
        return startedAddress("tp-2", "http://%s.example.org/data".formatted(counterPartyId));
    }

    private static ServiceResult<TransferProcess> successfulTransferInitiation(String transferProcessId) {
        return ServiceResult.success(TransferProcess.Builder.newInstance().id(transferProcessId).build());
    }

    private TransferProcess transportFaultTransfer(String transferProcessId) {
        return terminatedTransfer(transferProcessId, EdcHttpClientException.class.getName() + ": Failed to connect to provider");
    }

    private void stubTransportFaultAtCatalog(String counterPartyId) {
        when(catalogService.requestCatalog(any(), eq(counterPartyId), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new EdcHttpClientException("no response", 0, null)));
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

    private static AssetAccessRequest requestForMemberContext(String memberCode, String... servers) {
        var candidates = Stream.of(servers)
                .map(server -> new AssetAccessRequest.Candidate("%s-for-%s".formatted(server, memberCode),
                        "https://%s.example.org:8183/api/dsp/DEV:COM:%s/http-dsp-profile-2025-1".formatted(server, memberCode)))
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
        return buildAgreement(agreementId, "provider-1", System.currentTimeMillis());
    }

    private ContractAgreement buildAgreement(String agreementId, String providerId, long signingDate) {
        return ContractAgreement.Builder.newInstance()
                .id(agreementId)
                .providerId(providerId)
                .consumerId("participant1")
                .contractSigningDate(signingDate)
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

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FakeTicker extends Ticker {
        private long ticks;
        @Override
        public long read() {
            return ticks;
        }
        private void advance(Duration duration) {
            ticks += duration.toNanos();
        }
    }
}
