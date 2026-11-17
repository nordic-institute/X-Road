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

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.niis.xroad.serverconf.model.PublicationState;

import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Decides how one globalconf check moves the publication state of a binding row, for the SYSTEM values and for a
 * member DID alike. Pure: no clock, no storage.
 *
 * <p>A submitted row that globalconf announces unchanged becomes published. Values that globalconf announces but that
 * differ from the stored ones, or that are stale, change nothing: only an absence counts as missing. A published row
 * that globalconf no longer announces, and a submitted row that stayed submitted past the cutoff while globalconf
 * announces nothing, go back to not sent so that the next reconciler pass sends them again. Both resends are behind
 * the same guard: the SYSTEM values need a registered authentication certificate, a member DID needs the member to
 * be hosted on this server. A row with no known change time is never resent by the timeout.
 */
@UtilityClass
public class PublicationCheck {

    /**
     * @param storedState     the stored publication state of the row; {@code null} counts as not sent
     * @param comparison      how what globalconf announces compares with the row; asked only for a submitted or
     *                        published row
     * @param resendAllowed   whether the row may be sent again; asked only when globalconf announces nothing and the
     *                        row is published or submitted since before the cutoff
     * @param changedAt       the time the publication state of the row last changed, {@code null} when unknown
     * @param submittedBefore the cutoff: a row submitted since before this instant has been submitted too long
     * @return the transition to apply to the row
     */
    public static PublicationCheckAction decide(@Nullable PublicationState storedState,
            Supplier<GlobalConfComparison> comparison, BooleanSupplier resendAllowed, @Nullable Instant changedAt,
            Instant submittedBefore) {
        if (!PublicationState.isSent(storedState)) {
            return PublicationCheckAction.NONE;
        }
        return switch (comparison.get()) {
            case EQUAL -> storedState == PublicationState.SUBMITTED ? PublicationCheckAction.PUBLISH
                    : PublicationCheckAction.NONE;
            case ABSENT -> whenAbsent(storedState, resendAllowed, changedAt, submittedBefore);
            case DIFFERENT, STALE -> PublicationCheckAction.NONE;
        };
    }

    private static PublicationCheckAction whenAbsent(PublicationState state, BooleanSupplier resendAllowed,
            @Nullable Instant changedAt, Instant submittedBefore) {
        if (state == PublicationState.PUBLISHED) {
            return resendAllowed.getAsBoolean() ? PublicationCheckAction.RESEND : PublicationCheckAction.NONE;
        }
        var submittedTooLong = changedAt != null && changedAt.isBefore(submittedBefore);
        return submittedTooLong && resendAllowed.getAsBoolean()
                ? PublicationCheckAction.RESEND_AFTER_TIMEOUT : PublicationCheckAction.NONE;
    }
}
