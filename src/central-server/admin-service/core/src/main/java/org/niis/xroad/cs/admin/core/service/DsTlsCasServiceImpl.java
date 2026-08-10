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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.AddDsTlsCaRequest;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaIntermediateCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaListItem;
import org.niis.xroad.cs.admin.api.service.DsTlsCasService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsCaConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsCaInfoConverter;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaEntity;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaInfoEntity;
import org.niis.xroad.cs.admin.core.repository.DsTlsCaInfoRepository;
import org.niis.xroad.cs.admin.core.repository.DsTlsCaRepository;
import org.niis.xroad.cs.admin.core.validation.UrlValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CERTIFICATE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.DS_TLS_CA_INTERMEDIATE_CA_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.DS_TLS_CA_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ACME_DIRECTORY_URL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CERTIFICATE_PROFILE_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;

@Service
@Transactional
@RequiredArgsConstructor
public class DsTlsCasServiceImpl implements DsTlsCasService {
    private final DsTlsCaRepository dsTlsCaRepository;
    private final DsTlsCaInfoRepository dsTlsCaInfoRepository;
    private final AuditDataHelper auditDataHelper;
    private final DsTlsCaConverter dsTlsCaConverter;
    private final DsTlsCaInfoConverter dsTlsCaInfoConverter;
    private final CertificateConverter certConverter;
    private final UrlValidator urlValidator;

    @Override
    public DsTlsCa add(AddDsTlsCaRequest request) {
        if (isNotBlank(request.getAcmeServerDirectoryUrl())) {
            urlValidator.validateUrl(request.getAcmeServerDirectoryUrl());
        }

        X509Certificate certificate = handledCertificationChainRead(request.getCertificate());

        final var dsTlsCaEntity = new DsTlsCaEntity();
        dsTlsCaEntity.setName(CertUtils.getSubjectCommonName(certificate));
        dsTlsCaEntity.setAcmeServerDirectoryUrl(request.getAcmeServerDirectoryUrl());
        dsTlsCaEntity.setDsTlsCertificateProfileId(request.getDsTlsCertificateProfileId());

        final var caInfo = new DsTlsCaInfoEntity();
        caInfo.setCert(request.getCertificate());
        caInfo.setValidFrom(certificate.getNotBefore().toInstant());
        caInfo.setValidTo(certificate.getNotAfter().toInstant());

        dsTlsCaEntity.setCaInfo(dsTlsCaInfoRepository.saveAndFlush(caInfo));

        final DsTlsCaEntity persistedDsTlsCa = dsTlsCaRepository.saveAndFlush(dsTlsCaEntity);
        addAuditData(persistedDsTlsCa);

        return dsTlsCaConverter.convert(persistedDsTlsCa);
    }

    private X509Certificate handledCertificationChainRead(byte[] certificate) {
        try {
            return CertUtils.readCertificateChain(certificate)[0];
        } catch (Exception e) {
            throw new BadRequestException(e, INVALID_CERTIFICATE.build());
        }
    }

    @Override
    public DsTlsCa get(Integer id) {
        return dsTlsCaConverter.convert(getById(id));
    }

    @Override
    public void delete(Integer id) {
        auditDataHelper.put(CA_ID, id);

        final DsTlsCaEntity entity = getById(id);
        dsTlsCaRepository.delete(entity);
    }

    @Override
    public DsTlsCa update(DsTlsCa dsTlsCa) {
        DsTlsCaEntity persistedDsTlsCa = getById(dsTlsCa.getId());
        Optional.ofNullable(dsTlsCa.getAcmeServerDirectoryUrl())
                .ifPresent(acmeServerDirectoryUrl -> {
                    if (isNotBlank(acmeServerDirectoryUrl)) {
                        urlValidator.validateUrl(acmeServerDirectoryUrl);
                    }
                    persistedDsTlsCa.setAcmeServerDirectoryUrl(acmeServerDirectoryUrl);
                });
        Optional.ofNullable(dsTlsCa.getDsTlsCertificateProfileId())
                .ifPresent(persistedDsTlsCa::setDsTlsCertificateProfileId);

        final DsTlsCaEntity updatedDsTlsCa = dsTlsCaRepository.save(persistedDsTlsCa);
        addAuditData(updatedDsTlsCa);

        return dsTlsCaConverter.convert(updatedDsTlsCa);
    }

