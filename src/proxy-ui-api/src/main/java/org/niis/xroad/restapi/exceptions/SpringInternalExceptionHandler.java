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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.niis.xroad.restapi.config.LimitRequestSizesException;
import org.niis.xroad.restapi.config.audit.AuditEventHolder;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handle Spring internal exceptions
 */
@ControllerAdvice
@Order(SpringInternalExceptionHandler.TEN)
@Slf4j
public class SpringInternalExceptionHandler extends ResponseEntityExceptionHandler {
    public static final int TEN = 10;

    private final ValidationErrorHelper validationErrorHelper;

    @Autowired
    @Lazy
    private AuditEventHolder auditEventHolder;

    @Autowired
    public SpringInternalExceptionHandler(ValidationErrorHelper validationErrorHelper) {
        this.validationErrorHelper = validationErrorHelper;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatus status,
                                                             WebRequest request) {
        auditEventHolder.auditLogFail("SpringInternalExceptionHandler");
        log.error("exception caught", ex);
        ErrorInfo errorInfo = new ErrorInfo();
        if (causedBySizeLimitExceeded(ex)) {
            status = HttpStatus.PAYLOAD_TOO_LARGE;
        } else if (ex instanceof MethodArgumentNotValidException) {
            errorInfo.setError(validationErrorHelper.createError((MethodArgumentNotValidException) ex));
        }
        errorInfo.setStatus(status.value());
        return super.handleExceptionInternal(ex, errorInfo, headers,
                status, request);
    }


    /**
     * LimitRequestSizesException is typically wrapped in an HttpMessageNotReadableException
     */
    private boolean causedBySizeLimitExceeded(Throwable t) {
        return ExceptionUtils.indexOfThrowable(t, LimitRequestSizesException.class) != -1;
    }
}
