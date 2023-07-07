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
package org.niis.xroad.cs.admin.core.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderRequest;
import org.niis.xroad.cs.admin.api.service.OcspRespondersService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.OcspResponderConverter;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.OcspInfoRepository;
import org.niis.xroad.cs.admin.core.validation.UrlValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Optional;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.OCSP_RESPONDER_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_URL;

@Service
@Transactional
@RequiredArgsConstructor
public class OcspRespondersServiceImpl implements OcspRespondersService {
    private final OcspInfoRepository ocspInfoRepository;
    private final CertificateConverter certConverter;
    private final OcspResponderConverter ocspResponderConverter;

    private final AuditDataHelper auditDataHelper;
    private final UrlValidator urlValidator;

    @Override
    public CertificateDetails getOcspResponderCertificateDetails(Integer id) {
        return ocspInfoRepository.findById(id)
                .map(OcspInfoEntity::getCert)
                .map(certConverter::toCertificateDetails)
                .orElseThrow(() -> new NotFoundException(OCSP_RESPONDER_NOT_FOUND));
    }

    private OcspInfoEntity get(Integer id) {
        return ocspInfoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(OCSP_RESPONDER_NOT_FOUND));
    }

    @Override
    public OcspResponder update(OcspResponderRequest updateRequest) {
        final OcspInfoEntity ocspInfo = get(updateRequest.getId());
        Optional.ofNullable(updateRequest.getUrl()).ifPresent(url -> {
            urlValidator.validateUrl(url);
            ocspInfo.setUrl(url);
        });
        Optional.ofNullable(updateRequest.getCertificate()).ifPresent(ocspInfo::setCert);
        final OcspInfoEntity savedOcspInfo = ocspInfoRepository.save(ocspInfo);

        auditDataHelper.put(OCSP_ID, savedOcspInfo.getId());
        auditDataHelper.put(OCSP_URL, savedOcspInfo.getUrl());
        auditDataHelper.put(OCSP_CERT_HASH, calculateCertHexHashDelimited(savedOcspInfo.getCert()));
        auditDataHelper.put(OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

        return ocspResponderConverter.toModel(savedOcspInfo);
    }

    @Override
    public void delete(Integer id) {
        OcspInfoEntity ocspResponder = get(id);
        ocspInfoRepository.delete(ocspResponder);

        auditDataHelper.put(OCSP_ID, ocspResponder.getId());
    }

}
