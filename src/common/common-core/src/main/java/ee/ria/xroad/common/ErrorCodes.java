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

import org.niis.xroad.common.core.exception.XrdRuntimeException;

/**
 * Enumeration class for various error codes.
 */
public final class ErrorCodes {

    // Error code prefixes

    public static final String SERVER_SERVERPROXY_X = "server.serverproxy";
    public static final String CLIENT_X = "client";
    public static final String SERVER_CLIENTPROXY_X = "server.clientproxy";
    public static final String SERVER_SERVER_PROXY_OPMONITOR_X = SERVER_SERVERPROXY_X + ".opmonitor";

    // Verification errors

    public static final String X_CANNOT_CREATE_SIGNATURE = "cannot_create_signature";
    public static final String X_CANNOT_CREATE_CERT_PATH = "cannot_create_cert_path";
    public static final String X_MALFORMED_SIGNATURE = "malformed_signature";
    public static final String X_INVALID_CERT_PATH_X = "invalid_cert_path";
    public static final String X_SIGNATURE_VERIFICATION_X = "signature_verification";

    // Message processing errors

    public static final String X_SSL_AUTH_FAILED = "ssl_authentication_failed";
    public static final String X_LOGGING_FAILED_X = "logging_failed";
    public static final String X_TIMESTAMPING_FAILED_X = "timestamping_failed";
    public static final String X_SERVICE_FAILED_X = "service_failed";

    // Signer Errors

    public static final String X_CANNOT_SIGN = "cannot_sign";
    public static final String X_FAILED_TO_GENERATE_R_KEY = "failed_to_generate_private_key";

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code.
     *
     * @param ex the exception
     * @return translated XrdRuntimeException
     */
    public static XrdRuntimeException translateException(Throwable ex) {
        return XrdRuntimeException.systemException(ex);
    }

    /**
     * Translates technical exceptions to proxy exceptions with
     * the appropriate error code. It also prepends the prefix
     * in front of error code.
     *
     * @param prefix the prefix
     * @param ex     the exception
     * @return translated exception with prefix
     */
    public static XrdRuntimeException translateWithPrefix(String prefix, Throwable ex) {
        return translateException(ex).withPrefix(prefix);
    }

    private ErrorCodes() {
    }
}
