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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.identifier.ClientId;

import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.common.identifiers.jpa.dao.impl.IdentifierDAOImpl;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.ds.identity.RegisteredHostMatcher;
import org.niis.xroad.serverconf.impl.dao.DsParticipantDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@code ds_participant} table: persistence, and the unique and
 * type/identifier consistency constraints from XRDADR-41.
 */
public class DsParticipantDAOImplTest {

    private static final String XROAD_INSTANCE = "XX";
    private static final String MEMBER_CLASS = "FooClass";
    private static final String SS_HOST = "ss0.example.org";
    private static final String NEW_HOST = "ss0-new.example.org";

    private static final DatabaseCtx DATABASE_CTX = new ServerConfDatabaseCtx(TestUtil.serverConfDbProperties);

    private final DsParticipantDAOImpl dao = new DsParticipantDAOImpl();
    private final IdentifierDAOImpl identifierDAO = new IdentifierDAOImpl();

    /**
     * The production at-most-one-SYSTEM-row rule is the Postgres partial unique index from changelog
     * {@code 012-ds-participant-binding}; HSQLDB has no partial indexes, so the test schema emulates it
     * with a generated column carrying the same constraint name.
     */
    @BeforeClass
    public static void createSystemSingletonConstraint() {
        DATABASE_CTX.doInTransaction(session -> {
            session.doWork(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE ds_participant ADD COLUMN system_singleton VARCHAR(16)"
                            + " GENERATED ALWAYS AS (CASE WHEN participant_type = 'SYSTEM' THEN 'SYSTEM' END)");
                    statement.execute("ALTER TABLE ds_participant ADD CONSTRAINT uniq_ds_participant_system_singleton"
                            + " UNIQUE (system_singleton)");
                }
            });
            return null;
        });
    }

    @AfterClass
    public static void afterClass() {
        DATABASE_CTX.destroy();
    }

    @Test
    public void persistsAndLoadsMemberParticipant() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-1");

        DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, memberParticipant(session, member));
            return null;
        });

        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(session -> dao.findByMemberIdentifier(session, member));

        assertTrue(loaded.isPresent());
        assertEquals(ParticipantType.MEMBER, loaded.get().getParticipantType());
        assertEquals(member, loaded.get().getMemberIdentifier());
        assertEquals(ParticipantIdentifierScheme.memberCtxId(member), loaded.get().getCtxId());
        assertEquals(ParticipantIdentifierScheme.memberDid(member, SS_HOST).toString(), loaded.get().getDid());
        assertEquals(ParticipantIdentifierScheme.SCHEME_VERSION, loaded.get().getSchemeVersion());
        assertEquals(ParticipantState.ACTIVE, loaded.get().getState());
    }

    @Test
    public void persistsAndLoadsSystemParticipant() {
        ensureSystemParticipant();

        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(dao::findSystemParticipant);

        assertTrue(loaded.isPresent());
        assertEquals(ParticipantType.SYSTEM, loaded.get().getParticipantType());
        assertNull(loaded.get().getMemberIdentifier());
        assertEquals(ParticipantIdentifierScheme.SYSTEM_SEGMENT, loaded.get().getCtxId());
        assertEquals(ParticipantIdentifierScheme.systemDid(SS_HOST).toString(), loaded.get().getDid());
    }

    @Test
    public void findActiveSystemParticipantReturnsAnActiveRow() {
        Optional<DsParticipantEntity> loaded = inRolledBackTransactionWithSystemRow(ParticipantState.ACTIVE);

        assertTrue(loaded.isPresent());
        assertEquals(ParticipantType.SYSTEM, loaded.get().getParticipantType());
    }

    @Test
    public void findActiveSystemParticipantIsEmptyForADecommissionedRow() {
        Optional<DsParticipantEntity> loaded = inRolledBackTransactionWithSystemRow(ParticipantState.DECOMMISSIONED);

        assertFalse(loaded.isPresent());
    }

    @Test
    public void findByMemberIdentifierIsEmptyForUnprovisionedMember() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-unprovisioned");

        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(session -> dao.findByMemberIdentifier(session, member));

        assertFalse(loaded.isPresent());
    }

    @Test
    public void rejectsDuplicateMemberIdentifier() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-dup");

        DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, memberParticipant(session, member));
            return null;
        });

        assertThrows(RuntimeException.class, () -> DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, memberParticipant(session, member));
            session.flush();
            return null;
        }));
    }

    @Test
    public void rejectsMemberRowWithoutIdentifier() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            DsParticipantEntity invalid = new DsParticipantEntity();
            invalid.setParticipantType(ParticipantType.MEMBER);
            invalid.setCtxId(XROAD_INSTANCE + ":" + MEMBER_CLASS + ":no-identifier");
            invalid.setDid("did:web:" + SS_HOST + ":v1:" + XROAD_INSTANCE + ":" + MEMBER_CLASS + ":no-identifier");
            invalid.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
            invalid.setState(ParticipantState.ACTIVE);

            var violation = assertThrows(ConstraintViolationException.class, () -> {
                session.persist(invalid);
                session.flush();
            });
            assertThat(violation.getMessage()).containsIgnoringCase("valid_participant_type_identifier");
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void rejectsSystemRowWithIdentifier() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-system-owner");

        Session session = DATABASE_CTX.beginTransaction();
        try {
            // cleared within this rolled-back transaction so only the type/identifier CHECK can fire,
            // not the SYSTEM singleton constraint
            session.createMutationQuery("delete from DsParticipantEntity where participantType = :type")
                    .setParameter("type", ParticipantType.SYSTEM)
                    .executeUpdate();

            DsParticipantEntity invalid = systemParticipant();
            invalid.setMemberIdentifier(identifierDAO.findOrCreateClientId(session, member));

            var violation = assertThrows(ConstraintViolationException.class, () -> {
                session.persist(invalid);
                session.flush();
            });
            assertThat(violation.getMessage()).containsIgnoringCase("valid_participant_type_identifier");
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void decommissionMemberFlipsExistingActiveRow() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-decom-flip");
        DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, memberParticipant(session, member));
            return null;
        });
        String originalCtxId = ParticipantIdentifierScheme.memberCtxId(member);
        String originalDid = ParticipantIdentifierScheme.memberDid(member, SS_HOST).toString();

        boolean flipped = DATABASE_CTX.doInTransaction(session -> dao.decommissionMember(session, member));

        assertTrue(flipped);
        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(session -> dao.findByMemberIdentifier(session, member));
        assertTrue(loaded.isPresent());
        assertEquals(ParticipantState.DECOMMISSIONED, loaded.get().getState());
        assertEquals(originalCtxId, loaded.get().getCtxId());
        assertEquals(originalDid, loaded.get().getDid());
    }

    @Test
    public void decommissionMemberOnAlreadyDecommissionedRowIsNoOp() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-decom-idempotent");
        DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, memberParticipant(session, member));
            return null;
        });

        boolean firstFlip = DATABASE_CTX.doInTransaction(session -> dao.decommissionMember(session, member));
        boolean secondFlip = DATABASE_CTX.doInTransaction(session -> dao.decommissionMember(session, member));

        assertTrue(firstFlip);
        assertTrue(secondFlip);
        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(session -> dao.findByMemberIdentifier(session, member));
        assertTrue(loaded.isPresent());
        assertEquals(ParticipantState.DECOMMISSIONED, loaded.get().getState());
    }

    @Test
    public void decommissionMemberOnUnboundMemberDoesNothing() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-decom-unbound");

        boolean flipped = DATABASE_CTX.doInTransaction(session -> dao.decommissionMember(session, member));

        assertFalse(flipped);
        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(session -> dao.findByMemberIdentifier(session, member));
        assertFalse(loaded.isPresent());
    }

    @Test
    public void findDecommissionedReturnsOnlyDecommissionedRows() {
        ClientId decommissionedMember = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-find-decom");
        ClientId activeMember = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-find-active");
        DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, memberParticipant(session, decommissionedMember));
            dao.save(session, memberParticipant(session, activeMember));
            return null;
        });

        DATABASE_CTX.doInTransaction(session -> dao.decommissionMember(session, decommissionedMember));
        List<DsParticipantEntity> decommissioned = DATABASE_CTX.doInTransaction(dao::findDecommissioned);

        assertTrue(decommissioned.stream().allMatch(row -> row.getState() == ParticipantState.DECOMMISSIONED));
        assertTrue(decommissioned.stream().anyMatch(row -> decommissionedMember.equals(row.getMemberIdentifier())));
        assertTrue(decommissioned.stream().noneMatch(row -> activeMember.equals(row.getMemberIdentifier())));
    }

    @Test
    public void deleteRemovesRow() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-delete");
        Long id = DATABASE_CTX.doInTransaction(session -> dao.save(session, memberParticipant(session, member)).getId());

        boolean deleted = DATABASE_CTX.doInTransaction(session -> dao.delete(session, id));

        assertTrue(deleted);
        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(session -> dao.findByMemberIdentifier(session, member));
        assertFalse(loaded.isPresent());
    }

    @Test
    public void deleteRemovesThePublicationStateTogetherWithTheRow() {
        Long id = saveMember("participant-delete-published");
        Instant at = Instant.now();
        DATABASE_CTX.doInTransaction(session -> {
            dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            dao.transitionPublication(session, id, PublicationState.SUBMITTED, PublicationState.PUBLISHED, null, at);
            return null;
        });
        assertEquals(PublicationState.PUBLISHED, loadRow(id).getPublicationState());

        boolean deleted = DATABASE_CTX.doInTransaction(session -> dao.delete(session, id));

        assertTrue(deleted);
        assertNull(loadRow(id));
    }

    @Test
    public void deleteOfAbsentRowIsNotAnError() {
        boolean deleted = DATABASE_CTX.doInTransaction(session -> dao.delete(session, Long.MAX_VALUE));

        assertFalse(deleted);
    }

    @Test
    public void rejectsSecondSystemParticipant() {
        ensureSystemParticipant();

        Session session = DATABASE_CTX.beginTransaction();
        try {
            var violation = assertThrows(ConstraintViolationException.class, () -> {
                session.persist(systemParticipant());
                session.flush();
            });
            assertThat(violation.getMessage()).containsIgnoringCase("uniq_ds_participant_system_singleton");
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void transitionExpectingNotSentAppliesToARowWithNoPublicationState() {
        Long id = saveMember("participant-publication-null");
        Instant at = Instant.parse("2026-11-03T10:15:30Z");

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at));

        assertTrue(applied);
        var row = loadRow(id);
        assertEquals(PublicationState.SUBMITTED, row.getPublicationState());
        assertNull(row.getPublicationReason());
        assertEquals(at, row.getPublicationChangedAt());
    }

    @Test
    public void aLaterFlushOfAManagedRowKeepsAPublicationTransitionMadeByTheConditionalUpdate() {
        Long id = saveMember("participant-publication-managed-row");

        DATABASE_CTX.doInTransaction(session -> {
            var managed = session.find(DsParticipantEntity.class, id);
            dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null,
                    Instant.parse("2026-11-03T10:15:30Z"));
            managed.setState(ParticipantState.DECOMMISSIONED);
            session.flush();
            return null;
        });

        var row = loadRow(id);
        assertEquals(ParticipantState.DECOMMISSIONED, row.getState());
        assertEquals(PublicationState.SUBMITTED, row.getPublicationState());
    }

    @Test
    public void transitionStoresTheReasonOfARejectedRow() {
        Long id = saveMember("participant-publication-rejected");

        DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.REJECTED, "DID_TAKEN",
                        Instant.now()));

        var row = loadRow(id);
        assertEquals(PublicationState.REJECTED, row.getPublicationState());
        assertEquals("DID_TAKEN", row.getPublicationReason());
    }

    @Test
    public void transitionExpectingNotSentAppliesToAStoredNotSentRow() {
        Long id = saveMember("participant-publication-not-sent");
        DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.NOT_SENT, "SEND_FAILURE",
                        Instant.now()));

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null,
                        Instant.now()));

        assertTrue(applied);
        assertEquals(PublicationState.SUBMITTED, loadRow(id).getPublicationState());
    }

    @Test
    public void transitionChangesNothingWhenTheRowIsNoLongerInTheExpectedState() {
        Long id = saveMember("participant-publication-lost-race");
        Instant first = Instant.parse("2026-11-03T10:15:30Z");
        DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, first));

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.REJECTED, "DID_TAKEN",
                        Instant.now()));

        assertFalse(applied);
        var row = loadRow(id);
        assertEquals(PublicationState.SUBMITTED, row.getPublicationState());
        assertNull(row.getPublicationReason());
        assertEquals(first, row.getPublicationChangedAt());
    }

    @Test
    public void transitionExpectingAnotherStateDoesNotMatchARowWithNoPublicationState() {
        Long id = saveMember("participant-publication-expected-submitted");

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.SUBMITTED, PublicationState.PUBLISHED, null,
                        Instant.now()));

        assertFalse(applied);
        assertNull(loadRow(id).getPublicationState());
    }

    @Test
    public void transitionOfAMissingRowReportsNotApplied() {
        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, Long.MAX_VALUE, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null,
                        Instant.now()));

        assertFalse(applied);
    }

    @Test
    public void transitionOfADecommissionedRowReportsNotApplied() {
        Long id = saveMember("participant-publication-decommissioned");
        DATABASE_CTX.doInTransaction(session ->
                session.createMutationQuery("update DsParticipantEntity set state = :state where id = :id")
                        .setParameter("state", ParticipantState.DECOMMISSIONED)
                        .setParameter("id", id)
                        .executeUpdate());

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.transitionPublication(session, id, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null,
                        Instant.now()));

        assertFalse(applied);
        assertNull(loadRow(id).getPublicationState());
    }

    @Test
    public void rowsWithoutPublicationColumnsSetLoadAsNotSent() {
        Long id = saveMember("participant-publication-unset");

        var row = loadRow(id);

        assertNull(row.getPublicationState());
        assertNull(row.getPublicationReason());
        assertNull(row.getPublicationChangedAt());
    }

    @Test
    public void findsOnlyActiveMemberRowsThatAreNotSent() {
        Long unset = saveMember("participant-not-sent-unset");
        Long notSent = saveMember("participant-not-sent-stored");
        Long submitted = saveMember("participant-not-sent-submitted");
        Long rejected = saveMember("participant-not-sent-rejected");
        Long decommissioned = saveMember("participant-not-sent-decommissioned");
        ensureSystemParticipant();
        Instant at = Instant.now();
        DATABASE_CTX.doInTransaction(session -> {
            dao.transitionPublication(session, notSent, PublicationState.NOT_SENT, PublicationState.NOT_SENT, "SEND_FAILURE", at);
            dao.transitionPublication(session, submitted, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            dao.transitionPublication(session, rejected, PublicationState.NOT_SENT, PublicationState.REJECTED, "DID_TAKEN", at);
            session.createMutationQuery("update DsParticipantEntity set state = :state where id = :id")
                    .setParameter("state", ParticipantState.DECOMMISSIONED)
                    .setParameter("id", decommissioned)
                    .executeUpdate();
            return null;
        });

        List<DsParticipantEntity> found = DATABASE_CTX.doInTransaction(session ->
                dao.findActiveMembers(session, Set.of(PublicationState.NOT_SENT)));

        var ids = found.stream().map(DsParticipantEntity::getId).toList();
        assertThat(ids).contains(unset, notSent).doesNotContain(submitted, rejected, decommissioned);
        assertThat(found).allMatch(row -> row.getParticipantType() == ParticipantType.MEMBER);
    }

    @Test
    public void findsOnlyActiveMemberRowsThatAreSubmittedOrPublished() {
        Long unset = saveMember("participant-sent-unset");
        Long notSent = saveMember("participant-sent-not-sent");
        Long submitted = saveMember("participant-sent-submitted");
        Long published = saveMember("participant-sent-published");
        Long rejected = saveMember("participant-sent-rejected");
        Long decommissioned = saveMember("participant-sent-decommissioned");
        ensureSystemParticipant();
        Instant at = Instant.now();
        DATABASE_CTX.doInTransaction(session -> {
            dao.transitionPublication(session, notSent, PublicationState.NOT_SENT, PublicationState.NOT_SENT, "SEND_FAILURE", at);
            dao.transitionPublication(session, submitted, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            dao.transitionPublication(session, published, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            dao.transitionPublication(session, published, PublicationState.SUBMITTED, PublicationState.PUBLISHED, null, at);
            dao.transitionPublication(session, rejected, PublicationState.NOT_SENT, PublicationState.REJECTED, "DID_TAKEN", at);
            dao.transitionPublication(session, decommissioned, PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            session.createMutationQuery("update DsParticipantEntity set state = :state where id = :id")
                    .setParameter("state", ParticipantState.DECOMMISSIONED)
                    .setParameter("id", decommissioned)
                    .executeUpdate();
            var system = dao.findSystemParticipant(session).orElseThrow();
            dao.transitionPublication(session, system.getId(), PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            return null;
        });

        List<DsParticipantEntity> found = DATABASE_CTX.doInTransaction(session ->
                dao.findActiveMembers(session, Set.of(PublicationState.SUBMITTED, PublicationState.PUBLISHED)));

        var ids = found.stream().map(DsParticipantEntity::getId).toList();
        assertThat(ids).contains(submitted, published).doesNotContain(unset, notSent, rejected, decommissioned);
        assertThat(found).allMatch(row -> row.getParticipantType() == ParticipantType.MEMBER);
    }

    @Test
    public void retireBindingsFlipsSystemAndMemberRowsAtTheOldHostInEveryPublicationState() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            Instant at = Instant.now();
            var system = dao.save(session, systemParticipant());
            var notSent = dao.save(session, memberParticipantAt(session, "retire-not-sent", SS_HOST));
            var submitted = dao.save(session, memberParticipantAt(session, "retire-submitted", SS_HOST));
            var published = dao.save(session, memberParticipantAt(session, "retire-published", SS_HOST));
            var rejected = dao.save(session, memberParticipantAt(session, "retire-rejected", SS_HOST));
            var atNewHost = dao.save(session, memberParticipantAt(session, "retire-at-new-host", NEW_HOST));
            session.flush();
            dao.transitionPublication(session, submitted.getId(), PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            dao.transitionPublication(session, published.getId(), PublicationState.NOT_SENT, PublicationState.SUBMITTED, null, at);
            dao.transitionPublication(session, published.getId(), PublicationState.SUBMITTED, PublicationState.PUBLISHED, null, at);
            dao.transitionPublication(session, rejected.getId(), PublicationState.NOT_SENT, PublicationState.REJECTED, "DID_TAKEN", at);

            int retired = retireDiffering(session, RegisteredHostMatcher.of(NEW_HOST));

            session.flush();
            session.clear();
            assertEquals(5, retired);
            for (var retiredRow : List.of(system, notSent, submitted, published, rejected)) {
                assertEquals(ParticipantState.DECOMMISSIONED, session.find(DsParticipantEntity.class, retiredRow.getId()).getState());
            }
            assertEquals(ParticipantState.ACTIVE, session.find(DsParticipantEntity.class, atNewHost.getId()).getState());
            assertEquals(PublicationState.PUBLISHED,
                    session.find(DsParticipantEntity.class, published.getId()).getPublicationState());
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void retireBindingsReturnsZeroAndLeavesEveryRowActiveWhenTheRegisteredAddressIsUnusable() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var system = dao.save(session, systemParticipant());
            var member = dao.save(session, memberParticipantAt(session, "retire-unusable-address", SS_HOST));
            session.flush();

            int retired = retireDiffering(session, RegisteredHostMatcher.of(null));

            session.flush();
            session.clear();
            assertEquals(0, retired);
            assertEquals(ParticipantState.ACTIVE, session.find(DsParticipantEntity.class, system.getId()).getState());
            assertEquals(ParticipantState.ACTIVE, session.find(DsParticipantEntity.class, member.getId()).getState());
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void retireBindingsLeavesRowsWithAnUndecodableDidOrAnUnsupportedSchemeVersionActive() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var undecodable = memberParticipantAt(session, "retire-undecodable", SS_HOST);
            undecodable.setDid("not-a-did");
            var unsupportedVersion = memberParticipantAt(session, "retire-unsupported-version", SS_HOST);
            unsupportedVersion.setSchemeVersion("v9");
            var differing = memberParticipantAt(session, "retire-differing", SS_HOST);
            var undecodableId = dao.save(session, undecodable).getId();
            var unsupportedVersionId = dao.save(session, unsupportedVersion).getId();
            var differingId = dao.save(session, differing).getId();
            session.flush();

            int retired = retireDiffering(session, RegisteredHostMatcher.of(NEW_HOST));

            session.flush();
            session.clear();
            assertEquals(1, retired);
            assertEquals(ParticipantState.DECOMMISSIONED, session.find(DsParticipantEntity.class, differingId).getState());
            assertEquals(ParticipantState.ACTIVE, session.find(DsParticipantEntity.class, undecodableId).getState());
            assertEquals(ParticipantState.ACTIVE, session.find(DsParticipantEntity.class, unsupportedVersionId).getState());
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void aSecondRetireOverRowsThatAreAlreadyDecommissionedUpdatesNothing() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var matcher = RegisteredHostMatcher.of(NEW_HOST);
            var system = dao.save(session, systemParticipant());
            var member = dao.save(session, memberParticipantAt(session, "retire-twice", SS_HOST));
            session.flush();

            var ids = dao.findDifferingBindings(session, matcher).stream().map(DsParticipantEntity::getId).toList();

            assertEquals(2, dao.retireBindings(session, ids));
            assertEquals(0, dao.retireBindings(session, ids));

            session.clear();
            assertEquals(ParticipantState.DECOMMISSIONED, session.find(DsParticipantEntity.class, system.getId()).getState());
            assertEquals(ParticipantState.DECOMMISSIONED, session.find(DsParticipantEntity.class, member.getId()).getState());
            assertFalse(dao.hasDifferingBindings(session, matcher));
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void retireBindingsFlipsEveryRowWhenTheIdListNeedsMoreThanOneUpdate() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var matcher = RegisteredHostMatcher.of(NEW_HOST);
            int rowCount = 1001;
            for (int i = 0; i < rowCount; i++) {
                dao.save(session, memberParticipantAt(session, "retire-many-" + i, SS_HOST));
            }
            session.flush();

            assertEquals(rowCount, dao.findDifferingBindings(session, matcher).size());
            assertEquals(rowCount, retireDiffering(session, matcher));

            session.clear();
            assertFalse(dao.hasDifferingBindings(session, matcher));
            assertEquals(rowCount, dao.findDecommissioned(session).size());
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void findDifferingBindingsReturnsTheActiveRowsAtAnotherHostWithTheirMemberIdentifier() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var matcher = RegisteredHostMatcher.of(NEW_HOST);
            var system = dao.save(session, systemParticipant());
            var member = dao.save(session, memberParticipantAt(session, "find-differing", SS_HOST));
            dao.save(session, memberParticipantAt(session, "find-at-new-host", NEW_HOST));
            var decommissioned = memberParticipantAt(session, "find-decommissioned", SS_HOST);
            decommissioned.setState(ParticipantState.DECOMMISSIONED);
            dao.save(session, decommissioned);
            session.flush();
            session.clear();

            var found = dao.findDifferingBindings(session, matcher);

            assertEquals(Set.of(system.getId(), member.getId()),
                    found.stream().map(DsParticipantEntity::getId).collect(Collectors.toSet()));
            assertEquals(Set.of("find-differing"), found.stream()
                    .map(DsParticipantEntity::getMemberIdentifier)
                    .filter(Objects::nonNull)
                    .map(ClientIdEntity::getMemberCode)
                    .collect(Collectors.toSet()));
            assertTrue(dao.findDifferingBindings(session, RegisteredHostMatcher.of(null)).isEmpty());
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void hasDifferingBindingsIsTrueOnlyForAnActiveRowWhoseDecodableDidHostDiffers() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var matcher = RegisteredHostMatcher.of(NEW_HOST);
            var atNewHost = dao.save(session, memberParticipantAt(session, "has-differing-at-new-host", NEW_HOST));
            var undecodable = memberParticipantAt(session, "has-differing-undecodable", SS_HOST);
            undecodable.setDid("not-a-did");
            dao.save(session, undecodable);
            var unsupportedVersion = memberParticipantAt(session, "has-differing-unsupported-version", SS_HOST);
            unsupportedVersion.setSchemeVersion("v9");
            dao.save(session, unsupportedVersion);
            var decommissionedAtOldHost = memberParticipantAt(session, "has-differing-decommissioned", SS_HOST);
            decommissionedAtOldHost.setState(ParticipantState.DECOMMISSIONED);
            dao.save(session, decommissionedAtOldHost);
            session.flush();

            assertFalse(dao.hasDifferingBindings(session, matcher));

            atNewHost.setDid(ParticipantIdentifierScheme.memberDid(
                    ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "has-differing-at-new-host"), SS_HOST).toString());
            session.flush();

            assertTrue(dao.hasDifferingBindings(session, matcher));
            assertFalse(dao.hasDifferingBindings(session, RegisteredHostMatcher.of(null)));
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    @Test
    public void retireBindingsFlipsOnlyTheGivenRowsThatAreStillActive() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            clearParticipantRows(session);
            var given = dao.save(session, memberParticipantAt(session, "retire-ids-given", SS_HOST));
            var givenButDecommissioned = memberParticipantAt(session, "retire-ids-decommissioned", SS_HOST);
            givenButDecommissioned.setState(ParticipantState.DECOMMISSIONED);
            var givenButDecommissionedId = dao.save(session, givenButDecommissioned).getId();
            var notGiven = dao.save(session, memberParticipantAt(session, "retire-ids-not-given", SS_HOST));
            session.flush();

            int retired = dao.retireBindings(session, List.of(given.getId(), givenButDecommissionedId));

            session.flush();
            session.clear();
            assertEquals(1, retired);
            assertEquals(ParticipantState.DECOMMISSIONED, session.find(DsParticipantEntity.class, given.getId()).getState());
            assertEquals(ParticipantState.ACTIVE, session.find(DsParticipantEntity.class, notGiven.getId()).getState());
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    private int retireDiffering(Session session, RegisteredHostMatcher matcher) {
        return dao.retireBindings(session,
                dao.findDifferingBindings(session, matcher).stream().map(DsParticipantEntity::getId).toList());
    }

    private void clearParticipantRows(Session session) {
        session.createMutationQuery("delete from DsParticipantEntity").executeUpdate();
    }

    private DsParticipantEntity memberParticipantAt(Session session, String memberCode, String host) {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode);
        DsParticipantEntity participant = memberParticipant(session, member);
        participant.setDid(ParticipantIdentifierScheme.memberDid(member, host).toString());
        return participant;
    }

    private Long saveMember(String memberCode) {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, memberCode);
        return DATABASE_CTX.doInTransaction(session -> dao.save(session, memberParticipant(session, member)).getId());
    }

    private DsParticipantEntity loadRow(Long id) {
        return DATABASE_CTX.doInTransaction(session -> {
            session.clear();
            return session.find(DsParticipantEntity.class, id);
        });
    }

    private DsParticipantEntity memberParticipant(Session session, ClientId member) {
        ClientIdEntity identifier = identifierDAO.findOrCreateClientId(session, member);

        DsParticipantEntity participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.MEMBER);
        participant.setMemberIdentifier(identifier);
        participant.setCtxId(ParticipantIdentifierScheme.memberCtxId(member));
        participant.setDid(ParticipantIdentifierScheme.memberDid(member, SS_HOST).toString());
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

    private Optional<DsParticipantEntity> inRolledBackTransactionWithSystemRow(ParticipantState state) {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            session.createMutationQuery("delete from DsParticipantEntity where participantType = :type")
                    .setParameter("type", ParticipantType.SYSTEM)
                    .executeUpdate();
            DsParticipantEntity participant = systemParticipant();
            participant.setState(state);
            dao.save(session, participant);
            session.flush();
            return dao.findActiveSystemParticipant(session);
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    private void ensureSystemParticipant() {
        DATABASE_CTX.doInTransaction(session -> {
            if (dao.findSystemParticipant(session).isEmpty()) {
                dao.save(session, systemParticipant());
            }
            return null;
        });
    }

    private DsParticipantEntity systemParticipant() {
        DsParticipantEntity participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.SYSTEM);
        participant.setCtxId(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
        participant.setDid(ParticipantIdentifierScheme.systemDid(SS_HOST).toString());
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

}
