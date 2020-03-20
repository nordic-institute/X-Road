/**
 * The MIT License
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

package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.CertificateAuthorityDiagnosticsStatus;
import org.niis.xroad.restapi.openapi.model.CertificateAuthorityDiagnostics;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusCode;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test CertificateAuthorityDiagnosticConverter
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CertificateAuthorityDiagnosticConverterTest {
    private CertificateAuthorityDiagnosticConverter certificateAuthorityDiagnosticConverter;
    private static final String CA_NAME_1 = "CN=Xroad Test CA CN, OU=Xroad Test CA OU, O=Xroad Test, C=FI";
    private static final String CA_NAME_2 = "CN=Xroad Test, C=EE";
    private static final String CURRENT_TIME = "2020-03-16T10:16:12.123";
    private static final String URL_1 = "https://ocsp1.example.com";
    private static final String PREVIOUS_UPDATE_STR_1 = "2020-03-16T10:15:40.703";
    private static final LocalTime PREVIOUS_UPDATE_1 = LocalTime.of(10, 15, 40, 703000000);
    private static final String NEXT_UPDATE_STR_1 = "2020-03-16T10:16:40.703";
    private static final LocalTime NEXT_UPDATE_1 = LocalTime.of(10, 16, 40, 703000000);
    private static final String URL_2 = "https://ocsp2.example.com";
    private static final String NEXT_UPDATE_STR_2 = "2020-03-16T10:35:42.123";
    private static final LocalTime NEXT_UPDATE_2 = LocalTime.of(10, 35, 42, 123000000);


    @Before
    public void setup() {
        certificateAuthorityDiagnosticConverter = new CertificateAuthorityDiagnosticConverter();
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
    }

    @After
    public final void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void convertSingleCertificateAuthorityDiagnostics() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));

        CertificateAuthorityDiagnosticsStatus caStatus = new CertificateAuthorityDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus.setDescription(URL_1);
        caStatus.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        CertificateAuthorityDiagnostics caDiagnostics = certificateAuthorityDiagnosticConverter.convert(caStatus);

        assertEquals(1, caDiagnostics.getOcspResponders().size());

        assertEquals(CA_NAME_1, caDiagnostics.getDistinguishedName());
        assertEquals(DiagnosticStatusCode.SUCCESS, caDiagnostics.getOcspResponders().get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, caDiagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)caDiagnostics.getOcspResponders().get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_1),
                (Long)caDiagnostics.getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_1, caDiagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void convertMultipleCertificateAuthorityDiagnostics() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));

        CertificateAuthorityDiagnosticsStatus caStatus1 = new CertificateAuthorityDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus1 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus1.setDescription(URL_1);
        caStatus1.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus1));

        CertificateAuthorityDiagnosticsStatus caStatus2 = new CertificateAuthorityDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus2 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, null, NEXT_UPDATE_2);
        diagnosticsStatus2.setDescription(URL_2);
        DiagnosticsStatus diagnosticsStatus3 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus3.setDescription(URL_1);
        caStatus2.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus2, diagnosticsStatus3));

        List<CertificateAuthorityDiagnostics> caDiagnostics = certificateAuthorityDiagnosticConverter.convert(
                Arrays.asList(caStatus1, caStatus2));

        assertEquals(2, caDiagnostics.size());
        assertEquals(1, caDiagnostics.get(0).getOcspResponders().size());
        assertEquals(2, caDiagnostics.get(1).getOcspResponders().size());

        assertEquals(CA_NAME_1, caDiagnostics.get(0).getDistinguishedName());

        assertEquals(DiagnosticStatusCode.ERROR_CODE_INTERNAL, caDiagnostics.get(0).getOcspResponders().get(0)
                .getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, caDiagnostics.get(0).getOcspResponders().get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)caDiagnostics.get(0).getOcspResponders().get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_1),
                (Long)caDiagnostics.get(0).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_1, caDiagnostics.get(0).getOcspResponders().get(0).getUrl());

        assertEquals(CA_NAME_2, caDiagnostics.get(1).getDistinguishedName());

        assertEquals(DiagnosticStatusCode.ERROR_CODE_OCSP_UNINITIALIZED, caDiagnostics.get(1).getOcspResponders().get(0)
                .getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, caDiagnostics.get(1).getOcspResponders().get(0).getStatusClass());
        assertEquals(null, caDiagnostics.get(1).getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_2),
                (Long)caDiagnostics.get(1).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_2, caDiagnostics.get(1).getOcspResponders().get(0).getUrl());

        assertEquals(DiagnosticStatusCode.ERROR_CODE_OCSP_RESPONSE_INVALID, caDiagnostics.get(1).getOcspResponders()
                .get(1).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, caDiagnostics.get(1).getOcspResponders().get(1).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)caDiagnostics.get(1).getOcspResponders().get(1).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_1),
                (Long)caDiagnostics.get(1).getOcspResponders().get(1).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_1, caDiagnostics.get(1).getOcspResponders().get(1).getUrl());
    }
}
