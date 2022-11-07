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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.dto.converter.CaInfoConverter;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.repository.CaInfoRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INTERMEDIATE_CA_ID;

@ExtendWith(MockitoExtension.class)
class IntermediateCasServiceImplTest {

    private static final Integer ID = 123;

    @Mock
    private CaInfoRepository caInfoRepository;
    @Mock
    private CaInfoConverter caInfoConverter;
    @Mock
    private CaInfoEntity caInfo;
    @Mock
    private CertificateAuthority certificateAuthority;
    @Mock
    private AuditDataHelper auditData;
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
        verify(auditData).put(INTERMEDIATE_CA_ID, caInfo.getId());
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

}
