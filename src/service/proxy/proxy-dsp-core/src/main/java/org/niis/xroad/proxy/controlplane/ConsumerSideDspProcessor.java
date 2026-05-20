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

import io.opentelemetry.instrumentation.annotations.WithSpan;
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
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

/**
 * Consumer-side implementation of {@link DspRequestProcessor}. Resolves candidate provider security
 * servers via {@link ProviderSecurityServerResolver}, looks each up in the hardcoded
 * {@link CounterPartyTarget} map, and invokes
 * {@link ControlPlaneNegotiationService#acquireAssetAccess} in shuffled order until one succeeds.
 *
 * <p>ID construction:
 * <ul>
 *   <li>{@code assetId = serviceId.asEncodedId()} — symmetric with provider
 *       {@code AssetMapper.encodeAssetId}.</li>
 *   <li>{@code counterPartyId} + {@code counterPartyAddress} — looked up by candidate
 *       host-address in {@link CounterPartyTarget#defaultMap()} for normal requests or
 *       {@link CounterPartyTarget#managementMap()} for MANAGEMENT requests. Lookup miss is a hard
 *       error (fail fast; no silent fallback).</li>
 * </ul>
 *
 * <p>Exhaustion of all candidates ends in
 * {@link org.niis.xroad.common.core.exception.ErrorCode#NETWORK_ERROR} with the last thrown
 * exception chained.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ConsumerSideDspProcessor implements DspRequestProcessor {

    private final ControlPlaneNegotiationService controlPlaneNegotiationService;
    private final ProviderSecurityServerResolver providerSecurityServerResolver;

    @SuppressWarnings("deprecation")
    private final Map<String, CounterPartyTarget> counterPartyTargets = CounterPartyTarget.defaultMap();
    // Mgmt-ctx variant: same host keys, but URL and DID reference the mgmt participant context.
    private final Map<String, CounterPartyTarget> mgmtCounterPartyTargets = CounterPartyTarget.managementMap();

    @Override
    @WithSpan("dsp-execute")
    public AssetAccessResponse execute(DspRequest request) {
        log.trace("execute({})", request);

        var serviceId = request.serviceId();
        var assetId = serviceId.asEncodedId();
        var targets = request.management() ? mgmtCounterPartyTargets : counterPartyTargets;

        var candidates = new ArrayList<>(
                providerSecurityServerResolver.resolve(serviceId, request.targetSecurityServer()));
        Collections.shuffle(candidates);
        log.debug("processing DSP request for service {}, asset {}, management={}. Got {} possible targets",
                serviceId, assetId, request.management(), candidates.size());

        RuntimeException last = null;
        for (var candidate : candidates) {
            var target = targets.get(candidate.hostAddress());
            if (target == null) {
                last = XrdRuntimeException.systemException(NETWORK_ERROR,
                        "No DSP counter-party target configured for provider host-address \"%s\"",
                        candidate.hostAddress());
                log.warn("No counter-party target for SS {} (address {}), trying next",
                        candidate.serverId(), candidate.hostAddress(), last);
                continue;
            }
            try {
                return controlPlaneNegotiationService.acquireAssetAccess(
                        assetId, target.counterPartyId(), target.counterPartyAddress());
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
}
