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

import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_CANNOT_DOWNLOAD_CONF;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_EXPIRED_CONF;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_INTERNAL;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL;
import static ee.ria.xroad.common.DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT;
import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;

/**
 * Utilities for configuration client module
 */
public final class DiagnosticsUtils {

    private DiagnosticsUtils() {
    }

    /**
     * Translate exception to error code
     * @param e exception
     * @return error code
     */
    public static int getErrorCode(Exception e) {

        return switch (e) {
            case CodedException ce when X_HTTP_ERROR.equals(ce.getFaultCode()) -> ERROR_CODE_CANNOT_DOWNLOAD_CONF;
            case CodedException ce when X_OUTDATED_GLOBALCONF.equals(ce.getFaultCode()) -> ERROR_CODE_EXPIRED_CONF;
            case CodedException ce when X_INVALID_SIGNATURE_VALUE.equals(ce.getFaultCode()) -> ERROR_CODE_INVALID_SIGNATURE_VALUE;
            case UnknownHostException ignored -> ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT;
            case TimeoutException ignored -> ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT;
            case SocketTimeoutException ignored -> ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT;
            case MalformedURLException ignored -> ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL;
            case null, default -> ERROR_CODE_INTERNAL;
        };

    }
}
