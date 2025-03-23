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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.DiagnosticsErrorCodes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspStatusDto;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between OcspStatusDto in api (enum) and model (DiagnosticsErrorCode)
 */
@Getter
@RequiredArgsConstructor
public enum OcspStatusMapping {
    SUCCESS(DiagnosticsErrorCodes.RETURN_SUCCESS,
            OcspStatusDto.SUCCESS),
    ERROR_CODE_OCSP_CONNECTION_ERROR(DiagnosticsErrorCodes.ERROR_CODE_OCSP_CONNECTION_ERROR,
            OcspStatusDto.ERROR_CODE_OCSP_CONNECTION_ERROR),
    ERROR_CODE_OCSP_FAILED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_FAILED,
            OcspStatusDto.ERROR_CODE_OCSP_FAILED),
    ERROR_CODE_OCSP_RESPONSE_INVALID(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID,
            OcspStatusDto.ERROR_CODE_OCSP_RESPONSE_INVALID),
    ERROR_CODE_OCSP_UNINITIALIZED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED,
            OcspStatusDto.ERROR_CODE_OCSP_UNINITIALIZED),
    ERROR_CODE_OCSP_RESPONSE_UNVERIFIED(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_UNVERIFIED,
            OcspStatusDto.ERROR_CODE_OCSP_RESPONSE_UNVERIFIED),
    UNKNOWN(-1, OcspStatusDto.UNKNOWN);

    private static final int DIAGNOSTICS_ERROR_CODE_UNKNOWN = -1;
    private final Integer diagnosticsErrorCode;
    private final OcspStatusDto ocspStatusDto;

    /**
     * Return matching OcspStatusDto, if any
     *
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<OcspStatusDto> map(Integer diagnosticsErrorCode) {
        return getFor(diagnosticsErrorCode).map(OcspStatusMapping::getOcspStatusDto);
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
