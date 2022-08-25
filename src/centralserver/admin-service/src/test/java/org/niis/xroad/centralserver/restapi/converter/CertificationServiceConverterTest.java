/**
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
package org.niis.xroad.centralserver.restapi.converter;

import ee.ria.xroad.common.TestCertUtil;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.restapi.dto.AddApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.restapi.entity.ApprovedCa;
import org.niis.xroad.centralserver.restapi.entity.CaInfo;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CertificationServiceConverterTest {

    CertificationServiceConverter converter = new CertificationServiceConverter();

    @Test
    void convertToDomain() {
        ApprovedCa approvedCaMock = mockApprovedCa();

        ApprovedCertificationServiceDto result = converter.toDomain(approvedCaMock);

        assertEquals(approvedCaMock.getName(), result.getCaCertificate().getSubjectCommonName());
        assertEquals(FormatUtils.fromInstantToOffsetDateTime(approvedCaMock.getCaInfo().getValidFrom()),
                result.getCaCertificate().getNotBefore());
        assertEquals(FormatUtils.fromInstantToOffsetDateTime(approvedCaMock.getCaInfo().getValidTo()),
                result.getCaCertificate().getNotAfter());
    }

    @Test
    @SneakyThrows
    void convertToEntity() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile("test", "certification.crt",
                "multipart/form-data", TestCertUtil.generateAuthCert());
        String certProfileInfo = "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";
        var approvedCaMock = new AddApprovedCertificationServiceDto(mockMultipartFile, certProfileInfo, true);
        ApprovedCa result = converter.toEntity(approvedCaMock);

        assertEquals("Subject", result.getName());
        assertEquals(certProfileInfo, result.getCertProfileInfo());
        assertThat(result.getCaInfo().getValidFrom())
                .isAfter(FormatUtils.fromOffsetDateTimeToInstant(OffsetDateTime.now().minusMinutes(1)));
        assertThat(result.getCaInfo().getValidTo())
                .isBefore(FormatUtils.fromOffsetDateTimeToInstant(OffsetDateTime.now().plusYears(1)));
    }

    @SneakyThrows
    private ApprovedCa mockApprovedCa() {
        CaInfo caInfo = new CaInfo();
        caInfo.setValidFrom(FormatUtils.fromOffsetDateTimeToInstant(OffsetDateTime.now()));
        caInfo.setValidTo(FormatUtils.fromOffsetDateTimeToInstant(OffsetDateTime.now().plusDays(1)));
        caInfo.setCert(TestCertUtil.generateAuthCert());
        ApprovedCa ca = new ApprovedCa();
        ca.setName("X-Road Test CA CN");
        ca.setCaInfo(caInfo);
        return ca;
    }
}
