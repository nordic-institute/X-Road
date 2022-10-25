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
import org.niis.xroad.centralserver.restapi.dto.CertificateDetails;
import org.niis.xroad.centralserver.restapi.dto.OcspResponder;
import org.niis.xroad.centralserver.restapi.dto.converter.CaInfoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.OcspResponderConverter;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.entity.OcspInfo;
import org.niis.xroad.centralserver.restapi.repository.ApprovedCaRepository;
import org.niis.xroad.centralserver.restapi.repository.OcspInfoJpaRepository;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.CERTIFICATION_SERVICE_NOT_FOUND;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OcspRespondersService {
    private final OcspInfoJpaRepository ocspInfoRepository;
    private final ApprovedCaRepository approvedCaRepository;
    private final CaInfoConverter caInfoConverter;
    private final OcspResponderConverter ocspResponderConverter;

    public CertificateDetails getOcspResponderCertificateDetails(Integer id) {
        return ocspInfoRepository.findById(id)
                .map(OcspInfo::getCaInfo)
                .map(caInfoConverter::toCertificateDetails)
                .orElseThrow(() -> new NotFoundException(CERTIFICATION_SERVICE_NOT_FOUND));
    }

    public Set<OcspResponder> getOcspResponders(Integer certificationServiceId) {
        final ApprovedCa approvedCa = getById(certificationServiceId);
        return ocspInfoRepository.findByCaInfoId(approvedCa.getCaInfo().getId()).stream()
                .map(ocspResponderConverter::toModel)
                .collect(Collectors.toSet());
    }

    private ApprovedCa getById(Integer id) {
        return approvedCaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(CERTIFICATION_SERVICE_NOT_FOUND));
    }
}
