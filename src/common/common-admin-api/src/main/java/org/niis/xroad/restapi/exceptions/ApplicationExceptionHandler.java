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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.restapi.service.SignerNotReachableException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolationException;

/**
 * Application exception handler.
 * Some exception occurrences do not get processed by this class, but by
 * {@link SpringInternalExceptionHandler instead}
 */
@ControllerAdvice
@Slf4j
public class ApplicationExceptionHandler {

    public static final String EXCEPTION_CAUGHT = "exception caught";

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private AuditEventLoggingFacade auditEventLoggingFacade;

    /**
     * handle exceptions
     *
     * @param e Exception to handle
     * @return ErrorInfo response entity
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorInfo> exception(Exception e) {
        auditEventLoggingFacade.auditLogFail(e);
        log.error(EXCEPTION_CAUGHT, e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * handle auth exceptions
     *
     * @param e AuthenticationException
     * @return 401 response
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorInfo> exception(AuthenticationException e) {
        // prevent double audit logging with hasLoggedForThisRequestAny
        if (!auditEventLoggingFacade.hasAlreadyLoggedForThisRequestAny(
                RestApiAuditEvent.API_KEY_AUTHENTICATION, RestApiAuditEvent.AUTH_CREDENTIALS_DISCOVERY)) {
            auditEventLoggingFacade.auditLogFail(RestApiAuditEvent.UNSPECIFIED_AUTHENTICATION, e);
        }
        if (log.isDebugEnabled()) {
            log.error(EXCEPTION_CAUGHT, e);
        } else {
            log.error("Authentication failure: {}", e.getMessage());
        }
        return exceptionTranslator.toResponseEntity(e, HttpStatus.UNAUTHORIZED);
    }

    /**
     * handle access denied exceptions
     *
     * @param e AccessDeniedException
     * @return 403 response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorInfo> exception(AccessDeniedException e) {
        auditEventLoggingFacade.auditLogFail(RestApiAuditEvent.UNSPECIFIED_ACCESS_CHECK, e);
        if (log.isDebugEnabled()) {
            log.error("Access denied", e);
        } else {
            log.error("Access denied: {}", e.getMessage());
        }
        return exceptionTranslator.toResponseEntity(e, HttpStatus.FORBIDDEN);
    }

    /**
     * Check for nested SignerNotReachable exception when BeanCreationException is caught. This is needed because
     * we are using request scoped beans (see
     * {@link org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerConfig}) which can throw
     * BeanCreationException in runtime and wrap any causing exception inside therefore hiding the real exception.
     * This handler will force the error code of the SignerNotReachable to be propagated to the REST API response.
     *
     * @param beanCreationException may contain SignerNotReachable
     * @return 500 status
     */
    @SuppressWarnings("JavadocReference")
    @ExceptionHandler(BeanCreationException.class)
    public ResponseEntity<ErrorInfo> exception(BeanCreationException beanCreationException) {
        auditEventLoggingFacade.auditLogFail(beanCreationException);
        log.error(EXCEPTION_CAUGHT, beanCreationException);
        Exception exception = beanCreationException;
        int indexOfSignerException = ExceptionUtils
                .indexOfThrowable(beanCreationException, SignerNotReachableException.class);
        if (indexOfSignerException != -1) {
            exception = (SignerNotReachableException) ExceptionUtils
                    .getThrowables(beanCreationException)[indexOfSignerException];
        }
        return exceptionTranslator.toResponseEntity(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorInfo> exception(ConstraintViolationException constraintViolationException) {
        auditEventLoggingFacade.auditLogFail(constraintViolationException);
        log.error(EXCEPTION_CAUGHT, constraintViolationException);
        return exceptionTranslator.toResponseEntity(constraintViolationException, HttpStatus.BAD_REQUEST);

    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorInfo> exception(MaxUploadSizeExceededException maxUploadSizeExceededException) {
        auditEventLoggingFacade.auditLogFail(maxUploadSizeExceededException);
        log.error(EXCEPTION_CAUGHT, maxUploadSizeExceededException);
        return exceptionTranslator.toResponseEntity(maxUploadSizeExceededException, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
