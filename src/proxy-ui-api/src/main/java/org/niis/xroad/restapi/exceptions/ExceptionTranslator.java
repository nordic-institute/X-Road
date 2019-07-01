/**
 * The MIT License
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

import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.restapi.openapi.model.Warning;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Translate exceptions to ResponseEntities
 */
@Component
public class ExceptionTranslator {

    public static final String ADD_SERVICE_DESCRIPTION_WARNING_CODE = "adding_service_description";

    /**
     * Create ResponseEntity<ErrorInfo> from an Exception.
     * Use provided status or override it with value from
     * Exception's ResponseStatus annotation if one exists
     * @param e
     * @param defaultStatus
     * @param warnings
     * @return
     */
    public ResponseEntity<ErrorInfo> toResponseEntity(Exception e, HttpStatus defaultStatus, List<Warning> warnings) {
        HttpStatus status = defaultStatus;
        ResponseStatus statusAnnotation = AnnotationUtils.findAnnotation(
                e.getClass(), ResponseStatus.class);
        if (statusAnnotation != null) {
            // take status from exception annotation
            status = statusAnnotation.value();
        }
        ErrorInfo errorDto = new ErrorInfo();
        errorDto.setStatus(status.value());
        if (e instanceof ErrorCodedException) {
            ErrorCodedException errorCodedException = (ErrorCodedException) e;
            errorDto.setErrorCode(errorCodedException.getErrorCode());
        }
        if (warnings != null) {
            errorDto.setWarnings(warnings);
        }
        return new ResponseEntity<ErrorInfo>(errorDto, status);
    }

    /**
     * Create ResponseEntity<ErrorInfo> from an Exception.
     * Use provided status or override it with value from
     * Exception's ResponseStatus annotation if one exists
     * @param e
     * @return
     */
    public ResponseEntity<ErrorInfo> toResponseEntity(Exception e, HttpStatus defaultStatus) {
        return toResponseEntity(e, defaultStatus, null);
    }
}
