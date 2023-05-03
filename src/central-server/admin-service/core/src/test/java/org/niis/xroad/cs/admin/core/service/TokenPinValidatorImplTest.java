/**
 * The MIT License
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.TokenPinPolicy;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.core.util.DeviationTestUtils;
import org.niis.xroad.restapi.exceptions.DeviationCodes;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

/**
 * test token pin validator
 */
@Slf4j
class TokenPinValidatorImplTest {
    private final TokenPinValidatorImpl tokenPinValidator = new TokenPinValidatorImpl();

    private static final String SOFTWARE_TOKEN_PIN = "ABCdef123456.";
    private static final String SOFTWARE_TOKEN_WEAK_PIN = "a";
    private static final String SOFTWARE_TOKEN_INVALID_PIN = "‘œ‘–ßçıı–ç˛®ç†é®ß";

    @BeforeEach
    public void setup() {
        tokenPinValidator.setTokenPinEnforced(true);
    }

    @Test
    void validateSoftwareTokenPinSuccess() {
        tokenPinValidator.validateSoftwareTokenPin(SOFTWARE_TOKEN_PIN.toCharArray());
    }

    @Test
    void validateSoftwareTokenPinWeak() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> tokenPinValidator.validateSoftwareTokenPin(SOFTWARE_TOKEN_WEAK_PIN.toCharArray()))
                .satisfies(e -> DeviationTestUtils.assertErrorWithMetadata(
                        ErrorMessage.TOKEN_WEAK_PIN.getCode(), e, DeviationCodes.ERROR_METADATA_PIN_MIN_LENGTH,
                        String.valueOf(TokenPinPolicy.MIN_PASSWORD_LENGTH),
                        DeviationCodes.ERROR_METADATA_PIN_MIN_CHAR_CLASSES,
                        String.valueOf(TokenPinPolicy.MIN_CHARACTER_CLASS_COUNT)));
    }

    @Test
    void validateSoftwareTokenPinNotEnforcedSuccess() {
        tokenPinValidator.setTokenPinEnforced(false);
        tokenPinValidator.validateSoftwareTokenPin(SOFTWARE_TOKEN_WEAK_PIN.toCharArray());
    }

    @Test
    void validateSoftwareTokenPinInvalid() {
        assertThatExceptionOfType(ValidationFailureException.class)
                .isThrownBy(() -> tokenPinValidator.validateSoftwareTokenPin(SOFTWARE_TOKEN_INVALID_PIN.toCharArray()));

    }
}
