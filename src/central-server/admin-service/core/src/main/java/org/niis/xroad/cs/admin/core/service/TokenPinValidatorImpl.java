/*
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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.PasswordPolicy;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.DeviationBuilder;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.service.TokenPinValidator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static org.niis.xroad.common.core.exception.ErrorCodes.TOKEN_WEAK_PIN;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.TOKEN_INVALID_CHARACTERS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_MIN_CHAR_CLASSES;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_MIN_LENGTH;

@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class TokenPinValidatorImpl implements TokenPinValidator {
    private static final Object[] DEFAULT_WEAK_PIN_METADATA = new String[]{
            DeviationBuilder.TRANSLATABLE_PREFIX + ERROR_METADATA_PIN_MIN_LENGTH,
            String.valueOf(PasswordPolicy.MIN_PASSWORD_LENGTH),
            DeviationBuilder.TRANSLATABLE_PREFIX + ERROR_METADATA_PIN_MIN_CHAR_CLASSES,
            String.valueOf(PasswordPolicy.MIN_CHARACTER_CLASS_COUNT)
    };

    @Setter
    private boolean isTokenPinEnforced = SystemProperties.shouldEnforceTokenPinPolicy();

    @Override
    public void validateSoftwareTokenPin(char[] softwareTokenPin) throws BadRequestException {
        if (isTokenPinEnforced) {
            PasswordPolicy.Description description = PasswordPolicy.describe(softwareTokenPin);
            if (!description.isValid()) {
                if (description.hasInvalidCharacters()) {
                    throw new BadRequestException(TOKEN_INVALID_CHARACTERS.build());
                }
                throw new BadRequestException(TOKEN_WEAK_PIN.build(DEFAULT_WEAK_PIN_METADATA));
            }
        }
    }
}
