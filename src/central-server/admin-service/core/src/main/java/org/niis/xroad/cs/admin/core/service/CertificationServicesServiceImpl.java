/*
 * The MIT License
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.CertUtils;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.ApprovedCertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificationServiceListItem;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.core.converter.ApprovedCaConverter;
import org.niis.xroad.cs.admin.core.converter.CaInfoConverter;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.OcspResponderConverter;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.ApprovedCaRepository;
import org.niis.xroad.cs.admin.core.repository.CaInfoRepository;
import org.niis.xroad.cs.admin.core.repository.OcspInfoRepository;
import org.niis.xroad.cs.admin.core.validation.CertificateProfileInfoValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CERTIFICATION_SERVICE_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_CERTIFICATE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.AUTHENTICATION_ONLY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERTIFICATE_PROFILE_INFO;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_URL;

@Service
@Transactional
@RequiredArgsConstructor
public class CertificationServicesServiceImpl implements CertificationServicesService {
    private final ApprovedCaRepository approvedCaRepository;
    private final OcspInfoRepository ocspInfoRepository;
    private final CaInfoRepository caInfoRepository;
    private final AuditDataHelper auditDataHelper;
    private final ApprovedCaConverter approvedCaConverter;
    private final OcspResponderConverter ocspResponderConverter;
    private final CaInfoConverter caInfoConverter;
    private final CertificateConverter certConverter;

    @Override
    public CertificationService add(ApprovedCertificationService certificationService) {
        CertificateProfileInfoValidator.validate(certificationService.getCertificateProfileInfo());

        final var approvedCaEntity = new ApprovedCaEntity();
        approvedCaEntity.setCertProfileInfo(certificationService.getCertificateProfileInfo());
        approvedCaEntity.setAuthenticationOnly(certificationService.getTlsAuth());
        X509Certificate certificate = handledCertificationChainRead(certificationService.getCertificate());
        approvedCaEntity.setName(CertUtils.getSubjectCommonName(certificate));

        final var caInfo = new CaInfoEntity();
        caInfo.setCert(certificationService.getCertificate());
        caInfo.setValidFrom(certificate.getNotBefore().toInstant());
        caInfo.setValidTo(certificate.getNotAfter().toInstant());

        approvedCaEntity.setCaInfo(caInfoRepository.saveAndFlush(caInfo));

        final ApprovedCaEntity persistedApprovedCa = approvedCaRepository.saveAndFlush(approvedCaEntity);
        addAuditData(persistedApprovedCa);

        return approvedCaConverter.convert(persistedApprovedCa);
    }

    private X509Certificate handledCertificationChainRead(byte[] certificate) {
        try {
            return CertUtils.readCertificateChain(certificate)[0];
        } catch (Exception e) {
            throw new ValidationFailureException(INVALID_CERTIFICATE);
        }
    }

    @Override
    public CertificationService get(Integer id) {
        return approvedCaConverter.convert(getById(id));
    }

    @Override
    public void delete(Integer id) {
        auditDataHelper.put(CA_ID, id);

        final ApprovedCaEntity entity = getById(id);
        approvedCaRepository.delete(entity);
    }

    @Override
    public CertificationService update(CertificationService approvedCa) {
        ApprovedCaEntity persistedApprovedCa = getById(approvedCa.getId());
        Optional.ofNullable(approvedCa.getCertificateProfileInfo())
                .ifPresent(certProfile -> {
                    CertificateProfileInfoValidator.validate(certProfile);
                    persistedApprovedCa.setCertProfileInfo(certProfile);
                });
        Optional.ofNullable(approvedCa.getTlsAuth()).ifPresent(persistedApprovedCa::setAuthenticationOnly);
        final ApprovedCaEntity updatedApprovedCa = approvedCaRepository.save(persistedApprovedCa);
        addAuditData(updatedApprovedCa);

        return approvedCaConverter.convert(updatedApprovedCa);
    }

    private ApprovedCaEntity getById(Integer id) {
        return approvedCaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(CERTIFICATION_SERVICE_NOT_FOUND));
    }

    @Override
    public CertificateDetails getCertificateDetails(Integer id) {
        return approvedCaRepository.findById(id)
                .map(ApprovedCaEntity::getCaInfo)
                .map(CaInfoEntity::getCert)
                .map(certConverter::toCertificateDetails)
                .orElseThrow(() -> new NotFoundException(CERTIFICATION_SERVICE_NOT_FOUND));
    }

    @Override
    public CertificateAuthority addIntermediateCa(Integer certificationServiceId, byte[] cert) {
        final CaInfoEntity caInfo = caInfoConverter.toCaInfo(cert);
        caInfo.setApprovedCa(getById(certificationServiceId));

        caInfoRepository.save(caInfo);

        auditDataHelper.put(CA_ID, certificationServiceId);
        auditDataHelper.put(INTERMEDIATE_CA_ID, caInfo.getId());
        auditDataHelper.put(INTERMEDIATE_CA_CERT_HASH, calculateCertHexHashDelimited(cert));
        auditDataHelper.put(INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

        return caInfoConverter.toCertificateAuthority(caInfo);
    }

    @Override
    public List<CertificateAuthority> getIntermediateCas(Integer certificationServiceId) {
        final ApprovedCaEntity approvedCa = getById(certificationServiceId);
        return caInfoConverter.toCertificateAuthorities(approvedCa.getIntermediateCaInfos());
    }

    @Override
    public List<CertificationServiceListItem> getCertificationServices() {
        return approvedCaConverter.toListItems(approvedCaRepository.findAll());
    }

    @Override
    public OcspResponder addOcspResponder(OcspResponderAddRequest ocspAddResponderRequest) {
        OcspInfoEntity ocspInfo = ocspResponderConverter.toEntity(ocspAddResponderRequest);
        OcspInfoEntity persistedOcspInfo = ocspInfoRepository.save(ocspInfo);
        addOcspAuditData(persistedOcspInfo);
        return ocspResponderConverter.toModel(persistedOcspInfo);
    }

    @Override
    public List<OcspResponder> getOcspResponders(Integer certificationServiceId) {
        var approvedCa = getById(certificationServiceId);
        return approvedCa.getCaInfo().getOcspInfos().stream()
                .map(ocspResponderConverter::toModel)
                .sorted(Comparator.comparing(OcspResponder::getId))
                .collect(toList());
    }

    @Override
    public List<CertificationService> findAll() {
        return approvedCaRepository.findAll()
                .stream()
                .map(approvedCaConverter::convert)
                .collect(toList());
    }

    private void addAuditData(ApprovedCaEntity approvedCa) {
        auditDataHelper.putCertificationServiceData(Integer.toString(approvedCa.getId()), approvedCa.getCaInfo().getCert());
        auditDataHelper.put(AUTHENTICATION_ONLY, approvedCa.getAuthenticationOnly());
        auditDataHelper.put(CERTIFICATE_PROFILE_INFO, approvedCa.getCertProfileInfo());
    }

    private void addOcspAuditData(OcspInfoEntity ocspInfo) {
        auditDataHelper.put(CA_ID, ocspInfo.getCaInfo().getId());
        auditDataHelper.put(OCSP_ID, ocspInfo.getId());
        auditDataHelper.put(OCSP_URL, ocspInfo.getUrl());

        if (ocspInfo.getCert() != null) {
            auditDataHelper.put(OCSP_CERT_HASH, calculateCertHexHashDelimited(ocspInfo.getCert()));
            auditDataHelper.put(OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
        }
    }

}
