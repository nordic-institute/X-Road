/*
 * The MIT License
 * <p>
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
package org.niis.xroad.cs.admin.core.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.OcspResponderAddRequest;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.niis.xroad.cs.admin.core.repository.ApprovedCaRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcspResponderConverterTest {
    private static final Integer CA_ID = 123;
    private static final String URL = "https://github.com";

    @Mock
    private ApprovedCaRepository approvedCaRepository;

    @Mock
    private ApprovedCaEntity approvedCaEntity;
    @Mock
    private CaInfoEntity caInfoEntity;

    @InjectMocks
    private OcspResponderConverter ocspResponderConverter;

    @Test
    void shouldMapToEntity() {
        when(approvedCaRepository.findById(CA_ID)).thenReturn(Optional.of(approvedCaEntity));

        var request = createOcspResponderAddRequest();
        when(approvedCaEntity.getCaInfo()).thenReturn(caInfoEntity);
        var result = ocspResponderConverter.toEntity(request);

        assertThat(result).isNotNull();
        assertThat(result.getCert()).isEmpty();
        assertThat(result.getCaInfo()).isEqualTo(caInfoEntity);
        assertThat(result.getUrl()).isEqualTo(URL);
    }


    @Test
    void shouldFailMapToEntityOnWrongUrl() {
        when(approvedCaRepository.findById(CA_ID)).thenReturn(Optional.of(approvedCaEntity));

        var request = createOcspResponderAddRequest();
        request.setUrl("wrong");

        assertThatCode(() -> ocspResponderConverter.toEntity(request))
                .isInstanceOf(ValidationFailureException.class);
    }


    @Test
    void shouldMapToMode() {
        var request = createOcspInfoEntity();

        var result = ocspResponderConverter.toModel(request);

        assertThat(result).isNotNull();
        assertThat(result.getCaId()).isEqualTo(CA_ID);
        assertThat(result.getUrl()).isEqualTo(URL);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    private OcspResponderAddRequest createOcspResponderAddRequest() {
        var request = new OcspResponderAddRequest();
        request.setCaId(CA_ID);
        request.setUrl(URL);
        request.setCertificate(new byte[0]);
        return request;
    }

    private OcspInfoEntity createOcspInfoEntity() {
        when(caInfoEntity.getId()).thenReturn(CA_ID);

        var ocspInfo = new OcspInfoEntity(caInfoEntity, URL, new byte[0]);
        ocspInfo.getCaInfo().getOcspInfos().add(ocspInfo);

        return ocspInfo;
    }
}