    private DsTlsCaEntity getById(Integer id) {
        return dsTlsCaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(DS_TLS_CA_NOT_FOUND.build()));
    }

    @Override
    public List<DsTlsCa> findAll() {
        return dsTlsCaRepository.findAll()
                .stream()
                .map(dsTlsCaConverter::convert)
                .toList();
    }

    @Override
    public List<DsTlsCaListItem> getDsTlsCas() {
        return dsTlsCaConverter.toListItems(dsTlsCaRepository.findAll());
    }

    @Override
    public CertificateDetails getCertificateDetails(Integer id) {
        return dsTlsCaRepository.findById(id)
                .map(DsTlsCaEntity::getCaInfo)
                .map(DsTlsCaInfoEntity::getCert)
                .map(certConverter::toCertificateDetails)
                .orElseThrow(() -> new NotFoundException(DS_TLS_CA_NOT_FOUND.build()));
    }

    @Override
    public DsTlsCaIntermediateCa addIntermediateCa(Integer dsTlsCaId, byte[] cert) {
        final DsTlsCaInfoEntity caInfo = dsTlsCaInfoConverter.toDsTlsCaInfo(cert);
        caInfo.setDsTlsCa(getById(dsTlsCaId));

        dsTlsCaInfoRepository.save(caInfo);

        auditDataHelper.put(CA_ID, dsTlsCaId);
        auditDataHelper.put(INTERMEDIATE_CA_ID, caInfo.getId());
        auditDataHelper.put(INTERMEDIATE_CA_CERT_HASH, calculateCertHexHashDelimited(cert));
        auditDataHelper.put(INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

        return dsTlsCaInfoConverter.toDsTlsCaIntermediateCa(caInfo);
    }

    @Override
    public List<DsTlsCaIntermediateCa> getIntermediateCas(Integer dsTlsCaId) {
        final DsTlsCaEntity dsTlsCa = getById(dsTlsCaId);
        return dsTlsCaInfoConverter.toDsTlsCaIntermediateCas(dsTlsCa.getIntermediateCaInfos());
    }

    @Override
    public DsTlsCaIntermediateCa getIntermediateCa(Integer intermediateCaId) {
        return dsTlsCaInfoConverter.toDsTlsCaIntermediateCa(getIntermediateCaEntity(intermediateCaId));
    }

    @Override
    public void deleteIntermediateCa(Integer intermediateCaId) {
        DsTlsCaInfoEntity intermediateCa = getIntermediateCaEntity(intermediateCaId);
        dsTlsCaInfoRepository.delete(intermediateCa);
        auditDataHelper.put(INTERMEDIATE_CA_ID, intermediateCa.getId());
    }

    private DsTlsCaInfoEntity getIntermediateCaEntity(Integer id) {
        return dsTlsCaInfoRepository.findById(id)
                .filter(caInfo -> caInfo.getDsTlsCa() != null)
                .orElseThrow(() -> new NotFoundException(DS_TLS_CA_INTERMEDIATE_CA_NOT_FOUND.build()));
    }

    private void addAuditData(DsTlsCaEntity dsTlsCa) {
        auditDataHelper.putCertificationServiceData(Integer.toString(dsTlsCa.getId()), dsTlsCa.getCaInfo().getCert());
        if (dsTlsCa.getAcmeServerDirectoryUrl() != null) {
            auditDataHelper.put(ACME_DIRECTORY_URL, dsTlsCa.getAcmeServerDirectoryUrl());
        }
        if (dsTlsCa.getDsTlsCertificateProfileId() != null) {
            auditDataHelper.put(DS_TLS_CERTIFICATE_PROFILE_ID, dsTlsCa.getDsTlsCertificateProfileId());
        }
    }

}
