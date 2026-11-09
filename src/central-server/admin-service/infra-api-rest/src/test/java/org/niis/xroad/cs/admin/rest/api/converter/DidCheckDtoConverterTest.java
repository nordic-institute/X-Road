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
package org.niis.xroad.cs.admin.rest.api.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.openapi.model.DidCheckStatusDto;
import org.niis.xroad.cs.openapi.model.DidRefusalReasonDto;
import org.niis.xroad.cs.openapi.model.DidResolutionReasonDto;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DidCheckDtoConverterTest {

    private final DidCheckDtoConverter converter = new DidCheckDtoConverterImpl();

    @ParameterizedTest
    @EnumSource(DidRefusalReasonDto.class)
    void shouldMapEveryRefusalReasonOfTheApi(DidRefusalReasonDto expected) {
        var dto = converter.toTarget(DidCheck.refused(ParticipantValueRejection.valueOf(expected.name())));

        assertThat(dto.getStatus()).isEqualTo(DidCheckStatusDto.REFUSED);
        assertThat(dto.getRefusalReason()).isEqualTo(expected);
        assertThat(dto.getResolutionReason()).isNull();
    }

    @ParameterizedTest
    @MethodSource("reasonsTheCheckNeverGives")
    void shouldRefuseToMapAReasonTheCheckNeverGives(ParticipantValueRejection reason) {
        var check = DidCheck.refused(reason);

        assertThatThrownBy(() -> converter.toTarget(check)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<ParticipantValueRejection> reasonsTheCheckNeverGives() {
        return Stream.of(ParticipantValueRejection.values())
                .filter(reason -> reason.isDspBaseUrlReason() || reason == ParticipantValueRejection.VALUE_DIFFERS);
    }

    @Test
    void shouldMapAResolvedCheckWithoutReasons() {
        var dto = converter.toTarget(DidCheck.RESOLVED);

        assertThat(dto.getStatus()).isEqualTo(DidCheckStatusDto.RESOLVED);
        assertThat(dto.getRefusalReason()).isNull();
        assertThat(dto.getResolutionReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(DidResolutionReason.class)
    void shouldMapEveryResolutionReason(DidResolutionReason reason) {
        var dto = converter.toTarget(DidCheck.notResolved(reason));

        assertThat(dto.getStatus()).isEqualTo(DidCheckStatusDto.NOT_RESOLVED);
        assertThat(dto.getRefusalReason()).isNull();
        assertThat(dto.getResolutionReason()).isEqualTo(DidResolutionReasonDto.valueOf(reason.name()));
    }
}
