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

package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.api.dto.OcspResponder;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;
import org.niis.xroad.cs.admin.api.dto.OcspResponderRequest;
import org.niis.xroad.cs.admin.core.converter.CaInfoConverter;
import org.niis.xroad.cs.admin.core.converter.OcspResponderConverter;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.CaInfoRepository;
import org.niis.xroad.cs.admin.core.repository.OcspInfoRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.cert.CertificateEncodingException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.TestCertUtil.getOcspSigner;
import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_CERT_HASH_ALGORITHM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OCSP_URL;

@ExtendWith(MockitoExtension.class)
class IntermediateCasServiceImplTest {

    private static final Integer ID = 123;
    private static final Integer NEW_ID = 1369;
    private static final Integer OCSP_INFO_ID = 1234;
    private static final String URL = "http://test-url";
    private static final byte[] TEST_CERT;

    static {
        try {
            TEST_CERT = getOcspSigner().certChain[0].getEncoded();
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Mock
    private CaInfoRepository caInfoRepository;
    @Mock
    private OcspInfoRepository ocspInfoRepository;
    @Mock
    private CaInfoConverter caInfoConverter;
    @Mock
    private OcspResponderConverter ocspResponderConverter;
    @Mock
    private CaInfoEntity caInfo;
    @Mock
    private OcspResponder ocspResponder;
    @Mock
    private CertificateAuthority certificateAuthority;
    @Mock
    private AuditDataHelper auditDataHelper;
    @InjectMocks
    private IntermediateCasServiceImpl intermediateCasService;

    @Test
    void get() {
        when(caInfoRepository.findById(ID)).thenReturn(Optional.of(caInfo));
        when(caInfo.getApprovedCa()).thenReturn(new ApprovedCaEntity());
        when(caInfoConverter.toCertificateAuthority(caInfo)).thenReturn(certificateAuthority);

        final CertificateAuthority ca = intermediateCasService.get(ID);

        assertEquals(certificateAuthority, ca);
    }

    @Test
    void delete() {
        when(caInfoRepository.findById(ID)).thenReturn(Optional.of(caInfo));
        when(caInfo.getApprovedCa()).thenReturn(new ApprovedCaEntity());

        intermediateCasService.delete(ID);

        verify(caInfoRepository, times(1)).delete(caInfo);
        verify(auditDataHelper).put(INTERMEDIATE_CA_ID, caInfo.getId());
    }

    @Test
    void getShouldThrowNotFoundException() {
        when(caInfoRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> intermediateCasService.get(ID));
        assertThrows(NotFoundException.class, () -> intermediateCasService.delete(ID));

        verifyNoInteractions(caInfoConverter);
    }

    @Test
    void getShouldThrowNotFoundExceptionWhenNotIntermediate() {
        when(caInfoRepository.findById(ID)).thenReturn(Optional.of(caInfo));
        when(caInfo.getApprovedCa()).thenReturn(null);

        assertThrows(NotFoundException.class, () -> intermediateCasService.get(ID));
        verifyNoInteractions(caInfoConverter);
    }

    @Test
    void addOcspResponder() {
        final OcspResponderRequest ocspResponderRequest = new OcspResponderAddRequest()
                .setUrl(URL)
                .setCertificate(TEST_CERT);

        final OcspInfoEntity ocspInfoEntity = ocspInfoEntity(NEW_ID);

        when(caInfoRepository.findById(ID)).thenReturn(Optional.of(caInfo));
        when(caInfo.getApprovedCa()).thenReturn(new ApprovedCaEntity());
        when(ocspInfoRepository.save(isA(OcspInfoEntity.class))).thenReturn(ocspInfoEntity);
        when(ocspResponderConverter.toModel(isA(OcspInfoEntity.class))).thenReturn(ocspResponder);

        final OcspResponder ocspResponderSaved = intermediateCasService.addOcspResponder(ID, ocspResponderRequest);

        assertEquals(ocspResponder, ocspResponderSaved);

        ArgumentCaptor<OcspInfoEntity> captor = ArgumentCaptor.forClass(OcspInfoEntity.class);

        verify(ocspInfoRepository).save(captor.capture());
        verifyEntity(captor.getValue());

        verify(auditDataHelper).put(INTERMEDIATE_CA_ID, ID);
        verify(auditDataHelper).put(OCSP_ID, NEW_ID);
        verify(auditDataHelper).put(OCSP_URL, URL);
        verify(auditDataHelper).put(OCSP_CERT_HASH, "F5:1B:1F:9C:07:23:4C:DA:E6:4C:99:CB:FC:D8:EE:0E:C5:5F:A4:AF");
        verify(auditDataHelper).put(OCSP_CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);
    }

    @Test
    void deleteOcspResponder() {
        when(caInfoRepository.findById(ID)).thenReturn(Optional.of(caInfo));
        when(caInfo.getApprovedCa()).thenReturn(new ApprovedCaEntity());
        final OcspInfoEntity ocspResponderToDelete = ocspInfoEntity(OCSP_INFO_ID);
        final Set<OcspInfoEntity> ocspResponders = new HashSet<>(Set.of(ocspResponderToDelete));
        when(caInfo.getOcspInfos()).thenReturn(ocspResponders);

        intermediateCasService.deleteOcspResponder(ID, OCSP_INFO_ID);

        verify(ocspInfoRepository).delete(ocspResponderToDelete);
        verify(auditDataHelper).put(OCSP_ID, OCSP_INFO_ID);
    }

    @Test
    void getOcspResponders() {
        final OcspInfoEntity ocspInfo = ocspInfoEntity(NEW_ID);
        when(caInfoRepository.findById(ID)).thenReturn(Optional.of(caInfo));
        when(caInfo.getApprovedCa()).thenReturn(new ApprovedCaEntity());
        when(caInfo.getOcspInfos()).thenReturn(Set.of(ocspInfo));
        when(ocspResponderConverter.toModel(ocspInfo)).thenReturn(ocspResponder);

        final Set<OcspResponder> ocspResponders = intermediateCasService.getOcspResponders(ID);

        assertThat(ocspResponders).containsExactly(ocspResponder);
    }

    private OcspInfoEntity ocspInfoEntity(Integer ocspResponderId) {
        final OcspInfoEntity entity = new OcspInfoEntity(mock(CaInfoEntity.class), URL, TEST_CERT);
        ReflectionTestUtils.setField(entity, "id", ocspResponderId);
        return entity;
    }

    private void verifyEntity(OcspInfoEntity value) {
        assertEquals(URL, value.getUrl());
        assertEquals(TEST_CERT, value.getCert());

    }

}
