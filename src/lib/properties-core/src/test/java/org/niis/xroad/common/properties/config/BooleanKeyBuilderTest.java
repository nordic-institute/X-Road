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

package org.niis.xroad.common.properties.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BooleanKeyBuilderTest {

    private static Stream<Arguments> validSynonyms() {
        return Stream.of(
                Arguments.of("true", true), Arguments.of("TRUE", true),
                Arguments.of("on", true), Arguments.of("ON", true),
                Arguments.of("y", true), Arguments.of("Y", true),
                Arguments.of("t", true), Arguments.of("T", true),
                Arguments.of("yes", true), Arguments.of("YES", true),
                Arguments.of("false", false), Arguments.of("FALSE", false),
                Arguments.of("off", false), Arguments.of("OFF", false),
                Arguments.of("n", false), Arguments.of("N", false),
                Arguments.of("f", false), Arguments.of("F", false),
                Arguments.of("no", false), Arguments.of("NO", false)
        );
    }

    @ParameterizedTest
    @MethodSource("validSynonyms")
    void validSynonymsConvertToExpectedBoolean(String raw, boolean expected) {
        var key = Prefix.of("xroad.test").bool("flag").build();

        assertThat(key.convert(raw)).isEqualTo(expected);
    }

    @Test
    void invalidValueConvertsToNullAndFailsValidation() {
        var key = Prefix.of("xroad.test")
                .bool("invalid-flag")
                .build();
        var value = key.convert("invalid-value");
        var result = key.validate(value);

        assertThat(value).isNull();
        assertThat(result).isEqualTo(Validator.Result.error(
                "must be one of: true, false, TRUE, FALSE, True, False, 1, 0, Y, N, Yes, No, YES, NO, on, ON, off, OFF"));
    }

    @Test
    void declaredDefaultValueStillBuildsWithoutError() {
        var key = Prefix.of("xroad.test")
                .bool("flag-with-default")
                .withDefaultValue(true)
                .build();

        assertThat(key.convertedDefaultValue()).isTrue();
    }
}
