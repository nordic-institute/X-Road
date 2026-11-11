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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.serverconf.model.PublicationState;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.ABSENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.EQUAL;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.NONE;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.PUBLISH;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.RESEND;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.RESEND_AFTER_TIMEOUT;

class PublicationCheckTest {

    private static final Instant SUBMITTED_BEFORE = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant LONG_AGO = SUBMITTED_BEFORE.minus(Duration.ofDays(1));
    private static final BooleanSupplier ALLOWED = () -> true;
    private static final BooleanSupplier NOT_ALLOWED = () -> false;
    private static final BooleanSupplier MUST_NOT_BE_ASKED = () -> {
        throw new AssertionError("the resend guard must not be asked");
    };

    private static PublicationCheckAction decide(PublicationState state, GlobalConfComparison comparison,
            BooleanSupplier resendAllowed, Instant changedAt) {
        return PublicationCheck.decide(state, () -> comparison, resendAllowed, changedAt, SUBMITTED_BEFORE);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", textBlock = """
            SUBMITTED, EQUAL,     PUBLISH
            SUBMITTED, ABSENT,    RESEND_AFTER_TIMEOUT
            SUBMITTED, DIFFERENT, NONE
            SUBMITTED, STALE,     NONE
            PUBLISHED, EQUAL,     NONE
            PUBLISHED, ABSENT,    RESEND
            PUBLISHED, DIFFERENT, NONE
            PUBLISHED, STALE,     NONE
            NOT_SENT,  EQUAL,     NONE
            NOT_SENT,  ABSENT,    NONE
            NOT_SENT,  DIFFERENT, NONE
            NOT_SENT,  STALE,     NONE
            REJECTED,  EQUAL,     NONE
            REJECTED,  ABSENT,    NONE
            REJECTED,  DIFFERENT, NONE
            REJECTED,  STALE,     NONE
            NULL,      EQUAL,     NONE
            NULL,      ABSENT,    NONE
            NULL,      DIFFERENT, NONE
            NULL,      STALE,     NONE
            """)
    void everyStateAndComparisonMapsToOneAction(PublicationState state, GlobalConfComparison comparison,
            PublicationCheckAction expected) {
        assertThat(decide(state, comparison, ALLOWED, LONG_AGO)).isEqualTo(expected);
    }

    @Test
    void aSubmittedRowMissingFromGlobalconfStaysSubmittedUpToTheCutoffAndIsSentAgainAfterIt() {
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, ALLOWED, SUBMITTED_BEFORE.plusSeconds(1)))
                .isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, ALLOWED, SUBMITTED_BEFORE)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, ALLOWED, SUBMITTED_BEFORE.minusSeconds(1)))
                .isEqualTo(RESEND_AFTER_TIMEOUT);
    }

    @Test
    void aSubmittedRowWithUnknownChangeTimeIsNeverSentAgainByTheTimeout() {
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, ALLOWED, null)).isEqualTo(NONE);
    }

    @Test
    void aPublishedRowIsSentAgainWhateverItsChangeTime() {
        assertThat(decide(PublicationState.PUBLISHED, ABSENT, ALLOWED, null)).isEqualTo(RESEND);
        assertThat(decide(PublicationState.PUBLISHED, ABSENT, ALLOWED, SUBMITTED_BEFORE.plusSeconds(1)))
                .isEqualTo(RESEND);
    }

    @Test
    void aPublishedRowIsNeverSentAgainWhenTheResendGuardIsFalse() {
        assertThat(decide(PublicationState.PUBLISHED, ABSENT, NOT_ALLOWED, LONG_AGO)).isEqualTo(NONE);
    }

    @Test
    void aSubmittedRowIsNeverSentAgainByTheTimeoutWhenTheResendGuardIsFalse() {
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, NOT_ALLOWED, LONG_AGO)).isEqualTo(NONE);
    }

    @Test
    void aSubmittedRowIsStillPublishedWhenTheResendGuardIsFalse() {
        assertThat(decide(PublicationState.SUBMITTED, EQUAL, NOT_ALLOWED, LONG_AGO)).isEqualTo(PUBLISH);
    }

    @Test
    void theResendGuardIsAskedOnlyWhenItCanChangeTheDecision() {
        assertThat(decide(PublicationState.PUBLISHED, EQUAL, MUST_NOT_BE_ASKED, LONG_AGO)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, EQUAL, MUST_NOT_BE_ASKED, LONG_AGO)).isEqualTo(PUBLISH);
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, MUST_NOT_BE_ASKED, SUBMITTED_BEFORE)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, MUST_NOT_BE_ASKED, null)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, GlobalConfComparison.DIFFERENT, MUST_NOT_BE_ASKED, LONG_AGO))
                .isEqualTo(NONE);
        assertThat(decide(PublicationState.PUBLISHED, GlobalConfComparison.STALE, MUST_NOT_BE_ASKED, LONG_AGO))
                .isEqualTo(NONE);
        assertThat(decide(PublicationState.NOT_SENT, ABSENT, MUST_NOT_BE_ASKED, LONG_AGO)).isEqualTo(NONE);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void theComparisonIsBuiltOnlyForASubmittedOrPublishedRow(PublicationState state) {
        Supplier<GlobalConfComparison> mustNotBeBuilt = () -> {
            throw new AssertionError("the comparison must not be built");
        };

        assertThat(PublicationCheck.decide(state, mustNotBeBuilt, ALLOWED, LONG_AGO, SUBMITTED_BEFORE))
                .isEqualTo(NONE);
        assertThat(PublicationCheck.decide(null, mustNotBeBuilt, ALLOWED, LONG_AGO, SUBMITTED_BEFORE))
                .isEqualTo(NONE);
    }

    @Test
    void aSubmittedSystemValueStaysSubmittedWhileTheDspBaseUrlCannotBeBuiltAndGlobalconfCarriesValues() {
        var carried = new ServerSystemValues("did:web:ss.example.test%3A7183:xrd-ss0",
                "https://ss.example.test:7181/dsp");
        var comparison = SystemValuesComparison.of(carried.systemDid(), null, carried, _ -> false);

        assertThat(decide(PublicationState.SUBMITTED, comparison.overall(), ALLOWED, LONG_AGO)).isEqualTo(NONE);
    }
}
