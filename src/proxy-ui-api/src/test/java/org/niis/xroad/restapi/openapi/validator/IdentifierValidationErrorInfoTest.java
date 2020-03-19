/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi.validator;

import org.junit.Test;
import org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Defensive programming type of test:
 * ensure that {@link IdentifierValidationErrorInfo} and
 * {@link ValidationError}
 * enums are in sync (contain same values)
 */
public class IdentifierValidationErrorInfoTest {

    @Test
    public void sameItems() {
        Set<String> identifierValidationErrorInfoNames = Stream.of(
                IdentifierValidationErrorInfo.values())
                .map(Enum::name).collect(Collectors.toSet());
        Set<String> validationErrorNames = Stream.of(
                ValidationError.values())
                .map(Enum::name).collect(Collectors.toSet());
        assertEquals(identifierValidationErrorInfoNames.size(),
                validationErrorNames.size());
        assertTrue(identifierValidationErrorInfoNames.containsAll(validationErrorNames));
    }

    @Test
    public void of() {
        for (ValidationError error: ValidationError.values()) {
            assertNotNull(IdentifierValidationErrorInfo.of(error));
        }
    }

    @Test
    public void ofEnumSet() {
        assertTrue(IdentifierValidationErrorInfo.of(EnumSet.noneOf(ValidationError.class)).isEmpty());
        assertTrue(IdentifierValidationErrorInfo.of(EnumSet.allOf(ValidationError.class))
                .containsAll(EnumSet.allOf(IdentifierValidationErrorInfo.class)));
    }
}
