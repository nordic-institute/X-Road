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
package org.niis.xroad.securityserver.restapi.exceptions;

import ee.ria.xroad.common.CodedException;

import org.niis.xroad.securityserver.restapi.openapi.model.CodeWithDetails;
import org.niis.xroad.securityserver.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;
import java.util.List;

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
     * @param e
     * @param defaultStatus
     * @return
     */
    public ResponseEntity<ErrorInfo> toResponseEntity(Exception e, HttpStatus defaultStatus) {
        HttpStatus status = defaultStatus;
        ResponseStatus statusAnnotation = AnnotationUtils.findAnnotation(
                e.getClass(), ResponseStatus.class);
        if (statusAnnotation != null) {
            // take status from exception annotation
            status = statusAnnotation.value();
        }
        ErrorInfo errorDto = new ErrorInfo();
        errorDto.setStatus(status.value());
        if (e instanceof DeviationAware) {
            // add information about errors and warnings
            DeviationAware errorCodedException = (DeviationAware) e;
            if (errorCodedException.getErrorDeviation() != null) {
                errorDto.setError(convert(errorCodedException.getErrorDeviation()));
            }
            if (errorCodedException.getWarningDeviations() != null) {
                for (Deviation warning: errorCodedException.getWarningDeviations()) {
                    errorDto.addWarningsItem(convert(warning));

                }
            }
        } else if (e instanceof CodedException) {
            // map fault code and string from core CodedException
            CodedException c = (CodedException) e;
            Deviation deviation = new Deviation(CORE_CODED_EXCEPTION_PREFIX + c.getFaultCode(),
                    c.getFaultString());
            errorDto.setError(convert(deviation));
        } else if (e instanceof MethodArgumentNotValidException) {
            errorDto.setError(validationErrorHelper.createError((MethodArgumentNotValidException) e));
        }
        return new ResponseEntity<ErrorInfo>(errorDto, status);
    }


    private CodeWithDetails convert(Deviation deviation) {
        CodeWithDetails result = new CodeWithDetails();
        if (deviation != null) {
            result.setCode(deviation.getCode());
            if (deviation.getMetadata() != null && !deviation.getMetadata().isEmpty()) {
                List<String> metadata = new ArrayList<>();
                for (String metadataItem: deviation.getMetadata()) {
                    metadata.add(metadataItem);
                }
                result.setMetadata(metadata);
            }
        }
        return result;
    }
}
