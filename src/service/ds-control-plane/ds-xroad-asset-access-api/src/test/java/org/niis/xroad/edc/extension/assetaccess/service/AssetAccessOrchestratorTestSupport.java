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
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
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
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.time.CallBudget;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;
import org.niis.xroad.edc.extension.assetaccess.agreement.AgreementReuseNote;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
abstract class AssetAccessOrchestratorTestSupport {

    static final Duration COOLDOWN_PERIOD = Duration.ofSeconds(180);
    static final Duration CANDIDATE_TIMEOUT = Duration.ofSeconds(20);
    static final String PROVIDER_CONFLICT = providerAnswer(409, "agreement no longer valid");
    static final Duration CATALOG_TIMEOUT = Duration.ofSeconds(20);
    static final Duration DEFAULT_BUDGET = Duration.ofSeconds(60);
    static final String GROUP_CLIENT_A = "DEV:COM:222:A";
    static final String GROUP_CLIENT_B = "DEV:COM:222:B";

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

    AgreementReuseNote reuseNote;

    AssetAccessOrchestrator orchestrator;

    final FakeTicker ticker = new FakeTicker();
    final MutableClock clock = new MutableClock();
    final CooldownTracker cooldownTracker = new CooldownTracker(COOLDOWN_PERIOD, ticker);

    final Map<String, DataAddress> resolvableAddresses = new HashMap<>();

    int negotiationCount;

