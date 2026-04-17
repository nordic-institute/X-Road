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
package org.niis.xroad.proxy.controlplane;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.clientproxy.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.clientproxy.dsp.ControlPlaneNegotiationService;
import org.niis.xroad.proxy.core.clientproxy.dsp.DspRequest;
import org.niis.xroad.proxy.core.clientproxy.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver;

import java.util.ArrayList;
import java.util.Collections;

import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

/**
 * Default DSP sub-processor. Derives asset-access parameters from the typed
 * {@link DspRequest} and invokes {@link ControlPlaneNegotiationService#acquireAssetAccess}
 * against each candidate security server in shuffled order, returning the first successful
 * {@link AssetAccessResponse}.
 *
 * <p>ID construction (per phase 9 decisions):
 * <ul>
 *   <li>{@code assetId = serviceId.asEncodedId()} (D-05; symmetric with provider
 *       {@code AssetMapper.encodeAssetId})</li>
 *   <li>{@code counterPartyId = serviceId.getClientId().asEncodedId()} (D-06; DSP participant =
 *       provider subsystem identity, not hosting SS)</li>
 *   <li>{@code counterPartyAddress = scheme://host:port/basePath} from
 *       {@link AssetAccessClientProperties} (D-08); EDC appends version + message subpath at
 *       dispatch time</li>
 * </ul>
 *
 * <p>Exhaustion of all candidates ends in {@link org.niis.xroad.common.core.exception.ErrorCode#NETWORK_ERROR}
 * with the last thrown exception chained (D-19).
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class DspSubProcessor implements DspRequestProcessor {

    private final ControlPlaneNegotiationService controlPlaneNegotiationService;
    private final ProviderSecurityServerResolver providerSecurityServerResolver;
    private final AssetAccessClientProperties clientProperties;

    @Override
    public AssetAccessResponse execute(DspRequest request) {
        log.trace("execute({})", request);

        var serviceId = request.serviceId();
        var assetId = serviceId.asEncodedId();
        var counterPartyId = serviceId.getClientId().asEncodedId();

        var candidates = new ArrayList<>(
                providerSecurityServerResolver.resolve(serviceId, request.targetSecurityServer()));
        Collections.shuffle(candidates);

        RuntimeException last = null;
        for (var candidate : candidates) {
            var counterPartyAddress = buildCounterPartyAddress(candidate.hostAddress());
            try {
                return controlPlaneNegotiationService.acquireAssetAccess(
                        assetId, counterPartyId, counterPartyAddress);
            } catch (RuntimeException ex) {
                log.warn("Acquire failed for SS {} (address {}), trying next",
                        candidate.serverId(), candidate.hostAddress(), ex);
                last = ex;
            }
        }

        throw XrdRuntimeException.systemException(NETWORK_ERROR, last,
                "All %d candidate security servers failed to acquire asset access for service %s",
                candidates.size(), serviceId);
    }

    private String buildCounterPartyAddress(String host) {
        return "%s://%s:%d%s".formatted(
                clientProperties.counterPartyUrlScheme(),
                host,
                clientProperties.counterPartyPort(),
                clientProperties.counterPartyBasePath());
    }
}
