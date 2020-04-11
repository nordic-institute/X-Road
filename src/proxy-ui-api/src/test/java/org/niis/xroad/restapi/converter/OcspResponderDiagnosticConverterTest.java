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
import org.niis.xroad.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.restapi.openapi.model.OcspStatus;
import org.niis.xroad.restapi.util.TestUtils;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test CertificateAuthorityDiagnosticConverter
 */
public class OcspResponderDiagnosticConverterTest {
    private OcspResponderDiagnosticConverter ocspResponderDiagnosticConverter;
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
        ocspResponderDiagnosticConverter = new OcspResponderDiagnosticConverter();
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
    }

    @After
    public final void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void convertSingleCertificateAuthorityDiagnostics() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));

        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus.setDescription(URL_1);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        OcspResponderDiagnostics caDiagnostics = ocspResponderDiagnosticConverter.convert(status);

        assertEquals(1, caDiagnostics.getOcspResponders().size());

        assertEquals(CA_NAME_1, caDiagnostics.getDistinguishedName());
        assertEquals(OcspStatus.SUCCESS, caDiagnostics.getOcspResponders().get(0).getStatusCode());
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

        OcspResponderDiagnosticsStatus status1 = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus1 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus1.setDescription(URL_1);
        status1.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus1));

        OcspResponderDiagnosticsStatus status2 = new OcspResponderDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus2 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, null, NEXT_UPDATE_2);
        diagnosticsStatus2.setDescription(URL_2);
        DiagnosticsStatus diagnosticsStatus3 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, PREVIOUS_UPDATE_1, NEXT_UPDATE_1);
        diagnosticsStatus3.setDescription(URL_1);
        status2.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus2, diagnosticsStatus3));

        List<OcspResponderDiagnostics> diagnostics = ocspResponderDiagnosticConverter.convert(
                Arrays.asList(status1, status2));

        assertEquals(2, diagnostics.size());
        assertEquals(1, diagnostics.get(0).getOcspResponders().size());
        assertEquals(2, diagnostics.get(1).getOcspResponders().size());

        assertEquals(CA_NAME_1, diagnostics.get(0).getDistinguishedName());

        assertEquals(OcspStatus.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.get(0).getOcspResponders().get(0)
                .getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.get(0).getOcspResponders().get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)diagnostics.get(0).getOcspResponders().get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_1),
                (Long)diagnostics.get(0).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_1, diagnostics.get(0).getOcspResponders().get(0).getUrl());

        assertEquals(CA_NAME_2, diagnostics.get(1).getDistinguishedName());

        assertEquals(OcspStatus.ERROR_CODE_OCSP_UNINITIALIZED, diagnostics.get(1).getOcspResponders().get(0)
                .getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, diagnostics.get(1).getOcspResponders().get(0).getStatusClass());
        assertEquals(null, diagnostics.get(1).getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_2),
                (Long)diagnostics.get(1).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_2, diagnostics.get(1).getOcspResponders().get(0).getUrl());

        assertEquals(OcspStatus.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.get(1).getOcspResponders()
                .get(1).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.get(1).getOcspResponders().get(1).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)diagnostics.get(1).getOcspResponders().get(1).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR_1),
                (Long)diagnostics.get(1).getOcspResponders().get(1).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(URL_1, diagnostics.get(1).getOcspResponders().get(1).getUrl());
    }
}