    @BeforeEach
    void initFixture() {
        lenient().when(reusableAgreementLookup.find(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(reusableAgreementLookup.findById(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        reuseNote = new AgreementReuseNote(100);
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
        orchestrator = newOrchestrator(completionPoller, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    @AfterEach
    void stopFixture() {
        completionPoller.stop();
    }

    AssetAccessOrchestrator newOrchestrator(AssetAccessCompletionPoller poller, Duration negotiationTimeout,
                                            Duration transferTimeout) {
        return newOrchestrator(poller, CATALOG_TIMEOUT, negotiationTimeout, transferTimeout);
    }

    AssetAccessOrchestrator newOrchestrator(AssetAccessCompletionPoller poller, Duration catalogTimeout,
                                            Duration negotiationTimeout, Duration transferTimeout) {
        return newOrchestrator(poller, catalogTimeout, negotiationTimeout, transferTimeout, CANDIDATE_TIMEOUT);
    }

    AssetAccessOrchestrator newOrchestrator(AssetAccessCompletionPoller poller, Duration catalogTimeout,
                                            Duration negotiationTimeout, Duration transferTimeout,
                                            Duration candidateTimeout) {
        return new AssetAccessOrchestrator(new AssetAccessStateStore(), reusableAgreementLookup, reuseNote,
                catalogService, contractNegotiationService, transferProcessService, poller, cooldownTracker,
                candidateHandshakeRace,
                jsonLd, transformerRegistry, monitor,
                catalogTimeout, negotiationTimeout, transferTimeout, candidateTimeout);
    }

    CallBudget defaultBudget() {
        return CallBudget.startingNow(clock, DEFAULT_BUDGET);
    }

    DataAddress startedAddress(String transferProcessId, String endpoint) {
        var address = DataAddress.Builder.newInstance().type("HttpData").property("endpoint", endpoint).build();
        when(transferProcessStore.findAll(specWithId(transferProcessId))).thenAnswer(invocation ->
                Stream.of(startedTransfer(transferProcessId, address)));
        return address;
    }

    void pollUntilDone(CompletableFuture<?> future) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            assertThat(future).isDone();
        });
    }

    AssetAccessResult acquireAndAwait(AssetAccessRequest request) throws Exception {
        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request, defaultBudget());
        pollUntilDone(future);
        return future.get(5, TimeUnit.SECONDS);
    }

    static String providerAnswer(int code, String body) {
        return "Counter Party responded with Response{protocol=h2, code=%d, message=, url=https://provider.example/dsp}. Body: %s."
                .formatted(code, body);
    }

    static ServiceResult<TransferProcess> successfulTransferInitiation(String transferProcessId) {
        return ServiceResult.success(TransferProcess.Builder.newInstance().id(transferProcessId).build());
    }

    TransferProcess transportFaultTransfer(String transferProcessId) {
        return terminatedTransfer(transferProcessId, EdcHttpClientException.class.getName() + ": Failed to connect to provider");
    }

    ParticipantContext buildParticipantContext() {
        return ParticipantContext.Builder.newInstance()
                .participantContextId("participant1")
                .identity("participant1")
                .build();
    }

    ContractAgreement buildAgreement(String agreementId) {
        return buildAgreement(agreementId, "provider-1", System.currentTimeMillis());
    }

    ContractAgreement buildAgreement(String agreementId, String providerId, long signingDate) {
        return ContractAgreement.Builder.newInstance()
                .id(agreementId)
                .providerId(providerId)
                .consumerId("participant1")
                .contractSigningDate(signingDate)
                .assetId("asset-1")
                .policy(Policy.Builder.newInstance().build())
                .build();
    }

    ContractNegotiation finalizedNegotiation(String id, ContractAgreement agreement) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .state(ContractNegotiationStates.FINALIZED.code())
                .contractAgreement(agreement)
                .build();
    }

    TransferProcess startedTransfer(String id, DataAddress dataAddress) {
        resolvableAddresses.put(id, dataAddress);
        return TransferProcess.Builder.newInstance()
                .id(id)
                .state(TransferProcessStates.STARTED.code())
                .build();
    }

    TransferProcess terminatedTransfer(String id, String errorDetail) {
        return TransferProcess.Builder.newInstance()
                .id(id)
                .state(TransferProcessStates.TERMINATED.code())
                .errorDetail(errorDetail)
                .build();
    }

    Catalog buildCatalogWithNoOffers(String assetId) {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().format(XRoadTransferType.PULL.wireValue()).dataService(dataService).build();
        var dataset = Dataset.Builder.newInstance()
                .id(assetId)
                .distribution(distribution)
                .build();
        return Catalog.Builder.newInstance().dataset(dataset).build();
    }

    static QuerySpec specWithId(String id) {
        return argThat(spec -> spec != null && idsOf(spec).contains(id));
    }

    static Collection<?> idsOf(QuerySpec spec) {
        return spec.getFilterExpression().stream()
                .filter(criterion -> "id".equals(criterion.getOperandLeft()))
                .map(criterion -> (Collection<?>) criterion.getOperandRight())
                .findFirst()
                .orElseThrow();
    }

    static ContractNegotiation buildNegotiation() {
        return ContractNegotiation.Builder.newInstance()
                .id("neg-1")
                .protocol("http-dsp-profile-2025-1")
                .counterPartyId("provider-1")
                .counterPartyAddress("http://provider/dsp")
                .build();
    }

    static Policy clientPolicy(String encodedClientId) {
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

    static Catalog buildCatalogWithOffers(String assetId, Map<String, Policy> offers) {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance()
                .format(XRoadTransferType.PULL.wireValue()).dataService(dataService).build();
        var dataset = Dataset.Builder.newInstance().id(assetId).distribution(distribution);
        offers.forEach(dataset::offer);
        return Catalog.Builder.newInstance().dataset(dataset.build()).build();
    }

    void stubReusableAgreement(String agreementId) {
        when(reusableAgreementLookup.find("participant1", "participant1", "asset-1", "provider-1", null))
                .thenReturn(Optional.of(buildAgreement(agreementId)));
    }

    AgreementReuseNote.Key noteKey(String clientId) {
        return new AgreementReuseNote.Key("participant1", "participant1", "provider-1", "asset-1", clientId);
    }

    void stubFindById(ContractAgreement agreement) {
        lenient().when(reusableAgreementLookup.findById("participant1", "participant1", "asset-1", "provider-1", agreement.getId()))
                .thenReturn(Optional.of(agreement));
    }

    void stubStartedTransfers(String... transferIds) {
        var results = Stream.of(transferIds).map(AssetAccessOrchestratorTestSupport::successfulTransferInitiation).toList();
        var stubbing = when(transferProcessService.initiateTransfer(any(), any())).thenReturn(results.getFirst());
        results.stream().skip(1).forEach(stubbing::thenReturn);
        for (var id : transferIds) {
            startedAddress(id, "http://provider/data-" + id);
        }
    }

    void stubNegotiatedAgreements(String... agreementIds) {
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenAnswer(invocation -> ServiceResult.success(
                ContractNegotiation.Builder.newInstance().id("neg-" + (negotiationCount++))
                        .protocol("http-dsp-profile-2025-1").counterPartyId("provider-1")
                        .counterPartyAddress("http://provider/dsp").build()));
        for (int i = 0; i < agreementIds.length; i++) {
            var negotiationId = "neg-" + i;
            var agreement = buildAgreement(agreementIds[i]);
            lenient().when(negotiationStore.queryNegotiations(specWithId(negotiationId))).thenAnswer(invocation ->
                    Stream.of(finalizedNegotiation(negotiationId, agreement)));
        }
    }

    void refusedAddress(String transferProcessId) {
        var address = DataAddress.Builder.newInstance().type("HttpData")
                .property("endpoint", "http://provider/data-" + transferProcessId)
                .property(DspConventions.ACCESS_REFUSED_PROPERTY, "true").build();
        when(transferProcessStore.findAll(specWithId(transferProcessId))).thenAnswer(invocation ->
                Stream.of(startedTransfer(transferProcessId, address)));
    }

    AssetAccessResult acquire(String clientId) throws Exception {
        return acquireAndAwait(requestFor(clientId));
    }

    Throwable acquireExpectingFailure(String clientId) {
        return failureOf(orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(clientId), defaultBudget()));
    }

    Throwable failureOf(CompletableFuture<AssetAccessResult> future) {
        pollUntilDone(future);
        var failure = catchThrowable(() -> future.get(5, TimeUnit.SECONDS));
        assertThat(failure).isInstanceOf(ExecutionException.class);
        return failure.getCause();
    }

    static AssetAccessRequest requestFor(String clientId) {
        return new AssetAccessRequest("asset-1",
                List.of(new AssetAccessRequest.Candidate("provider-1", "http://provider/dsp")), null, clientId);
    }

    void stubCatalogAndTransformChain(String assetId) {
        var catalog = buildCatalog(assetId, "offer-1", XRoadTransferType.PULL.wireValue());
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(catalog));
    }

