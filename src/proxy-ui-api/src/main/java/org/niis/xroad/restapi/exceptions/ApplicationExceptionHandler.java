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
import org.niis.xroad.restapi.config.audit.AuditEventHolder;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * exception handler
 */
@ControllerAdvice
@Slf4j
public class ApplicationExceptionHandler {

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    @Lazy
    private AuditEventHolder auditEventHolder;

    /**
     * handle exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorInfo> exception(Exception e) {
        auditEventHolder.auditLogFail(e);
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * handle auth exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorInfo> exception(AuthenticationException e) {
        auditEventHolder.auditLogFail(e);
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.UNAUTHORIZED);
    }

    /**
     * handle access denied exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorInfo> exception(AccessDeniedException e) {
        auditEventHolder.auditLogFail(e);
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.FORBIDDEN);
    }
}
