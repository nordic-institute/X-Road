/*
 * The MIT License
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
package org.niis.xroad.ds.identity;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantIdentifierSchemeTest {

    private static final String SS_HOST = "ss0.example.org";

    @Test
    void shouldMatchAdrWorkedExample() {
        var member = ClientId.Conf.create("DEV", "COM", "222");

        assertThat(ParticipantIdentifierScheme.memberCtxId(member)).isEqualTo("DEV:COM:222");
        assertThat(ParticipantIdentifierScheme.memberDid(member, SS_HOST))
                .isEqualTo("did:web:ss0.example.org:v1:DEV:COM:222");
    }

    @Test
    void shouldRoundTripMemberCtxId() {
        var member = ClientId.Conf.create("DEV", "COM", "222");

        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var decoded = ParticipantIdentifierScheme.decodeMemberCtxId(ctxId);

        assertThat(decoded.getXRoadInstance()).isEqualTo(member.getXRoadInstance());
        assertThat(decoded.getMemberClass()).isEqualTo(member.getMemberClass());
        assertThat(decoded.getMemberCode()).isEqualTo(member.getMemberCode());
    }

    @Test
    void shouldRoundTripMemberDid() {
        var member = ClientId.Conf.create("DEV", "COM", "222");

        var did = ParticipantIdentifierScheme.memberDid(member, SS_HOST);
        var decoded = ParticipantIdentifierScheme.decodeDid(did);

        assertThat(decoded).isInstanceOfSatisfying(MemberParticipant.class, participant -> {
            assertThat(participant.ssHost()).isEqualTo(SS_HOST);
            assertThat(participant.member().getXRoadInstance()).isEqualTo(member.getXRoadInstance());
            assertThat(participant.member().getMemberClass()).isEqualTo(member.getMemberClass());
            assertThat(participant.member().getMemberCode()).isEqualTo(member.getMemberCode());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"'", "(", ")", "+", ",", "=", "?"})
    void shouldRoundTripLegalSpecialCharactersInMemberCode(String specialChar) {
        var member = ClientId.Conf.create("DEV", "COM", "222" + specialChar + "A");

        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var decodedFromCtxId = ParticipantIdentifierScheme.decodeMemberCtxId(ctxId);
        assertThat(decodedFromCtxId.getMemberCode()).isEqualTo(member.getMemberCode());

        var did = ParticipantIdentifierScheme.memberDid(member, SS_HOST);
        var decodedFromDid = ParticipantIdentifierScheme.decodeDid(did);
        assertThat(decodedFromDid).isInstanceOfSatisfying(MemberParticipant.class,
                participant -> assertThat(participant.member().getMemberCode()).isEqualTo(member.getMemberCode()));
    }

    @Test
    void shouldNotCollapseDistinctSpecialCharacterCodes() {
        // '222+A' and '222,A' both normalise to 'DEV-com-222-a' style slugs; percent-encoding keeps them distinct.
        var plus = ClientId.Conf.create("DEV", "COM", "222+A");
        var comma = ClientId.Conf.create("DEV", "COM", "222,A");

        assertThat(ParticipantIdentifierScheme.memberCtxId(plus))
                .isNotEqualTo(ParticipantIdentifierScheme.memberCtxId(comma));
    }

    @Test
    void shouldPreserveCase() {
        var member = ClientId.Conf.create("DeV-Inst", "CoM-class", "MiXeDcAsE222");

        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var decoded = ParticipantIdentifierScheme.decodeMemberCtxId(ctxId);

        assertThat(decoded.getXRoadInstance()).isEqualTo("DeV-Inst");
        assertThat(decoded.getMemberClass()).isEqualTo("CoM-class");
        assertThat(decoded.getMemberCode()).isEqualTo("MiXeDcAsE222");
    }

    @Test
    void shouldRoundTripNonAsciiUtf8MemberCode() {
        var member = ClientId.Conf.create("DEV", "COM", "Ääriston-Оператор-企業");

        var ctxId = ParticipantIdentifierScheme.memberCtxId(member);
        var decoded = ParticipantIdentifierScheme.decodeMemberCtxId(ctxId);

        assertThat(decoded.getMemberCode()).isEqualTo(member.getMemberCode());
    }

    @Test
    void shouldRoundTripHostWithPort() {
        var member = ClientId.Conf.create("DEV", "COM", "222");
        var hostWithPort = "ss0.example.org:7183";

        var did = ParticipantIdentifierScheme.memberDid(member, hostWithPort);
        assertThat(did).isEqualTo("did:web:ss0.example.org%3A7183:v1:DEV:COM:222");

        var decoded = ParticipantIdentifierScheme.decodeDid(did);
        assertThat(decoded.ssHost()).isEqualTo(hostWithPort);
    }

    @Test
    void shouldDeriveSystemIdentifiers() {
        assertThat(ParticipantIdentifierScheme.SYSTEM_SEGMENT).isEqualTo("system");
        assertThat(ParticipantIdentifierScheme.systemDid(SS_HOST)).isEqualTo("did:web:ss0.example.org:v1:system");
    }

    @Test
    void shouldRoundTripSystemDid() {
        var did = ParticipantIdentifierScheme.systemDid(SS_HOST);

        var decoded = ParticipantIdentifierScheme.decodeDid(did);

        assertThat(decoded).isInstanceOfSatisfying(SystemParticipant.class,
                participant -> assertThat(participant.ssHost()).isEqualTo(SS_HOST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"system", "SYSTEM", "sys%74em", "222"})
    void noMemberDerivedIdentifierCanEqualSystemForms(String memberCode) {
        var member = ClientId.Conf.create("DEV", "COM", memberCode);

        assertThat(ParticipantIdentifierScheme.memberCtxId(member))
                .isNotEqualTo(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
        assertThat(ParticipantIdentifierScheme.memberDid(member, SS_HOST))
                .isNotEqualTo(ParticipantIdentifierScheme.systemDid(SS_HOST));
    }

    @Test
    void decodingSystemCtxIdAsMemberCtxIdShouldFail() {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId(ParticipantIdentifierScheme.SYSTEM_SEGMENT))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DEV:COM",
            "DEV:COM:222:EXTRA",
            "",
    })
    void decodeMemberCtxIdShouldRejectWrongSegmentCount(String malformedCtxId) {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId(malformedCtxId))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void decodeMemberCtxIdShouldRejectBlankSegments() {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId("DEV::222"))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void decodeMemberCtxIdShouldRejectBadPercentEscapes() {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId("DEV:COM:222%2"))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId("DEV:COM:222%ZZ"))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId("DEV:COM:222+A"))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DEV:COM:%C0%80",
            "DEV:COM:%80",
            "DEV:COM:%C3",
            "DEV:COM:%E2%82",
            "DEV:COM:%ED%A0%80",
            "DEV:COM:2%FF2"
    })
    void decodeMemberCtxIdShouldRejectMalformedUtf8ByteSequences(String malformedCtxId) {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeMemberCtxId(malformedCtxId))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("invalid UTF-8");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http:web:ss0.example.org:v1:DEV:COM:222",
            "did:ftp:ss0.example.org:v1:DEV:COM:222",
    })
    void decodeDidShouldRejectWrongSchemePrefix(String malformedDid) {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeDid(malformedDid))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void decodeDidShouldRejectWrongVersionPrefix() {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeDid("did:web:ss0.example.org:v2:DEV:COM:222"))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "did:web:ss0.example.org:v1:DEV:COM",
            "did:web:ss0.example.org:v1:DEV:COM:222:EXTRA",
            "did:web:ss0.example.org:v1",
    })
    void decodeDidShouldRejectWrongSegmentCount(String malformedDid) {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeDid(malformedDid))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void decodeDidShouldRejectBadHostPercentEscapes() {
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeDid("did:web:ss0.example.org%ZZ:v1:DEV:COM:222"))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> ParticipantIdentifierScheme.decodeDid("did:web:ss0.example.org%3:v1:DEV:COM:222"))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void encodingShouldRejectSubsystemIdentifiers() {
        var subsystem = ClientId.Conf.create("DEV", "COM", "222", "SUB");

        assertThatThrownBy(() -> ParticipantIdentifierScheme.memberCtxId(subsystem))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> ParticipantIdentifierScheme.memberDid(subsystem, SS_HOST))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void encodingShouldRejectBlankHost() {
        var member = ClientId.Conf.create("DEV", "COM", "222");

        assertThatThrownBy(() -> ParticipantIdentifierScheme.memberDid(member, ""))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> ParticipantIdentifierScheme.systemDid(null))
                .isInstanceOf(XrdRuntimeException.class);
    }
}
