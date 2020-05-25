/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi.validator;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.validator.EncodedIdentifierValidator;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError.BACKSLASH;
import static org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError.COLON;
import static org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError.FORWARDSLASH;
import static org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError.NON_NORMALIZED_PATH;
import static org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError.PERCENT;
import static org.niis.xroad.restapi.validator.EncodedIdentifierValidator.ValidationError.SEMICOLON;

@Slf4j
public class EncodedIdentifierValidatorTest {

    private EncodedIdentifierValidator encodedIdentifierValidator;

    @Before
    public void setup() {
        encodedIdentifierValidator = new EncodedIdentifierValidator();
    }

    @Test
    public void valid() {
        assertTrue(encodedIdentifierValidator.getValidationErrors("adsdsa").isEmpty());
        assertTrue(encodedIdentifierValidator.getValidationErrors("a.b.c").isEmpty());
        assertTrue(encodedIdentifierValidator.getValidationErrors("a-b-c").isEmpty());
        assertTrue(encodedIdentifierValidator.getValidationErrors("äöå").isEmpty());
        assertTrue(encodedIdentifierValidator.getValidationErrors("列").isEmpty());
    }

    @Test
    public void nonNormalizedPaths() {
        assertEquals(EnumSet.of(NON_NORMALIZED_PATH, FORWARDSLASH),
                encodedIdentifierValidator.getValidationErrors("./"));
        assertEquals(EnumSet.of(NON_NORMALIZED_PATH, FORWARDSLASH),
                encodedIdentifierValidator.getValidationErrors("/../"));
        assertEquals(EnumSet.of(NON_NORMALIZED_PATH, FORWARDSLASH),
                encodedIdentifierValidator.getValidationErrors("/."));
        assertEquals(EnumSet.of(NON_NORMALIZED_PATH, FORWARDSLASH),
                encodedIdentifierValidator.getValidationErrors("aaa/../bbb"));
    }

    char semiColon = ';';
    char colon = ':';
    char slash = '/';
    char backslash = '\\';
    char percent = '%';

    @Test
    public void semiOrFullColons() {
        assertEquals(EnumSet.of(COLON),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(colon)));
        assertEquals(EnumSet.of(SEMICOLON),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(semiColon)));
        assertEquals(EnumSet.of(COLON, SEMICOLON),
                encodedIdentifierValidator.getValidationErrors("aaa:bbbb;cccc"));
    }

    @Test
    public void slashesOrPercent() {
        assertEquals(EnumSet.of(FORWARDSLASH),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(slash)));
        assertEquals(EnumSet.of(BACKSLASH),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(backslash)));
        assertEquals(EnumSet.of(PERCENT),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(percent)));

        assertEquals(EnumSet.of(FORWARDSLASH, BACKSLASH, PERCENT),
                encodedIdentifierValidator.getValidationErrors("aaa/bbbb\\cccc%ddd"));
    }

    @Test
    public void allErrors() {
        assertEquals(EnumSet.allOf(EncodedIdentifierValidator.ValidationError.class),
                encodedIdentifierValidator.getValidationErrors(":aa;bb/cc\\dd%ee/../ff"));
    }

}
