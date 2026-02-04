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
package org.niis.xroad.restapi.exceptions;

import jakarta.validation.ConstraintViolationException;
import org.niis.xroad.common.core.exception.Deviation;
import org.niis.xroad.common.core.exception.DeviationAware;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.HttpStatusAware;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.restapi.openapi.model.CodeWithDetails;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.core.exception.DeviationBuilder.TRANSLATABLE_PREFIX;
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
        HttpStatusCode status = resolveHttpStatus(e, defaultStatus);
        ErrorInfo errorDto = new ErrorInfo();
        errorDto.setStatus(status.value());
        switch (e) {
            case DeviationAware deviationAware -> {
                // add information about errors and warnings
                if (deviationAware.getErrorDeviation() != null) {
                    errorDto.setError(convert(deviationAware.getErrorDeviation()));
                }
                if (deviationAware.getWarningDeviations() != null) {
                    for (Deviation warning : deviationAware.getWarningDeviations()) {
                        errorDto.addWarningsItem(convert(warning));
                    }
                }
            }
            case XrdRuntimeException ce -> {
                // map fault code and string from core XrdRuntimeException
                var deviation = new ErrorDeviation(CORE_CODED_EXCEPTION_PREFIX + ce.getErrorCode(),
                        List.of(ce.getDetails(), TRANSLATABLE_PREFIX + ce.getErrorCode()));
                errorDto.setError(convert(deviation));
            }
            case MethodArgumentNotValidException manve -> errorDto.setError(validationErrorHelper.createError(manve));
            case ConstraintViolationException cve -> {
                Map<String, List<String>> violations = new HashMap<>();
                cve.getConstraintViolations()
                        .forEach(constraintViolation -> violations.put(constraintViolation.getPropertyPath().toString(),
                                List.of(constraintViolation.getMessage())));
                errorDto.setError(new CodeWithDetails().code(ERROR_VALIDATION_FAILURE).validationErrors(violations));
            }
            default -> {

            }
        }

        attachCausedBySignerIfNeeded(e, errorDto);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorDto);
    }

    private CodeWithDetails convert(Deviation deviation) {
        CodeWithDetails result = new CodeWithDetails();
        if (deviation != null) {
            result.setCode(deviation.code());
            if (deviation.metadata() != null && !deviation.metadata().isEmpty()) {
                result.setMetadata(deviation.metadata());
            }
        }
        return result;
    }

    private boolean isCausedByRemoteException(Throwable e) {
        return e != null
                && ((e instanceof XrdRuntimeException xrdRuntimeException && xrdRuntimeException.originatesFrom(ErrorOrigin.SIGNER))
                || isCausedByRemoteException(e.getCause()));
    }

    private void attachCausedBySignerIfNeeded(Throwable e, ErrorInfo errorDto) {
        if (isCausedByRemoteException(e)) {
            var metadata = errorDto.getError().getMetadata();
            metadata = metadata == null ? new LinkedList<>() : new LinkedList<>(metadata);
            metadata.add(TRANSLATABLE_PREFIX + "check_signer_logs");
            errorDto.getError().setMetadata(metadata);
        }
    }

    public HttpStatusCode resolveHttpStatus(Exception e, HttpStatus defaultStatus) {
        if (e instanceof HttpStatusAware hsa) {
            return hsa.getHttpStatus()
                    .map(ee.ria.xroad.common.HttpStatus::getCode)
                    .map(code -> (HttpStatusCode) HttpStatus.resolve(code))
                    .orElseGet(() -> getAnnotatedResponseStatus(e, defaultStatus));
        }
        return getAnnotatedResponseStatus(e, defaultStatus);
    }


}
