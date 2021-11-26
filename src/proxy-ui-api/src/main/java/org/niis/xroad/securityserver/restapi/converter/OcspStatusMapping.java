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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.DiagnosticsErrorCodes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between OcspStatus in api (enum) and model (DiagnosticsErrorCode)
 */
@Getter
@RequiredArgsConstructor
public enum OcspStatusMapping {
    SUCCESS(DiagnosticsErrorCodes.RETURN_SUCCESS,
            OcspStatus.SUCCESS),
    ERROR_CODE_OCSP_CONNECTION_ERROR(DiagnosticsErrorCodes.ERROR_CODE_OCSP_CONNECTION_ERROR,
            OcspStatus.ERROR_CODE_OCSP_CONNECTION_ERROR),
    ERROR_CODE_OCSP_FAILED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_FAILED,
            OcspStatus.ERROR_CODE_OCSP_FAILED),
    ERROR_CODE_OCSP_RESPONSE_INVALID(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID,
            OcspStatus.ERROR_CODE_OCSP_RESPONSE_INVALID),
    ERROR_CODE_OCSP_UNINITIALIZED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED,
            OcspStatus.ERROR_CODE_OCSP_UNINITIALIZED),
    ERROR_CODE_OCSP_RESPONSE_UNVERIFIED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_UNVERIFIED,
            OcspStatus.ERROR_CODE_OCSP_RESPONSE_UNVERIFIED),
    UNKNOWN(-1, OcspStatus.UNKNOWN);

    private static final int DIAGNOSTICS_ERROR_CODE_UNKNOWN = -1;
    private final Integer diagnosticsErrorCode;
    private final OcspStatus ocspStatus;

    /**
     * Return matching OcspStatus, if any
     *
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<OcspStatus> map(Integer diagnosticsErrorCode) {
        return getFor(diagnosticsErrorCode).map(OcspStatusMapping::getOcspStatus);
    }

    /**
     * return OcspStatusMapping matching the given DiagnosticsErrorCode, if any
     *
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<OcspStatusMapping> getFor(Integer diagnosticsErrorCode) {
        return Optional.of(Arrays.stream(values())
                .filter(mapping -> mapping.diagnosticsErrorCode.equals(diagnosticsErrorCode))
                .findFirst().orElse(UNKNOWN));
    }
}
