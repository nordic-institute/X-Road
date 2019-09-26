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

import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import com.google.common.collect.Streams;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.CertificateOcspStatus;
import org.niis.xroad.restapi.openapi.model.TokenCertificate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convert token certificate related data between openapi and service domain classes
 */
@Component
public class TokenCertificateConverter {

    private final CertificateDetailsConverter certificateDetailsConverter;
    private final ClientConverter clientConverter;

    @Autowired
    public TokenCertificateConverter(CertificateDetailsConverter certificateDetailsConverter,
            ClientConverter clientConverter) {
        this.certificateDetailsConverter = certificateDetailsConverter;
        this.clientConverter = clientConverter;
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
            tokenCertificate.setOwnerId(clientConverter.convertId(certificateInfo.getMemberId()));
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
        CertificateStatus certificateStatus = getCertificateStatus(info.getOcspBytes());
        if (certificateStatus == null) {
            return CertificateOcspStatus.OCSP_RESPONSE_GOOD;
        }
        if (certificateStatus instanceof RevokedStatus) {
            RevokedStatus revokedStatus = (RevokedStatus) certificateStatus;
            if (revokedStatus.hasRevocationReason()
                        && revokedStatus.getRevocationReason() == CRLReason.certificateHold) {
                return CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED;
            }
            return CertificateOcspStatus.OCSP_RESPONSE_REVOKED;
        }
        return CertificateOcspStatus.OCSP_RESPONSE_UNKNOWN;
    }

    /**
     * From ee.ria.xroad.signer.console.Utils#getOcspStatus
     * @param ocspBytes
     * @return
     */
    private CertificateStatus getCertificateStatus(byte[] ocspBytes) {
        try {
            OCSPResp response = new OCSPResp(ocspBytes);
            BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
            SingleResp resp = basicResponse.getResponses()[0];
            CertificateStatus status = resp.getCertStatus();
            return status;
        } catch (IOException | OCSPException e) {
            throw new RuntimeException("Certificate OCSP response processing failed", e);
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
                .map(this::convert)
                .collect(Collectors.toList());
    }

}
