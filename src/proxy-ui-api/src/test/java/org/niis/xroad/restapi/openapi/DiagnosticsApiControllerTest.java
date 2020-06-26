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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.restapi.openapi.model.ConfigurationStatus;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.restapi.openapi.model.GlobalConfDiagnostics;
import org.niis.xroad.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.restapi.openapi.model.OcspStatus;
import org.niis.xroad.restapi.openapi.model.TimestampingServiceDiagnostics;
import org.niis.xroad.restapi.openapi.model.TimestampingStatus;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Test DiagnosticsApiController
 */
public class DiagnosticsApiControllerTest extends ApiControllerTestContext {

    private static final String CURRENT_TIME = "2020-03-16T10:16:12.123";
    private static final String CURRENT_TIME_BEFORE_MIDNIGHT = "2019-12-31T23:59:50.123";
    private static final String CURRENT_TIME_AFTER_MIDNIGHT = "2020-01-01T00:00:30.123";
    private static final String PREVIOUS_UPDATE_STR = "2020-03-16T10:15:40.703";
    private static final String NEXT_UPDATE_STR = "2020-03-16T10:16:40.703";
    private static final String PREVIOUS_UPDATE_MIDNIGHT_STR = "2019-12-31T23:59:40.703";
    private static final String NEXT_UPDATE_MIDNIGHT_STR = "2020-01-01T00:00:40.703";
    private static final LocalTime PREVIOUS_UPDATE = LocalTime.of(10, 15, 40, 703000000);
    private static final LocalTime NEXT_UPDATE = LocalTime.of(10, 16, 40, 703000000);
    private static final LocalTime PREVIOUS_UPDATE_MIDNIGHT = LocalTime.of(23, 59, 40, 703000000);
    private static final LocalTime NEXT_UPDATE_MIDNIGHT = LocalTime.of(00, 00, 40, 703000000);
    private static final int ERROR_CODE_UNKNOWN = 999;
    private static final String TSA_URL_1 = "https://tsa1.example.com";
    private static final String CA_NAME_1 = "CN=Xroad Test CA CN, OU=Xroad Test CA OU, O=Xroad Test, C=FI";
    private static final String CA_NAME_2 = "CN=Xroad Test, C=EE";
    private static final String OCSP_URL_1 = "https://ocsp1.example.com";
    private static final String OCSP_URL_2 = "https://ocsp2.example.com";

