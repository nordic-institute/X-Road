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
package org.niis.xroad.restapi.openapi;

import org.niis.xroad.restapi.exceptions.DeviationAwareException;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collection;

/**
 * Thrown if client sent a bad request.
 * Results in http 400 BAD_REQUEST
 * TODO replaced by org.niis.xroad.common.exception exceptions
 */
@Deprecated
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class BadRequestException extends DeviationAwareRuntimeException {

    public BadRequestException(DeviationAwareException e) {
        super(e, e.getErrorDeviation(), e.getWarningDeviations());
    }

    public BadRequestException() {
    }

    public BadRequestException(String msg) {
        super(msg);
    }

    public BadRequestException(String msg, ErrorDeviation errorDeviation) {
        super(msg, errorDeviation);
    }

    public BadRequestException(String msg, Throwable t, ErrorDeviation errorDeviation) {
        super(msg, t, errorDeviation);
    }

    public BadRequestException(Throwable t, ErrorDeviation errorDeviation,
                               Collection<WarningDeviation> warningDeviations) {
        super(t, errorDeviation, warningDeviations);
    }

    public BadRequestException(ErrorDeviation errorDeviation, Collection<WarningDeviation> warningDeviations) {
        super(errorDeviation, warningDeviations);
    }

    public BadRequestException(ErrorDeviation errorDeviation) {
        super(errorDeviation);
    }

    public BadRequestException(Throwable t, ErrorDeviation errorDeviation) {
        super(t, errorDeviation);
    }
}
