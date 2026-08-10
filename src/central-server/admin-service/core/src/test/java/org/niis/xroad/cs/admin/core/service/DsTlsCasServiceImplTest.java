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
import ee.ria.xroad.common.util.TimeUtils;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.AddDsTlsCaRequest;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaIntermediateCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaListItem;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsCaConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsCaInfoConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaEntity;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaInfoEntity;
import org.niis.xroad.cs.admin.core.repository.DsTlsCaInfoRepository;
import org.niis.xroad.cs.admin.core.repository.DsTlsCaRepository;
import org.niis.xroad.cs.admin.core.validation.UrlValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.TestCertUtil.generateAuthCert;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ACME_DIRECTORY_URL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;

@ExtendWith(MockitoExtension.class)
class DsTlsCasServiceImplTest {
    private static final Integer ID = 123;
    private static final Instant VALID_FROM = TimeUtils.now().minus(1, DAYS);
    private static final Instant VALID_TO = TimeUtils.now().plus(1, DAYS);
    private static final String DS_TLS_CA_NAME = "X-Road Test DS TLS CA";
    private static final String ACME_SERVER_DIRECTORY_URL = "https://ca-for-test/acme/directory";
    private static final String DS_TLS_CERTIFICATE_PROFILE_ID = "xrd-ds-tls";

    @Mock
    private DsTlsCaRepository dsTlsCaRepository;
    @Mock
    private DsTlsCaInfoRepository dsTlsCaInfoRepository;
    @Mock
    private AuditDataHelper auditDataHelper;

    private DsTlsCasServiceImpl service;

    @BeforeEach
    void setup() {
        CertificateConverter certConverter = new CertificateConverter(new KeyUsageConverter());
        DsTlsCaInfoConverter dsTlsCaInfoConverter = new DsTlsCaInfoConverter(certConverter);
        DsTlsCaConverter dsTlsCaConverter = new DsTlsCaConverter(dsTlsCaInfoConverter);
        UrlValidator urlValidator = new UrlValidator();

        service = new DsTlsCasServiceImpl(
                dsTlsCaRepository,
                dsTlsCaInfoRepository,
                auditDataHelper,
                dsTlsCaConverter,
                dsTlsCaInfoConverter,
                certConverter,
                urlValidator);
    }

    @Test
    void getDsTlsCas() {
        when(dsTlsCaRepository.findAll()).thenReturn(List.of(dsTlsCa()));

        List<DsTlsCaListItem> dsTlsCas = service.getDsTlsCas();

        assertEquals(1, dsTlsCas.size());
        var ca = dsTlsCas.iterator().next();
        assertEquals(DS_TLS_CA_NAME, ca.getName());
        assertEquals(VALID_FROM, ca.getNotBefore());
        assertEquals(VALID_TO, ca.getNotAfter());
    }

