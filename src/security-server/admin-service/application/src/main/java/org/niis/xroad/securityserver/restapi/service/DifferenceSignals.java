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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compares the DIDs and the DSP base URL on the binding rows with the values globalconf announces for one Security
 * Server. The publication steps compare single rows; {@link #evaluate} evaluates the host status, the SYSTEM
 * mismatches and the member mismatches in one go and returns them as {@link Differences}.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DifferenceSignals {

    private static final String SYSTEM_NAME = "SYSTEM";

    private final SecurityServerId serverId;
    private final GlobalConfProvider globalConfProvider;
    private final RegisteredHostMatcher hostMatcher;
    @Nullable
    private final String dspBaseUrl;

    /**
     * @param serverId           the Security Server the DIDs are carried for
     * @param globalConfProvider the source of the carried DIDs
     * @param registeredAddress  the registered address of the Security Server, {@code null} when it is unknown
     * @param dspBaseUrl         the DSP base URL built for the server, {@code null} while it cannot be built
     * @return the signals for that server
     */
    public static DifferenceSignals of(SecurityServerId serverId, GlobalConfProvider globalConfProvider,
            @Nullable String registeredAddress, @Nullable String dspBaseUrl) {
        return new DifferenceSignals(serverId, globalConfProvider, RegisteredHostMatcher.of(registeredAddress),
                dspBaseUrl);
    }

    /**
     * @param row a member binding row
     * @return how the DID globalconf announces for the member compares with the DID on the row; a differing DID that
     * no longer fits the registered address is stale
     */
    public GlobalConfComparison compareMemberDid(DsParticipantEntity row) {
        var carried = globalConfProvider.getMemberDid(row.getMemberIdentifier(), serverId).orElse(null);
        return GlobalConfComparison.classify(row.getDid(), carried, did -> !hostMatcher.didHostMatches(did));
    }

    boolean memberMismatch(DsParticipantEntity row) {
        return PublicationState.isSent(row.getPublicationState()) && compareMemberDid(row) == GlobalConfComparison.DIFFERENT;
    }

    /**
     * @param row the SYSTEM binding row
     * @return how the SYSTEM values globalconf announces compare with the SYSTEM DID on the row and the built DSP base
     * URL; the pair is stale when the host of either carried value is not the registered address
     */
    public SystemValuesComparison compareSystemValues(DsParticipantEntity row) {
        var carried = globalConfProvider.getServerSystemValues(serverId).orElse(null);
        return SystemValuesComparison.of(row.getDid(), dspBaseUrl, carried,
                values -> hostMatcher.isSystemPairStale(values.systemDid(), values.dspBaseUrl()));
    }

    SystemMismatch systemMismatch(DsParticipantEntity row) {
        if (!PublicationState.isSent(row.getPublicationState())) {
            return SystemMismatch.NONE;
        }
        var comparison = compareSystemValues(row);
        return new SystemMismatch(comparison.systemDid() == GlobalConfComparison.DIFFERENT,
                comparison.dspBaseUrl() == GlobalConfComparison.DIFFERENT);
    }

    /**
     * Evaluates every signal at once, from the rows as they are now. When the registered address cannot be used
     * nothing is compared and the host status is {@code UNKNOWN}.
     *
     * @param system  the active SYSTEM binding row, {@code null} when there is none
     * @param members the active member binding rows, whatever their publication state
     * @return the host status, the SYSTEM mismatches, the members with a mismatch and the names of the bindings at
     * another host
     */
    public Differences evaluate(@Nullable DsParticipantEntity system, Collection<DsParticipantEntity> members) {
        if (!hostMatcher.isRegisteredAddressUsable()) {
            return Differences.UNKNOWN;
        }
        var memberMismatches = members.stream()
                .filter(this::memberMismatch)
                .<ClientId>map(DsParticipantEntity::getMemberIdentifier)
                .collect(Collectors.toUnmodifiableSet());
        var systemMismatch = system == null ? SystemMismatch.NONE : systemMismatch(system);
        var hostDiffering = Stream.concat(
                        members.stream()
                                .filter(row -> !hostMatcher.didHostMatches(row.getDid()))
                                .map(row -> row.getMemberIdentifier().asEncodedId()),
                        Stream.ofNullable(system)
                                .filter(row -> !hostMatcher.didHostMatches(row.getDid()))
                                .map(_ -> SYSTEM_NAME))
                .collect(Collectors.toUnmodifiableSet());
        return new Differences(hostDiffering.isEmpty() ? HostStatus.OK : HostStatus.DIFFERS, systemMismatch,
                memberMismatches, hostDiffering);
    }

    /**
     * Which SYSTEM values differ from the ones globalconf announces.
     *
     * @param systemDid  whether the SYSTEM DID differs
     * @param dspBaseUrl whether the DSP base URL differs
     */
    public record SystemMismatch(boolean systemDid, boolean dspBaseUrl) {

        static final SystemMismatch NONE = new SystemMismatch(false, false);
    }

    /**
     * All signals of one evaluation.
     *
     * @param hostStatus       whether the DIDs of all active bindings carry the registered address as their host
     * @param systemMismatch   which SYSTEM values differ from the ones globalconf announces
     * @param memberMismatches the members whose carried DID differs from the bound one
     * @param hostDiffering    the names of the active bindings whose DID host is not the registered address:
     *                         {@code SYSTEM} or the encoded member identifier
     */
    public record Differences(HostStatus hostStatus, SystemMismatch systemMismatch, Set<ClientId> memberMismatches,
                              Set<String> hostDiffering) {

        public static final Differences UNKNOWN =
                new Differences(HostStatus.UNKNOWN, SystemMismatch.NONE, Set.of(), Set.of());
    }
}
