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
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.niis.xroad.common.identifiers.jpa.dao.impl.IdentifierDAOImpl;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.jpa.dao.AbstractDAOImpl;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Data access object implementation for bound dataspace participants.
 */
public class DsParticipantDAOImpl extends AbstractDAOImpl<DsParticipantEntity> {

    private static final int ID_CHUNK_SIZE = 1000;

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
     * Finds the Security Server's SYSTEM participant row if it is {@link ParticipantState#ACTIVE}.
     *
     * @param session the Hibernate session
     * @return the active SYSTEM row, empty when none exists or the row is decommissioned
     */
    public Optional<DsParticipantEntity> findActiveSystemParticipant(Session session) {
        return findSystemParticipant(session).filter(row -> row.getState() == ParticipantState.ACTIVE);
    }

    /**
     * Finds every {@link ParticipantType#MEMBER} row that is {@link ParticipantState#ACTIVE} and whose publication
     * state is one of the given states. A row without a publication state counts as {@link PublicationState#NOT_SENT}.
     * The member identifier is fetched together with the row.
     *
     * @param session the Hibernate session
     * @param states  the publication states to match
     * @return the matching active member rows, in no particular order
     */
    public List<DsParticipantEntity> findActiveMembers(Session session, Set<PublicationState> states) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<DsParticipantEntity> query = cb.createQuery(DsParticipantEntity.class);
        final Root<DsParticipantEntity> root = query.from(DsParticipantEntity.class);
        root.fetch("memberIdentifier");

        var publicationState = root.<PublicationState>get("publicationState");
        var matches = states.contains(PublicationState.NOT_SENT)
                ? cb.or(publicationState.isNull(), publicationState.in(states))
                : publicationState.in(states);
        query.select(root).where(
                cb.equal(root.get("participantType"), ParticipantType.MEMBER),
                cb.equal(root.get("state"), ParticipantState.ACTIVE),
                matches);

        return session.createQuery(query).list();
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
     * Flips the rows with the given ids to {@link ParticipantState#DECOMMISSIONED}, whatever their publication state.
     * The rows are flipped by conditional bulk updates, chunked to stay under the database parameter limit, so a row
     * that is no longer {@link ParticipantState#ACTIVE} when its update runs is left as it is and not counted.
     *
     * @param session the Hibernate session
     * @param ids     the ids of the rows to flip
     * @return the number of rows flipped
     */
    public int retireBindings(Session session, List<Long> ids) {
        int retired = 0;
        for (var chunk : chunksOf(ids)) {
            retired += session.createMutationQuery("update DsParticipantEntity set state = :decommissioned "
                            + "where id in :ids and state = :active")
                    .setParameter("decommissioned", ParticipantState.DECOMMISSIONED)
                    .setParameter("ids", chunk)
                    .setParameter("active", ParticipantState.ACTIVE)
                    .executeUpdate();
        }
        return retired;
    }

    /**
     * Tells whether any {@link ParticipantState#ACTIVE} row, SYSTEM or MEMBER, has a DID host that is not the
     * registered address, by the rule that {@link #findDifferingBindings} uses. Stops at the first such row. Always
     * {@code false} when the registered address cannot be used.
     *
     * @param session     the Hibernate session
     * @param hostMatcher the matcher for the registered address
     * @return {@code true} if at least one active row has a DID host that is not the registered address
     */
    public boolean hasDifferingBindings(Session session, RegisteredHostMatcher hostMatcher) {
        if (!hostMatcher.isRegisteredAddressUsable()) {
            return false;
        }
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        final Root<DsParticipantEntity> root = query.from(DsParticipantEntity.class);
        query.multiselect(root.get("schemeVersion"), root.get("did"))
                .where(cb.equal(root.get("state"), ParticipantState.ACTIVE));

        try (var rows = session.createQuery(query).getResultStream()) {
            return rows.anyMatch(columns -> hostMatcher.isBindingHostDifferent((String) columns[0], (String) columns[1]));
        }
    }

    /**
     * Finds every {@link ParticipantState#ACTIVE} row, SYSTEM and MEMBER, whose DID host is not the registered
     * address. A row whose DID cannot be decoded or whose scheme version is not supported is not among them, since
     * its host cannot be read. Empty when the registered address cannot be used, since then no host can be
     * compared. The member identifier is fetched together with the row.
     *
     * @param session     the Hibernate session
     * @param hostMatcher the matcher for the registered address
     * @return the active rows whose DID host is not the registered address, in no particular order
     */
    public List<DsParticipantEntity> findDifferingBindings(Session session, RegisteredHostMatcher hostMatcher) {
        if (!hostMatcher.isRegisteredAddressUsable()) {
            return List.of();
        }
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<DsParticipantEntity> query = cb.createQuery(DsParticipantEntity.class);
        final Root<DsParticipantEntity> root = query.from(DsParticipantEntity.class);
        root.fetch("memberIdentifier", JoinType.LEFT);
        query.select(root).where(cb.equal(root.get("state"), ParticipantState.ACTIVE));

        return session.createQuery(query).list().stream()
                .filter(row -> hostMatcher.isBindingHostDifferent(row.getSchemeVersion(), row.getDid()))
                .toList();
    }

    private static List<List<Long>> chunksOf(List<Long> ids) {
        return IntStream.range(0, (ids.size() + ID_CHUNK_SIZE - 1) / ID_CHUNK_SIZE)
                .mapToObj(chunk -> ids.subList(chunk * ID_CHUNK_SIZE, Math.min(ids.size(), (chunk + 1) * ID_CHUNK_SIZE)))
                .toList();
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
     * still {@link ParticipantState#ACTIVE} and in the expected state, in one conditional
     * update. A row with no publication state counts as {@link PublicationState#NOT_SENT}, so expecting
     * {@code NOT_SENT} also matches it.
     *
     * @param session  the Hibernate session
     * @param id       the participant row id
     * @param expected the state the row must still be in
     * @param next     the new state
     * @param reason   the new reason, may be {@code null}
     * @param at       the time of the change
     * @return {@code true} if the transition applied, {@code false} if the row was in another state, is not active
     * or is gone
     */
    public boolean transitionPublication(Session session, Long id, PublicationState expected, PublicationState next,
            @Nullable String reason, Instant at) {
        String expectedMatches = expected == PublicationState.NOT_SENT
                ? "(publicationState is null or publicationState = :expected)"
                : "publicationState = :expected";
        int updated = session.createMutationQuery("update DsParticipantEntity set publicationState = :next, "
                        + "publicationReason = :reason, publicationChangedAt = :at where id = :id and state = :active and "
                        + expectedMatches)
                .setParameter("next", next)
                .setParameter("reason", reason)
                .setParameter("at", at)
                .setParameter("id", id)
                .setParameter("active", ParticipantState.ACTIVE)
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
