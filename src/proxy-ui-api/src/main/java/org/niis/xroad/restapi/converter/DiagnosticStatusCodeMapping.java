/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.DiagnosticsErrorCodes;

import lombok.Getter;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusCode;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between DiagnosticStatusCode in api (enum) and model (DiagnosticsErrorCode)
 */
@Getter
public enum DiagnosticStatusCodeMapping {
    SUCCESS(DiagnosticsErrorCodes.RETURN_SUCCESS, DiagnosticStatusCode.SUCCESS),
    ERROR_CODE_INTERNAL(DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, DiagnosticStatusCode.ERROR_CODE_INTERNAL),
    ERROR_CODE_INVALID_SIGNATURE_VALUE(DiagnosticsErrorCodes.ERROR_CODE_INVALID_SIGNATURE_VALUE,
            DiagnosticStatusCode.ERROR_CODE_INVALID_SIGNATURE_VALUE),
    ERROR_CODE_EXPIRED_CONF(DiagnosticsErrorCodes.ERROR_CODE_EXPIRED_CONF,
            DiagnosticStatusCode.ERROR_CODE_EXPIRED_CONF),
    ERROR_CODE_CANNOT_DOWNLOAD_CONF(DiagnosticsErrorCodes.ERROR_CODE_CANNOT_DOWNLOAD_CONF,
            DiagnosticStatusCode.ERROR_CODE_CANNOT_DOWNLOAD_CONF),
    ERROR_CODE_MISSING_PRIVATE_PARAMS(DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS,
            DiagnosticStatusCode.ERROR_CODE_MISSING_PRIVATE_PARAMS),
    ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT,
            DiagnosticStatusCode.ERROR_CODE_TIMESTAMP_REQUEST_TIMED_OUT),
    ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL(DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL,
            DiagnosticStatusCode.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL),
    ERROR_CODE_UNINITIALIZED(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED,
            DiagnosticStatusCode.ERROR_CODE_UNINITIALIZED),
    ERROR_CODE_TIMESTAMP_UNINITIALIZED(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED,
            DiagnosticStatusCode.ERROR_CODE_TIMESTAMP_UNINITIALIZED),
    ERROR_CODE_OCSP_CONNECTION_ERROR(DiagnosticsErrorCodes.ERROR_CODE_OCSP_CONNECTION_ERROR,
            DiagnosticStatusCode.ERROR_CODE_OCSP_CONNECTION_ERROR),
    ERROR_CODE_OCSP_FAILED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_FAILED, DiagnosticStatusCode.ERROR_CODE_OCSP_FAILED),
    ERROR_CODE_OCSP_RESPONSE_INVALID(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID,
            DiagnosticStatusCode.ERROR_CODE_OCSP_RESPONSE_INVALID),
    ERROR_CODE_OCSP_UNINITIALIZED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED,
            DiagnosticStatusCode.ERROR_CODE_OCSP_UNINITIALIZED),
    ERROR_CODE_LOGMANAGER_UNAVAILABLE(DiagnosticsErrorCodes.ERROR_CODE_LOGMANAGER_UNAVAILABLE,
            DiagnosticStatusCode.ERROR_CODE_LOGMANAGER_UNAVAILABLE),
    UNKNOWN(-1, DiagnosticStatusCode.UNKNOWN);

    private static final int DIAGNOSTICS_ERROR_CODE_UNKNOWN = -1;
    private final Integer diagnosticsErrorCode;
    private final DiagnosticStatusCode diagnosticStatusCode;

    DiagnosticStatusCodeMapping(Integer diagnosticsErrorCode, DiagnosticStatusCode diagnosticStatusCode) {
        this.diagnosticsErrorCode = diagnosticsErrorCode;
        this.diagnosticStatusCode = diagnosticStatusCode;
    }

    /**
     * Return matching DiagnosticStatusCode, if any
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<DiagnosticStatusCode> map(Integer diagnosticsErrorCode) {
        return getFor(diagnosticsErrorCode).map(DiagnosticStatusCodeMapping::getDiagnosticStatusCode);
    }

    /**
     * return DiagnosticsStatusCodeMapping matching the given DiagnosticsErrorCode, if any
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<DiagnosticStatusCodeMapping> getFor(Integer diagnosticsErrorCode) {
        Optional<DiagnosticStatusCodeMapping> result = Arrays.stream(values())
                .filter(mapping -> mapping.diagnosticsErrorCode.equals(diagnosticsErrorCode))
                .findFirst();
        if (result.isPresent()) {
            return result;
        }  else {
            return getFor(DIAGNOSTICS_ERROR_CODE_UNKNOWN);
        }
    }
}
