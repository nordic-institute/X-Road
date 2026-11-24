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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ClientFacingErrorPolicy;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.ds.identity.DecodedParticipant;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.ds.identity.MemberParticipant;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.ds.identity.SystemParticipant;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver.ProviderCandidates;
import org.niis.xroad.proxy.core.service.DefaultServiceAddressResolver.ServerInMaintenance;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.niis.xroad.common.core.exception.ErrorCode.VALIDATION_ERROR;

/**
 * Chooses the call mode of a call from the DIDs the provider's servers published in the global configuration. The
 * servers that published the called DID are the DSP candidates; a server whose published values do not cover the
 * called DID is not a candidate. When no server published it, the call is unnegotiated.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CallModeSelector {

    private final GlobalConfProvider globalConfProvider;
    private final DefaultServiceAddressResolver serviceAddressResolver;
    private final ProxyProperties proxyProperties;
    private final DspRequestProcessor dspRequestProcessor;
    private final LegacyCallRefusalPolicy legacyCallRefusalPolicy;

    /**
     * Decides whether a request goes to the provider over DSP. A request that is neither bypassing DSP nor going
     * over DSP is an unnegotiated call, which is refused while legacy calls are refused for the provider. With DSP
     * disabled every request that does not bypass DSP is unnegotiated, and no provider lookup is made.
     *
     * @param request the request to route
     * @return the request carrying its DSP candidates, or empty when the request bypasses DSP, DSP is disabled or
     *         no provider server published the called DID, and the unnegotiated call is allowed
     * @throws XrdRuntimeException {@code LEGACY_CALL_REFUSED} when the call is unnegotiated and refused; see
     *                             {@link #select(CalledTarget, ProviderCandidates)} for the selection failures
     */
    public Optional<DspRequest> overDsp(DspRequest request) {
        if (dspRequestProcessor.bypassesDsp(request)) {
            return Optional.empty();
        }
        if (proxyProperties.dspEnabled()) {
            var servers = serviceAddressResolver.resolveCandidates(request.serviceId(), request.targetSecurityServer());
            var target = CalledTarget.of(request.serviceId(), request.managementSubsystem());
            if (select(target, servers) instanceof CallMode.Dsp(var candidates)) {
                return Optional.of(request.withCandidates(candidates));
            }
        }
        legacyCallRefusalPolicy.checkConsumerCallAllowed(request.serviceId());
        return Optional.empty();
    }

    /**
     * {@link #select(CalledTarget, ProviderCandidates)} over servers that are all available.
     *
     * @param target    the called participant
     * @param addresses the provider's candidate servers
     * @return the call mode
     * @throws XrdRuntimeException see {@link #select(CalledTarget, ProviderCandidates)}
     */
    CallMode select(CalledTarget target, List<ProviderAddress> addresses) {
        return select(target, new ProviderCandidates(addresses, List.of()));
    }

    /**
     * Selects the call mode. A server that published the called DID without the values its DSP target needs, or
     * with a DID that does not belong to the called participant, is published but unusable: it is left out while
     * another candidate remains. A server in maintenance mode still counts as published, so an unpublished sibling
     * never takes over its calls.
     *
     * @param target  the called participant
     * @param servers the provider's candidate servers; an address without a server id is matched to the servers
     *                that serve the provider at that address
     * @return {@link CallMode.Dsp} with the usable candidates when any available server published the called DID,
     *         {@link CallMode.Unnegotiated} when no server published it
     * @throws XrdRuntimeException {@code INTERNAL_ERROR} with a generic message for the first failure to build a DSP
     *                             target, when servers published the called DID and none of the published values is
     *                             usable, the failure itself being logged and kept as {@code originalCode=} metadata;
     *                             the maintenance mode error of a server that published the called DID when every such
     *                             server is in maintenance mode
     */
    public CallMode select(CalledTarget target, ProviderCandidates servers) {
        var serversByAddress = needsServerLookup(servers)
                ? serversByAddress(target.provider())
                : Map.<String, List<SecurityServerId>>of();
        var candidates = new ArrayList<AcquisitionCandidate>();
        XrdRuntimeException firstFailure = null;
        var published = false;
        for (var address : servers.available()) {
            for (var serverId : serversOf(address, serversByAddress)) {
                var systemValues = globalConfProvider.getServerSystemValues(serverId);
                var did = publishedDid(target, serverId, systemValues);
                if (did.isEmpty()) {
                    continue;
                }
                published = true;
                try {
                    candidates.add(candidateOf(target, serverId, did.get(), systemValues));
                } catch (XrdRuntimeException ex) {
                    log.warn("Skipping SS {} (address {}): its published DSP target is not usable",
                            serverId, address.hostAddress(), ex);
                    if (firstFailure == null) {
                        firstFailure = ex;
                    }
                }
            }
        }
        if (!candidates.isEmpty()) {
            return new CallMode.Dsp(candidates);
        }
        if (published) {
            throw ClientFacingErrorPolicy.sanitizeProviderTargetFailure(firstFailure);
        }
        failOnPublishedServerInMaintenance(target, servers, serversByAddress);
        return new CallMode.Unnegotiated();
    }

    private void failOnPublishedServerInMaintenance(CalledTarget target, ProviderCandidates servers,
                                                    Map<String, List<SecurityServerId>> serversByAddress) {
        for (var unavailable : servers.inMaintenance()) {
            var publishedInMaintenance = serversOf(unavailable.address(), serversByAddress).stream()
                    .anyMatch(serverId -> publishedDid(target, serverId, globalConfProvider.getServerSystemValues(serverId)).isPresent());
            if (publishedInMaintenance) {
                throw unavailable.error();
            }
        }
    }

    private static boolean needsServerLookup(ProviderCandidates servers) {
        return Stream.concat(servers.available().stream(), servers.inMaintenance().stream().map(ServerInMaintenance::address))
                .anyMatch(address -> address.serverId() == null);
    }

    private Map<String, List<SecurityServerId>> serversByAddress(ClientId provider) {
        var serversByAddress = new HashMap<String, List<SecurityServerId>>();
        for (var serverId : globalConfProvider.getClientSecurityServers(provider)) {
            var hostAddress = globalConfProvider.getSecurityServerAddress(serverId);
            if (hostAddress != null) {
                serversByAddress.computeIfAbsent(hostAddress, _ -> new ArrayList<>()).add(serverId);
            }
        }
        return serversByAddress;
    }

    private static List<SecurityServerId> serversOf(ProviderAddress address, Map<String, List<SecurityServerId>> serversByAddress) {
        if (address.serverId() != null) {
            return List.of(address.serverId());
        }
        return serversByAddress.getOrDefault(address.hostAddress(), List.of());
    }

    private Optional<String> publishedDid(CalledTarget target, SecurityServerId serverId, Optional<ServerSystemValues> systemValues) {
        if (target.system()) {
            return systemValues.map(ServerSystemValues::systemDid);
        }
        return globalConfProvider.getMemberDid(target.provider(), serverId);
    }

    private static AcquisitionCandidate candidateOf(CalledTarget target, SecurityServerId serverId, String did,
                                                     Optional<ServerSystemValues> systemValues) {
        var values = systemValues.orElseThrow(() -> XrdRuntimeException.systemException(VALIDATION_ERROR,
                "Security server %s published the DID %s without its DSP base URL", serverId, did));
        var participant = ParticipantIdentifierScheme.decodeDid(did);
        requireDidOf(target, serverId, did, participant);
        return new AcquisitionCandidate(did, DspConventions.dspTarget(values.dspBaseUrl(), participant));
    }

    private static void requireDidOf(CalledTarget target, SecurityServerId serverId, String did, DecodedParticipant participant) {
        var belongsToTarget = switch (participant) {
            case MemberParticipant member -> !target.system() && member.member().memberEquals(target.provider());
            case SystemParticipant _ -> target.system();
        };
        if (!belongsToTarget) {
            throw XrdRuntimeException.systemException(VALIDATION_ERROR,
                    "Security server %s published the DID %s, which is not the DID of the called %s",
                    serverId, did, target.system() ? "SYSTEM participant" : "member " + target.provider().toShortString());
        }
    }
}
