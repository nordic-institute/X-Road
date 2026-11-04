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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.service.PublicationOutcomeClassifier.BackoffClass;
import org.niis.xroad.securityserver.restapi.service.PublicationOutcomeClassifier.Transition;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationOutcomeClassifierTest {

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of(SystemDidRegistrationSendOutcome.stored(7),
                        new Transition(PublicationState.SUBMITTED, null, BackoffClass.NONE)),
                Arguments.of(SystemDidRegistrationSendOutcome.finalConflict("DID_TAKEN"),
                        new Transition(PublicationState.REJECTED, "DID_TAKEN", BackoffClass.NONE)),
                Arguments.of(SystemDidRegistrationSendOutcome.finalConflict("INVALID_DID"),
                        new Transition(PublicationState.REJECTED, "INVALID_DID", BackoffClass.NONE)),
                Arguments.of(SystemDidRegistrationSendOutcome.fixableConflict("ADDRESS_MISMATCH"),
                        new Transition(PublicationState.NOT_SENT, "ADDRESS_MISMATCH", BackoffClass.FIXABLE_CONFLICT)),
                Arguments.of(SystemDidRegistrationSendOutcome.notResolved("DID_UNRESOLVABLE"),
                        new Transition(PublicationState.NOT_SENT, "DID_UNRESOLVABLE", BackoffClass.NOT_RESOLVED)),
                Arguments.of(SystemDidRegistrationSendOutcome.sendFailure(new IllegalStateException("refused")),
                        new Transition(PublicationState.NOT_SENT, "SEND_FAILURE: IllegalStateException",
                                BackoffClass.SEND_FAILURE)),
                Arguments.of(SystemDidRegistrationSendOutcome.sendFailure(new TimeoutException()),
                        new Transition(PublicationState.NOT_SENT, "SEND_FAILURE: timeout", BackoffClass.SEND_FAILURE)),
                Arguments.of(SystemDidRegistrationSendOutcome.sendFailure(
                                XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_OUTDATED).build()),
                        new Transition(PublicationState.NOT_SENT, "SEND_FAILURE: " + ErrorCode.GLOBAL_CONF_OUTDATED.code(),
                                BackoffClass.SEND_FAILURE)));
    }

    @ParameterizedTest
    @MethodSource("outcomes")
    void mapsEachOutcomeToItsTransition(SystemDidRegistrationSendOutcome outcome, Transition expected) {
        assertThat(PublicationOutcomeClassifier.classify(outcome)).isEqualTo(expected);
    }

    @Test
    void aReasonLongerThanTheColumnIsCutAndTheStateIsKept() {
        var tooLong = "x".repeat(DsParticipantEntity.PUBLICATION_REASON_MAX_LENGTH + 500);

        var rejected = PublicationOutcomeClassifier.classify(SystemDidRegistrationSendOutcome.finalConflict(tooLong));
        var notSent = PublicationOutcomeClassifier.classify(SystemDidRegistrationSendOutcome.fixableConflict(tooLong));

        assertThat(rejected.state()).isEqualTo(PublicationState.REJECTED);
        assertThat(rejected.reason()).isEqualTo("x".repeat(DsParticipantEntity.PUBLICATION_REASON_MAX_LENGTH));
        assertThat(notSent.state()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(notSent.reason()).hasSize(DsParticipantEntity.PUBLICATION_REASON_MAX_LENGTH);
    }

    @Test
    void aCutNeverSplitsASurrogatePair() {
        var max = DsParticipantEntity.PUBLICATION_REASON_MAX_LENGTH;
        var reason = "x".repeat(max - 1) + "\uD83D\uDE00";

        var transition = PublicationOutcomeClassifier.classify(SystemDidRegistrationSendOutcome.finalConflict(reason));

        assertThat(transition.reason()).isEqualTo("x".repeat(max - 1));
    }

    @Test
    void aSendFailureReasonNeverCarriesTheExceptionMessage() {
        var first = PublicationOutcomeClassifier.classify(
                SystemDidRegistrationSendOutcome.sendFailure(new IllegalStateException("attempt at 10:00:01")));
        var second = PublicationOutcomeClassifier.classify(
                SystemDidRegistrationSendOutcome.sendFailure(new IllegalStateException("attempt at 10:00:31")));

        assertThat(first.reason()).isEqualTo(second.reason());
        assertThat(first.reason()).doesNotContain("10:00");
    }

    @Test
    void everySendOutcomeStatusIsClassified() {
        var classified = outcomes().map(arguments -> ((SystemDidRegistrationSendOutcome) arguments.get()[0]).status())
                .distinct().toList();

        assertThat(classified).containsExactlyInAnyOrder(SystemDidRegistrationSendOutcome.Status.values());
    }

    @Test
    void aTransitionToTheStoredStateAndReasonChangesNothing() {
        var stayNotSent = new Transition(PublicationState.NOT_SENT, "SEND_FAILURE", BackoffClass.SEND_FAILURE);

        assertThat(stayNotSent.changesNothing(PublicationState.NOT_SENT, "SEND_FAILURE")).isTrue();
    }

    @Test
    void aNullStoredStateCountsAsNotSent() {
        var stayNotSentWithoutReason = new Transition(PublicationState.NOT_SENT, null, BackoffClass.NOT_RESOLVED);
        var stayNotSentWithReason = new Transition(PublicationState.NOT_SENT, "SEND_FAILURE", BackoffClass.SEND_FAILURE);

        assertThat(stayNotSentWithoutReason.changesNothing(null, null)).isTrue();
        assertThat(stayNotSentWithReason.changesNothing(null, null)).isFalse();
    }

    @Test
    void aNewReasonOrANewStateIsAChange() {
        var stayNotSent = new Transition(PublicationState.NOT_SENT, "SEND_FAILURE", BackoffClass.SEND_FAILURE);
        var submitted = new Transition(PublicationState.SUBMITTED, null, BackoffClass.NONE);

        assertThat(stayNotSent.changesNothing(PublicationState.NOT_SENT, "ADDRESS_MISMATCH")).isFalse();
        assertThat(submitted.changesNothing(PublicationState.NOT_SENT, null)).isFalse();
        assertThat(submitted.changesNothing(PublicationState.SUBMITTED, null)).isTrue();
    }
}
