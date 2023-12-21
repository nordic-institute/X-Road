/*
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.TestCertUtil;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderRequest;
import org.niis.xroad.cs.admin.core.converter.CaInfoConverter;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.cs.admin.core.converter.OcspResponderConverter;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.ApprovedCaRepository;
import org.niis.xroad.cs.admin.core.repository.OcspInfoRepository;
import org.niis.xroad.cs.admin.core.validation.UrlValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.util.Optional;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.DIGITAL_SIGNATURE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.OCSP_RESPONDER_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_URL;

@ExtendWith(MockitoExtension.class)
class OcspRespondersServiceImplTest {
    private static final Integer ID = 123;

    @Mock
    private OcspInfoRepository ocspInfoRepository;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private UrlValidator urlValidator;

    @Spy
    private OcspResponderConverter ocspResponderConverter = new OcspResponderConverter(mock(ApprovedCaRepository.class));

    @Spy
    private CaInfoConverter caInfoConverter = new CaInfoConverter(
            new CertificateConverter(new KeyUsageConverter()), ocspResponderConverter);

    @Spy
    private CertificateConverter certConverter = new CertificateConverter(new KeyUsageConverter());

    @InjectMocks
    private OcspRespondersServiceImpl service;

    @Test
    void getCertificateDetails() {
        when(ocspInfoRepository.findById(ID)).thenReturn(Optional.of(ocspInfo()));

        final CertificateDetails certificateDetails = service.getOcspResponderCertificateDetails(ID);

        assertNotNull(certificateDetails);
        assertThat(certificateDetails.getKeyUsages()).contains(DIGITAL_SIGNATURE);
        assertEquals("Subject", certificateDetails.getSubjectCommonName());
        assertEquals("CN=Subject", certificateDetails.getSubjectDistinguishedName());
        assertEquals("Cyber", certificateDetails.getIssuerCommonName());
        assertEquals("1", certificateDetails.getSerial());
        assertEquals("SHA256withRSA", certificateDetails.getSignatureAlgorithm());
        assertEquals("EMAILADDRESS=aaa@bbb.ccc, CN=Cyber, OU=ITO, O=Cybernetica, C=EE",
                certificateDetails.getIssuerDistinguishedName());
    }

    @Test
    void update() throws Exception {
        final byte[] cert = TestCertUtil.getOcspSigner().certChain[0].getEncoded();
        final String newUrl = "http://new.url";
        final OcspResponderRequest request = new OcspResponderRequest()
                .setId(ID)
                .setUrl(newUrl)
                .setCertificate(cert);

        final OcspInfoEntity ocspInfo = ocspInfo();

        when(ocspInfoRepository.findById(ID)).thenReturn(Optional.of(ocspInfo));
        when(ocspInfoRepository.save(isA(OcspInfoEntity.class))).thenReturn(ocspInfo);

        final OcspResponder result = service.update(request);

        verify(urlValidator).validateUrl(newUrl);

        ArgumentCaptor<OcspInfoEntity> captor = ArgumentCaptor.forClass(OcspInfoEntity.class);
        verify(ocspInfoRepository).save(captor.capture());
        assertEquals(newUrl, captor.getValue().getUrl());
        assertEquals(cert, captor.getValue().getCert());

        assertEquals(newUrl, result.getUrl());

        assertAuditMessages(ocspInfo, newUrl);
    }

    @Test
    void updateOnlyUrl() {
        final String newUrl = "http://new.url";
        final OcspResponderRequest request = new OcspResponderRequest()
                .setId(ID)
                .setUrl(newUrl);

        final OcspInfoEntity ocspInfo = ocspInfo();
        final byte[] cert = ocspInfo.getCert();

        when(ocspInfoRepository.findById(ID)).thenReturn(Optional.of(ocspInfo));
        when(ocspInfoRepository.save(isA(OcspInfoEntity.class))).thenReturn(ocspInfo);

        final OcspResponder result = service.update(request);
        verify(urlValidator).validateUrl(newUrl);

        ArgumentCaptor<OcspInfoEntity> captor = ArgumentCaptor.forClass(OcspInfoEntity.class);
        verify(ocspInfoRepository).save(captor.capture());
        assertEquals(newUrl, captor.getValue().getUrl());
        assertEquals(cert, captor.getValue().getCert());

        assertEquals(newUrl, result.getUrl());

        assertAuditMessages(ocspInfo, newUrl);
    }

    @Test
    void shouldDelete() {
        final OcspInfoEntity ocspInfo = ocspInfo();
        when(ocspInfoRepository.findById(ID)).thenReturn(Optional.of(ocspInfo));

        service.delete(ID);

        verify(auditDataHelper).put(OCSP_ID, ocspInfo.getId());
    }

    @Test
    void shouldThrowExceptionWhenOcspInfoNotFound() {
        when(ocspInfoRepository.findById(ID)).thenReturn(Optional.empty());

        Executable testable = () -> service.delete(ID);

        NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
        assertEquals(OCSP_RESPONDER_NOT_FOUND.getDescription(), actualThrown.getMessage());
    }

    private void assertAuditMessages(OcspInfoEntity ocspInfo, String url) {
        verify(auditDataHelper).put(OCSP_ID, ocspInfo.getId());
        verify(auditDataHelper).put(OCSP_URL, url);
        verify(auditDataHelper).put(eq(OCSP_CERT_HASH), isA(String.class));
        verify(auditDataHelper).put(OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @SneakyThrows
    private OcspInfoEntity ocspInfo() {
        return new OcspInfoEntity(new CaInfoEntity(), "https://flakyocsp:666", TestCertUtil.generateAuthCert());
    }

}
