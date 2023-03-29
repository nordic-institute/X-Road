/*
 * The MIT License
 *
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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.ApprovedTsa;
import org.niis.xroad.cs.admin.api.dto.TimestampServiceRequest;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;
import org.niis.xroad.cs.admin.core.entity.ApprovedTsaEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ApprovedTsaMapper;
import org.niis.xroad.cs.admin.core.repository.ApprovedTsaRepository;
import org.niis.xroad.cs.admin.core.validation.UrlValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.TIMESTAMPING_AUTHORITY_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TSA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TSA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TSA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TSA_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TSA_URL;

@Service
@Transactional
@RequiredArgsConstructor
public class TimestampingServicesServiceImpl implements TimestampingServicesService {

    private final ApprovedTsaRepository approvedTsaRepository;

    private final AuditDataHelper auditDataHelper;

    private final ApprovedTsaMapper approvedTsaMapper;
    private final UrlValidator urlValidator;

    @Override
    public Set<ApprovedTsa> getTimestampingServices() {
        return approvedTsaRepository.findAll().stream()
                .map(approvedTsaMapper::toTarget)
                .collect(toSet());
    }

    @Override
    public ApprovedTsa add(String url, byte[] certificate) {
        urlValidator.validateUrl(url);
        final ApprovedTsaEntity entity = approvedTsaMapper.toEntity(url, certificate);
        final ApprovedTsaEntity savedTsa = approvedTsaRepository.save(entity);
        addAuditMessages(savedTsa);
        return approvedTsaMapper.toTarget(savedTsa);
    }

    @Override
    public ApprovedTsa update(TimestampServiceRequest updateRequest) {
        var entity = getApprovedTsaEntity(updateRequest.getId());

        urlValidator.validateUrl(updateRequest.getUrl());
        entity.setUrl(updateRequest.getUrl());
        Optional.ofNullable(updateRequest.getCertificate()).ifPresent(entity::setCert);

        final ApprovedTsaEntity savedEntity = approvedTsaRepository.save(entity);
        addAuditMessages(savedEntity);
        return approvedTsaMapper.toTarget(savedEntity);
    }

    @Override
    public void delete(Integer id) {
        final ApprovedTsaEntity approvedTsaEntity = getApprovedTsaEntity(id);
        addDeleteAuditMessages(approvedTsaEntity);
        approvedTsaRepository.delete(approvedTsaEntity);
    }

    @Override
    public ApprovedTsa get(Integer id) {
        return approvedTsaMapper.toTarget(getApprovedTsaEntity(id));
    }

    private ApprovedTsaEntity getApprovedTsaEntity(Integer id) {
        return approvedTsaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(TIMESTAMPING_AUTHORITY_NOT_FOUND));
    }

    private void addDeleteAuditMessages(ApprovedTsaEntity approvedTsaEntity) {
        auditDataHelper.put(TSA_ID, approvedTsaEntity.getId());
        auditDataHelper.put(TSA_NAME, approvedTsaEntity.getName());
        auditDataHelper.put(TSA_URL, approvedTsaEntity.getUrl());
    }

    private void addAuditMessages(ApprovedTsaEntity tsaEntity) {
        auditDataHelper.put(TSA_ID, tsaEntity.getId());
        auditDataHelper.put(TSA_NAME, tsaEntity.getName());
        auditDataHelper.put(TSA_URL, tsaEntity.getUrl());
        auditDataHelper.put(TSA_CERT_HASH, calculateCertHexHashDelimited(tsaEntity.getCert()));
        auditDataHelper.put(TSA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

}
