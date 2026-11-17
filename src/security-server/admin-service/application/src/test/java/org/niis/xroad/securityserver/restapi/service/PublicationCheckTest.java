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
import org.junit.jupiter.params.provider.NullSource;
import org.niis.xroad.serverconf.model.PublicationState;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.ABSENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.DIFFERENT;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.EQUAL;
import static org.niis.xroad.securityserver.restapi.service.GlobalConfComparison.STALE;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.NONE;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.PUBLISH;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.RESEND;
import static org.niis.xroad.securityserver.restapi.service.PublicationCheckAction.RESEND_AFTER_TIMEOUT;

class PublicationCheckTest {

    private static final Instant SUBMITTED_BEFORE = Instant.parse("2026-11-10T11:00:00Z");
    private static final Instant LONG_AGO = SUBMITTED_BEFORE.minus(Duration.ofDays(1));
    private static final BooleanSupplier ALLOWED = () -> true;
    private static final BooleanSupplier MUST_NOT_BE_ASKED = () -> {
        throw new AssertionError("the resend guard must not be asked");
    };

    private static PublicationCheckAction decide(PublicationState state, GlobalConfComparison comparison,
            BooleanSupplier resendAllowed, Instant changedAt) {
        return PublicationCheck.decide(state, () -> comparison, resendAllowed, changedAt, SUBMITTED_BEFORE);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            SUBMITTED, EQUAL,     true,  PUBLISH
            SUBMITTED, EQUAL,     false, PUBLISH
            SUBMITTED, ABSENT,    true,  RESEND_AFTER_TIMEOUT
            SUBMITTED, ABSENT,    false, NONE
            SUBMITTED, DIFFERENT, true,  NONE
            SUBMITTED, STALE,     true,  NONE
            PUBLISHED, EQUAL,     true,  NONE
            PUBLISHED, ABSENT,    true,  RESEND
            PUBLISHED, ABSENT,    false, NONE
            PUBLISHED, DIFFERENT, true,  NONE
            PUBLISHED, STALE,     true,  NONE
            """)
    void everyStateAndComparisonMapsToOneAction(PublicationState state, GlobalConfComparison comparison,
            boolean resendAllowed, PublicationCheckAction expected) {
        assertThat(decide(state, comparison, () -> resendAllowed, LONG_AGO)).isEqualTo(expected);
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
    void theResendGuardIsAskedOnlyWhenItCanChangeTheDecision() {
        assertThat(decide(PublicationState.PUBLISHED, EQUAL, MUST_NOT_BE_ASKED, LONG_AGO)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, EQUAL, MUST_NOT_BE_ASKED, LONG_AGO)).isEqualTo(PUBLISH);
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, MUST_NOT_BE_ASKED, SUBMITTED_BEFORE)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, ABSENT, MUST_NOT_BE_ASKED, null)).isEqualTo(NONE);
        assertThat(decide(PublicationState.SUBMITTED, DIFFERENT, MUST_NOT_BE_ASKED, LONG_AGO))
                .isEqualTo(NONE);
        assertThat(decide(PublicationState.PUBLISHED, STALE, MUST_NOT_BE_ASKED, LONG_AGO))
                .isEqualTo(NONE);
        assertThat(decide(PublicationState.NOT_SENT, ABSENT, MUST_NOT_BE_ASKED, LONG_AGO)).isEqualTo(NONE);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void theComparisonIsBuiltOnlyForASubmittedOrPublishedRow(PublicationState state) {
        Supplier<GlobalConfComparison> mustNotBeBuilt = () -> {
            throw new AssertionError("the comparison must not be built");
        };

        assertThat(PublicationCheck.decide(state, mustNotBeBuilt, ALLOWED, LONG_AGO, SUBMITTED_BEFORE))
                .isEqualTo(NONE);
    }
}
