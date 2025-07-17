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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateStatusDto;
import org.niis.xroad.signer.api.dto.CertificateInfo;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link CertificateStatusDto} in api (enum) and model {@link CertificateInfo} status string
 */
@Getter
@RequiredArgsConstructor
public enum CertificateStatusMapping {
    SAVED(CertificateInfo.STATUS_SAVED, CertificateStatusDto.SAVED),
    REGISTRATION_IN_PROGRESS(CertificateInfo.STATUS_REGINPROG, CertificateStatusDto.REGISTRATION_IN_PROGRESS),
    REGISTERED(CertificateInfo.STATUS_REGISTERED, CertificateStatusDto.REGISTERED),
    DELETION_IN_PROGRESS(CertificateInfo.STATUS_DELINPROG, CertificateStatusDto.DELETION_IN_PROGRESS),
    GLOBAL_ERROR(CertificateInfo.STATUS_GLOBALERR, CertificateStatusDto.GLOBAL_ERROR);

    private final String status;
    private final CertificateStatusDto certificateStatusDto;

    /**
     * Return matching status, if any
     * @param certificateStatusDto
     */
    public static Optional<String> map(CertificateStatusDto certificateStatusDto) {
        return getFor(certificateStatusDto).map(CertificateStatusMapping::getStatus);
    }

    /**
     * Return matching {@link CertificateStatusDto}, if any
     * @param status
     */
    public static Optional<CertificateStatusDto> map(String status) {
        return getFor(status).map(CertificateStatusMapping::getCertificateStatusDto);
    }

    /**
     * Return matching {@link CertificateStatusMapping}, if any
     * @param status
     */
    public static Optional<CertificateStatusMapping> getFor(String status) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.status.equals(status))
                .findFirst();
    }

    /**
     * Return matching {@link CertificateStatusMapping}, if any
     * @param certificateStatusDto
     */
    public static Optional<CertificateStatusMapping> getFor(CertificateStatusDto certificateStatusDto) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.certificateStatusDto.equals(certificateStatusDto))
                .findFirst();
    }

}
