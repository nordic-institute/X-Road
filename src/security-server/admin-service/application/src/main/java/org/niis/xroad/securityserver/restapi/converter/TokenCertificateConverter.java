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
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateOcspStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.securityserver.restapi.util.OcspUtils;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convert token certificate related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class TokenCertificateConverter {

    private final CertificateDetailsConverter certificateDetailsConverter;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final PossibleActionConverter possibleActionConverter;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Convert {@link CertificateInfo} to {@link TokenCertificate}
     * and populate possibleActions
     */
    public TokenCertificate convert(CertificateInfo certificateInfo,
            KeyInfo keyInfo,
            TokenInfo tokenInfo) {
        TokenCertificate tokenCertificate = convert(certificateInfo);
        tokenCertificate.setPossibleActions(possibleActionConverter.convert(
                possibleActionsRuleEngine.getPossibleCertificateActions(
                        tokenInfo, keyInfo, certificateInfo)));
        return tokenCertificate;
    }

    /**
     * Convert {@link CertificateInfo} to {@link TokenCertificate}
     * @param certificateInfo
     * @return {@link TokenCertificate}
     */
    public TokenCertificate convert(CertificateInfo certificateInfo) {
        TokenCertificate tokenCertificate = new TokenCertificate();

        tokenCertificate.setActive(certificateInfo.isActive());
        tokenCertificate.setCertificateDetails(certificateDetailsConverter.convert(certificateInfo));
        if (certificateInfo.getMemberId() != null) {
            tokenCertificate.setOwnerId(clientIdConverter.convertId(certificateInfo.getMemberId()));
        }
        tokenCertificate.setOcspStatus(getOcspStatus(certificateInfo,
                tokenCertificate.getCertificateDetails()));
        tokenCertificate.setSavedToConfiguration(certificateInfo.isSavedToConfiguration());
        tokenCertificate.setStatus(CertificateStatusMapping.map(certificateInfo.getStatus())
                .orElse(null));
        return tokenCertificate;
    }

    /**
     * Logic for determining CertificateOcspStatus, based on
     * token_renderer.rb#cert_ocsp_response
     * @param info
     * @return
     */
    private CertificateOcspStatus getOcspStatus(CertificateInfo info,
            CertificateDetails details) {
        if (!info.isActive()) {
            return CertificateOcspStatus.DISABLED;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(details.getNotAfter())) {
            return CertificateOcspStatus.EXPIRED;
        }
        if (!info.getStatus().equals(CertificateInfo.STATUS_REGISTERED)) {
            return null;
        }
        if (info.getOcspBytes() == null || info.getOcspBytes().length == 0) {
            return CertificateOcspStatus.OCSP_RESPONSE_UNKNOWN;
        }
        String ocspResponseStatus = null;
        try {
            ocspResponseStatus = OcspUtils.getOcspResponseStatus(info.getOcspBytes());
        } catch (OcspUtils.OcspStatusExtractionException e) {
            throw new RuntimeException("extracting OCSP status failed", e);
        }
        switch (ocspResponseStatus) {
            case CertificateInfo.OCSP_RESPONSE_GOOD:
                return CertificateOcspStatus.OCSP_RESPONSE_GOOD;
            case CertificateInfo.OCSP_RESPONSE_SUSPENDED:
                return CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED;
            case CertificateInfo.OCSP_RESPONSE_REVOKED:
                return CertificateOcspStatus.OCSP_RESPONSE_REVOKED;
            case CertificateInfo.OCSP_RESPONSE_UNKNOWN:
                return CertificateOcspStatus.OCSP_RESPONSE_UNKNOWN;
            default:
                throw new AssertionError("unexpected ocsp response status: " + ocspResponseStatus);
        }
    }

    /**
     * Convert a group of {@link CertificateInfo certificateInfos} to a list of
     * {@link TokenCertificate token certificates}
     * @param certificateInfos
     * @return List of {@link TokenCertificate token certificates}
     */
    public List<TokenCertificate> convert(Iterable<CertificateInfo> certificateInfos) {
        return Streams.stream(certificateInfos)
                .map(c -> convert(c))
                .collect(Collectors.toList());
    }

    /**
     * Convert a group of {@link CertificateInfo certificateInfos} to a list of
     * {@link TokenCertificate token certificates}, while populating possibleActions.
     * @param keyInfo all certificates need to belong to the same key
     * @param tokenInfo all certificates need to belong to the same token
     * @param certificateInfos
     * @return List of {@link TokenCertificate token certificates}
     */
    public List<TokenCertificate> convert(Iterable<CertificateInfo> certificateInfos,
            KeyInfo keyInfo,
            TokenInfo tokenInfo) {
        return Streams.stream(certificateInfos)
                .map(c -> convert(c, keyInfo, tokenInfo))
                .collect(Collectors.toList());
    }

}
