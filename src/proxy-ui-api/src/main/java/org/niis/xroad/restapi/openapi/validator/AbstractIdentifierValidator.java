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

import ee.ria.xroad.common.validation.EncodedIdentifierValidator;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.Collection;
import java.util.EnumSet;

/**
 * Base class for validators that validate objects with fields that are used to create encoded identifiers
 */
@Slf4j
abstract class AbstractIdentifierValidator implements Validator {

    @Data
    @Builder
    public static class ValidatedField {
        private String value;
        private String fieldName;
    }

    /**
     * Get fields to validate
     * @return
     */
    abstract Collection<ValidatedField> getValidatedFields(Object target);

    @Override
    public void validate(Object target, Errors errors) {
        for (ValidatedField validatedField: getValidatedFields(target)) {
            if (!StringUtils.isEmpty(validatedField.getValue())) {
                validateIdentifierField(validatedField.getValue(), validatedField.getFieldName(), errors);
            }
        }
    }

    private void validateIdentifierField(String memberCode, String fieldName, Errors errors) {
        EncodedIdentifierValidator validator = new EncodedIdentifierValidator();
        EnumSet<IdentifierValidationErrorInfo> validationErrors = IdentifierValidationErrorInfo.of(
                validator.getValidationErrors(memberCode));
        validationErrors.forEach(error ->
                errors.rejectValue(fieldName, error.getErrorCode(), null, error.getDefaultMessage()));
    }
}
