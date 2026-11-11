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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.Differences;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.model.PublicationState;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Builds the {@link DifferenceSignals} of this Security Server, from the registered address and globalconf, and
 * evaluates them against all active binding rows. The publication steps build the signals here and read their own
 * rows. Nothing is kept between calls: each evaluation reads the rows as they are at that moment.
 */
@Component
@RequiredArgsConstructor
public class DifferenceEvaluator {

    private final DsParticipantRepository dsParticipantRepository;
    private final GlobalConfProvider globalConfProvider;
    private final DataspaceDidAuthority didAuthority;

    /**
     * @param serverId the Security Server the DIDs are carried for
     * @param address  the registered address of the server as resolved from globalconf
     * @return the signals for that server; every differing DID is stale while the address is not registered
     */
    public DifferenceSignals signals(SecurityServerId serverId, OwnAddress address) {
        return DifferenceSignals.of(serverId, globalConfProvider, address.registeredAddress().orElse(null));
    }

    /**
     * Reads the SYSTEM row and every active member row and evaluates all signals.
     *
     * @param address the registered address of this Security Server as resolved from globalconf
     * @return the evaluated signals, with {@link HostStatus#UNKNOWN} and nothing else set while the address is not
     * registered; the binding rows are not read then
     */
    public Differences evaluate(OwnAddress address) {
        if (!(address instanceof OwnAddress.Registered(var serverId, _))) {
            return Differences.UNKNOWN;
        }
        var members = dsParticipantRepository.findActiveMembers(EnumSet.allOf(PublicationState.class));
        var system = dsParticipantRepository.findActiveSystemParticipant().orElse(null);
        return signals(serverId, address).evaluate(system, members,
                () -> didAuthority.dspBaseUrlIfValid().orElse(null));
    }
}
