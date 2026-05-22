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
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Consumer-side implementation of {@link DspRequestProcessor}. Resolves candidate provider security
 * servers via {@link ProviderSecurityServerResolver}, looks each up in the hardcoded
 * {@link CounterPartyTarget} map, and invokes
 * {@link AssetAccessAcquisitionService#acquireAssetAccess} in shuffled order until one succeeds.
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

    private static final Set<String> BUILTIN_SERVICE_CODES = Set.of(
            "getSecurityServerMetrics",
            "getSecurityServerOperationalData",
            "getSecurityServerHealthData",
            "listMethods",
            "allowedMethods",
            "getWsdl",
            "getOpenAPI");

    private final AssetAccessAcquisitionService assetAccessAcquisitionService;
    private final ProviderSecurityServerResolver providerSecurityServerResolver;

    @SuppressWarnings("deprecation")
    private final Map<String, CounterPartyTarget> counterPartyTargets = CounterPartyTarget.defaultMap();
    // Mgmt-ctx variant: same host keys, but URL and DID reference the mgmt participant context.
    // Reused for built-in server-proxy handler services because they are inherently self-hosted
    // and would otherwise produce DSP self-negotiation collisions on the host context.
    private final Map<String, CounterPartyTarget> mgmtCounterPartyTargets = CounterPartyTarget.managementMap();

    @Override
    @WithSpan("dsp-execute")
    public AssetAccessResponse execute(DspRequest request) {
        log.trace("execute({})", request);

        var serviceId = request.serviceId();
        var assetId = serviceId.asEncodedId();
        var useMgmtCtx = request.managementSubsystem() || isBuiltinService(serviceId);
        var targets = useMgmtCtx ? mgmtCounterPartyTargets : counterPartyTargets;

        var candidates = new ArrayList<>(
                providerSecurityServerResolver.resolve(serviceId, request.targetSecurityServer()));
        Collections.shuffle(candidates);
        log.debug("processing DSP request for service {}, asset {}, management={}. Got {} possible targets",
                serviceId, assetId, request.managementSubsystem(), candidates.size());

        if (candidates.isEmpty()) {
            throw XrdRuntimeException.systemException(UNKNOWN_MEMBER,
                    "No candidate security servers found for service %s", serviceId);
        }

        var remoteFailures = new ArrayList<RuntimeException>();
        var localFailures = new ArrayList<RuntimeException>();
        for (var candidate : candidates) {
            var target = targets.get(candidate.hostAddress());
            if (target == null) {
                var ex = XrdRuntimeException.systemException(NETWORK_ERROR,
                        "No DSP counter-party target configured for provider host-address \"%s\"",
                        candidate.hostAddress());
                log.warn("No counter-party target for SS {} (address {}), trying next",
                        candidate.serverId(), candidate.hostAddress(), ex);
                localFailures.add(ex);
                continue;
            }
            try {
                return assetAccessAcquisitionService.acquireAssetAccess(
                        assetId, target.counterPartyId(), target.counterPartyAddress());
            } catch (RuntimeException ex) {
                log.warn("Acquire failed for SS {} (address {}), trying next",
                        candidate.serverId(), candidate.hostAddress(), ex);
                remoteFailures.add(ex);
            }
        }

        throw buildFinalException(remoteFailures, localFailures, serviceId, candidates.size());
    }

    private static boolean isBuiltinService(ee.ria.xroad.common.identifier.ServiceId serviceId) {
        return serviceId != null
                && serviceId.getSubsystemCode() == null
                && BUILTIN_SERVICE_CODES.contains(serviceId.getServiceCode());
    }

    private RuntimeException buildFinalException(List<RuntimeException> remoteFailures,
                                                 List<RuntimeException> localFailures,
                                                 Object serviceId, int candidateCount) {
        if (localFailures.isEmpty() && !remoteFailures.isEmpty()
                && remoteFailures.stream().allMatch(XrdRuntimeException.class::isInstance)) {
            var xrdCodes = remoteFailures.stream()
                    .map(XrdRuntimeException.class::cast)
                    .map(XrdRuntimeException::getCode)
                    .collect(Collectors.toSet());
            if (xrdCodes.size() == 1) {
                return remoteFailures.getFirst();
            }
        }
        var last = !remoteFailures.isEmpty() ? remoteFailures.getLast() : localFailures.getLast();
        return XrdRuntimeException.systemException(NETWORK_ERROR, last,
                "All %d candidate security servers failed to acquire asset access for service %s",
                candidateCount, serviceId);
    }
}
