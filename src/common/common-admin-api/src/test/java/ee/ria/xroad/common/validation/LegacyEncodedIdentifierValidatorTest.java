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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.BACKSLASH;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.COLON;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.CONTROL_CHAR;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.FORWARDSLASH;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.PERCENT;
import static ee.ria.xroad.common.validation.LegacyEncodedIdentifierValidator.ValidationError.SEMICOLON;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class LegacyEncodedIdentifierValidatorTest {

    private LegacyEncodedIdentifierValidator encodedIdentifierValidator;

    @BeforeEach
    void setup() {
        encodedIdentifierValidator = new LegacyEncodedIdentifierValidator();
    }

    @Test
    void valid() {
        assertThat(encodedIdentifierValidator.isValid("adsdsa")).isTrue();
        assertThat(encodedIdentifierValidator.isValid("a.b.c")).isTrue();
        assertThat(encodedIdentifierValidator.isValid("a-b-c")).isTrue();
        assertThat(encodedIdentifierValidator.isValid("äöå")).isTrue();
        assertThat(encodedIdentifierValidator.isValid("列")).isTrue();
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
    void semiOrFullColons() {
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(colon)))
                .isEqualTo(EnumSet.of(COLON));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(semiColon)))
                .isEqualTo(EnumSet.of(SEMICOLON));
        assertThat(encodedIdentifierValidator.getValidationErrors("aaa:bbbb;cccc"))
                .isEqualTo(EnumSet.of(COLON, SEMICOLON));
    }

    @Test
    void slashesOrPercent() {
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(slash)))
                .isEqualTo(EnumSet.of(FORWARDSLASH));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(backslash)))
                .isEqualTo(EnumSet.of(BACKSLASH));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(percent)))
                .isEqualTo(EnumSet.of(PERCENT));

        assertThat(encodedIdentifierValidator.getValidationErrors("aaa/./bbbb\\cc/../cc%ddd"))
                .isEqualTo(EnumSet.of(FORWARDSLASH, BACKSLASH, PERCENT));
    }

    @Test
    void controlChars() {
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(tab)))
                .isEqualTo(EnumSet.of(CONTROL_CHAR));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(newline)))
                .isEqualTo(EnumSet.of(CONTROL_CHAR));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(cr)))
                .isEqualTo(EnumSet.of(CONTROL_CHAR));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(esc)))
                .isEqualTo(EnumSet.of(CONTROL_CHAR));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(sos)))
                .isEqualTo(EnumSet.of(CONTROL_CHAR));
        assertThat(encodedIdentifierValidator.getValidationErrors(String.valueOf(space)))
                .isEqualTo(EnumSet.noneOf(LegacyEncodedIdentifierValidator.ValidationError.class));
    }

    @Test
    void allErrors() {
        assertThat(encodedIdentifierValidator.getValidationErrors(":aa;bb/cc\\dd%ee/../f\tf"))
                .isEqualTo(EnumSet.allOf(LegacyEncodedIdentifierValidator.ValidationError.class));
    }

}
