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

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Maps the outcome of sending a value to the Central Server to the publication transition to apply.
 * Pure: no clock, no storage. A send failure carries a stable description of its cause, never the exception
 * message, so a repeated failure yields the same reason.
 */
public final class PublicationOutcomeClassifier {

    static final String SEND_FAILURE_REASON = "SEND_FAILURE";

    private PublicationOutcomeClassifier() {
    }

    /**
     * How soon a value that stays not sent may be tried again.
     */
    public enum BackoffClass {
        NONE,
        SEND_FAILURE,
        FIXABLE_CONFLICT,
        NOT_RESOLVED
    }

    /**
     * @param state   the state to move the value to
     * @param reason  the reason for the state, {@code null} when there is none
     * @param backoff the backoff class of a value that stays {@link PublicationState#NOT_SENT}, otherwise {@code NONE}
     */
    public record Transition(PublicationState state, String reason, BackoffClass backoff) {

        /**
         * Whether applying this transition to a value in the given stored state and reason would change nothing.
         * A stored state of {@code null} counts as {@link PublicationState#NOT_SENT}.
         */
        public boolean changesNothing(PublicationState storedState, String storedReason) {
            var current = storedState == null ? PublicationState.NOT_SENT : storedState;
            return current == state && Objects.equals(storedReason, reason);
        }
    }

    /**
     * @param outcome the outcome of sending the SYSTEM values
     * @return the transition to apply to the SYSTEM row; a reason longer than the stored column is cut to fit,
     * so a repeated outcome carries the same reason as the stored one
     */
    public static Transition classify(SystemDidRegistrationSendOutcome outcome) {
        return switch (outcome.status()) {
            case STORED -> new Transition(PublicationState.SUBMITTED, null, BackoffClass.NONE);
            case FINAL_CONFLICT ->
                    new Transition(PublicationState.REJECTED, boundedReason(outcome.reason()), BackoffClass.NONE);
            case FIXABLE_CONFLICT ->
                    new Transition(PublicationState.NOT_SENT, boundedReason(outcome.reason()), BackoffClass.FIXABLE_CONFLICT);
            case NOT_RESOLVED ->
                    new Transition(PublicationState.NOT_SENT, boundedReason(outcome.reason()), BackoffClass.NOT_RESOLVED);
            case SEND_FAILURE ->
                    new Transition(PublicationState.NOT_SENT, sendFailureReason(outcome.cause()), BackoffClass.SEND_FAILURE);
        };
    }

    private static String sendFailureReason(Throwable cause) {
        return switch (cause) {
            case null -> SEND_FAILURE_REASON;
            case XrdRuntimeException xrd -> SEND_FAILURE_REASON + ": " + xrd.getCode();
            case TimeoutException _ -> SEND_FAILURE_REASON + ": timeout";
            default -> SEND_FAILURE_REASON + ": " + cause.getClass().getSimpleName();
        };
    }

    private static String boundedReason(String reason) {
        var max = DsParticipantEntity.PUBLICATION_REASON_MAX_LENGTH;
        if (reason == null || reason.length() <= max) {
            return reason;
        }
        var end = Character.isHighSurrogate(reason.charAt(max - 1)) ? max - 1 : max;
        return reason.substring(0, end);
    }
}
