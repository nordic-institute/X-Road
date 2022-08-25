/**
 * The MIT License
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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.commonui.CertificateProfileInfoValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.restapi.converter.CertificationServiceConverter;
import org.niis.xroad.centralserver.restapi.dto.AddApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.repository.ApprovedCaRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.AUTHENTICATION_ONLY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERTIFICATE_PROFILE_INFO;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CertificationServicesService {

    private final AuditDataHelper auditDataHelper;

    private final ApprovedCaRepository approvedCaRepository;

    private final CertificationServiceConverter certificationServiceConverter;

    public ApprovedCertificationServiceDto add(AddApprovedCertificationServiceDto approvedCa) {
        CertificateProfileInfoValidator.validate(approvedCa.getCertificateProfileInfo());

        ApprovedCa approvedCaEntity = certificationServiceConverter.toEntity(approvedCa);
        ApprovedCa persistedApprovedCa = approvedCaRepository.save(approvedCaEntity);
        addAuditData(persistedApprovedCa);
        return certificationServiceConverter.toDomain(persistedApprovedCa);
    }

    public Set<ApprovedCertificationServiceDto> getCertificationServices() {
        List<ApprovedCa> approvedCas = approvedCaRepository.findAll();
        return approvedCas.stream()
                .map(certificationServiceConverter::toDomain)
                .collect(toSet());
    }

    private void addAuditData(ApprovedCa approvedCa) {
        auditDataHelper.putCertificateData(Integer.toString(approvedCa.getId()), approvedCa.getCaInfo().getCert());
        auditDataHelper.put(AUTHENTICATION_ONLY, approvedCa.getAuthenticationOnly());
        auditDataHelper.put(CERTIFICATE_PROFILE_INFO, approvedCa.getCertProfileInfo());
    }
}
