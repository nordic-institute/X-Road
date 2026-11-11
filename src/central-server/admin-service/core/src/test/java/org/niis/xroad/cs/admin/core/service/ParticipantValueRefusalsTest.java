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
package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParticipantValueRefusalsTest {

    private static final String CONSTRAINT = "some_did_excl";

    @ParameterizedTest
    @CsvSource({
            "NO_STORED_VALUE, did_not_stored",
            "WRONG_SCHEME, did_wrong_scheme",
            "DID_TOO_LONG, did_too_long",
            "NOT_CANONICAL, did_not_canonical",
            "HOST_DIFFERS, did_host_differs",
            "NON_ASCII_HOST, did_non_ascii_host",
            "UNPARSEABLE_REGISTERED_ADDRESS, did_unparseable_server_address",
            "UNDECODABLE_DID, did_undecodable",
            "FOREIGN_SYSTEM_DID, did_foreign_system",
            "DID_OF_OTHER_MEMBER, did_of_other_member",
            "MEMBER_NOT_SERVED, did_member_not_served",
            "DID_HELD_BY_ANOTHER_SERVER, did_held_by_another_server",
            "DSP_BASE_URL_NOT_HTTPS, dsp_base_url_not_https",
            "DSP_BASE_URL_MALFORMED, dsp_base_url_malformed",
            "DSP_BASE_URL_NOT_CANONICAL, dsp_base_url_not_canonical",
            "DSP_BASE_URL_HOST_DIFFERS, dsp_base_url_host_differs",
            "DSP_BASE_URL_NON_ASCII_HOST, dsp_base_url_non_ascii_host"})
    void shouldRefuseARejectedReplacementWithTheCodeOfItsReason(ParticipantValueRejection rejection, String code) {
        assertThatThrownBy(() -> ParticipantValueRefusals.replaceOrRefuse(
                () -> ParticipantValueResult.rejected(rejection), CONSTRAINT))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(code));
    }

    @Test
    void shouldHaveACodeForEveryReasonButTheOneAReplacementNeverGives() {
        for (var rejection : ParticipantValueRejection.values()) {
            if (rejection == ParticipantValueRejection.VALUE_DIFFERS) {
                assertThatThrownBy(() -> ParticipantValueRefusals.errorOf(rejection))
                        .isInstanceOf(IllegalStateException.class);
            } else {
                assertThat(ParticipantValueRefusals.errorOf(rejection)).isNotNull();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = ParticipantValueRejection.class, names = "VALUE_DIFFERS", mode = EnumSource.Mode.EXCLUDE)
    void shouldTreatExactlyTheReasonsWithADspBaseUrlCodeAsDspBaseUrlReasons(ParticipantValueRejection rejection) {
        var hasDspBaseUrlCode = ParticipantValueRefusals.errorOf(rejection).code().startsWith("dsp_base_url_");

        assertThat(rejection.isDspBaseUrlReason()).isEqualTo(hasDspBaseUrlCode);
    }

    @ParameterizedTest
    @EnumSource(value = ParticipantValueResult.Outcome.class, names = {"STORED", "UNCHANGED"})
    void shouldAcceptAReplacementThatWasStoredOrLeftAsItWas(ParticipantValueResult.Outcome outcome) {
        var result = outcome == ParticipantValueResult.Outcome.STORED
                ? ParticipantValueResult.STORED : ParticipantValueResult.UNCHANGED;

        assertThatCode(() -> ParticipantValueRefusals.replaceOrRefuse(() -> result, CONSTRAINT)).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseAsHeldByAnotherServerWhenTheExclusionConstraintIsViolated() {
        assertThatThrownBy(() -> ParticipantValueRefusals.replaceOrRefuse(() -> {
            throw new DataIntegrityViolationException(CONSTRAINT);
        }, CONSTRAINT))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo("did_held_by_another_server"));
    }

    @Test
    void shouldRethrowAViolationOfAnotherConstraint() {
        var failure = new DataIntegrityViolationException("another_constraint");

        assertThatThrownBy(() -> ParticipantValueRefusals.replaceOrRefuse(() -> {
            throw failure;
        }, CONSTRAINT)).isSameAs(failure);
    }
}
