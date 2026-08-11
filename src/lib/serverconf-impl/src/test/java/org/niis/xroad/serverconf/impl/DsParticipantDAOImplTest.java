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
import org.junit.AfterClass;
import org.junit.Test;
import org.niis.xroad.common.identifiers.jpa.dao.impl.IdentifierDAOImpl;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.serverconf.impl.dao.DsParticipantDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@code ds_participant} pinning table: persistence, and the unique and
 * type/identifier consistency constraints from XRDADR-41.
 */
public class DsParticipantDAOImplTest {

    private static final String XROAD_INSTANCE = "XX";
    private static final String MEMBER_CLASS = "FooClass";
    private static final String SS_HOST = "ss0.example.org";

    private static final DatabaseCtx DATABASE_CTX = new ServerConfDatabaseCtx(TestUtil.serverConfDbProperties);

    private final DsParticipantDAOImpl dao = new DsParticipantDAOImpl();
    private final IdentifierDAOImpl identifierDAO = new IdentifierDAOImpl();

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
        assertEquals(ParticipantIdentifierScheme.memberDid(member, SS_HOST), loaded.get().getDid());
        assertEquals(ParticipantIdentifierScheme.SCHEME_VERSION, loaded.get().getSchemeVersion());
        assertEquals(ParticipantState.ACTIVE, loaded.get().getState());
    }

    @Test
    public void persistsAndLoadsSystemParticipant() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.save(session, systemParticipant());
            return null;
        });

        Optional<DsParticipantEntity> loaded = DATABASE_CTX.doInTransaction(dao::findSystemParticipant);

        assertTrue(loaded.isPresent());
        assertEquals(ParticipantType.SYSTEM, loaded.get().getParticipantType());
        assertNull(loaded.get().getMemberIdentifier());
        assertEquals(ParticipantIdentifierScheme.SYSTEM_SEGMENT, loaded.get().getCtxId());
        assertEquals(ParticipantIdentifierScheme.systemDid(SS_HOST), loaded.get().getDid());
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
        assertThrows(RuntimeException.class, () -> DATABASE_CTX.doInTransaction(session -> {
            DsParticipantEntity invalid = new DsParticipantEntity();
            invalid.setParticipantType(ParticipantType.MEMBER);
            invalid.setCtxId(XROAD_INSTANCE + ":" + MEMBER_CLASS + ":no-identifier");
            invalid.setDid("did:web:" + SS_HOST + ":v1:" + XROAD_INSTANCE + ":" + MEMBER_CLASS + ":no-identifier");
            invalid.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
            invalid.setState(ParticipantState.ACTIVE);
            session.persist(invalid);
            session.flush();
            return null;
        }));
    }

    @Test
    public void rejectsSystemRowWithIdentifier() {
        ClientId member = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, "participant-member-system-owner");

        assertThrows(RuntimeException.class, () -> DATABASE_CTX.doInTransaction(session -> {
            ClientIdEntity identifier = identifierDAO.findOrCreateClientId(session, member);

            DsParticipantEntity invalid = new DsParticipantEntity();
            invalid.setParticipantType(ParticipantType.SYSTEM);
            invalid.setMemberIdentifier(identifier);
            invalid.setCtxId(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
            invalid.setDid(ParticipantIdentifierScheme.systemDid(SS_HOST));
            invalid.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
            invalid.setState(ParticipantState.ACTIVE);
            session.persist(invalid);
            session.flush();
            return null;
        }));
    }

    private DsParticipantEntity memberParticipant(Session session, ClientId member) {
        ClientIdEntity identifier = identifierDAO.findOrCreateClientId(session, member);

        DsParticipantEntity participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.MEMBER);
        participant.setMemberIdentifier(identifier);
        participant.setCtxId(ParticipantIdentifierScheme.memberCtxId(member));
        participant.setDid(ParticipantIdentifierScheme.memberDid(member, SS_HOST));
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

    private DsParticipantEntity systemParticipant() {
        DsParticipantEntity participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.SYSTEM);
        participant.setCtxId(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
        participant.setDid(ParticipantIdentifierScheme.systemDid(SS_HOST));
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

}
