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

import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.Set;

/**
 * Compares the DID on a member binding row with the DID globalconf carries for the member on one Security Server.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DifferenceSignals {

    /**
     * The publication states in which a DID has been sent to the Control Plane and Identity Hub.
     */
    public static final Set<PublicationState> SENT = Set.of(PublicationState.SUBMITTED, PublicationState.PUBLISHED);

    private final SecurityServerId serverId;
    private final GlobalConfProvider globalConfProvider;
    private final RegisteredHostMatcher hostMatcher;

    /**
     * @param serverId           the Security Server the DIDs are carried for
     * @param globalConfProvider the source of the carried DIDs
     * @param registeredAddress  the registered address of the Security Server, {@code null} when it is unknown
     * @return the signals for that server
     */
    public static DifferenceSignals of(SecurityServerId serverId, GlobalConfProvider globalConfProvider,
            @Nullable String registeredAddress) {
        return new DifferenceSignals(serverId, globalConfProvider, RegisteredHostMatcher.of(registeredAddress));
    }

    /**
     * @param row a member binding row
     * @return how the DID globalconf carries for the member compares with the DID on the row; a differing DID that
     * no longer fits the registered address is stale
     */
    public GlobalConfComparison compareMemberDid(DsParticipantEntity row) {
        var carried = globalConfProvider.getMemberDid(row.getMemberIdentifier(), serverId).orElse(null);
        return GlobalConfComparison.classify(row.getDid(), carried, hostMatcher::isMemberDidStale);
    }

    /**
     * @param row a member binding row
     * @return whether the row is in submitted or published state and globalconf carries another, not stale, DID for
     * the member
     */
    public boolean memberMismatch(DsParticipantEntity row) {
        return row.getPublicationState() != null && SENT.contains(row.getPublicationState())
                && compareMemberDid(row) == GlobalConfComparison.DIFFERENT;
    }
}
