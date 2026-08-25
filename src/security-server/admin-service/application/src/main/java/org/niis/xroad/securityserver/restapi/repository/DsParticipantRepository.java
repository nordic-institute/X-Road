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
package org.niis.xroad.securityserver.restapi.repository;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.identifiers.jpa.dao.impl.IdentifierDAOImpl;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.serverconf.impl.dao.DsParticipantDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Repository for the {@code ds_participant} pinning table, per XRDADR-41's derive-then-store decision.
 */
@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class DsParticipantRepository {

    private final PersistenceUtils persistenceUtils;

    /**
     * Finds the pinned participant row for the given member identifier.
     *
     * @param member the member identifier
     * @return the pinned MEMBER row, if one has been provisioned
     */
    public Optional<DsParticipantEntity> findByMemberIdentifier(ClientId member) {
        DsParticipantDAOImpl dao = new DsParticipantDAOImpl();
        return dao.findByMemberIdentifier(persistenceUtils.getCurrentSession(), member);
    }

    /**
     * Pins a new MEMBER participant row. Never call this for a member that is already pinned —
     * the pinned row is authoritative and must never be overwritten. The insert is flushed
     * immediately, so a concurrent pin of the same member surfaces here as a constraint
     * violation rather than at the surrounding transaction's commit.
     *
     * @param member the member identifier
     * @param ctxId  the derived ctx-id
     * @param did    the derived DID
     * @return the persisted row
     */
    public DsParticipantEntity pinMemberParticipant(ClientId member, String ctxId, String did) {
        var session = persistenceUtils.getCurrentSession();
        var identifier = new IdentifierDAOImpl().findOrCreateClientId(session, member);

        var participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.MEMBER);
        participant.setMemberIdentifier(identifier);
        participant.setCtxId(ctxId);
        participant.setDid(did);
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);

        var saved = new DsParticipantDAOImpl().save(session, participant);
        session.flush();
        return saved;
    }
}
