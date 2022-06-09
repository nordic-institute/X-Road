/**
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
package org.niis.xroad.centralserver.restapi.converter;

import ee.ria.xroad.common.util.CertUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationService;
import org.niis.xroad.centralserver.openapi.model.CertificateDetails;
import org.niis.xroad.centralserver.restapi.dto.ApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.entity.CaInfo;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;

import static org.apache.commons.lang3.StringUtils.stripToEmpty;

@Slf4j
@Component
public class CertificationServiceConverter {

    @SneakyThrows
    public ApprovedCa toEntity(ApprovedCertificationServiceDto approvedCa) {
        var caEntity = new ApprovedCa();
        caEntity.setCertProfileInfo(approvedCa.getCertificateProfileInfo());
        caEntity.setAuthenticationOnly(approvedCa.getTlsAuth());
        X509Certificate[] certificate = CertUtils.readCertificateChain(approvedCa.getCertificate().getBytes());
        caEntity.setName(CertUtils.getSubjectCommonName(certificate[0]));

        var caInfo = new CaInfo();
        caInfo.setCert(approvedCa.getCertificate().getBytes());
        caInfo.setValidFrom(certificate[0].getNotBefore());
        caInfo.setValidTo(certificate[0].getNotAfter());
        caEntity.setCaInfo(caInfo);
        return caEntity;
    }

    @SneakyThrows
    public ApprovedCertificationService toDomain(ApprovedCa approvedCa) {
        var certificateDetails = new CertificateDetails()
                .subjectCommonName(approvedCa.getName())
                .issuerCommonName(stripToEmpty(getIssuerCommonName(approvedCa.getCaInfo().getCert())))
                .notBefore(FormatUtils.fromDateToOffsetDateTime(approvedCa.getCaInfo().getValidFrom()))
                .notAfter(FormatUtils.fromDateToOffsetDateTime(approvedCa.getCaInfo().getValidTo()));
        return new ApprovedCertificationService()
                .id(String.valueOf(approvedCa.getId()))
                .tlsAuth(approvedCa.getAuthenticationOnly())
                .certificateProfileInfo(approvedCa.getCertProfileInfo())
                .caCertificate(certificateDetails);
    }

    private String getIssuerCommonName(byte[] bytes) {
        try {
            X509Certificate[] certificate = CertUtils.readCertificateChain(bytes);
            return CertUtils.getIssuerCommonName(certificate[0]);
        } catch (Exception e) {
            log.error("Failed to read the certificate chain.");
        }
        return null;
    }

}
