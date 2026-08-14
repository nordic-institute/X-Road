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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.ApprovedDsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthority;
import org.niis.xroad.cs.admin.api.dto.DsTlsCertificationAuthorityListItem;
import org.niis.xroad.cs.admin.api.dto.DsTlsIntermediateCertificateAuthority;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsCaConverter;
import org.niis.xroad.cs.admin.core.converter.DsTlsIntermediateCaConverter;
import org.niis.xroad.cs.admin.core.converter.KeyUsageConverter;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaEntity;
import org.niis.xroad.cs.admin.core.entity.DsTlsIntermediateCaEntity;
import org.niis.xroad.cs.admin.core.repository.DsTlsCaRepository;
import org.niis.xroad.cs.admin.core.repository.DsTlsIntermediateCaRepository;
import org.niis.xroad.cs.admin.core.validation.UrlValidator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ACME_DIRECTORY_URL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CA_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_CERTIFICATE_PROFILE_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_INTERMEDIATE_CA_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_INTERMEDIATE_CA_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DS_TLS_INTERMEDIATE_CA_ID;

@ExtendWith(MockitoExtension.class)
class DsTlsCertificationAuthoritiesServiceImplTest {

    private static final Integer ID = 123;
    private static final String CA_NAME = "Let's Encrypt";
    private static final String ACME_SERVER_DIRECTORY_URL = "https://acme-v02.api.letsencrypt.org/directory";
    private static final String DS_TLS_CERTIFICATE_PROFILE_ID_VALUE = "xrd-ds-tls";

    @Mock
    private DsTlsCaRepository dsTlsCaRepository;
    @Mock
    private DsTlsIntermediateCaRepository dsTlsIntermediateCaRepository;
    @Mock
    private AuditDataHelper auditDataHelper;

    private DsTlsCertificationAuthoritiesServiceImpl service;

    @BeforeEach
    void setup() {
        CertificateConverter certConverter = new CertificateConverter(new KeyUsageConverter());
        DsTlsIntermediateCaConverter dsTlsIntermediateCaConverter = new DsTlsIntermediateCaConverter(certConverter);
        DsTlsCaConverter dsTlsCaConverter = new DsTlsCaConverter(dsTlsIntermediateCaConverter);
        UrlValidator urlValidator = new UrlValidator();

        service = new DsTlsCertificationAuthoritiesServiceImpl(
                dsTlsCaRepository,
                dsTlsIntermediateCaRepository,
                dsTlsCaConverter,
                dsTlsIntermediateCaConverter,
                certConverter,
                urlValidator,
                auditDataHelper);
    }

