/**
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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.restapi.dto.ApprovedCertificationService;
import org.niis.xroad.centralserver.restapi.dto.CertificateAuthority;
import org.niis.xroad.centralserver.restapi.dto.CertificateDetails;
import org.niis.xroad.centralserver.restapi.dto.CertificationService;
import org.niis.xroad.centralserver.restapi.dto.CertificationServiceListItem;
import org.niis.xroad.centralserver.restapi.dto.OcspResponder;
import org.niis.xroad.centralserver.restapi.dto.converter.ApprovedCaConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.CaInfoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.OcspResponderConverter;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.entity.CaInfo;
import org.niis.xroad.centralserver.restapi.entity.OcspInfo;
import org.niis.xroad.centralserver.restapi.repository.ApprovedCaRepository;
import org.niis.xroad.centralserver.restapi.repository.OcspInfoJpaRepository;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.CERTIFICATION_SERVICE_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CertificationServicesService {
    private final ApprovedCaRepository approvedCaRepository;
    private final OcspInfoJpaRepository ocspInfoRepository;
    private final AuditDataHelper auditDataHelper;
    private final ApprovedCaConverter approvedCaConverter;
    private final OcspResponderConverter ocspResponderConverter;
    private final CaInfoConverter caInfoConverter;

    public CertificationService add(ApprovedCertificationService certificationService) {
        final ApprovedCa approvedCaEntity = approvedCaConverter.toEntity(certificationService);
        final ApprovedCa persistedApprovedCa = approvedCaRepository.save(approvedCaEntity);
        addAuditData(persistedApprovedCa);

        return approvedCaConverter.convert(persistedApprovedCa);
    }

    public CertificationService get(Integer id) {
        return approvedCaConverter.convert(getById(id));
    }

    public CertificationService update(CertificationService approvedCa) {
        ApprovedCa persistedApprovedCa = getById(approvedCa.getId());
        Optional.ofNullable(approvedCa.getCertificateProfileInfo()).ifPresent(persistedApprovedCa::setCertProfileInfo);
        Optional.ofNullable(approvedCa.getTlsAuth()).ifPresent(persistedApprovedCa::setAuthenticationOnly);
        final ApprovedCa updatedApprovedCa = approvedCaRepository.save(persistedApprovedCa);
        addAuditData(updatedApprovedCa);

        return approvedCaConverter.convert(updatedApprovedCa);
    }

    private ApprovedCa getById(Integer id) {
        return approvedCaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(CERTIFICATION_SERVICE_NOT_FOUND));
    }

    public CertificateDetails getCertificateDetails(Integer id) {
        return approvedCaRepository.findById(id)
                .map(ApprovedCa::getCaInfo)
                .map(caInfoConverter::toCertificateDetails)
                .orElseThrow(() -> new NotFoundException(CERTIFICATION_SERVICE_NOT_FOUND));
    }

    public CertificateAuthority addIntermediateCa(Integer certificationServiceId, byte[] cert) {
        final CaInfo caInfo = caInfoConverter.toCaInfo(cert);

        final ApprovedCa approvedCa = getById(certificationServiceId);
        approvedCa.addIntermediateCa(caInfo);
        approvedCaRepository.save(approvedCa);

        auditDataHelper.put(CA_ID, certificationServiceId);
        auditDataHelper.put(INTERMEDIATE_CA_ID, caInfo.getId());
        auditDataHelper.put(INTERMEDIATE_CA_CERT_HASH, calculateCertHash(cert));
        auditDataHelper.put(INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

        return caInfoConverter.toCertificateAuthority(caInfo);
    }

    public List<CertificationServiceListItem> getCertificationServices() {
        return approvedCaConverter.toListItems(approvedCaRepository.findAll());
    }

    public OcspResponder addOcspResponder(OcspResponder ocspResponder) {
        OcspInfo ocspInfo = ocspResponderConverter.toEntity(ocspResponder);
        OcspInfo persistedOcspInfo = ocspInfoRepository.save(ocspInfo);
        addAuditData(persistedOcspInfo);
        return ocspResponderConverter.toModel(persistedOcspInfo);
    }


    private void addAuditData(ApprovedCa approvedCa) {
        auditDataHelper.putCertificateData(Integer.toString(approvedCa.getId()), approvedCa.getCaInfo().getCert());
        auditDataHelper.put(RestApiAuditProperty.AUTHENTICATION_ONLY, approvedCa.getAuthenticationOnly());
        auditDataHelper.put(RestApiAuditProperty.CERTIFICATE_PROFILE_INFO, approvedCa.getCertProfileInfo());
    }

    private void addAuditData(OcspInfo ocspInfo) {
        auditDataHelper.put(CA_ID, ocspInfo.getCaInfo().getId());
        auditDataHelper.put(RestApiAuditProperty.OCSP_ID, ocspInfo.getId());
        auditDataHelper.put(RestApiAuditProperty.OCSP_URL, ocspInfo.getUrl());
        auditDataHelper.put(RestApiAuditProperty.OCSP_CERT_HASH, calculateCertHash(ocspInfo.getCert()));
        auditDataHelper.put(RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @SneakyThrows
    private String calculateCertHash(byte[] cert) {
        return CryptoUtils.calculateCertHexHash(cert).toUpperCase().replaceAll("(?<=..)(..)", ":$1");
    }
}
