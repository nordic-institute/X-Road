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
import org.niis.xroad.securityserver.restapi.openapi.model.ConfigurationStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between ConfigurationStatus in api (enum) and model (DiagnosticsErrorCode)
 */
@Getter
@RequiredArgsConstructor
public enum ConfigurationStatusMapping {
    SUCCESS(DiagnosticsErrorCodes.RETURN_SUCCESS,
            ConfigurationStatus.SUCCESS),
    ERROR_CODE_INTERNAL(DiagnosticsErrorCodes.ERROR_CODE_INTERNAL,
            ConfigurationStatus.ERROR_CODE_INTERNAL),
    ERROR_CODE_INVALID_SIGNATURE_VALUE(DiagnosticsErrorCodes.ERROR_CODE_INVALID_SIGNATURE_VALUE,
            ConfigurationStatus.ERROR_CODE_INVALID_SIGNATURE_VALUE),
    ERROR_CODE_EXPIRED_CONF(DiagnosticsErrorCodes.ERROR_CODE_EXPIRED_CONF,
            ConfigurationStatus.ERROR_CODE_EXPIRED_CONF),
    ERROR_CODE_CANNOT_DOWNLOAD_CONF(DiagnosticsErrorCodes.ERROR_CODE_CANNOT_DOWNLOAD_CONF,
            ConfigurationStatus.ERROR_CODE_CANNOT_DOWNLOAD_CONF),
    ERROR_CODE_MISSING_PRIVATE_PARAM(DiagnosticsErrorCodes.ERROR_CODE_MISSING_PRIVATE_PARAMS,
            ConfigurationStatus.ERROR_CODE_MISSING_PRIVATE_PARAMS),
    ERROR_CODE_UNINITIALIZED(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED,
            ConfigurationStatus.ERROR_CODE_UNINITIALIZED),
    UNKNOWN(-1,
            ConfigurationStatus.UNKNOWN);

    private static final int DIAGNOSTICS_ERROR_CODE_UNKNOWN = -1;
    private final Integer diagnosticsErrorCode;
    private final ConfigurationStatus configurationStatus;

    /**
     * Return matching ConfigurationStatus, if any
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<ConfigurationStatus> map(Integer diagnosticsErrorCode) {
        return getFor(diagnosticsErrorCode).map(ConfigurationStatusMapping::getConfigurationStatus);
    }

    /**
     * return ConfigurationStatusMapping matching the given DiagnosticsErrorCode, if any
     * @param diagnosticsErrorCode
     * @return
     */
    public static Optional<ConfigurationStatusMapping> getFor(Integer diagnosticsErrorCode) {
        Optional<ConfigurationStatusMapping> result = Arrays.stream(values())
                .filter(mapping -> mapping.diagnosticsErrorCode.equals(diagnosticsErrorCode))
                .findFirst();
        if (result.isPresent()) {
            return result;
        }  else {
            return getFor(DIAGNOSTICS_ERROR_CODE_UNKNOWN);
        }
    }
}
