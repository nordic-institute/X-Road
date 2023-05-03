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

import ee.ria.xroad.common.TestCertUtil;

import lombok.SneakyThrows;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.CertificationService;
import org.niis.xroad.cs.admin.api.dto.CertificationServiceListItem;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;
import org.niis.xroad.cs.admin.core.converter.ApprovedCaConverter;
import org.niis.xroad.cs.admin.core.converter.CaInfoConverter;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.cs.admin.core.converter.OcspResponderConverter;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.ApprovedCaRepository;
import org.niis.xroad.cs.admin.core.repository.CaInfoRepository;
import org.niis.xroad.cs.admin.core.repository.OcspInfoRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.TestCertUtil.generateAuthCert;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.dto.KeyUsageEnum.DIGITAL_SIGNATURE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_URL;

@ExtendWith(MockitoExtension.class)
class CertificationServicesServiceImplTest {
    private static final Integer ID = 123;
    private static final Instant VALID_FROM = Instant.now().minus(1, DAYS);
    private static final Instant VALID_TO = Instant.now().plus(1, DAYS);
    private static final String CA_NAME = "X-Road Test CA";
    private static final String CERT_PROFILE = "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";

    @Mock
    private ApprovedCaRepository approvedCaRepository;
    @Mock
    private CaInfoRepository caInfoRepository;
    @Mock
    private OcspInfoRepository ocspInfoRepository;
    @Mock
    private AuditDataHelper auditDataHelper;

    private CertificationServicesServiceImpl service;

    @BeforeEach
    void setup() {
        OcspResponderConverter ocspResponderConverter = new OcspResponderConverter(approvedCaRepository);
        CertificateConverter certConverter = new CertificateConverter(new KeyUsageConverter());
        CaInfoConverter caInfoConverter = new CaInfoConverter(certConverter, ocspResponderConverter);
        ApprovedCaConverter approvedCaConverter = new ApprovedCaConverter(ocspResponderConverter, caInfoConverter);

        service = new CertificationServicesServiceImpl(
                approvedCaRepository,
                ocspInfoRepository,
                caInfoRepository,
                auditDataHelper,
                approvedCaConverter,
                ocspResponderConverter,
                caInfoConverter,
                certConverter);
    }


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
    void delete() {
        ApprovedCaEntity entity = approvedCa();
        when(approvedCaRepository.findById(ID)).thenReturn(Optional.of(entity));

        service.delete(ID);

        verify(approvedCaRepository).delete(entity);
        verify(auditDataHelper).put(CA_ID, ID);
    }

    @Test
    void deleteShouldThrowNotFound() {
        when(approvedCaRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Certification service not found.");

        verify(auditDataHelper).put(CA_ID, ID);
    }

    @Test
    void getShouldThrowNotFoundException() {
        when(approvedCaRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(ID));
    }

    @Test
    void addCertificationServiceOcspResponder() throws Exception {
        var mockOcspResponderRequest = ocspResponderAddRequest();
        var mockOcspInfo = ocspInfo();
        when(approvedCaRepository.findById(mockOcspResponderRequest.getCaId()))
                .thenReturn(Optional.of(approvedCa()));
        when(ocspInfoRepository.save(any())).thenReturn(mockOcspInfo);

        var result = service.addOcspResponder(mockOcspResponderRequest);

        assertThat(result).usingRecursiveComparison(RecursiveComparisonConfiguration.builder()
                        .withIgnoredFields("caId", "certificate")
                        .build())
                .isEqualTo(mockOcspInfo);
        verify(auditDataHelper).put(CA_ID, mockOcspInfo.getCaInfo().getId());
        verify(auditDataHelper).put(OCSP_ID, mockOcspInfo.getId());
        verify(auditDataHelper).put(OCSP_URL, mockOcspInfo.getUrl());
        verify(auditDataHelper).put(OCSP_CERT_HASH, "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF");
        verify(auditDataHelper).put(OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @Test
    @SneakyThrows
    void addIntermediateCa() {
        final X509Certificate certificate = TestCertUtil.getCa().certChain[0];
        final byte[] certificateBytes = certificate.getEncoded();
        var approvedCaMock = mock(ApprovedCaEntity.class);

        when(approvedCaRepository.findById(ID)).thenReturn(Optional.of(approvedCaMock));

        final CertificateAuthority certificateAuthority = service.addIntermediateCa(ID, certificateBytes);

        assertEquals("24AFDE09AA818A20D3EE7A4A2264BA247DA5C3F9", certificateAuthority.getCaCertificate().getHash());

        ArgumentCaptor<CaInfoEntity> captor = ArgumentCaptor.forClass(CaInfoEntity.class);
        verify(caInfoRepository).save(captor.capture());
        assertEquals(certificate.getNotBefore().toInstant(), captor.getValue().getValidFrom());
        assertEquals(certificate.getNotAfter().toInstant(), captor.getValue().getValidTo());
        assertEquals(certificateBytes, captor.getValue().getCert());

        verify(auditDataHelper).put(CA_ID, ID);
        verify(auditDataHelper).put(INTERMEDIATE_CA_ID, 0);
        verify(auditDataHelper).put(INTERMEDIATE_CA_CERT_HASH, "24:AF:DE:09:AA:81:8A:20:D3:EE:7A:4A:22:64:BA:24:7D:A5:C3:F9");
        verify(auditDataHelper).put(INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @Test
    void getIntermediateCas() {
        when(approvedCaRepository.findById(ID)).thenReturn(Optional.of(approvedCa()));

        final List<CertificateAuthority> intermediateCas = service.getIntermediateCas(ID);

        assertEquals(2, intermediateCas.size());
        intermediateCas
                .forEach(ca -> assertNotNull(ca.getCaCertificate()));
    }

    @Test
    void getCertificateDetails() {
        when(approvedCaRepository.findById(ID)).thenReturn(Optional.of(approvedCa()));

        final CertificateDetails certificateDetails = service.getCertificateDetails(ID);

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

    private ApprovedCaEntity approvedCa() {
        var ca = new ApprovedCaEntity();
        ca.setName(CA_NAME);
        ca.setAuthenticationOnly(true);
        ca.setCertProfileInfo(CERT_PROFILE);
        ca.setCaInfo(caInfo());
        ca.setIntermediateCaInfos(Set.of(caInfo(), caInfo()));
        return ca;
    }

    @SneakyThrows
    private CaInfoEntity caInfo() {
        var caInfo = new CaInfoEntity();
        caInfo.setValidFrom(VALID_FROM);
        caInfo.setValidTo(VALID_TO);
        caInfo.setCert(generateAuthCert());
        return caInfo;
    }


    private OcspResponderAddRequest ocspResponderAddRequest() throws CertificateEncodingException {
        var request = new OcspResponderAddRequest();
        request.setCaId(1)
                .setUrl("https://flakyocsp:666")
                .setCertificate(TestCertUtil.getOcspSigner().certChain[0].getEncoded());
        return request;
    }

    private OcspInfoEntity ocspInfo() throws CertificateEncodingException {
        return new OcspInfoEntity(new CaInfoEntity(), "https://flakyocsp:666",
                TestCertUtil.getOcspSigner().certChain[0].getEncoded());
    }

}
