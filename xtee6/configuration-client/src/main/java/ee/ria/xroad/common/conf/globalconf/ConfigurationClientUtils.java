/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;

import static ee.ria.xroad.common.DiagnosticsErrorCodes.*;
import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Utilities for configuration client module
 */
public final class ConfigurationClientUtils {

    private ConfigurationClientUtils() {
    }

    /**
     * Translates exception to error code
     * @param e exception
     * @return error code
     */
    public static int getErrorCode(Exception e) {
        if (e instanceof CodedException) {
            CodedException ce = (CodedException) e;
            switch (ce.getFaultCode()) {
                case X_HTTP_ERROR:
                    return ERROR_CODE_CANNOT_DOWNLOAD_CONF;
                case X_OUTDATED_GLOBALCONF:
                    return ERROR_CODE_EXPIRED_CONF;
                case X_INVALID_SIGNATURE_VALUE:
                    return ERROR_CODE_INVALID_SIGNATURE_VALUE;
                default: // do nothing
                    break;
            }
        }
        return ERROR_CODE_INTERNAL;
    }
}
