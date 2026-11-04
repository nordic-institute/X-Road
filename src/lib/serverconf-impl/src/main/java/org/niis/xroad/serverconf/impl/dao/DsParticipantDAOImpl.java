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
package org.niis.xroad.serverconf.impl.dao;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.niis.xroad.common.identifiers.jpa.dao.impl.IdentifierDAOImpl;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.jpa.dao.AbstractDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access object implementation for bound dataspace participants.
 */
public class DsParticipantDAOImpl extends AbstractDAOImpl<DsParticipantEntity> {

    private final IdentifierDAOImpl identifierDAO = new IdentifierDAOImpl();

    /**
     * Finds the bound participant row for the given member identifier.
     *
     * @param session the Hibernate session
     * @param member  the member identifier
     * @return the bound MEMBER row, if one has been provisioned
     */
    public Optional<DsParticipantEntity> findByMemberIdentifier(Session session, ClientId member) {
        ClientIdEntity identifier = identifierDAO.findClientId(session, member);
        if (identifier == null) {
            return Optional.empty();
        }
        return findByMemberIdentifier(session, identifier);
    }

    /**
     * Finds the Security Server's SYSTEM participant row, if it has been provisioned.
     *
     * @param session the Hibernate session
     * @return the bound SYSTEM row, if one has been provisioned
     */
    public Optional<DsParticipantEntity> findSystemParticipant(Session session) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<DsParticipantEntity> query = cb.createQuery(DsParticipantEntity.class);
        final Root<DsParticipantEntity> root = query.from(DsParticipantEntity.class);

        query.select(root).where(cb.equal(root.get("participantType"), ParticipantType.SYSTEM));

        return session.createQuery(query).uniqueResultOptional();
    }

    /**
     * Flips the member's bound participant row to {@link ParticipantState#DECOMMISSIONED}, whatever
     * its current state — a no-op if it already is. Does nothing when the member has no bound row:
     * every bound member is provisioned with an {@code ACTIVE} row up front, so a missing row means
     * there was never a published identity to tear down.
     *
     * @param session the Hibernate session
     * @param member  the member identifier
     * @return {@code true} if the member had a bound row, {@code false} if it had none
     */
    public boolean decommissionMember(Session session, ClientId member) {
        Optional<DsParticipantEntity> existing = findByMemberIdentifier(session, member);
        if (existing.isEmpty()) {
            return false;
        }
        existing.get().setState(ParticipantState.DECOMMISSIONED);
        return true;
    }

    /**
     * Finds every bound participant row currently marked {@link ParticipantState#DECOMMISSIONED},
     * i.e. awaiting teardown convergence.
     *
     * @param session the Hibernate session
     * @return the decommissioned rows
     */
    public List<DsParticipantEntity> findDecommissioned(Session session) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<DsParticipantEntity> query = cb.createQuery(DsParticipantEntity.class);
        final Root<DsParticipantEntity> root = query.from(DsParticipantEntity.class);

        query.select(root).where(cb.equal(root.get("state"), ParticipantState.DECOMMISSIONED));

        return session.createQuery(query).list();
    }

    /**
     * Deletes the given participant row by id. Idempotent: a missing row is not an error.
     *
     * @param session the Hibernate session
     * @param id      the participant row id
     * @return {@code true} if a row was deleted, {@code false} if none existed
     */
    public boolean delete(Session session, Long id) {
        return deleteById(session, DsParticipantEntity.class, id);
    }

    /**
     * Moves the row's publication state from {@code expected} to {@code next} only if the row is
     * still in the expected state, in one conditional update. A row with no publication state counts
     * as {@link PublicationState#NOT_SENT}, so expecting {@code NOT_SENT} also matches it.
     *
     * @param session  the Hibernate session
     * @param id       the participant row id
     * @param expected the state the row must still be in
     * @param next     the new state
     * @param reason   the new reason, may be {@code null}
     * @param at       the time of the change
     * @return {@code true} if the transition applied, {@code false} if the row was in another state or is gone
     */
    public boolean transitionPublication(Session session, Long id, PublicationState expected, PublicationState next,
            @Nullable String reason, Instant at) {
        String expectedMatches = expected == PublicationState.NOT_SENT
                ? "(publicationState is null or publicationState = :expected)"
                : "publicationState = :expected";
        int updated = session.createMutationQuery("update DsParticipantEntity set publicationState = :next, "
                        + "publicationReason = :reason, publicationChangedAt = :at where id = :id and " + expectedMatches)
                .setParameter("next", next)
                .setParameter("reason", reason)
                .setParameter("at", at)
                .setParameter("id", id)
                .setParameter("expected", expected)
                .executeUpdate();
        return updated > 0;
    }

    private Optional<DsParticipantEntity> findByMemberIdentifier(Session session, ClientIdEntity identifier) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<DsParticipantEntity> query = cb.createQuery(DsParticipantEntity.class);
        final Root<DsParticipantEntity> root = query.from(DsParticipantEntity.class);

        query.select(root).where(cb.equal(root.get("memberIdentifier"), identifier));

        return session.createQuery(query).uniqueResultOptional();
    }

}
