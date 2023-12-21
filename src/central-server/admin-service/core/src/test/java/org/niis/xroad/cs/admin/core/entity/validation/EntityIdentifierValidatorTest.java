/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.entity.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityIdentifierValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @RequiredArgsConstructor
    public class Model {
        @EntityIdentifier
        private final String entityIdentifier;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "A",
            "A/B",
            "A/.B",
            "A/.B/D.",
    })
    public void shouldBeValid(String input) {
        Model model = new Model(input);

        Set<ConstraintViolation<Model>> violations = validator.validate(model);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidEntityIdentifiers")
    public void shouldHaveInvalid(String input, String expectedFault) {
        Model model = new Model(input);

        Set<ConstraintViolation<Model>> violations = validator.validate(model);

        assertThat(violations)
                .hasSize(1)
                .first()
                .<ConstraintViolation<Model>, String>extracting(ConstraintViolation::getMessage)
                .startsWith(expectedFault);
    }

    private static Stream<Arguments> invalidEntityIdentifiers() {
        return Stream.of(
                Arguments.of("", "must not be blank"),
                Arguments.of("./", EntityIdentifier.PATTERN_VIOLATION_MESSAGE),
                Arguments.of("../", EntityIdentifier.PATTERN_VIOLATION_MESSAGE),
                Arguments.of("/../", EntityIdentifier.PATTERN_VIOLATION_MESSAGE),
                Arguments.of("/.", EntityIdentifier.PATTERN_VIOLATION_MESSAGE),
                Arguments.of("/./", EntityIdentifier.PATTERN_VIOLATION_MESSAGE),
                Arguments.of(">CONTAINS NON-PRINTABLE CHARACTER: \u0000<", EntityIdentifier.PATTERN_VIOLATION_MESSAGE),
                Arguments.of("TOO LONG INPUT" + " ".repeat(255), "length must be less or equal to 255")
        );
    }
}
