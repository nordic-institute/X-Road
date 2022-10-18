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

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.dto.CertificationService;
import org.niis.xroad.centralserver.restapi.dto.CertificationServiceListItem;
import org.niis.xroad.centralserver.restapi.dto.OcspResponder;
import org.niis.xroad.centralserver.restapi.dto.converter.ApprovedCaConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.OcspResponderConverter;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.entity.CaInfo;
import org.niis.xroad.centralserver.restapi.entity.OcspInfo;
import org.niis.xroad.centralserver.restapi.repository.ApprovedCaRepository;
import org.niis.xroad.centralserver.restapi.repository.OcspInfoRepository;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;

import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificationServicesServiceTest {

    private static final Integer ID = 123;
    private static final Instant VALID_FROM = Instant.now().minus(1, DAYS);
    private static final Instant VALID_TO = Instant.now().plus(1, DAYS);
    private static final String CA_NAME = "X-Road Test CA";
    private static final String CERT_PROFILE = "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";

    @Mock
    private ApprovedCaRepository approvedCaRepository;
    @Mock
    private OcspInfoRepository ocspInfoRepository;
    @Spy
    private ApprovedCaConverter approvedCaConverter = new ApprovedCaConverter();
    @Mock
    private OcspResponderConverter ocspResponderConverter;
    @Mock
    private AuditDataHelper auditDataHelper;

    @InjectMocks
    private CertificationServicesService service;

    @Test
    void getCertificationServices() {
        when(approvedCaRepository.findAll()).thenReturn(List.of(approvedCa()));

        List<CertificationServiceListItem> approvedCertificationServices = service.getCertificationServices();

        assertEquals(1, approvedCertificationServices.size());
        var ca = approvedCertificationServices.iterator().next();
        assertEquals(CA_NAME, ca.getName());
        assertEquals(VALID_FROM, ca.getNotBefore());
        assertEquals(VALID_TO, ca.getNotAfter());
    }

    @Test
    void get() {
        when(approvedCaRepository.findById(ID)).thenReturn(Optional.of(approvedCa()));

        final CertificationService certificationService = service.get(ID);

        assertEquals(CERT_PROFILE, certificationService.getCertificateProfileInfo());
        assertEquals(CA_NAME, certificationService.getName());
        assertEquals(VALID_FROM, certificationService.getNotBefore());
        assertEquals(VALID_TO, certificationService.getNotAfter());
        assertTrue(certificationService.getTlsAuth());
    }

    @Test
    void getShouldThrowNotFoundException() {
        when(approvedCaRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(ID));
    }

    @Test
    void addCertificationServiceOcspResponder() throws Exception {
        var mockOcspResponder = ocspResponder();
        var mockOcspInfo = ocspInfo();
        when(ocspResponderConverter.toEntity(mockOcspResponder)).thenReturn(mockOcspInfo);
        when(ocspInfoRepository.save(mockOcspInfo)).thenReturn(mockOcspInfo);
        when(ocspResponderConverter.toModel(mockOcspInfo)).thenReturn(mockOcspResponder);

        var result = service.addOcspResponder(mockOcspResponder);

        assertThat(result).isEqualTo(mockOcspResponder);
        verify(ocspResponderConverter).toEntity(mockOcspResponder);
        verify(ocspInfoRepository).save(mockOcspInfo);
        verify(ocspResponderConverter).toModel(mockOcspInfo);
        verify(auditDataHelper).put(RestApiAuditProperty.CA_ID, mockOcspInfo.getCaInfo().getId());
        verify(auditDataHelper).put(RestApiAuditProperty.OCSP_ID, mockOcspInfo.getId());
        verify(auditDataHelper).put(RestApiAuditProperty.OCSP_URL, mockOcspInfo.getUrl());
        verify(auditDataHelper).put(RestApiAuditProperty.OCSP_CERT_HASH, "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF");
        verify(auditDataHelper)
                .put(RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM, CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @SneakyThrows
    private ApprovedCa approvedCa() {
        CaInfo caInfo = new CaInfo();
        caInfo.setValidFrom(VALID_FROM);
        caInfo.setValidTo(VALID_TO);
        caInfo.setCert(TestCertUtil.generateAuthCert());
        ApprovedCa ca = new ApprovedCa();
        ca.setName(CA_NAME);
        ca.setAuthenticationOnly(true);
        ca.setCertProfileInfo(CERT_PROFILE);
        ca.setCaInfo(caInfo);
        return ca;
    }


    private OcspResponder ocspResponder() throws CertificateEncodingException {
        return new OcspResponder()
                .setCaId(1)
                .setUrl("https://flakyocsp:666")
                .setCertificate(TestCertUtil.getOcspSigner().certChain[0].getEncoded());
    }

    private OcspInfo ocspInfo() throws CertificateEncodingException {
        return new OcspInfo(new CaInfo(), "https://flakyocsp:666",
                TestCertUtil.getOcspSigner().certChain[0].getEncoded());
    }

}
