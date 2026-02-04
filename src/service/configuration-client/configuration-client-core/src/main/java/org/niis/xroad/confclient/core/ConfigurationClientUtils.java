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
package org.niis.xroad.confclient.core;

import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_ANCHOR_FILE_NOT_FOUND;
import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_CANNOT_DOWNLOAD_CONF;
import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_DATABASE_ERROR;
import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_EXPIRED_CONF;
import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_INVALID_SIGNATURE_VALUE;
import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_MALFORMED_ANCHOR;
import static org.niis.xroad.confclient.core.ReturnCodes.ERROR_CODE_UNKNOWN_HOST;

/**
 * Utilities for configuration client module
 */
public final class ConfigurationClientUtils {

    private ConfigurationClientUtils() {
    }

    /**
     * Translates exception to error code
     *
     * @param e exception
     * @return error code
     */
    public static int getErrorCode(Exception e) {
        XrdRuntimeException ce = (e instanceof XrdRuntimeException xrdRuntimeException)
                ? xrdRuntimeException
                : XrdRuntimeException.systemException(e);
        return getErrorCode(ce);
    }

    private static int getErrorCode(XrdRuntimeException ex) {
        return switch (ErrorCode.fromCode(ex.getErrorCode())) {
            case HTTP_ERROR, NETWORK_ERROR, GLOBAL_CONF_DOWNLOAD_URL_CONNECTION_FAILURE -> ERROR_CODE_CANNOT_DOWNLOAD_CONF;
            case GLOBAL_CONF_OUTDATED -> ERROR_CODE_EXPIRED_CONF;
            case INVALID_SIGNATURE_VALUE -> ERROR_CODE_INVALID_SIGNATURE_VALUE;
            case MALFORMED_ANCHOR -> ERROR_CODE_MALFORMED_ANCHOR;
            case ANCHOR_FILE_NOT_FOUND -> ERROR_CODE_ANCHOR_FILE_NOT_FOUND;
            case UNKNOWN_HOST -> ERROR_CODE_UNKNOWN_HOST;
            case DATABASE_ERROR -> ERROR_CODE_DATABASE_ERROR;

            case null, default -> ReturnCodes.ERROR_CODE_INTERNAL;
        };
    }
}
