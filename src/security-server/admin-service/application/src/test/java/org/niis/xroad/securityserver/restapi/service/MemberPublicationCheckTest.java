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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.serverconf.model.PublicationState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.NONE;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.PUBLISH;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.RESEND;

class MemberPublicationCheckTest {

    private static final String DID = "did:web:ss.example.test%3A7183:xrd-ss0-m01";
    private static final String OTHER_DID = "did:web:other.example.test%3A7183:xrd-ss0-m01";

    @Test
    void aSubmittedDidThatGlobalconfCarriesUnchangedIsPublished() {
        assertThat(MemberPublicationCheck.decide(PublicationState.SUBMITTED, DID, DID, true)).isEqualTo(PUBLISH);
    }

    @Test
    void aPublishedDidMissingFromGlobalconfWhileHostedIsSentAgain() {
        assertThat(MemberPublicationCheck.decide(PublicationState.PUBLISHED, DID, null, true)).isEqualTo(RESEND);
    }

    @Test
    void aPublishedDidIsNeverResetWhenTheMemberIsNoLongerHostedHere() {
        assertThat(MemberPublicationCheck.decide(PublicationState.PUBLISHED, DID, null, false)).isEqualTo(NONE);
    }

    @Test
    void aDifferentDidIsNeverTreatedAsMissingNorPublishesASubmittedDid() {
        assertThat(MemberPublicationCheck.decide(PublicationState.PUBLISHED, DID, OTHER_DID, true)).isEqualTo(NONE);
        assertThat(MemberPublicationCheck.decide(PublicationState.SUBMITTED, DID, OTHER_DID, true)).isEqualTo(NONE);
    }

    @Test
    void aPublishedDidThatGlobalconfCarriesUnchangedIsLeftAlone() {
        assertThat(MemberPublicationCheck.decide(PublicationState.PUBLISHED, DID, DID, true)).isEqualTo(NONE);
    }

    @Test
    void aSubmittedDidThatGlobalconfDoesNotCarryIsLeftAlone() {
        assertThat(MemberPublicationCheck.decide(PublicationState.SUBMITTED, DID, null, true)).isEqualTo(NONE);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void aNotSentOrRejectedDidIsNeverTouched(PublicationState state) {
        assertThat(MemberPublicationCheck.decide(state, DID, DID, true)).isEqualTo(NONE);
        assertThat(MemberPublicationCheck.decide(state, DID, OTHER_DID, true)).isEqualTo(NONE);
        assertThat(MemberPublicationCheck.decide(state, DID, null, true)).isEqualTo(NONE);
    }

    @Test
    void aDidWithNoStoredStateIsNotSentAndNeverTouched() {
        assertThat(MemberPublicationCheck.decide(null, DID, DID, true)).isEqualTo(NONE);
        assertThat(MemberPublicationCheck.decide(null, DID, null, true)).isEqualTo(NONE);
    }
}