    @Before
    public void setup() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }

    @After
    public final void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getGlobalConfDiagnosticsSuccess() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE, NEXT_UPDATE));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.SUCCESS, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR),
                (Long) globalConfDiagnostics.getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR),
                (Long) globalConfDiagnostics.getNextUpdateAt().toInstant().toEpochMilli());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getGlobalConfDiagnosticsWaiting() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, PREVIOUS_UPDATE, NEXT_UPDATE));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.ERROR_CODE_UNINITIALIZED, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR),
                (Long) globalConfDiagnostics.getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR),
                (Long) globalConfDiagnostics.getNextUpdateAt().toInstant().toEpochMilli());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME_BEFORE_MIDNIGHT));

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.ERROR_CODE_INTERNAL, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_MIDNIGHT_STR),
                (Long) globalConfDiagnostics.getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_MIDNIGHT_STR),
                (Long) globalConfDiagnostics.getNextUpdateAt().toInstant().toEpochMilli());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME_AFTER_MIDNIGHT));

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                ERROR_CODE_UNKNOWN, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.UNKNOWN, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_MIDNIGHT_STR),
                (Long) globalConfDiagnostics.getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_MIDNIGHT_STR),
                (Long) globalConfDiagnostics.getNextUpdateAt().toInstant().toEpochMilli());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getGlobalConfDiagnosticsException() {
        when(diagnosticService.queryGlobalConfStatus()).thenThrow(new RuntimeException());

        try {
            ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getTimestampingServiceDiagnosticsSuccess() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE);
        diagnosticsStatus.setDescription(TSA_URL_1);

        when(diagnosticService.queryTimestampingStatus()).thenReturn(Arrays.asList(diagnosticsStatus));

        ResponseEntity<List<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                .getTimestampingServicesDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingServiceDiagnostics> timestampingServiceDiagnostics = response.getBody();
        assertEquals(1, timestampingServiceDiagnostics.size());
        assertEquals(TimestampingStatus.SUCCESS, timestampingServiceDiagnostics.get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, timestampingServiceDiagnostics.get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR),
                (Long) timestampingServiceDiagnostics.get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getTimestampingServiceDiagnosticsWaiting() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED, PREVIOUS_UPDATE);
        diagnosticsStatus.setDescription(TSA_URL_1);

        when(diagnosticService.queryTimestampingStatus()).thenReturn(Arrays.asList(diagnosticsStatus));

        ResponseEntity<List<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                .getTimestampingServicesDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingServiceDiagnostics> timestampingServiceDiagnostics = response.getBody();
        assertEquals(1, timestampingServiceDiagnostics.size());
        assertEquals(TimestampingStatus.ERROR_CODE_TIMESTAMP_UNINITIALIZED,
                timestampingServiceDiagnostics.get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, timestampingServiceDiagnostics.get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR),
                (Long) timestampingServiceDiagnostics.get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getTimestampingServiceDiagnosticsFailPreviousUpdateYesterday() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME_AFTER_MIDNIGHT));
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL, PREVIOUS_UPDATE_MIDNIGHT);
        diagnosticsStatus.setDescription(TSA_URL_1);

        when(diagnosticService.queryTimestampingStatus()).thenReturn(Arrays.asList(diagnosticsStatus));

        ResponseEntity<List<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                .getTimestampingServicesDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<TimestampingServiceDiagnostics> timestampingServiceDiagnostics = response.getBody();
        assertEquals(1, timestampingServiceDiagnostics.size());
        assertEquals(TimestampingStatus.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL,
                timestampingServiceDiagnostics.get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, timestampingServiceDiagnostics.get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_MIDNIGHT_STR),
                (Long) timestampingServiceDiagnostics.get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getTimestampingServiceDiagnosticsException() {
        when(diagnosticService.queryTimestampingStatus()).thenThrow(new RuntimeException());

        try {
            ResponseEntity<List<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                    .getTimestampingServicesDiagnostics();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getOcspResponderDiagnosticsSuccess() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE, NEXT_UPDATE);
        diagnosticsStatus.setDescription(OCSP_URL_1);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<List<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<OcspResponderDiagnostics> diagnostics = response.getBody();
        assertEquals(1, diagnostics.size());
        assertEquals(1, diagnostics.get(0).getOcspResponders().size());

        assertEquals(CA_NAME_1, diagnostics.get(0).getDistinguishedName());
        assertEquals(OcspStatus.SUCCESS, diagnostics.get(0).getOcspResponders().get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, diagnostics.get(0).getOcspResponders().get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR),
                (Long) diagnostics.get(0).getOcspResponders().get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR),
                (Long) diagnostics.get(0).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(OCSP_URL_1, diagnostics.get(0).getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getOcspResponderDiagnosticsWaiting() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, null, NEXT_UPDATE);
        diagnosticsStatus.setDescription(OCSP_URL_2);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<List<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<OcspResponderDiagnostics> diagnostics = response.getBody();
        assertEquals(1, diagnostics.size());
        assertEquals(1, diagnostics.get(0).getOcspResponders().size());

        assertEquals(CA_NAME_2, diagnostics.get(0).getDistinguishedName());
        assertEquals(OcspStatus.ERROR_CODE_OCSP_UNINITIALIZED, diagnostics.get(0).getOcspResponders()
                .get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, diagnostics.get(0).getOcspResponders().get(0).getStatusClass());
        assertEquals(null, diagnostics.get(0).getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_STR),
                (Long) diagnostics.get(0).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(OCSP_URL_2, diagnostics.get(0).getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME_BEFORE_MIDNIGHT));
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, null, NEXT_UPDATE_MIDNIGHT);
        diagnosticsStatus.setDescription(OCSP_URL_1);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<List<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<OcspResponderDiagnostics> diagnostics = response.getBody();
        assertEquals(1, diagnostics.size());
        assertEquals(1, diagnostics.get(0).getOcspResponders().size());

        assertEquals(CA_NAME_1, diagnostics.get(0).getDistinguishedName());
        assertEquals(OcspStatus.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.get(0).getOcspResponders()
                .get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.get(0).getOcspResponders().get(0).getStatusClass());
        assertEquals(null, diagnostics.get(0).getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_MIDNIGHT_STR),
                (Long) diagnostics.get(0).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(OCSP_URL_1, diagnostics.get(0).getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME_AFTER_MIDNIGHT));
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                ERROR_CODE_UNKNOWN, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT);
        diagnosticsStatus.setDescription(OCSP_URL_2);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<List<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<OcspResponderDiagnostics> diagnostics = response.getBody();
        assertEquals(1, diagnostics.size());
        assertEquals(1, diagnostics.get(0).getOcspResponders().size());

        assertEquals(CA_NAME_2, diagnostics.get(0).getDistinguishedName());
        assertEquals(OcspStatus.UNKNOWN, diagnostics.get(0).getOcspResponders().get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.get(0).getOcspResponders().get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_MIDNIGHT_STR),
                (Long) diagnostics.get(0).getOcspResponders().get(0).getPrevUpdateAt().toInstant().toEpochMilli());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(NEXT_UPDATE_MIDNIGHT_STR),
                (Long) diagnostics.get(0).getOcspResponders().get(0).getNextUpdateAt().toInstant().toEpochMilli());
        assertEquals(OCSP_URL_2, diagnostics.get(0).getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = { "DIAGNOSTICS" })
    public void getOcspResponderDiagnosticsException() {
        when(diagnosticService.queryOcspResponderStatus()).thenThrow(new RuntimeException());

        try {
            ResponseEntity<List<OcspResponderDiagnostics>> response = diagnosticsApiController
                    .getOcspRespondersDiagnostics();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }
}
