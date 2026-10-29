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
import org.niis.xroad.serverconf.model.ParticipantState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
 * <p>Binding comes before provisioning: the caller binds a member first and provisions only the
 * members this service reports as bound. Deciding when binding is allowed (readiness, a known
 * registered address, a registered authentication certificate) is the caller's job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceParticipantBindingService {

    private final DsParticipantRepository dsParticipantRepository;
    private final DataspaceDidAuthority didAuthority;

    /**
     * Binds the derived ctx-id and DID of every given member that has no row yet. Existing rows are
     * left untouched. Never throws: a member that cannot be bound now is left out of the result and
     * retried on a later pass, and does not stop the other members from being bound.
     *
     * @param members the hosted members to bind
     * @return the given members that hold an active binding row afterwards, in the given order
     */
    public List<ClientId> bindMembersIfAbsent(Collection<ClientId> members) {
        List<ClientId> bound = new ArrayList<>();
        for (var member : members) {
            try {
                if (bindIfAbsent(member)) {
                    bound.add(member);
                }
            } catch (Exception e) {
                log.warn("Data space: could not bind participant identity for member {}", member, e);
            }
        }
        return bound;
    }

    private boolean bindIfAbsent(ClientId member) {
        var existing = dsParticipantRepository.findByMemberIdentifier(member);
        if (existing.isPresent()) {
            return existing.get().getState() == ParticipantState.ACTIVE;
        }
        bindMember(member);
        return true;
    }

    private void bindMember(ClientId member) {
        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var did = didAuthority.memberDid(member);
        dsParticipantRepository.bindMemberParticipant(member, ctxId, did.toString());
        log.info("Dataspace: bound participant identity for member {} (ctx-id '{}', DID '{}')", member, ctxId, did);
    }
}
