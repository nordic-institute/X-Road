/*
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
package ee.ria.xroad.common;

import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Utilities for configuration client module
 */
public final class DiagnosticsUtils {

    private DiagnosticsUtils() {
    }

    public static ErrorCode getErrorCode(Exception e) {
        return switch (e) {
            case XrdRuntimeException xre -> getErrorCode(xre);
            case UnknownHostException ignored -> ErrorCode.UNKNOWN_HOST;
            case SQLException ignored -> ErrorCode.DATABASE_ERROR;
            case TimeoutException ignored -> ErrorCode.TIMESTAMP_REQUEST_TIMED_OUT;
            case SocketTimeoutException ignored -> ErrorCode.TIMESTAMP_REQUEST_TIMED_OUT;
            case MalformedURLException ignored -> ErrorCode.MALFORMED_TIMESTAMP_SERVER_URL;
            case null, default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private static ErrorCode getErrorCode(XrdRuntimeException e) {
        return Arrays.stream(ErrorCode.values())
                .filter(ec -> ec.code().equals(e.getErrorCode()))
                .findFirst()
                .orElse(ErrorCode.INTERNAL_ERROR);
    }

    public static List<String> getErrorCodeMetadata(Exception e) {
        if (e instanceof XrdRuntimeException xre) {
             return xre.getErrorCodeMetadata();
        }
        return null;
    }


}
