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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.springframework.stereotype.Service;

/**
 * Writes the dataspace participant binding tombstone: called by the client-deletion domain flow when
 * a member's last client on this Security Server is deleted, per XRDADR-41's derive-then-store rule.
 * The provisioning reconciler never calls this — it only ever reads and converges on tombstones
 * written here.
 *
 * <p>A no-op when the data space feature is disabled, consistent with {@link CatalogInvalidationNotifier}:
 * a non-dataspace deployment never attempts the derivation, which needs a configured identity-hub URL.
 */
@Service
@RequiredArgsConstructor
public class DataspaceParticipantTombstoneService {

    private final AdminServiceProperties adminServiceProperties;
    private final DataspaceProvisioningService dataspaceProvisioningService;
    private final DsParticipantRepository dsParticipantRepository;

    /**
     * Marks the member's participant binding decommissioned, in the caller's transaction: flips an
     * existing row (a no-op if already decommissioned), or inserts a new decommissioned row carrying
     * the member's identity, derived on the fly with no external calls. Unconditional — called even
     * for a member that was never provisioned.
     *
     * @param member the member whose last client on this server was just deleted
     */
    public void decommission(ClientId member) {
        if (!adminServiceProperties.getDataspace().isEnabled()) {
            return;
        }
        var identity = dataspaceProvisioningService.deriveMemberIdentity(member);
        dsParticipantRepository.decommissionMember(member, identity.ctxId(), identity.did());
    }
}
