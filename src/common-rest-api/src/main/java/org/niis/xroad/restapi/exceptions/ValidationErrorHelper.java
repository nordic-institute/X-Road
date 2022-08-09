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
package org.niis.xroad.restapi.exceptions;

import org.niis.xroad.restapi.openapi.model.CodeWithDetails;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_VALIDATION_FAILURE;

/**
 * Helper class for transforming Spring validation errors into error dtos.
 * Used from {@link ExceptionTranslator} and {@link SpringInternalExceptionHandler}
 */
@Component
public class ValidationErrorHelper {
    /**
     * Create DeviationAware error code & metadata from given org.springframework.validation.Errors.
     * Error code = {@link DeviationCodes#ERROR_VALIDATION_FAILURE}, metadata is the
     * String representation of {@link Errors}
     */
    public CodeWithDetails createError(Errors validationErrors) {
        CodeWithDetails result = new CodeWithDetails();
        result.setCode(ERROR_VALIDATION_FAILURE);
        result.setValidationErrors(getValidationErrorsAsMap(validationErrors));
        return result;
    }

    /**
     * Create DeviationAware error code & metadata from given MethodArgumentNotValidException.
     * Error code = {@link DeviationCodes#ERROR_VALIDATION_FAILURE}, metadata is the
     * String representation of {@link Errors}
     */
    public CodeWithDetails createError(MethodArgumentNotValidException e) {
        Errors errors = e.getBindingResult();
        return createError(errors);
    }

    /**
     * Create DeviationAware error code & metadata from given MethodArgumentTypeMismatchException.
     * Error code = {@link DeviationCodes#ERROR_VALIDATION_FAILURE}, metadata is the
     * String representation of {@link Errors}
     */
    public CodeWithDetails createError(final MethodArgumentTypeMismatchException e) {
        final CodeWithDetails result = new CodeWithDetails();
        result.setCode(ERROR_VALIDATION_FAILURE);

        final String message = Optional.ofNullable(e.getMessage()).orElseGet(e::toString);

        result.setValidationErrors(Map.of(e.getName(), List.of(message)));
        return result;
    }

    private Map<String, List<String>> getValidationErrorsAsMap(Errors validationErrors) {
        Map<String, List<String>> errorMap = new HashMap<>();
        List<FieldError> fieldErrors = validationErrors.getFieldErrors();
        Set<String> fields = fieldErrors.stream()
                .map(FieldError::getField)
                .collect(Collectors.toSet());
        fields.forEach(fieldName -> {
            String objectFieldName = null;
            List<String> fieldValidationErrors = new ArrayList<>();
            for (FieldError fieldError : fieldErrors) {
                if (fieldError.getField().equals(fieldName)) {
                    if (objectFieldName == null) {
                        objectFieldName = fieldError.getObjectName() + "." + fieldName;
                    }
                    fieldValidationErrors.add(fieldError.getCode());
                }
            }
            errorMap.put(objectFieldName, fieldValidationErrors);
        });
        return errorMap;
    }

}
