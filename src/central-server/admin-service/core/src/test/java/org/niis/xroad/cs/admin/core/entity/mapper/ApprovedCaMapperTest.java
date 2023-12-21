/*
 * The MIT License
 *
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
package org.niis.xroad.cs.admin.core.entity.mapper;

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.cs.admin.core.entity.OcspInfoEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {ApprovedCaMapperImpl.class})
class ApprovedCaMapperTest {
    private static final String URL = "https://github.com";
    private static final Instant DATE_FROM = TimeUtils.now().minusSeconds(60);
    private static final Instant DATE_TO = TimeUtils.now();

    @Autowired
    private ApprovedCaMapper approvedCaMapper;

    @Test
    void shouldMapAllFields() {
        var source = new ApprovedCaEntity();
        source.setCaInfo(getCaInfoEntity());
        source.setName("name");
        source.setAuthenticationOnly(true);
        source.setIdentifierDecoderMemberClass("DecoderMemberClass");
        source.setIdentifierDecoderMethodName("DecoderMethodName");
        source.setCertProfileInfo("certProfileInfo");
        source.getIntermediateCaInfos().add(getCaInfoEntity());

        var result = approvedCaMapper.toTarget(source);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(source.getName());
        assertThat(result.getAuthenticationOnly()).isEqualTo(source.getAuthenticationOnly());
        assertThat(result.getIdentifierDecoderMemberClass()).isEqualTo(source.getIdentifierDecoderMemberClass());
        assertThat(result.getIdentifierDecoderMethodName()).isEqualTo(source.getIdentifierDecoderMethodName());
        assertThat(result.getCertProfileInfo()).isEqualTo(source.getCertProfileInfo());
        assertThat(result.getIntermediateCaInfos()).hasSize(1);

        assertThat(result.getCaInfo().getCert()).isEmpty();
        assertThat(result.getCaInfo().getValidFrom()).isEqualTo(DATE_FROM);
        assertThat(result.getCaInfo().getValidTo()).isEqualTo(DATE_TO);

        var ocspInfo = result.getCaInfo().getOcspInfos().stream().findFirst().orElseThrow();
        assertThat(ocspInfo.getUrl()).isEqualTo(URL);
        assertThat(ocspInfo.getCert()).isEmpty();
    }

    private CaInfoEntity getCaInfoEntity() {
        var caInfo = new CaInfoEntity();

        var ocspInfoEntity = new OcspInfoEntity();
        ocspInfoEntity.setUrl(URL);
        ocspInfoEntity.setCert(new byte[0]);

        caInfo.setOcspInfos(Set.of(ocspInfoEntity));
        caInfo.setCert(new byte[0]);
        caInfo.setValidFrom(DATE_FROM);
        caInfo.setValidTo(DATE_TO);
        return caInfo;
    }
}
