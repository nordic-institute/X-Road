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

package org.niis.xroad.cs.admin.core.converter;

import ee.ria.xroad.common.TestCertUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.core.entity.ApprovedCaEntity;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.niis.xroad.globalconf.model.CsrFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class ApprovedCaConverterTest {

    @Mock
    private OcspResponderConverter ocspResponderConverter;

    @Mock
    private CaInfoConverter caInfoConverter;

    @InjectMocks
    private ApprovedCaConverter approvedCaConverter;

    @Test
    void convert() throws Exception {
        var response = approvedCaConverter.convert(createApprovedCaEntity());
        assertNotNull(response);
        assertNull(response.getDefaultCsrFormat());
    }

    @ParameterizedTest
    @EnumSource(CsrFormat.class)
    void convertWithDefaultCsrFormatDefined(CsrFormat csrFormat) throws Exception {
        var approvedCaEntity = createApprovedCaEntity();
        approvedCaEntity.setDefaultCsrFormat(csrFormat.name());

        var response = approvedCaConverter.convert(approvedCaEntity);
        assertNotNull(response);
        assertEquals(csrFormat, response.getDefaultCsrFormat());
    }

    private static ApprovedCaEntity createApprovedCaEntity() throws Exception {
        var caInfoEntity = new CaInfoEntity();
        caInfoEntity.setCert(TestCertUtil.getCaCert().getEncoded());

        var entity = new ApprovedCaEntity();
        entity.setCaInfo(caInfoEntity);
        return entity;
    }

}
