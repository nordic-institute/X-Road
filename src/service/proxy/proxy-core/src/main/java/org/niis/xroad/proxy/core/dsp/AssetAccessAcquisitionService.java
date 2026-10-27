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
package org.niis.xroad.proxy.core.dsp;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Acquires asset access responses from the DSP control plane.
 * <p>
 * The control plane orchestrates the full negotiation internally (catalog, contract, transfer)
 * and returns an asset access response containing the dataplane endpoint URL and optional authorization credentials.
 */
public interface AssetAccessAcquisitionService {

    /**
     * Acquires an asset access response from the control plane for the given asset. The control plane
     * chooses among the candidates; the result is the same for the same candidates in any order.
     *
     * @param participantContextId the consumer participant context to negotiate as
     * @param assetId              the asset identifier for the data transfer
     * @param candidates           the provider participants that may serve the asset, in the caller's order
     * @param forcedTarget         the Security Server the caller forced the target to, which {@code candidates}
     *                             is then narrowed to; {@code null} when the caller states none
     * @param consumerClientId     the calling client, subsystem included; {@code null} when the request states none
     * @return the parsed asset access response containing the dataplane endpoint URL
     */
    AssetAccessResponse acquireAssetAccess(String participantContextId, String assetId,
                                           List<AcquisitionCandidate> candidates,
                                           @Nullable SecurityServerId forcedTarget,
                                           @Nullable ClientId consumerClientId);

    /**
     * Drops any cached asset access acquired for the given participant context, asset and calling client,
     * so that the next acquisition asks the control plane afresh. A no-op when nothing is cached.
     *
     * @param participantContextId the consumer participant context the asset access was acquired for
     * @param assetId              the asset identifier
     * @param consumerClientId     the calling client; {@code null} when the request states none
     */
    default void invalidateAssetAccess(String participantContextId, String assetId, @Nullable ClientId consumerClientId) {
    }
}
