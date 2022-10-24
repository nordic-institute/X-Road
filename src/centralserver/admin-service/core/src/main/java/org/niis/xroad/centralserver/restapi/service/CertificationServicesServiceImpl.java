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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.ApprovedCa;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ApprovedCaMapper;
import org.niis.xroad.cs.admin.core.repository.ApprovedCaRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CertificationServicesServiceImpl implements CertificationServicesService {
    private final ApprovedCaRepository approvedCaRepository;
    private final AuditDataHelper auditDataHelper;
    private final ApprovedCaMapper approvedCaMapper;

    public ApprovedCa add(ApprovedCa approvedCa) {
        var approvedCaEntity = approvedCaMapper.fromDto(approvedCa);
        approvedCaEntity = approvedCaRepository.save(approvedCaEntity);
        addAuditData(approvedCaEntity);

        return approvedCaMapper.toDto(approvedCaEntity);
    }

    public List<ApprovedCa> getCertificationServices() {
        return approvedCaRepository.findAll().stream()
                .map(approvedCaMapper::toDto)
                .collect(Collectors.toList());
    }

    private void addAuditData(ApprovedCaEntity approvedCa) {
        auditDataHelper.putCertificateData(Integer.toString(approvedCa.getId()), approvedCa.getCaInfo().getCert());
        auditDataHelper.put(RestApiAuditProperty.AUTHENTICATION_ONLY, approvedCa.getAuthenticationOnly());
        auditDataHelper.put(RestApiAuditProperty.CERTIFICATE_PROFILE_INFO, approvedCa.getCertProfileInfo());
    }
}
