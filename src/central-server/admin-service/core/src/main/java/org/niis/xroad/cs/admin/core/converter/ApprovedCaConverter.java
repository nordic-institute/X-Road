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
package org.niis.xroad.cs.admin.core.converter;

import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.niis.xroad.cs.admin.api.dto.ApprovedCertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificationServiceListItem;
import org.niis.xroad.cs.admin.api.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_CERTIFICATE;

@Component
@RequiredArgsConstructor
public class ApprovedCaConverter {
    private final OcspResponderConverter ocspResponderConverter;
    private final CaInfoConverter caInfoConverter;

    public ApprovedCaEntity toEntity(ApprovedCertificationService certificationService) {
        var caEntity = new ApprovedCaEntity();
        caEntity.setCertProfileInfo(certificationService.getCertificateProfileInfo());
        caEntity.setAuthenticationOnly(certificationService.getTlsAuth());
        X509Certificate certificate = handledCertificationChainRead(certificationService.getCertificate());
        caEntity.setName(CertUtils.getSubjectCommonName(certificate));

        var caInfo = new CaInfoEntity();
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
    public CertificationService convert(ApprovedCaEntity entity) {
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
                .setCertificate(entity.getCaInfo().getCert())
                .setOcspResponders(entity.getCaInfo().getOcspInfos().stream()
                        .map(ocspResponderConverter::toModel)
                        .collect(toList()))
                .setIntermediateCas(entity.getIntermediateCaInfos().stream()
                        .map(caInfoConverter::toCertificateAuthority)
                        .collect(toList()))
                .setCreatedAt(entity.getCreatedAt())
                .setUpdatedAt(entity.getUpdatedAt());
    }

    public List<CertificationServiceListItem> toListItems(Collection<ApprovedCaEntity> entities) {
        return entities.stream()
                .map(this::toListItem)
                .collect(toList());
    }

    private CertificationServiceListItem toListItem(final ApprovedCaEntity approvedCa) {
        return new CertificationServiceListItem()
                .setId(approvedCa.getId())
                .setName(approvedCa.getName())
                .setNotBefore(approvedCa.getCaInfo().getValidFrom())
                .setNotAfter(approvedCa.getCaInfo().getValidTo());
    }

}
