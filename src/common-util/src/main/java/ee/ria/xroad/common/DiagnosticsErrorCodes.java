/**
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

/**
 * Configuration client error codes
 */
public final class DiagnosticsErrorCodes {

    private DiagnosticsErrorCodes() {
    }

    public static final int RETURN_SUCCESS = 0;
    public static final int ERROR_CODE_LOGMANAGER_UNAVAILABLE = 132;
    public static final int ERROR_CODE_OCSP_RESPONSE_UNVERIFIED = 133;
    public static final int ERROR_CODE_OCSP_UNINITIALIZED = 131;
    public static final int ERROR_CODE_OCSP_RESPONSE_INVALID = 130;
    public static final int ERROR_CODE_OCSP_CONNECTION_ERROR = 129;
    public static final int ERROR_CODE_OCSP_FAILED = 128;
    public static final int ERROR_CODE_TIMESTAMP_UNINITIALIZED = 127;
    public static final int ERROR_CODE_UNINITIALIZED = 126;
    public static final int ERROR_CODE_INTERNAL = 125;
    public static final int ERROR_CODE_INVALID_SIGNATURE_VALUE = 124;
    public static final int ERROR_CODE_EXPIRED_CONF = 123;
    public static final int ERROR_CODE_CANNOT_DOWNLOAD_CONF = 122;
    public static final int ERROR_CODE_MISSING_PRIVATE_PARAMS = 121;
    public static final int ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT = 120;
    public static final int ERROR_CODE_NO_NETWORK_CONNECTION = 119;
    public static final int ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL = 118;
    public static final int ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE = 117;

}
