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
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.time.CallBudget;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssetAccessOrchestratorBudgetTest extends AssetAccessOrchestratorTestSupport {

    private static final String PROVIDER_ADDRESS = "http://provider/dsp";

    @Test
    void anAcquisitionWithNoTimeLeftFailsWithoutStartingAnyStep() {
        var budget = CallBudget.startingNow(clock, Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(11));

        var failure = failureOf(orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), budget));

        assertAcquisitionTimeout(failure);
        verifyNoInteractions(catalogService);
        assertThat(cooldownTracker.isCoolingDown(PROVIDER_ADDRESS)).isFalse();
    }

    @Test
    void catalogFetchIsCappedByTheTimeLeftOfTheBudget() {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(new CompletableFuture<>());
        var shortBudget = CallBudget.startingNow(clock, Duration.ofMillis(100));

        var failure = failureOf(orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), shortBudget));

        assertAcquisitionTimeout(failure);
        assertThat(cooldownTracker.isCoolingDown(PROVIDER_ADDRESS)).isFalse();
    }

    @Test
    void catalogFetchStopsAtItsOwnTimeout() {
        var timed = newOrchestrator(completionPoller, Duration.ofMillis(100), Duration.ofSeconds(60), Duration.ofSeconds(60));
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(new CompletableFuture<>());

        var failure = failureOf(timed.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget()));

        assertThat(failure).isInstanceOf(XrdRuntimeException.class);
        assertThat(((XrdRuntimeException) failure).getErrorCode()).isEqualTo("dataspace.dsp_acquisition_failed");
        assertThat(failure.getCause()).isInstanceOf(XrdRuntimeException.class);
        assertThat(((XrdRuntimeException) failure.getCause()).getErrorCode()).isEqualTo("dataspace.dsp_catalog_fetch_failed");
        assertThat(failure.getCause().getCause()).isInstanceOf(TimeoutException.class);
        assertThat(cooldownTracker.isCoolingDown(PROVIDER_ADDRESS)).isTrue();
    }

    @Test
    void negotiationAndTransferWaitsAreCappedByTheTimeLeftOfTheBudget() {
        var poller = spy(completionPoller);
        var timed = newOrchestrator(poller, Duration.ofSeconds(60), Duration.ofSeconds(60));
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        var negotiated = new AtomicBoolean();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                negotiated.get() ? Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-1"))) : Stream.empty());

        timed.acquireAssetAccess(buildParticipantContext(), requestFor(null),
                CallBudget.startingNow(clock, Duration.ofSeconds(100)));
        verify(poller).awaitNegotiation("neg-1", Duration.ofSeconds(60));

        clock.advance(Duration.ofSeconds(55));
        negotiated.set(true);
        completionPoller.poll();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(poller).awaitTransfer("tp-1", Duration.ofSeconds(45)));
    }

    @Test
    void aNegotiationWaitCutShortByTheBudgetFailsWithTheBudgetTimeoutAndCoolsNothing() {
        var poller = spy(completionPoller);
        var timed = newOrchestrator(poller, Duration.ofSeconds(60), Duration.ofSeconds(60));
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        var future = timed.acquireAssetAccess(buildParticipantContext(), requestFor(null),
                CallBudget.startingNow(clock, Duration.ofSeconds(30)));
        verify(poller).awaitNegotiation("neg-1", Duration.ofSeconds(30));

        clock.advance(Duration.ofSeconds(31));
        var failure = failureOf(future);

        assertAcquisitionTimeout(failure);
        assertThat(cooldownTracker.isCoolingDown(PROVIDER_ADDRESS)).isFalse();
    }

    @Test
    void aTransferWaitCutShortByTheBudgetFailsWithTheBudgetTimeoutAndCoolsNothing() {
        var poller = spy(completionPoller);
        var timed = newOrchestrator(poller, Duration.ofSeconds(60), Duration.ofSeconds(60));
        stubCatalogAndTransformChain("asset-1");
        stubNegotiatedAgreements("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        var future = timed.acquireAssetAccess(buildParticipantContext(), requestFor(null),
                CallBudget.startingNow(clock, Duration.ofSeconds(30)));
        verify(poller).awaitNegotiation("neg-0", Duration.ofSeconds(30));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            verify(poller).awaitTransfer("tp-1", Duration.ofSeconds(30));
        });

        clock.advance(Duration.ofSeconds(31));
        var failure = failureOf(future);

        assertAcquisitionTimeout(failure);
        assertThat(cooldownTracker.isCoolingDown(PROVIDER_ADDRESS)).isFalse();
    }

    @Test
    void theRenegotiationAfterARefusalStaysWithinTheBudget() {
        stubReusableAgreement("agreement-1");
        when(transferProcessService.initiateTransfer(any(), any())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(11));
            return ServiceResult.conflict(PROVIDER_CONFLICT);
        });

        var failure = failureOf(orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null),
                CallBudget.startingNow(clock, Duration.ofSeconds(10))));

        assertAcquisitionTimeout(failure);
        verifyNoInteractions(catalogService);
        verifyNoInteractions(contractNegotiationService);
    }

    @Test
    void eachStageDurationReachesItsOwnStage() {
        var poller = spy(completionPoller);
        var timed = newOrchestrator(poller, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(
                new CompletableFuture<>(), CompletableFuture.completedFuture(StatusResult.success("{}".getBytes())));
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class)))
                .thenReturn(Result.success(buildCatalog("asset-1", "offer-1", XRoadTransferType.PULL.wireValue())));
        stubNegotiationInitiation();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(invocation ->
                Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-1"))));
        stubStartedTransfers("tp-1");

        var catalogTimedOut = failureOf(timed.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget()));

        assertThat(catalogTimedOut.getCause()).isInstanceOf(XrdRuntimeException.class);
        assertThat(((XrdRuntimeException) catalogTimedOut.getCause()).getDetails()).contains("1000 ms");

        var future = timed.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());
        verify(poller).awaitNegotiation("neg-1", Duration.ofSeconds(2));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            completionPoller.poll();
            verify(poller).awaitTransfer("tp-1", Duration.ofSeconds(3));
        });
        pollUntilDone(future);
    }

    private static void assertAcquisitionTimeout(Throwable failure) {
        assertThat(failure).isInstanceOf(XrdRuntimeException.class);
        assertThat(((XrdRuntimeException) failure).getErrorCode()).isEqualTo("dataspace.dsp_acquisition_timeout");
    }
}
