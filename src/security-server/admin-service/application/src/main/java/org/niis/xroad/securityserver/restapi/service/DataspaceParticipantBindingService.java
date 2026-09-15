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
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Writes the {@code ds_participant} MEMBER rows that bind a member's data space identity, per
 * XRDADR-41's derive-then-bind decision. The only writer of that table.
 *
 * <p>Binding is level-triggered: each provisioning pass binds whichever hosted members still lack a
 * row, so a member is bound once it is a registered client of this Security Server and stays bound
 * from then on. A bound row is authoritative and is never rewritten.
 *
 * <p>The pass binds nothing until this server has a registered authentication certificate. Binding
 * an interim or misconfigured host would freeze a DID that can then only be repaired by hand, and a
 * member that stays unbound simply keeps deriving its DID on the fly until a later pass binds it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceParticipantBindingService {

    private final DsParticipantRepository dsParticipantRepository;
    private final DataspaceReadinessPredicates readinessPredicates;
    private final DataspaceDidAuthority didAuthority;

    /**
     * Binds the derived ctx-id and DID of every given member that is not bound yet. Existing rows
     * are left untouched. Never throws: a member that cannot be bound now is left for a later pass.
     *
     * @param members the hosted members to bind
     * @return the number of rows written
     */
    public int bindMembersIfAbsent(Collection<ClientId> members) {
        try {
            List<ClientId> unbound = members.stream()
                    .filter(member -> dsParticipantRepository.findByMemberIdentifier(member).isEmpty())
                    .toList();
            if (unbound.isEmpty()) {
                return 0;
            }
            if (!readinessPredicates.hasRegisteredAuthCert()) {
                log.debug("Data space: no registered authentication certificate, leaving {} member(s) unbound",
                        unbound.size());
                return 0;
            }

            var ssHost = didAuthority.current();
            return (int) unbound.stream().filter(member -> bindMember(member, ssHost)).count();
        } catch (Exception e) {
            log.warn("Data space: participant identity binding pass failed", e);
            return 0;
        }
    }

    private boolean bindMember(ClientId member, String ssHost) {
        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var did = ParticipantIdentifierScheme.memberDid(member, ssHost);
        try {
            dsParticipantRepository.bindMemberParticipant(member, ctxId, did);
            log.info("Data space: bound participant identity for member {} (ctx-id '{}', DID '{}')", member, ctxId, did);
            return true;
        } catch (Exception e) {
            log.warn("Data space: could not bind participant identity for member {}", member, e);
            return false;
        }
    }
}
