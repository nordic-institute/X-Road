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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionOutcome;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_OF_OTHER_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_TOO_LONG;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DSP_BASE_URL_NOT_HTTPS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.FOREIGN_SYSTEM_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MALFORMED_DSP_BASE_URL;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MEMBER_NOT_SERVED;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NON_ASCII_HOST;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NOT_CANONICAL;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNDECODABLE_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNPARSEABLE_REGISTERED_ADDRESS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.WRONG_SCHEME;

class MemberDidRegistrationOutcomeTest {
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "111");
    private static final String ADDRESS = "ss.example.org";
    private static final String NON_ASCII_ADDRESS = "ss.exämple.org";

    private static final Set<ParticipantValueRejection> FINAL_REASONS =
            EnumSet.of(WRONG_SCHEME, DID_TOO_LONG, NOT_CANONICAL, UNDECODABLE_DID, DID_OF_OTHER_MEMBER);
    private static final Set<ParticipantValueRejection> FIXABLE_REASONS =
            EnumSet.of(HOST_DIFFERS, UNPARSEABLE_REGISTERED_ADDRESS, DID_HELD_BY_ANOTHER_SERVER, VALUE_DIFFERS, MEMBER_NOT_SERVED);
    private static final Set<ParticipantValueRejection> ADDRESS_DEPENDENT_REASONS = EnumSet.of(NON_ASCII_HOST);
    private static final Set<ParticipantValueRejection> UNREACHABLE_REASONS =
            EnumSet.of(DSP_BASE_URL_NOT_HTTPS, MALFORMED_DSP_BASE_URL, FOREIGN_SYSTEM_DID);

    @Test
    void everyRejectionIsInExactlyOneGroup() {
        var groups = List.of(FINAL_REASONS, FIXABLE_REASONS, ADDRESS_DEPENDENT_REASONS, UNREACHABLE_REASONS);

        var seen = EnumSet.noneOf(ParticipantValueRejection.class);
        for (var group : groups) {
            assertThat(seen).doesNotContainAnyElementsOf(group);
            seen.addAll(group);
        }
        assertThat(seen).isEqualTo(EnumSet.allOf(ParticipantValueRejection.class));
    }

    @Test
    void theUnreachableGroupIsTheSetTheClassificationRefuses() {
        assertThat(MemberDidRegistrationOutcome.SYSTEM_PAIR_ONLY).isEqualTo(UNREACHABLE_REASONS);
    }

    @ParameterizedTest
    @EnumSource(ParticipantValueRejection.class)
    void aRejectionMapsToItsGroupClass(ParticipantValueRejection rejection) {
        if (UNREACHABLE_REASONS.contains(rejection)) {
            assertThatThrownBy(() -> MemberDidRegistrationOutcome.rejected(MEMBER, rejection, ADDRESS))
                    .isInstanceOf(IllegalArgumentException.class);
        } else if (ADDRESS_DEPENDENT_REASONS.contains(rejection)) {
            assertThat(MemberDidRegistrationOutcome.rejected(MEMBER, rejection, ADDRESS))
                    .isEqualTo(new Result(MEMBER, Status.FINAL_CONFLICT, rejection.name()));
            assertThat(MemberDidRegistrationOutcome.rejected(MEMBER, rejection, NON_ASCII_ADDRESS))
                    .isEqualTo(new Result(MEMBER, Status.FIXABLE_CONFLICT, rejection.name()));
        } else {
            var expected = FINAL_REASONS.contains(rejection) ? Status.FINAL_CONFLICT : Status.FIXABLE_CONFLICT;
            assertThat(MemberDidRegistrationOutcome.rejected(MEMBER, rejection, ADDRESS))
                    .isEqualTo(new Result(MEMBER, expected, rejection.name()));
        }
    }

    @ParameterizedTest
    @EnumSource(DidResolutionReason.class)
    void everyResolverReasonIsNotResolvedWithThatReason(DidResolutionReason reason) {
        var result = MemberDidRegistrationOutcome.notResolved(MEMBER, DidResolution.notResolved("did:web:x", reason));

        assertThat(result).isEqualTo(new Result(MEMBER, Status.NOT_RESOLVED, reason.name()));
    }

    @ParameterizedTest
    @EnumSource(DidResolutionOutcome.class)
    void aResolverOutcomeIsAnAnswerOnlyWhenNotResolved(DidResolutionOutcome outcome) {
        var resolution = new DidResolution("did:web:x", outcome, null);

        if (outcome == DidResolutionOutcome.NOT_RESOLVED) {
            assertThat(MemberDidRegistrationOutcome.notResolved(MEMBER, resolution))
                    .isEqualTo(new Result(MEMBER, Status.NOT_RESOLVED, SystemDidRegistrationOutcome.RESOLVER_UNAVAILABLE));
        } else {
            assertThatThrownBy(() -> MemberDidRegistrationOutcome.notResolved(MEMBER, resolution))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void serviceLevelReasonsEachMapToOneClass() {
        assertThat(MemberDidRegistrationOutcome.conflictingDuplicate(MEMBER))
                .isEqualTo(new Result(MEMBER, Status.FINAL_CONFLICT, MemberDidRegistrationOutcome.CONFLICTING_DUPLICATE));
        assertThat(MemberDidRegistrationOutcome.systemValuesNotStored(MEMBER))
                .isEqualTo(new Result(MEMBER, Status.FIXABLE_CONFLICT, MemberDidRegistrationOutcome.SYSTEM_VALUES_NOT_STORED));
        assertThat(MemberDidRegistrationOutcome.ownerChanged(MEMBER))
                .isEqualTo(new Result(MEMBER, Status.FIXABLE_CONFLICT, SystemDidRegistrationOutcome.OWNER_CHANGED));
        assertThat(MemberDidRegistrationOutcome.resolverUnavailable(MEMBER))
                .isEqualTo(new Result(MEMBER, Status.NOT_RESOLVED, SystemDidRegistrationOutcome.RESOLVER_UNAVAILABLE));
        assertThat(MemberDidRegistrationOutcome.stored(MEMBER)).isEqualTo(new Result(MEMBER, Status.STORED, null));
    }
}
