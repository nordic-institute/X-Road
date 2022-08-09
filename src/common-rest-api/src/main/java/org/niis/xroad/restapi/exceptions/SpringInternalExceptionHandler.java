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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Optional;

import static org.niis.xroad.restapi.exceptions.ResponseStatusUtil.getAnnotatedResponseStatus;

/**
 * Handle Spring internal exceptions, which are not caught by {@link ApplicationExceptionHandler}.
 * For example some JSON body validation exceptions (org.springframework.web.bind.MethodArgumentNotValidException)
 * are handled by this class instead of ApplicationExceptionHandler.
 */
@ControllerAdvice
@Order(SpringInternalExceptionHandler.BEFORE_APPLICATION_EXCEPTION_HANDLER)
@Slf4j
public class SpringInternalExceptionHandler extends ResponseEntityExceptionHandler {
    // ApplicationExceptionHandler has default order, LOWEST_PRECEDENCE
    public static final int BEFORE_APPLICATION_EXCEPTION_HANDLER = 10;

    private final ValidationErrorHelper validationErrorHelper;

    @Autowired
    private AuditEventLoggingFacade auditEventLoggingFacade;

    @Autowired
    public SpringInternalExceptionHandler(ValidationErrorHelper validationErrorHelper) {
        this.validationErrorHelper = validationErrorHelper;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatus status,
            WebRequest request) {
        auditEventLoggingFacade.auditLogFail(ex);
        log.error("exception caught", ex);
        ErrorInfo errorInfo = new ErrorInfo();
        Optional<Throwable> wrappedStatusCause = getWrappedStatusCarryingExceptionCause(ex);
        if (wrappedStatusCause.isPresent()) {
            status = getAnnotatedResponseStatus(wrappedStatusCause.get(), status);
        } else if (ex instanceof MethodArgumentNotValidException) {
            errorInfo.setError(validationErrorHelper.createError((MethodArgumentNotValidException) ex));
        } else if (ex instanceof MethodArgumentTypeMismatchException) {
            errorInfo.setError(validationErrorHelper.createError((MethodArgumentTypeMismatchException) ex));
        }

        errorInfo.setStatus(status.value());
        return super.handleExceptionInternal(ex, errorInfo, headers,
                status, request);
    }

    /**
     * Return possible WrappedStatusCarryingException cause (or Throwable itself), if any exist.
     * If multiple, returns first match.
     *
     * Some Exceptions are wrapped in others, but should still control which HTTP status to use.
     * These are marked with WrappedStatusCarryingException interface.
     * One example is LimitRequestSizesException, which is typically wrapped in
     * an HttpMessageNotReadableException
     */
    private Optional<Throwable> getWrappedStatusCarryingExceptionCause(Throwable t) {
        return ExceptionUtils.getThrowableList(t).stream()
                .filter(e -> e instanceof WrappedStatusCarryingException)
                .findFirst();
    }
}
