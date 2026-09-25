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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code ds_participant} table, per XRDADR-41's derive-then-bind decision.
 */
@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class DsParticipantRepository {

    private final PersistenceUtils persistenceUtils;

    private final DsParticipantDAOImpl dsParticipantDAO = new DsParticipantDAOImpl();
    private final IdentifierDAOImpl identifierDAO = new IdentifierDAOImpl();

    /**
     * Finds the bound participant row for the given member identifier.
     *
     * @param member the member identifier
     * @return the bound MEMBER row, if one has been provisioned
     */
    public Optional<DsParticipantEntity> findByMemberIdentifier(ClientId member) {
        return dsParticipantDAO.findByMemberIdentifier(persistenceUtils.getCurrentSession(), member);
    }

    /**
     * Flips the member's bound participant row to decommissioned, in the caller's transaction — a
     * no-op if it already is. Does nothing when the member has no bound row.
     *
     * @param member the member identifier
     * @return {@code true} if the member had a bound row, {@code false} if it had none
     */
    public boolean decommissionMember(ClientId member) {
        return dsParticipantDAO.decommissionMember(persistenceUtils.getCurrentSession(), member);
    }

    /**
     * Finds every bound participant row currently marked decommissioned, awaiting teardown convergence.
     *
     * @return the decommissioned rows
     */
    public List<DsParticipantEntity> findDecommissioned() {
        return dsParticipantDAO.findDecommissioned(persistenceUtils.getCurrentSession());
    }

    /**
     * Deletes the given participant row by id, in the caller's transaction. Idempotent: deleting an
     * already-absent row is not an error.
     *
     * @param id the participant row id
     * @return {@code true} if a row was deleted, {@code false} if none existed
     */
    public boolean delete(Long id) {
        return dsParticipantDAO.delete(persistenceUtils.getCurrentSession(), id);
    }

    /**
     * Finds the Security Server's bound SYSTEM participant row, if one has been provisioned.
     *
     * @return the bound SYSTEM row, if one has been provisioned
     */
    public Optional<DsParticipantEntity> findSystemParticipant() {
        return dsParticipantDAO.findSystemParticipant(persistenceUtils.getCurrentSession());
    }

    /**
     * Binds a new MEMBER participant row. Never call this for a member that is already bound —
     * the bound row is authoritative and must never be overwritten. The insert is flushed
     * immediately, so a concurrent bind of the same member surfaces here as a constraint
     * violation rather than at the surrounding transaction's commit.
     *
     * <p>Runs in its own transaction: callers bind as a side effect of unrelated work, and a
     * losing race on the unique member identifier must not mark the caller's transaction
     * rollback-only.
     *
     * @param member the member identifier
     * @param ctxId  the derived ctx-id
     * @param did    the derived DID
     * @return the persisted row
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DsParticipantEntity bindMemberParticipant(ClientId member, String ctxId, String did) {
        var session = persistenceUtils.getCurrentSession();
        var identifier = identifierDAO.findOrCreateClientId(session, member);

        var participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.MEMBER);
        participant.setMemberIdentifier(identifier);
        participant.setCtxId(ctxId);
        participant.setDid(did);
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);

        var saved = dsParticipantDAO.save(session, participant);
        session.flush();
        return saved;
    }
}
