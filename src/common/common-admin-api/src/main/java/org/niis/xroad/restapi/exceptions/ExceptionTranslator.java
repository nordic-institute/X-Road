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
package org.niis.xroad.restapi.exceptions;

import ee.ria.xroad.common.CodedException;

import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.restapi.openapi.model.CodeWithDetails;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.validation.ConstraintViolationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_VALIDATION_FAILURE;
import static org.niis.xroad.restapi.exceptions.ResponseStatusUtil.getAnnotatedResponseStatus;

/**
 * Translate exceptions to ResponseEntities that contain the standard error object {@link ErrorInfo}
 */
@Component
public class ExceptionTranslator {

    public static final String CORE_CODED_EXCEPTION_PREFIX = "core.";

    private final ValidationErrorHelper validationErrorHelper;

    @Autowired
    public ExceptionTranslator(ValidationErrorHelper validationErrorHelper) {
        this.validationErrorHelper = validationErrorHelper;
    }

    /**
     * Create ResponseEntity<ErrorInfo> from an Exception.
     * Use provided status or override it with value from
     * Exception's ResponseStatus annotation if one exists
     *
     * @param e             exception to convert
     * @param defaultStatus status to be used if not specified with method annotation
     * @return ResponseEntity with properly filled ErrorInfo
     */
    public ResponseEntity<ErrorInfo> toResponseEntity(Exception e, HttpStatus defaultStatus) {
        HttpStatus status = resolveHttpStatus(e, defaultStatus);
        ErrorInfo errorDto = new ErrorInfo();
        errorDto.setStatus(status.value());
        if (e instanceof DeviationAware) {
            // add information about errors and warnings
            DeviationAware errorCodedException = (DeviationAware) e;
            if (errorCodedException.getErrorDeviation() != null) {
                errorDto.setError(convert(errorCodedException.getErrorDeviation()));
            }
            if (errorCodedException.getWarningDeviations() != null) {
                for (Deviation warning : errorCodedException.getWarningDeviations()) {
                    errorDto.addWarningsItem(convert(warning));
                }
            }
        } else if (e instanceof CodedException) {
            // map fault code and string from core CodedException
            CodedException c = (CodedException) e;
            Deviation deviation = new ErrorDeviation(CORE_CODED_EXCEPTION_PREFIX + c.getFaultCode(), c.getFaultString());
            errorDto.setError(convert(deviation));
        } else if (e instanceof MethodArgumentNotValidException) {
            errorDto.setError(validationErrorHelper.createError((MethodArgumentNotValidException) e));
        } else if (e instanceof ConstraintViolationException) {
            Map<String, List<String>> violations = new HashMap<>();
            ((ConstraintViolationException) e).getConstraintViolations()
                    .forEach(constraintViolation -> violations.put(constraintViolation.getPropertyPath().toString(),
                            List.of(constraintViolation.getMessage())));
            errorDto.setError(new CodeWithDetails().code(ERROR_VALIDATION_FAILURE).validationErrors(violations));
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorDto);
    }

    private CodeWithDetails convert(Deviation deviation) {
        CodeWithDetails result = new CodeWithDetails();
        if (deviation != null) {
            result.setCode(deviation.getCode());
            if (deviation.getMetadata() != null && !deviation.getMetadata().isEmpty()) {
                result.setMetadata(deviation.getMetadata());
            }
        }
        return result;
    }

    public HttpStatus resolveHttpStatus(Exception e, HttpStatus defaultStatus) {
        if (e instanceof ServiceException) {
            return HttpStatus.resolve(((ServiceException) e).getHttpStatus());
        }
        return getAnnotatedResponseStatus(e, defaultStatus);
    }
}