    @Test
    @SneakyThrows
    void add() {
        final X509Certificate certificate = TestCertUtil.getCa().certChain[0];
        final byte[] certificateBytes = certificate.getEncoded();
        final var newCa = new ApprovedDsTlsCertificationAuthority(certificateBytes, CA_NAME, ACME_SERVER_DIRECTORY_URL,
                DS_TLS_CERTIFICATE_PROFILE_ID_VALUE);

        when(dsTlsCaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final DsTlsCertificationAuthority result = service.add(newCa);

        assertEquals(CA_NAME, result.getName());
        assertEquals(ACME_SERVER_DIRECTORY_URL, result.getAcmeServerDirectoryUrl());
        assertEquals(DS_TLS_CERTIFICATE_PROFILE_ID_VALUE, result.getDsTlsCertificateProfileId());

        ArgumentCaptor<DsTlsCaEntity> captor = ArgumentCaptor.forClass(DsTlsCaEntity.class);
        verify(dsTlsCaRepository).saveAndFlush(captor.capture());
        assertEquals(CA_NAME, captor.getValue().getName());
        assertEquals(certificate.getNotBefore().toInstant(), captor.getValue().getValidFrom());
        assertEquals(certificate.getNotAfter().toInstant(), captor.getValue().getValidTo());

        verify(auditDataHelper).put(DS_TLS_CA_NAME, CA_NAME);
        verify(auditDataHelper).put(ACME_DIRECTORY_URL, ACME_SERVER_DIRECTORY_URL);
        verify(auditDataHelper).put(DS_TLS_CERTIFICATE_PROFILE_ID, DS_TLS_CERTIFICATE_PROFILE_ID_VALUE);
    }

    @Test
    void addShouldThrowBadRequestOnInvalidCertificate() {
        final var newCa = new ApprovedDsTlsCertificationAuthority("not a cert".getBytes(), CA_NAME, null, null);

        assertThrows(BadRequestException.class, () -> service.add(newCa));
    }

    @Test
    void get() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCa()));

        final DsTlsCertificationAuthority result = service.get(ID);

        assertEquals(CA_NAME, result.getName());
        assertEquals(ACME_SERVER_DIRECTORY_URL, result.getAcmeServerDirectoryUrl());
        assertEquals(DS_TLS_CERTIFICATE_PROFILE_ID_VALUE, result.getDsTlsCertificateProfileId());
    }

    @Test
    void getShouldThrowNotFoundException() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(ID));
    }

    @Test
    void delete() {
        DsTlsCaEntity entity = dsTlsCa();
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(entity));

        service.delete(ID);

        verify(dsTlsCaRepository).delete(entity);
        verify(auditDataHelper).put(DS_TLS_CA_ID, ID);
    }

    @Test
    void deleteShouldThrowNotFound() {
        when(dsTlsCaRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=ds_tls_certification_authority_not_found]");
    }

    @Test
    void update() {
        DsTlsCaEntity entity = dsTlsCa();
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(dsTlsCaRepository.save(entity)).thenReturn(entity);

        final var updatedAcmeUrl = "https://new-acme.example/directory";
        final var updatedProfileId = "new-profile-id";
        final DsTlsCertificationAuthority settings = new DsTlsCertificationAuthority()
                .setId(ID)
                .setAcmeServerDirectoryUrl(updatedAcmeUrl)
                .setDsTlsCertificateProfileId(updatedProfileId);

        final DsTlsCertificationAuthority result = service.update(settings);

        assertEquals(updatedAcmeUrl, result.getAcmeServerDirectoryUrl());
        assertEquals(updatedProfileId, result.getDsTlsCertificateProfileId());
        verify(auditDataHelper).put(ACME_DIRECTORY_URL, updatedAcmeUrl);
        verify(auditDataHelper).put(DS_TLS_CERTIFICATE_PROFILE_ID, updatedProfileId);
    }

    @Test
    void getCertificateDetails() {
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(dsTlsCa()));

        final CertificateDetails certificateDetails = service.getCertificateDetails(ID);

        assertThat(certificateDetails).isNotNull();
    }

    @Test
    @SneakyThrows
    void addIntermediateCa() {
        final X509Certificate certificate = TestCertUtil.getCa().certChain[0];
        final byte[] certificateBytes = certificate.getEncoded();
        final DsTlsCaEntity parent = dsTlsCa();

        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(parent));

        final DsTlsIntermediateCertificateAuthority result = service.addIntermediateCa(ID, certificateBytes);

        assertThat(result.getCaCertificate()).isNotNull();

        ArgumentCaptor<DsTlsIntermediateCaEntity> captor = ArgumentCaptor.forClass(DsTlsIntermediateCaEntity.class);
        verify(dsTlsIntermediateCaRepository).save(captor.capture());
        assertEquals(parent, captor.getValue().getDsTlsCa());
        assertEquals(certificateBytes, captor.getValue().getCert());

        verify(auditDataHelper).put(DS_TLS_CA_ID, ID);
        verify(auditDataHelper).put(DS_TLS_INTERMEDIATE_CA_ID, captor.getValue().getId());
        verify(auditDataHelper).put(DS_TLS_INTERMEDIATE_CA_CERT_HASH, calculateCertHexHashDelimited(certificateBytes));
        verify(auditDataHelper).put(DS_TLS_INTERMEDIATE_CA_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @Test
    void getIntermediateCas() {
        final DsTlsCaEntity entity = dsTlsCa();
        entity.setIntermediateCas(new HashSet<>(Set.of(intermediateCa(entity))));
        when(dsTlsCaRepository.findById(ID)).thenReturn(Optional.of(entity));

        final List<DsTlsIntermediateCertificateAuthority> result = service.getIntermediateCas(ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void getDsTlsCertificationAuthorities() {
        when(dsTlsCaRepository.findAll()).thenReturn(List.of(dsTlsCa()));

        final List<DsTlsCertificationAuthorityListItem> result = service.getDsTlsCertificationAuthorities();

        assertThat(result).singleElement().satisfies(item -> assertEquals(CA_NAME, item.getName()));
    }

    @Test
    void findAll() {
        when(dsTlsCaRepository.findAll()).thenReturn(List.of(dsTlsCa()));

        final List<DsTlsCertificationAuthority> result = service.findAll();

        assertThat(result).singleElement().satisfies(ca -> {
            assertEquals(CA_NAME, ca.getName());
            assertEquals(ACME_SERVER_DIRECTORY_URL, ca.getAcmeServerDirectoryUrl());
            assertEquals(DS_TLS_CERTIFICATE_PROFILE_ID_VALUE, ca.getDsTlsCertificateProfileId());
        });
    }

    @SneakyThrows
    private DsTlsCaEntity dsTlsCa() {
        var ca = new DsTlsCaEntity();
        ca.setName(CA_NAME);
        ca.setCert(TestCertUtil.getCa().certChain[0].getEncoded());
        ca.setValidFrom(TimeUtils.now().minus(1, ChronoUnit.DAYS));
        ca.setValidTo(TimeUtils.now().plus(1, ChronoUnit.DAYS));
        ca.setAcmeServerDirectoryUrl(ACME_SERVER_DIRECTORY_URL);
        ca.setDsTlsCertificateProfileId(DS_TLS_CERTIFICATE_PROFILE_ID_VALUE);
        return ca;
    }

    @SneakyThrows
    private DsTlsIntermediateCaEntity intermediateCa(DsTlsCaEntity parent) {
        var intermediateCa = new DsTlsIntermediateCaEntity();
        intermediateCa.setDsTlsCa(parent);
        intermediateCa.setCert(TestCertUtil.getCa().certChain[0].getEncoded());
        return intermediateCa;
    }

}
