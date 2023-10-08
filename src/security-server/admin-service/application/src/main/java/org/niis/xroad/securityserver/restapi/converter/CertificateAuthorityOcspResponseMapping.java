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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateAuthorityOcspResponse;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link CertificateAuthorityOcspResponse} in api (enum) and model {@link CertificateInfo}
 * ocsp response string or CertificateAuthorityService.OCSP_RESPONSE_NOT_AVAILABLE string
 */
@Getter
@RequiredArgsConstructor
public enum CertificateAuthorityOcspResponseMapping {
    NOT_AVAILABLE(
            CertificateAuthorityService.OCSP_RESPONSE_NOT_AVAILABLE, CertificateAuthorityOcspResponse.NOT_AVAILABLE),
    OCSP_RESPONSE_GOOD(
            CertificateInfo.OCSP_RESPONSE_GOOD, CertificateAuthorityOcspResponse.OCSP_RESPONSE_GOOD),
    OCSP_RESPONSE_REVOKED(
            CertificateInfo.OCSP_RESPONSE_REVOKED, CertificateAuthorityOcspResponse.OCSP_RESPONSE_REVOKED),
    OCSP_RESPONSE_SUSPENDED(
            CertificateInfo.OCSP_RESPONSE_SUSPENDED, CertificateAuthorityOcspResponse.OCSP_RESPONSE_SUSPENDED),
    OCSP_RESPONSE_UNKNOWN(
            CertificateInfo.OCSP_RESPONSE_UNKNOWN, CertificateAuthorityOcspResponse.OCSP_RESPONSE_UNKNOWN);

    private final String ocspResponse;
    private final CertificateAuthorityOcspResponse certificateAuthorityOcspResponse;

    /**
     * Return matching ocspResponse string, if any
     * @param certificateAuthorityOcspResponse
     */
    public static Optional<String> map(CertificateAuthorityOcspResponse certificateAuthorityOcspResponse) {
        return getFor(certificateAuthorityOcspResponse).map(CertificateAuthorityOcspResponseMapping::getOcspResponse);
    }

    /**
     * Return matching {@link CertificateAuthorityOcspResponse}, if any
     * @param ocspResponse
     */
    public static Optional<CertificateAuthorityOcspResponse> map(String ocspResponse) {
        return getFor(ocspResponse).map(CertificateAuthorityOcspResponseMapping::getCertificateAuthorityOcspResponse);
    }

    /**
     * Return matching {@link CertificateAuthorityOcspResponseMapping}, if any
     * @param ocspResponse
     */
    public static Optional<CertificateAuthorityOcspResponseMapping> getFor(String ocspResponse) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.ocspResponse.equals(ocspResponse))
                .findFirst();
    }

    /**
     * Return matching {@link CertificateAuthorityOcspResponseMapping}, if any
     * @param certificateAuthorityOcspResponse
     */
    public static Optional<CertificateAuthorityOcspResponseMapping> getFor(
            CertificateAuthorityOcspResponse certificateAuthorityOcspResponse) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.certificateAuthorityOcspResponse.equals(certificateAuthorityOcspResponse))
                .findFirst();
    }

}
