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

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collection;

/**
 * Thrown if client sent bad request.
 * Results in http 400 BAD_REQUEST
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends DeviationAwareRuntimeException {
    public BadRequestException() {
    }

    public BadRequestException(String msg) {
        super(msg);
    }

    public BadRequestException(String msg, Error error) {
        super(msg, error);
    }

    public BadRequestException(String msg, Throwable t, Error error) {
        super(msg, t, error);
    }

    public BadRequestException(Throwable t, Error error, Collection<Warning> warnings) {
        super(t, error, warnings);
    }

    public BadRequestException(Error error, Collection<Warning> warnings) {
        super(error, warnings);
    }

    public BadRequestException(Throwable t, Error error) {
        super(t, error);
    }

    /**
     * Use deviation data from original exception
     * @param e
     */
    public BadRequestException(DeviationAwareRuntimeException e) {
        this(e, e.getError(), e.getWarnings());
    }

}
