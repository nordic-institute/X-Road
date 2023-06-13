/*
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
package ee.ria.xroad.common.validation;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;

import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.BACKSLASH;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.COLON;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.CONTROL_CHAR;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.FORWARDSLASH;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.PERCENT;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.SEMICOLON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
public class LegacyEncodedIdentifierValidatorTest {

    private LegacyEncodedIdentifierValidator encodedIdentifierValidator;

    @Before
    public void setup() {
        encodedIdentifierValidator = new LegacyEncodedIdentifierValidator();
    }

    @Test
    public void valid() {
        assertTrue(encodedIdentifierValidator.isValid("adsdsa"));
        assertTrue(encodedIdentifierValidator.isValid("a.b.c"));
        assertTrue(encodedIdentifierValidator.isValid("a-b-c"));
        assertTrue(encodedIdentifierValidator.isValid("äöå"));
        assertTrue(encodedIdentifierValidator.isValid("列"));
    }

    final char semiColon = ';';
    final char colon = ':';
    final char slash = '/';
    final char backslash = '\\';
    final char percent = '%';
    final char tab = '\t';
    final char newline = '\n';
    final char cr = '\r';
    final char esc = '\u001b';
    final char sos = '\u0098';
    final char space = ' ';

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
                encodedIdentifierValidator.getValidationErrors("aaa/./bbbb\\cc/../cc%ddd"));
    }

    @Test
    public void controlChars() {
        assertEquals(EnumSet.of(CONTROL_CHAR),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(tab)));
        assertEquals(EnumSet.of(CONTROL_CHAR),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(newline)));
        assertEquals(EnumSet.of(CONTROL_CHAR),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(cr)));
        assertEquals(EnumSet.of(CONTROL_CHAR),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(esc)));
        assertEquals(EnumSet.of(CONTROL_CHAR),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(sos)));
        assertEquals(EnumSet.noneOf(LegacyEncodedIdentifierValidator.ValidationError.class),
                encodedIdentifierValidator.getValidationErrors(String.valueOf(space)));
    }

    @Test
    public void allErrors() {
        assertEquals(EnumSet.allOf(LegacyEncodedIdentifierValidator.ValidationError.class),
                encodedIdentifierValidator.getValidationErrors(":aa;bb/cc\\dd%ee/../f\tf"));
    }

}
