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

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link CertificateStatus} in api (enum) and model {@link CertificateInfo} status string
 */
@Getter
@RequiredArgsConstructor
public enum CertificateStatusMapping {
    SAVED(CertificateInfo.STATUS_SAVED, CertificateStatus.SAVED),
    REGISTRATION_IN_PROGRESS(CertificateInfo.STATUS_REGINPROG, CertificateStatus.REGISTRATION_IN_PROGRESS),
    REGISTERED(CertificateInfo.STATUS_REGISTERED, CertificateStatus.REGISTERED),
    DELETION_IN_PROGRESS(CertificateInfo.STATUS_DELINPROG, CertificateStatus.DELETION_IN_PROGRESS),
    GLOBAL_ERROR(CertificateInfo.STATUS_GLOBALERR, CertificateStatus.GLOBAL_ERROR);

    private final String status;
    private final CertificateStatus certificateStatus;

    /**
     * Return matching status, if any
     * @param certificateStatus
     */
    public static Optional<String> map(CertificateStatus certificateStatus) {
        return getFor(certificateStatus).map(CertificateStatusMapping::getStatus);
    }

    /**
     * Return matching {@link CertificateStatus}, if any
     * @param status
     */
    public static Optional<CertificateStatus> map(String status) {
        return getFor(status).map(CertificateStatusMapping::getCertificateStatus);
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
     * @param certificateStatus
     */
    public static Optional<CertificateStatusMapping> getFor(CertificateStatus certificateStatus) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.certificateStatus.equals(certificateStatus))
                .findFirst();
    }

}
