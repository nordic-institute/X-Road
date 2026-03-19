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
package org.niis.xroad.confclient.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Configuration client error codes
 */

@RequiredArgsConstructor
public enum ReturnCode {

    RETURN_SUCCESS(0),
    ERROR_CODE_NO_TIMESTAMPING_PROVIDER_FOUND(134),
    ERROR_CODE_LOGMANAGER_UNAVAILABLE(132),
    ERROR_CODE_OCSP_RESPONSE_UNVERIFIED(133),
    ERROR_CODE_OCSP_UNINITIALIZED(131),
    ERROR_CODE_OCSP_RESPONSE_INVALID(130),
    ERROR_CODE_OCSP_CONNECTION_ERROR(129),
    ERROR_CODE_OCSP_FAILED(128),
    ERROR_CODE_TIMESTAMP_UNINITIALIZED(127),
    ERROR_CODE_UNINITIALIZED(126),
    ERROR_CODE_INTERNAL(125),
    ERROR_CODE_INVALID_SIGNATURE_VALUE(124),
    ERROR_CODE_EXPIRED_CONF(123),
    ERROR_CODE_CANNOT_DOWNLOAD_CONF(122),
    ERROR_CODE_MISSING_PRIVATE_PARAMS(121),
    ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT(120),
    ERROR_CODE_NO_NETWORK_CONNECTION(119),
    ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL(118),
    ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE(117),
    ERROR_CODE_MALFORMED_ANCHOR(116),
    ERROR_CODE_ANCHOR_FILE_NOT_FOUND(115),
    ERROR_CODE_UNKNOWN_HOST(114),
    ERROR_CODE_DATABASE_ERROR(113);

    @Getter
    private final int code;
}
