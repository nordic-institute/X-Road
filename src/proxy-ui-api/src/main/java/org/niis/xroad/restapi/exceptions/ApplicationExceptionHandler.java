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
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.restapi.service.InvalidUrlException;
import org.niis.xroad.restapi.service.NotFoundException;
import org.niis.xroad.restapi.service.ServiceDescriptionNotFoundException;
import org.niis.xroad.restapi.service.ServiceDescriptionService;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.wsdl.InvalidWsdlException;
import org.niis.xroad.restapi.wsdl.WsdlParser;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * handle exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorInfo> exception(Exception e) {
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
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.FORBIDDEN);
    }

    /**
     * handle NotFound service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorInfo> exception(NotFoundException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.NOT_FOUND);
    }

    /**
     * handle unhandled warnings service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(UnhandledWarningsException.class)
    public ResponseEntity<ErrorInfo> exception(UnhandledWarningsException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.CONFLICT);
    }

    /**
     * handle ServiceDescriptionService.ServiceCodeAlreadyExistsException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(ServiceDescriptionService.ServiceCodeAlreadyExistsException.class)
    public ResponseEntity<ErrorInfo> exception(ServiceDescriptionService.ServiceCodeAlreadyExistsException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.CONFLICT);
    }

    /**
     * handle InvalidWsdlException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(InvalidWsdlException.class)
    public ResponseEntity<ErrorInfo> exception(InvalidWsdlException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.BAD_REQUEST);
    }

    /**
     * handle WsdlParser.WsdlNotFoundException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(WsdlParser.WsdlNotFoundException.class)
    public ResponseEntity<ErrorInfo> exception(WsdlParser.WsdlNotFoundException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.NOT_FOUND);
    }

    /**
     * handle InvalidUrlException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorInfo> exception(InvalidUrlException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.BAD_REQUEST);
    }

    /**
     * handle ServiceDescriptionService.WrongServiceDescriptionTypeException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(ServiceDescriptionService.WrongServiceDescriptionTypeException.class)
    public ResponseEntity<ErrorInfo> exception(ServiceDescriptionService.WrongServiceDescriptionTypeException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.BAD_REQUEST);
    }

    /**
     * handle ServiceDescriptionService.WsdlUrlAlreadyExistsException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(ServiceDescriptionService.WsdlUrlAlreadyExistsException.class)
    public ResponseEntity<ErrorInfo> exception(ServiceDescriptionService.WsdlUrlAlreadyExistsException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.CONFLICT);
    }

    /**
     * handle ServiceDescriptionNotFoundException service exceptions
     * @param e
     * @return
     */
    @ExceptionHandler(ServiceDescriptionNotFoundException.class)
    public ResponseEntity<ErrorInfo> exception(ServiceDescriptionNotFoundException e) {
        log.error("exception caught", e);
        return exceptionTranslator.toResponseEntity(e, HttpStatus.NOT_FOUND);
    }
}
