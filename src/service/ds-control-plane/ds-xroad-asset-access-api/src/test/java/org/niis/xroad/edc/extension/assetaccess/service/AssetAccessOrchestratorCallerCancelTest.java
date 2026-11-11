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
import org.junit.jupiter.api.Test;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.protocol.assetaccess.XRoadTransferType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetAccessOrchestratorCallerCancelTest extends AssetAccessOrchestratorTestSupport {

    @Test
    void aCallerGivingUpEndsOnlyItsOwnWaitWhileTheSharedAcquisitionContinues() throws Exception {
        var catalogFuture = new CompletableFuture<StatusResult<byte[]>>();
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(catalogFuture);
        var first = orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());
        var second = orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());

        first.cancel(true);

        assertThat(first).isCancelled();
        assertThat(second).isNotDone();
        assertThat(catalogFuture).isNotDone();
        stubCatalogParsing();
        stubStartedTransfers("tp-1");
        stubNegotiatedAgreements("agreement-1");
        catalogFuture.complete(StatusResult.success("{}".getBytes()));
        pollUntilDone(second);
        assertThat(second.get(5, TimeUnit.SECONDS).dataAddress().getStringProperty("endpoint"))
                .isEqualTo("http://provider/data-tp-1");
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
    }

    @Test
    void theLastCallerGivingUpCancelsTheCatalogFetch() {
        var catalogFuture = new CompletableFuture<StatusResult<byte[]>>();
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(catalogFuture);
        var first = orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());
        var second = orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());

        first.cancel(true);
        assertThat(catalogFuture).isNotCancelled();
        second.cancel(true);

        assertThat(catalogFuture).isCancelled();
    }

    @Test
    void theLastCallerGivingUpWhileNegotiatingStopsAwaitingTheNegotiation() {
        stubCatalogAndTransformChain("asset-1");
        stubNegotiationInitiation();
        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());
        completionPoller.poll();
        verify(contractNegotiationService, times(1)).initiateNegotiation(any(), any());
        clearInvocations(negotiationStore);

        future.cancel(true);
        completionPoller.poll();

        verify(negotiationStore, never()).queryNegotiations(any());
        verify(transferProcessService, never()).initiateTransfer(any(), any());
    }

    @Test
    void theLastCallerGivingUpDuringTheHandshakeRaceCancelsTheRaceAndSendsNoCatalogRequest() {
        var request = new AssetAccessRequest("asset-1", List.of(
                new AssetAccessRequest.Candidate("provider-1", "http://provider-1.example.org/dsp"),
                new AssetAccessRequest.Candidate("provider-2", "http://provider-2.example.org/dsp")), null, null);
        var ordering = new CompletableFuture<List<AssetAccessRequest.Candidate>>();
        when(candidateHandshakeRace.order(any())).thenReturn(ordering);
        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request, defaultBudget());

        future.cancel(true);
        ordering.complete(request.candidates());

        assertThat(ordering).isCancelled();
        verify(catalogService, never()).requestCatalog(any(), any(), any(), any(), any());
    }

    @Test
    void anAcquisitionStartedAfterTheLastCallerGaveUpStartsNewWork() {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(new CompletableFuture<>());
        orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget()).cancel(true);

        orchestrator.acquireAssetAccess(buildParticipantContext(), requestFor(null), defaultBudget());

        verify(catalogService, times(2)).requestCatalog(any(), any(), any(), any(), any());
    }

    private void stubCatalogParsing() {
        var catalog = buildCatalog("asset-1", "offer-1", XRoadTransferType.PULL.wireValue());
        when(jsonLd.expand(any())).thenReturn(Result.success(mock(JsonObject.class)));
        when(transformerRegistry.transform(any(), eq(Catalog.class))).thenReturn(Result.success(catalog));
    }
}
