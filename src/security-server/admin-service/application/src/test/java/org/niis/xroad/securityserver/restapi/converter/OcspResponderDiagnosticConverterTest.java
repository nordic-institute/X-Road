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

package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.DiagnosticsStatus;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.CostType;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.CaOcspDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CostTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClassDto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test CertificateAuthorityDiagnosticConverter
 */
public class OcspResponderDiagnosticConverterTest {
    private OcspResponderDiagnosticConverter ocspResponderDiagnosticConverter;
    private static final String CA_NAME_1 = "CN=Xroad Test CA CN, OU=Xroad Test CA OU, O=Xroad Test, C=FI";
    private static final String CA_NAME_2 = "CN=Xroad Test, C=EE";
    private static final String URL_1 = "https://ocsp1.example.com";
    private static final OffsetDateTime PREVIOUS_UPDATE_1 = OffsetDateTime.parse("2020-03-16T10:16:40.703Z");
    private static final OffsetDateTime NEXT_UPDATE_1 = OffsetDateTime.parse("2020-03-16T10:35:42.123Z");
    private static final String URL_2 = "https://ocsp2.example.com";
    private static final OffsetDateTime NEXT_UPDATE_2 = OffsetDateTime.parse("2020-03-16T10:35:42.123Z");

    @Before
    public void setup() {
        GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConfProvider.getOcspResponderCostType("DEV", URL_1)).thenReturn(CostType.FREE);
        when(globalConfProvider.getOcspResponderCostType("DEV", URL_2)).thenReturn(null);

        ocspResponderDiagnosticConverter = new OcspResponderDiagnosticConverter(globalConfProvider);
    }

    @Test
    public void convertSingleCertificateAuthorityDiagnostics() {
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(DiagnosticStatus.OK, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus.setDescription(URL_1);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        CaOcspDiagnosticsDto caDiagnostics = ocspResponderDiagnosticConverter.convert(status);

        assertEquals(1, caDiagnostics.getOcspResponders().size());

        assertEquals(CA_NAME_1, caDiagnostics.getDistinguishedName());
        assertEquals(DiagnosticStatusClassDto.OK, caDiagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE_1, caDiagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_1, caDiagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(URL_1, caDiagnostics.getOcspResponders().get(0).getUrl());
        assertEquals(CostTypeDto.FREE, caDiagnostics.getOcspResponders().get(0).getCostType());
    }

    @Test
    public void convertMultipleCertificateAuthorityDiagnostics() {
        OcspResponderDiagnosticsStatus status1 = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus1 = new DiagnosticsStatus(
                DiagnosticStatus.ERROR, PREVIOUS_UPDATE_1, NEXT_UPDATE_1, ErrorCode.OCSP_FAILED);
        diagnosticsStatus1.setDescription(URL_1);
        status1.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus1));

        OcspResponderDiagnosticsStatus status2 = new OcspResponderDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus2 = new DiagnosticsStatus(
                DiagnosticStatus.UNINITIALIZED, null, NEXT_UPDATE_2);
        diagnosticsStatus2.setDescription(URL_2);
        DiagnosticsStatus diagnosticsStatus3 = new DiagnosticsStatus(
                DiagnosticStatus.ERROR, PREVIOUS_UPDATE_1, NEXT_UPDATE_1, ErrorCode.OCSP_RESPONSE_PARSING_FAILURE);
        diagnosticsStatus3.setDescription(URL_1);
        status2.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus2, diagnosticsStatus3));

        Set<CaOcspDiagnosticsDto> diagnostics = ocspResponderDiagnosticConverter.convert(
                Arrays.asList(status1, status2));
        CaOcspDiagnosticsDto firstDiagnostic = diagnostics
                .stream()
                .filter(item -> item.getDistinguishedName().equals(CA_NAME_1))
                .findFirst()
                .orElse(null);
        CaOcspDiagnosticsDto secondDiagnostic = diagnostics
                .stream()
                .filter(item -> item.getDistinguishedName().equals(CA_NAME_2))
                .findFirst()
                .orElse(null);

        assertEquals(2, diagnostics.size());
        assertEquals(1, firstDiagnostic.getOcspResponders().size());
        assertEquals(2, secondDiagnostic.getOcspResponders().size());

        assertEquals(ErrorCode.OCSP_FAILED.code(), firstDiagnostic.getOcspResponders().get(0).getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, firstDiagnostic.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE_1, firstDiagnostic.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_1, firstDiagnostic.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(URL_1, firstDiagnostic.getOcspResponders().get(0).getUrl());
        assertEquals(CostTypeDto.FREE, firstDiagnostic.getOcspResponders().get(0).getCostType());

        assertEquals(CA_NAME_2, secondDiagnostic.getDistinguishedName());

        assertEquals(DiagnosticStatusClassDto.WAITING, secondDiagnostic.getOcspResponders().get(0).getStatusClass());
        assertEquals(null, secondDiagnostic.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_2, secondDiagnostic.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(URL_2, secondDiagnostic.getOcspResponders().get(0).getUrl());
        assertEquals(null, secondDiagnostic.getOcspResponders().get(0).getCostType());

        assertEquals(ErrorCode.OCSP_RESPONSE_PARSING_FAILURE.code(), secondDiagnostic.getOcspResponders()
                .get(1).getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, secondDiagnostic.getOcspResponders().get(1).getStatusClass());
        assertEquals(PREVIOUS_UPDATE_1, secondDiagnostic.getOcspResponders().get(1).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_1, secondDiagnostic.getOcspResponders().get(1).getNextUpdateAt());
        assertEquals(URL_1, secondDiagnostic.getOcspResponders().get(1).getUrl());
        assertEquals(CostTypeDto.FREE, secondDiagnostic.getOcspResponders().get(1).getCostType());
    }
}
