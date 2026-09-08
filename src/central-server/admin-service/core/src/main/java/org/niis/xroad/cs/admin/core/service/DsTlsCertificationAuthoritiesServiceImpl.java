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
import org.niis.xroad.cs.admin.api.dto.ApprovedDsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthorityListItem;
import org.niis.xroad.cs.admin.api.dto.DsTlsIntermediateCertificateAuthority;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsCaConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsIntermediateCaConverter;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaEntity;
import org.niis.xroad.cs.admin.core.entity.DsTlsIntermediateCaEntity;
import org.niis.xroad.cs.admin.core.repository.DsTlsCaRepository;
import org.niis.xroad.cs.admin.core.repository.DsTlsIntermediateCaRepository;
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
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.DS_TLS_CERTIFICATION_AUTHORITY_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ACME_DIRECTORY_URL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CA_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CERTIFICATE_PROFILE_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_INTERMEDIATE_CA_ID;

@Service
@Transactional
@RequiredArgsConstructor
public class DsTlsCertificationAuthoritiesServiceImpl implements DsTlsCertificationAuthoritiesService {

    private final DsTlsCaRepository dsTlsCaRepository;
    private final DsTlsIntermediateCaRepository dsTlsIntermediateCaRepository;
    private final DsTlsCaConverter dsTlsCaConverter;
    private final DsTlsIntermediateCaConverter dsTlsIntermediateCaConverter;
    private final CertificateConverter certConverter;
    private final UrlValidator urlValidator;
    private final AuditDataHelper auditDataHelper;

    @Override
    public DsTlsCertificationAuthority add(ApprovedDsTlsCertificationAuthority newCa) {
        if (isNotBlank(newCa.getAcmeServerDirectoryUrl())) {
            urlValidator.validateUrl(newCa.getAcmeServerDirectoryUrl());
        }

        final X509Certificate certificate = handledCertificateChainRead(newCa.getCertificate());

        final var entity = new DsTlsCaEntity();
        entity.setName(newCa.getName());
        entity.setCert(newCa.getCertificate());
        entity.setValidFrom(certificate.getNotBefore().toInstant());
        entity.setValidTo(certificate.getNotAfter().toInstant());
        entity.setAcmeServerDirectoryUrl(newCa.getAcmeServerDirectoryUrl());
        entity.setDsTlsCertificateProfileId(newCa.getDsTlsCertificateProfileId());

        final DsTlsCaEntity persisted = dsTlsCaRepository.saveAndFlush(entity);
        addAuditData(persisted);

        return dsTlsCaConverter.convert(persisted);
    }

    private X509Certificate handledCertificateChainRead(byte[] certificate) {
        try {
            return CertUtils.readCertificateChain(certificate)[0];
        } catch (Exception e) {
            throw new BadRequestException(e, INVALID_CERTIFICATE.build());
        }
    }

    @Override
    public DsTlsCertificationAuthority get(Integer id) {
        return dsTlsCaConverter.convert(getById(id));
    }

    @Override
    public void delete(Integer id) {
        auditDataHelper.put(DS_TLS_CA_ID, id);

        final DsTlsCaEntity entity = getById(id);
        dsTlsCaRepository.delete(entity);
    }

    @Override
    public DsTlsCertificationAuthority update(DsTlsCertificationAuthority settings) {
        DsTlsCaEntity persisted = getById(settings.getId());

        Optional.ofNullable(settings.getName()).ifPresent(persisted::setName);
        Optional.ofNullable(settings.getAcmeServerDirectoryUrl())
                .ifPresent(acmeServerDirectoryUrl -> {
                    if (isNotBlank(acmeServerDirectoryUrl)) {
                        urlValidator.validateUrl(acmeServerDirectoryUrl);
                    }
                    persisted.setAcmeServerDirectoryUrl(acmeServerDirectoryUrl);
                });
        Optional.ofNullable(settings.getDsTlsCertificateProfileId()).ifPresent(persisted::setDsTlsCertificateProfileId);

        final DsTlsCaEntity updated = dsTlsCaRepository.save(persisted);
        addAuditData(updated);

        return dsTlsCaConverter.convert(updated);
    }

    private DsTlsCaEntity getById(Integer id) {
        return dsTlsCaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(DS_TLS_CERTIFICATION_AUTHORITY_NOT_FOUND.build()));
    }

    @Override
    public CertificateDetails getCertificateDetails(Integer id) {
        return dsTlsCaRepository.findById(id)
                .map(DsTlsCaEntity::getCert)
                .map(certConverter::toCertificateDetails)
                .orElseThrow(() -> new NotFoundException(DS_TLS_CERTIFICATION_AUTHORITY_NOT_FOUND.build()));
    }

    @Override
    public DsTlsIntermediateCertificateAuthority addIntermediateCa(Integer dsTlsCertificationAuthorityId, byte[] cert) {
        final DsTlsIntermediateCaEntity intermediateCa = dsTlsIntermediateCaConverter.toEntity(cert);
        intermediateCa.setDsTlsCa(getById(dsTlsCertificationAuthorityId));

        dsTlsIntermediateCaRepository.save(intermediateCa);

        auditDataHelper.put(DS_TLS_CA_ID, dsTlsCertificationAuthorityId);
        auditDataHelper.put(DS_TLS_INTERMEDIATE_CA_ID, intermediateCa.getId());
        auditDataHelper.put(DS_TLS_INTERMEDIATE_CA_CERT_HASH, calculateCertHexHashDelimited(cert));
        auditDataHelper.put(DS_TLS_INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

        return dsTlsIntermediateCaConverter.convert(intermediateCa);
    }

    @Override
    public List<DsTlsIntermediateCertificateAuthority> getIntermediateCas(Integer dsTlsCertificationAuthorityId) {
        return dsTlsIntermediateCaConverter.convert(getById(dsTlsCertificationAuthorityId).getIntermediateCas());
    }

    @Override
    public List<DsTlsCertificationAuthorityListItem> getDsTlsCertificationAuthorities() {
        return dsTlsCaConverter.toListItems(dsTlsCaRepository.findAll());
    }

    @Override
    public List<DsTlsCertificationAuthority> findAll() {
        return dsTlsCaRepository.findAll().stream()
                .map(dsTlsCaConverter::convert)
                .toList();
    }

    private void addAuditData(DsTlsCaEntity entity) {
        auditDataHelper.put(DS_TLS_CA_ID, entity.getId());
        auditDataHelper.put(DS_TLS_CA_NAME, entity.getName());
        auditDataHelper.putCertificateHash(entity.getCert());
        if (entity.getAcmeServerDirectoryUrl() != null) {
            auditDataHelper.put(ACME_DIRECTORY_URL, entity.getAcmeServerDirectoryUrl());
        }
        if (entity.getDsTlsCertificateProfileId() != null) {
            auditDataHelper.put(DS_TLS_CERTIFICATE_PROFILE_ID, entity.getDsTlsCertificateProfileId());
        }
    }

}