    Catalog buildCatalog(String assetId, String offerId, String transferType) {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().format(transferType).dataService(dataService).build();
        var dataset = Dataset.Builder.newInstance()
                .id(assetId)
                .offer(offerId, Policy.Builder.newInstance().build())
                .distribution(distribution)
                .build();
        return Catalog.Builder.newInstance().dataset(dataset).build();
    }

    void stubNegotiationInitiation() {
        when(contractNegotiationService.initiateNegotiation(any(), any()))
                .thenReturn(ServiceResult.success(buildNegotiation()));
    }

    void stubCatalog(Catalog catalog) {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));
    }

    static Policy groupPolicy(String encodedGroupId) {
        var groupConstraint = AtomicConstraint.Builder.newInstance()
                .leftExpression(new LiteralExpression(XRoadPolicyNamespace.XROAD_GLOBAL_GROUP))
                .operator(Operator.EQ)
                .rightExpression(new LiteralExpression(encodedGroupId))
                .build();
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance().constraint(groupConstraint).build())
                .build();
    }

    static Policy unrestrictedPolicy() {
        return Policy.Builder.newInstance().build();
    }

    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
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

    static final class FakeTicker extends Ticker {
        private long ticks;

        @Override
        public long read() {
            return ticks;
        }

        void advance(Duration duration) {
            ticks += duration.toNanos();
        }
    }
}
