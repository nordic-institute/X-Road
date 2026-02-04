/*
 * The MIT License
 * <p>
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
package org.niis.xroad.common.exception;

import ee.ria.xroad.common.HttpStatus;

import lombok.NonNull;
import org.niis.xroad.common.core.exception.DeviationAware;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.HttpStatusAware;

import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

/**
 * Base exception for any manually thrown exception. It has an error message which optionally can be thrown to api layer.
 * Note: Usually if used within rest API this exception leads to http code 500.
 */
public class InternalServerErrorException extends ServerErrorException implements HttpStatusAware {

    public InternalServerErrorException(String message, Throwable cause, @NonNull final ErrorDeviation errorDeviation) {
        super(message, cause, errorDeviation);
    }

    public InternalServerErrorException(String message, @NonNull final ErrorDeviation errorDeviation) {
        super(message, errorDeviation);
    }

    public InternalServerErrorException(Throwable cause, @NonNull final ErrorDeviation errorDeviation) {
        super(cause, errorDeviation);
    }

    public InternalServerErrorException(@NonNull ErrorDeviation errorDeviation) {
        super(errorDeviation);
    }

    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause, INTERNAL_ERROR.build());
    }

    public InternalServerErrorException(String message) {
        super(message, INTERNAL_ERROR.build());
    }

    public  InternalServerErrorException(@NonNull final Throwable exception) {
        super(exception, INTERNAL_ERROR.build());
    }

    public <DE extends Exception & DeviationAware> InternalServerErrorException(@NonNull final DE exception) {
        super(exception);
    }

    @Override
    public Optional<HttpStatus> getHttpStatus() {
        return Optional.of(HttpStatus.INTERNAL_SERVER_ERROR);
    }


}
