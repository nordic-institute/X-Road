/*
 * The MIT License
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
package org.niis.xroad.proxy.core.clientproxy;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;

/**
 * Reacts to a failed DSP data-plane send: after a network fault it drops the cached access won by the failed
 * provider Security Server and reports the server to the control plane.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DspSendFailureHandler {

    private final DspRequestProcessor dspRequestProcessor;

    /**
     * Does nothing unless nothing was sent and an asset access was used.
     * Never throws; the caller rethrows the original failure.
     *
     * @param failure     the exception thrown by the send
     * @param assetAccess the asset access the send used; {@code null} when none was acquired
     */
    public void handle(Throwable failure, @Nullable AssetAccessResponse assetAccess) {
        if (assetAccess == null || !TransportFaultClassifier.nothingWasSent(failure)) {
            return;
        }
        var winnerId = assetAccess.winner().counterPartyId();
        var winnerAddress = assetAccess.winner().counterPartyAddress();
        log.warn("DSP data-plane send failed before the request was sent: counterPartyId={} counterPartyAddress={}",
                winnerId, winnerAddress);
        try {
            dspRequestProcessor.invalidateAssetAccessWonBy(winnerAddress);
        } catch (RuntimeException e) {
            log.warn("Failed to drop the asset access won by the failed server: counterPartyAddress={}", winnerAddress, e);
        }
        try {
            dspRequestProcessor.reportCounterPartyFailure(winnerId, winnerAddress);
        } catch (RuntimeException e) {
            log.warn("Failed to report the failed server: counterPartyId={} counterPartyAddress={}", winnerId, winnerAddress, e);
        }
    }
}
