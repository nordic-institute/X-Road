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

package org.niis.xroad.centralserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.dto.converter.CaInfoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.OcspResponderConverter;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderRequest;
import org.niis.xroad.cs.admin.api.service.IntermediateCasService;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.CaInfoRepository;
import org.niis.xroad.cs.admin.core.repository.OcspInfoRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.INTERMEDIATE_CA_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_URL;

@Service
@Transactional
@RequiredArgsConstructor
public class IntermediateCasServiceImpl implements IntermediateCasService {

    private final CaInfoRepository caInfoJpaRepository;
    private final OcspInfoRepository ocspInfoRepository;

    private final CaInfoConverter caInfoConverter;
    private final OcspResponderConverter ocspResponderConverter;

    private final AuditDataHelper auditDataHelper;

    @Override
    public CertificateAuthority get(Integer id) {
        return caInfoConverter.toCertificateAuthority(getIntermediateCa(id));
    }

    private CaInfoEntity getIntermediateCa(Integer id) {
        return caInfoJpaRepository.findById(id)
                .filter(this::isIntermediateCa)
                .orElseThrow(() -> new NotFoundException(INTERMEDIATE_CA_NOT_FOUND));
    }

    @Override
    public void delete(Integer id) {
        CaInfoEntity intermediateCa = getIntermediateCa(id);
        caInfoJpaRepository.delete(intermediateCa);
        auditDataHelper.put(INTERMEDIATE_CA_ID, intermediateCa.getId());
    }

    @Override
    public OcspResponder addOcspResponder(Integer intermediateCaId, OcspResponderRequest ocspResponderRequest) {
        final CaInfoEntity intermediateCa = getIntermediateCa(intermediateCaId);
        final OcspInfoEntity ocspInfoEntity = new OcspInfoEntity(intermediateCa, ocspResponderRequest.getUrl(),
                ocspResponderRequest.getCertificate());

        intermediateCa.addOcspInfos(ocspInfoEntity);

        final OcspInfoEntity savedOcspInfo = ocspInfoRepository.save(ocspInfoEntity);

        auditDataHelper.put(INTERMEDIATE_CA_ID, intermediateCaId);
        auditDataHelper.put(OCSP_ID, savedOcspInfo.getId());
        auditDataHelper.put(OCSP_URL, savedOcspInfo.getUrl());
        auditDataHelper.put(OCSP_CERT_HASH, calculateCertHexHashDelimited(savedOcspInfo.getCert()));
        auditDataHelper.put(OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

        return ocspResponderConverter.toModel(savedOcspInfo);
    }

    private boolean isIntermediateCa(CaInfoEntity caInfo) {
        return caInfo.getApprovedCa() != null;
    }

}
