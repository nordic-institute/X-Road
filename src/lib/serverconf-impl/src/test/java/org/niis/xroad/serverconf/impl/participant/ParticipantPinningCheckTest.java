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
package org.niis.xroad.serverconf.impl.participant;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_IDENTIFIER_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED;

class ParticipantPinningCheckTest {

    private static final String SS_HOST = "ss0.example.org";
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");

    @Test
    void verifyPassesWhenMemberDerivationMatchesPinnedRow() {
        DsParticipantEntity pinned = memberParticipant(MEMBER, SS_HOST);

        assertDoesNotThrow(() -> ParticipantPinningCheck.verify(pinned, SS_HOST));
    }

    @Test
    void verifyPassesWhenSystemDerivationMatchesPinnedRow() {
        DsParticipantEntity pinned = systemParticipant(SS_HOST);

        assertDoesNotThrow(() -> ParticipantPinningCheck.verify(pinned, SS_HOST));
    }

    @Test
    void verifyThrowsWithBothValuesWhenMemberHostChanged() {
        DsParticipantEntity pinned = memberParticipant(MEMBER, SS_HOST);
        String newHost = "ss1.example.org";

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> ParticipantPinningCheck.verify(pinned, newHost));

        assertEquals(DSP_PARTICIPANT_IDENTIFIER_MISMATCH.code(), ex.getCode());
        assertEquals(List.of(
                        pinned.getCtxId(),
                        pinned.getDid(),
                        ParticipantIdentifierScheme.memberCtxId(MEMBER),
                        ParticipantIdentifierScheme.memberDid(MEMBER, newHost)),
                ex.getErrorCodeMetadata());

        // the pinned row itself is never mutated by a failed check
        assertEquals(ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST), pinned.getDid());
        assertEquals(ParticipantIdentifierScheme.memberCtxId(MEMBER), pinned.getCtxId());
    }

    @Test
    void verifyThrowsWithBothValuesWhenSystemHostChanged() {
        DsParticipantEntity pinned = systemParticipant(SS_HOST);
        String newHost = "ss1.example.org";

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> ParticipantPinningCheck.verify(pinned, newHost));

        assertEquals(DSP_PARTICIPANT_IDENTIFIER_MISMATCH.code(), ex.getCode());
        assertEquals(List.of(
                        ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                        ParticipantIdentifierScheme.systemDid(SS_HOST),
                        ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                        ParticipantIdentifierScheme.systemDid(newHost)),
                ex.getErrorCodeMetadata());

        // the pinned row itself is never mutated by a failed check
        assertEquals(ParticipantIdentifierScheme.systemDid(SS_HOST), pinned.getDid());
    }

    @Test
    void verifyThrowsWhenRowIsPinnedUnderUnsupportedSchemeVersion() {
        DsParticipantEntity pinned = memberParticipant(MEMBER, SS_HOST);
        pinned.setSchemeVersion("v2");

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> ParticipantPinningCheck.verify(pinned, SS_HOST));

        assertEquals(DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED.code(), ex.getCode());
        assertEquals(List.of("v2", ParticipantIdentifierScheme.SCHEME_VERSION), ex.getErrorCodeMetadata());

        // the pinned row itself is never mutated by a failed check
        assertEquals("v2", pinned.getSchemeVersion());
        assertEquals(ParticipantIdentifierScheme.memberCtxId(MEMBER), pinned.getCtxId());
    }

    @Test
    void verifyThrowsVersionErrorNotMismatchWhenVersionAndIdentifiersBothDiffer() {
        DsParticipantEntity pinned = memberParticipant(MEMBER, SS_HOST);
        pinned.setSchemeVersion("v2");

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> ParticipantPinningCheck.verify(pinned, "ss1.example.org"));

        assertEquals(DSP_PARTICIPANT_SCHEME_VERSION_UNSUPPORTED.code(), ex.getCode());
    }

    private static DsParticipantEntity memberParticipant(ClientId member, String ssHost) {
        DsParticipantEntity participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.MEMBER);
        participant.setMemberIdentifier(ClientIdEntityFactory.create(member));
        participant.setCtxId(ParticipantIdentifierScheme.memberCtxId(member));
        participant.setDid(ParticipantIdentifierScheme.memberDid(member, ssHost));
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

    private static DsParticipantEntity systemParticipant(String ssHost) {
        DsParticipantEntity participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.SYSTEM);
        participant.setCtxId(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
        participant.setDid(ParticipantIdentifierScheme.systemDid(ssHost));
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

}
