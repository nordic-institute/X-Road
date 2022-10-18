/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.dto.converter;

import ee.ria.xroad.common.util.CertUtils;

import lombok.SneakyThrows;
import org.niis.xroad.centralserver.restapi.dto.ApprovedCertificationService;
import org.niis.xroad.centralserver.restapi.dto.CertificationService;
import org.niis.xroad.centralserver.restapi.dto.CertificationServiceListItem;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.entity.CaInfo;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.INVALID_CERTIFICATE;

@Component
public class ApprovedCaConverter {

    public ApprovedCa toEntity(ApprovedCertificationService certificationService) {
        var caEntity = new ApprovedCa();
        caEntity.setCertProfileInfo(certificationService.getCertificateProfileInfo());
        caEntity.setAuthenticationOnly(certificationService.getTlsAuth());
        X509Certificate certificate = handledCertificationChainRead(certificationService.getCertificate());
        caEntity.setName(CertUtils.getSubjectCommonName(certificate));

        var caInfo = new CaInfo();
        caInfo.setCert(certificationService.getCertificate());
        caInfo.setValidFrom(certificate.getNotBefore().toInstant());
        caInfo.setValidTo(certificate.getNotAfter().toInstant());
        caEntity.setCaInfo(caInfo);
        return caEntity;
    }

    private X509Certificate handledCertificationChainRead(byte[] certificate) {
        try {
            return CertUtils.readCertificateChain(certificate)[0];
        } catch (Exception e) {
            throw new ValidationFailureException(INVALID_CERTIFICATE);
        }
    }

    @SneakyThrows
    public CertificationService convert(ApprovedCa entity) {
        final X509Certificate[] certificates = CertUtils.readCertificateChain(entity.getCaInfo().getCert());
        final X509Certificate certificate = certificates[0];

        return new CertificationService()
                .setId(entity.getId())
                .setName(entity.getName())
                .setCertificateProfileInfo(entity.getCertProfileInfo())
                .setTlsAuth(entity.getAuthenticationOnly())
                .setIssuerDistinguishedName(certificate.getIssuerDN().getName())
                .setSubjectDistinguishedName(certificate.getSubjectDN().getName())
                .setNotBefore(entity.getCaInfo().getValidFrom())
                .setNotAfter(entity.getCaInfo().getValidTo())
                .setCreatedAt(entity.getCreatedAt())
                .setUpdatedAt(entity.getUpdatedAt());
    }

    public List<CertificationServiceListItem> toListItems(Collection<ApprovedCa> entities) {
        return entities.stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    private CertificationServiceListItem toListItem(final ApprovedCa approvedCa) {
        return new CertificationServiceListItem()
                .setId(approvedCa.getId())
                .setName(approvedCa.getName())
                .setNotBefore(approvedCa.getCaInfo().getValidFrom())
                .setNotAfter(approvedCa.getCaInfo().getValidTo());
    }

}