    @Test
    void get() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCa()));

        final DsTlsCa result = service.get(ID);

        assertEquals(DS_TLS_CA_NAME, result.getName());
        assertEquals(VALID_FROM, result.getNotBefore());
        assertEquals(VALID_TO, result.getNotAfter());
        assertEquals(ACME_SERVER_DIRECTORY_URL, result.getAcmeServerDirectoryUrl());
        assertEquals(DS_TLS_CERTIFICATE_PROFILE_ID, result.getDsTlsCertificateProfileId());
    }

    @Test
    void getShouldThrowNotFound() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=ds_tls_ca_not_found]");
    }

    @Test
    void delete() {
        DsTlsCaEntity entity = dsTlsCa();
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(entity));

        service.delete(ID);

        verify(dsTlsCaRepository).delete(entity);
        verify(auditDataHelper).put(CA_ID, ID);
    }

    @Test
    @SneakyThrows
    void add() {
        final X509Certificate certificate = TestCertUtil.getCa().certChain[0];
        final byte[] certificateBytes = certificate.getEncoded();
        var request = new AddDsTlsCaRequest(certificateBytes, ACME_SERVER_DIRECTORY_URL, DS_TLS_CERTIFICATE_PROFILE_ID);

        when(dsTlsCaInfoRepository.saveAndFlush(any())).thenReturn(dsTlsCaInfo());
        when(dsTlsCaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DsTlsCa result = service.add(request);

        assertEquals(ACME_SERVER_DIRECTORY_URL, result.getAcmeServerDirectoryUrl());
        assertEquals(DS_TLS_CERTIFICATE_PROFILE_ID, result.getDsTlsCertificateProfileId());

        ArgumentCaptor<DsTlsCaInfoEntity> caInfoCaptor = ArgumentCaptor.forClass(DsTlsCaInfoEntity.class);
        verify(dsTlsCaInfoRepository).saveAndFlush(caInfoCaptor.capture());
        assertEquals(certificate.getNotBefore().toInstant(), caInfoCaptor.getValue().getValidFrom());
        assertEquals(certificate.getNotAfter().toInstant(), caInfoCaptor.getValue().getValidTo());

        verify(auditDataHelper).put(ACME_DIRECTORY_URL, ACME_SERVER_DIRECTORY_URL);
    }

    @Test
    void update() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCa()));
        when(dsTlsCaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var update = new DsTlsCa()
                .setId(ID)
                .setAcmeServerDirectoryUrl("https://updated/acme/directory")
                .setDsTlsCertificateProfileId("updated-profile");

        DsTlsCa result = service.update(update);

        assertEquals("https://updated/acme/directory", result.getAcmeServerDirectoryUrl());
        assertEquals("updated-profile", result.getDsTlsCertificateProfileId());
    }

    @Test
    @SneakyThrows
    void addIntermediateCa() {
        final X509Certificate certificate = TestCertUtil.getCa().certChain[0];
        final byte[] certificateBytes = certificate.getEncoded();
        var dsTlsCaMock = mock(DsTlsCaEntity.class);

        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCaMock));

        final DsTlsCaIntermediateCa intermediateCa = service.addIntermediateCa(ID, certificateBytes);

        assertNotNull(intermediateCa.getCaCertificate());

        ArgumentCaptor<DsTlsCaInfoEntity> captor = ArgumentCaptor.forClass(DsTlsCaInfoEntity.class);
        verify(dsTlsCaInfoRepository).save(captor.capture());
        assertEquals(certificate.getNotBefore().toInstant(), captor.getValue().getValidFrom());
        assertEquals(certificate.getNotAfter().toInstant(), captor.getValue().getValidTo());

        verify(auditDataHelper).put(CA_ID, ID);
        verify(auditDataHelper).put(INTERMEDIATE_CA_ID, 0);
        verify(auditDataHelper).put(INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
        verify(auditDataHelper).put(eq(INTERMEDIATE_CA_CERT_HASH), any());
    }

    @Test
    void getIntermediateCas() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCa()));

        final List<DsTlsCaIntermediateCa> intermediateCas = service.getIntermediateCas(ID);

        assertEquals(2, intermediateCas.size());
        intermediateCas.forEach(ca -> assertNotNull(ca.getCaCertificate()));
    }

    @Test
    @SneakyThrows
    void getIntermediateCa() {
        var intermediateCaInfo = intermediateCaInfo();
        when(dsTlsCaInfoRepository.findById(1)).thenReturn(Optional.of(intermediateCaInfo));

        final DsTlsCaIntermediateCa result = service.getIntermediateCa(1);

        assertNotNull(result.getCaCertificate());
    }

    @Test
    void getIntermediateCaShouldThrowNotFoundWhenNotAnIntermediateCa() {
        when(dsTlsCaInfoRepository.findById(1)).thenReturn(Optional.of(dsTlsCaInfo()));

        assertThatThrownBy(() -> service.getIntermediateCa(1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=ds_tls_ca_intermediate_ca_not_found]");
    }

    @Test
    @SneakyThrows
    void deleteIntermediateCa() {
        var intermediateCaInfo = intermediateCaInfo();
        when(dsTlsCaInfoRepository.findById(1)).thenReturn(Optional.of(intermediateCaInfo));

        service.deleteIntermediateCa(1);

        verify(dsTlsCaInfoRepository).delete(intermediateCaInfo);
        verify(auditDataHelper).put(INTERMEDIATE_CA_ID, intermediateCaInfo.getId());
    }

    @Test
    void getCertificateDetails() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCa()));

        final var certificateDetails = service.getCertificateDetails(ID);

        assertNotNull(certificateDetails);
    }

    private DsTlsCaEntity dsTlsCa() {
        var ca = new DsTlsCaEntity();
        ca.setName(DS_TLS_CA_NAME);
        ca.setCaInfo(dsTlsCaInfo());
        ca.setAcmeServerDirectoryUrl(ACME_SERVER_DIRECTORY_URL);
        ca.setDsTlsCertificateProfileId(DS_TLS_CERTIFICATE_PROFILE_ID);
        ca.setIntermediateCaInfos(Set.of(intermediateCaInfo(), intermediateCaInfo()));
        return ca;
    }

    @SneakyThrows
    private DsTlsCaInfoEntity dsTlsCaInfo() {
        var caInfo = new DsTlsCaInfoEntity();
        caInfo.setValidFrom(VALID_FROM);
        caInfo.setValidTo(VALID_TO);
        caInfo.setCert(generateAuthCert());
        return caInfo;
    }

    @SneakyThrows
    private DsTlsCaInfoEntity intermediateCaInfo() {
        var caInfo = new DsTlsCaInfoEntity();
        caInfo.setDsTlsCa(new DsTlsCaEntity());
        caInfo.setValidFrom(VALID_FROM);
        caInfo.setValidTo(VALID_TO);
        caInfo.setCert(generateAuthCert());
        return caInfo;
    }

}
