/*
 * The MIT License
 *
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

package org.niis.xroad.common.core.exception;

import ee.ria.xroad.common.HttpStatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XrdRuntimeHttpExceptionTest {

    @Test
    void shouldCreateExceptionWithHttpStatus() {
        String identifier = "http-test";
        ExceptionCategory category = ExceptionCategory.SYSTEM;
        var errorDeviation = ErrorCode.NOT_FOUND;
        String details = "Resource not found";
        HttpStatus httpStatus = HttpStatus.NOT_FOUND;

        XrdRuntimeHttpException exception = XrdRuntimeHttpException.builder(errorDeviation)
                .identifier(identifier)
                .details(details)
                .httpStatus(httpStatus)
                .build();

        assertEquals(identifier, exception.getIdentifier());
        assertEquals(category, exception.getCategory());
        assertEquals(errorDeviation.code(), exception.getCode());
        assertEquals(details, exception.getDetails());
        assertEquals(httpStatus, exception.getHttpStatus().orElse(null));

        String expectedMessage = "[http-test] [SYSTEM] not_found: Resource not found";
        assertEquals(expectedMessage, exception.toString());
    }

}
