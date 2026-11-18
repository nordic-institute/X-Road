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

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.time.CallBudget;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessRequest;
import org.niis.xroad.edc.extension.assetaccess.AssetAccessResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.result.ServiceResult.success;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetAccessOrchestratorCandidateTimeTest extends AssetAccessOrchestratorTestSupport {

    private static final String PETSTORE_ASSET_ID = "DEV:COM:222:TESTSERVICE:petstore";
    private static final AssetAccessRequest.Candidate SECURITY_SERVER =
            new AssetAccessRequest.Candidate("ss0.example.org", "http://ss0.example.org/dsp");
    private static final AssetAccessRequest.Candidate SIBLING_SECURITY_SERVER =
            new AssetAccessRequest.Candidate("ss1.example.org", "http://ss1.example.org/dsp");

    @Test
    void aLoneCandidateStillNegotiatingAfterTwentySecondsIsWaitedForAndNotCooled() {
        stubCatalogAndTransformChain(PETSTORE_ASSET_ID);
        when(contractNegotiationService.initiateNegotiation(any(), any())).thenReturn(success(buildNegotiation()));
        var negotiated = new AtomicBoolean();
        when(negotiationStore.queryNegotiations(specWithId("neg-1"))).thenAnswer(_ -> negotiated.get()
                ? Stream.of(finalizedNegotiation("neg-1", buildAgreement("agreement-1", SECURITY_SERVER.counterPartyId(), 1_000L)))
                : Stream.empty());
        when(transferProcessService.initiateTransfer(any(), any())).thenReturn(successfulTransferInitiation("tp-1"));
        var address = startedAddress("tp-1", "http://ss0.example.org/data");

        var future = orchestrator.acquireAssetAccess(buildParticipantContext(), request(SECURITY_SERVER), defaultBudget());
        clock.advance(Duration.ofSeconds(21));
        completionPoller.poll();
        assertThat(future).isNotDone();
        negotiated.set(true);
        pollUntilDone(future);

        var result = future.join();
        assertThat(result.dataAddress()).isSameAs(address);
        assertThat(result.winner()).isEqualTo(new AssetAccessResult.Winner(
                SECURITY_SERVER.counterPartyId(), SECURITY_SERVER.counterPartyAddress()));
        assertThat(cooldownTracker.isCoolingDown(SECURITY_SERVER.counterPartyAddress())).isFalse();
    }

    @Test
    void aCatalogThatNeverAnswersUntilTheBudgetRunsOutFailsAsAcquisitionTimeoutAndCoolsNoCandidate() {
        when(catalogService.requestCatalog(any(), any(), any(), any(), any())).thenReturn(new CompletableFuture<>());
        var shortBudget = CallBudget.startingNow(clock, Duration.ofMillis(100));

        var failure = failureOf(orchestrator.acquireAssetAccess(buildParticipantContext(),
                request(SECURITY_SERVER, SIBLING_SECURITY_SERVER), shortBudget));

        assertThat(failure).isInstanceOf(XrdRuntimeException.class);
        assertThat(((XrdRuntimeException) failure).getErrorCode()).isEqualTo("dataspace.dsp_acquisition_timeout");
        verify(catalogService, times(1)).requestCatalog(any(), any(), any(), any(), any());
        assertThat(cooldownTracker.isCoolingDown(SECURITY_SERVER.counterPartyAddress())).isFalse();
        assertThat(cooldownTracker.isCoolingDown(SIBLING_SECURITY_SERVER.counterPartyAddress())).isFalse();
    }

    private static AssetAccessRequest request(AssetAccessRequest.Candidate... candidates) {
        return new AssetAccessRequest(PETSTORE_ASSET_ID, List.of(candidates), null, null);
    }
}
