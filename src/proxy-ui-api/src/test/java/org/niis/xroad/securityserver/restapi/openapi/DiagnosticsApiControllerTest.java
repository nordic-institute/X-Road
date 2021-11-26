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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.ConfigurationStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Test DiagnosticsApiController
 */
public class DiagnosticsApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    DiagnosticsApiController diagnosticsApiController;

    private static final OffsetDateTime PREVIOUS_UPDATE = OffsetDateTime.now().with(LocalTime.of(10, 42));
    private static final OffsetDateTime NEXT_UPDATE = PREVIOUS_UPDATE.plusHours(1);
    private static final OffsetDateTime PREVIOUS_UPDATE_MIDNIGHT = PREVIOUS_UPDATE.with(LocalTime.of(0, 0));
    private static final OffsetDateTime NEXT_UPDATE_MIDNIGHT = PREVIOUS_UPDATE_MIDNIGHT.plusHours(1);
    private static final int ERROR_CODE_UNKNOWN = 999;
    private static final String TSA_URL_1 = "https://tsa1.example.com";
    private static final String CA_NAME_1 = "CN=Xroad Test CA CN, OU=Xroad Test CA OU, O=Xroad Test, C=FI";
    private static final String CA_NAME_2 = "CN=Xroad Test, C=EE";
    private static final String OCSP_URL_1 = "https://ocsp1.example.com";
    private static final String OCSP_URL_2 = "https://ocsp2.example.com";

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getGlobalConfDiagnosticsSuccess() {
        final OffsetDateTime prevUpdate = OffsetDateTime.now();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.SUCCESS, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getGlobalConfDiagnosticsWaiting() {
        final OffsetDateTime prevUpdate = OffsetDateTime.now();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.ERROR_CODE_UNINITIALIZED, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() {
        final OffsetDateTime prevUpdate = OffsetDateTime.now();
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.ERROR_CODE_INTERNAL, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() {
        final OffsetDateTime prevUpdate = OffsetDateTime.now().with(LocalTime.of(0, 0));
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);

        when(diagnosticService.queryGlobalConfStatus()).thenReturn(new DiagnosticsStatus(
                ERROR_CODE_UNKNOWN, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.UNKNOWN, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
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
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getTimestampingServiceDiagnosticsSuccess() {
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE);
        diagnosticsStatus.setDescription(TSA_URL_1);

        when(diagnosticService.queryTimestampingStatus()).thenReturn(
                new HashSet<>(Collections.singletonList(diagnosticsStatus)));

        ResponseEntity<Set<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                .getTimestampingServicesDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<TimestampingServiceDiagnostics> timestampingServiceDiagnosticsSet = response.getBody();
        assertEquals(1, timestampingServiceDiagnosticsSet.size());
        TimestampingServiceDiagnostics timestampingServiceDiagnostics = timestampingServiceDiagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(TimestampingStatus.SUCCESS, timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getTimestampingServiceDiagnosticsWaiting() {
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED, PREVIOUS_UPDATE);
        diagnosticsStatus.setDescription(TSA_URL_1);

        when(diagnosticService.queryTimestampingStatus()).thenReturn(
                new HashSet<>(Collections.singletonList(diagnosticsStatus)));

        ResponseEntity<Set<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                .getTimestampingServicesDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<TimestampingServiceDiagnostics> timestampingServiceDiagnosticsSet = response.getBody();
        assertEquals(1, timestampingServiceDiagnosticsSet.size());
        TimestampingServiceDiagnostics timestampingServiceDiagnostics = timestampingServiceDiagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(TimestampingStatus.ERROR_CODE_TIMESTAMP_UNINITIALIZED,
                timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getTimestampingServiceDiagnosticsFailPreviousUpdateYesterday() {
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL, PREVIOUS_UPDATE_MIDNIGHT);
        diagnosticsStatus.setDescription(TSA_URL_1);

        when(diagnosticService.queryTimestampingStatus()).thenReturn(
                new HashSet<>(Collections.singletonList(diagnosticsStatus)));

        ResponseEntity<Set<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                .getTimestampingServicesDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<TimestampingServiceDiagnostics> timestampingServiceDiagnosticsSet = response.getBody();
        assertEquals(1, timestampingServiceDiagnosticsSet.size());

        TimestampingServiceDiagnostics timestampingServiceDiagnostics = timestampingServiceDiagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);

        assertEquals(TimestampingStatus.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL,
                timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getTimestampingServiceDiagnosticsException() {
        when(diagnosticService.queryTimestampingStatus()).thenThrow(new RuntimeException());

        try {
            ResponseEntity<Set<TimestampingServiceDiagnostics>> response = diagnosticsApiController
                    .getTimestampingServicesDiagnostics();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getOcspResponderDiagnosticsSuccess() {
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE, NEXT_UPDATE);
        diagnosticsStatus.setDescription(OCSP_URL_1);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<Set<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();

        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnostics diagnostics = diagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());

        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.SUCCESS, diagnostics.getOcspResponders().get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getOcspResponderDiagnosticsWaiting() {
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, null, NEXT_UPDATE);
        diagnosticsStatus.setDescription(OCSP_URL_2);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<Set<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnostics diagnostics = diagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());

        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.ERROR_CODE_OCSP_UNINITIALIZED, diagnostics.getOcspResponders()
                .get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(null, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() {
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_1);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, null, NEXT_UPDATE_MIDNIGHT);
        diagnosticsStatus.setDescription(OCSP_URL_1);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<Set<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnostics diagnostics = diagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());

        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.getOcspResponders()
                .get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(null, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() {
        OcspResponderDiagnosticsStatus status = new OcspResponderDiagnosticsStatus(CA_NAME_2);
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                ERROR_CODE_UNKNOWN, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT);
        diagnosticsStatus.setDescription(OCSP_URL_2);
        status.setOcspResponderStatusMap(Arrays.asList(diagnosticsStatus));

        when(diagnosticService.queryOcspResponderStatus()).thenReturn(Arrays.asList(status));

        ResponseEntity<Set<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());

        OcspResponderDiagnostics diagnostics = diagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.UNKNOWN, diagnostics.getOcspResponders().get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getOcspResponderDiagnosticsException() {
        when(diagnosticService.queryOcspResponderStatus()).thenThrow(new RuntimeException());

        try {
            diagnosticsApiController.getOcspRespondersDiagnostics();
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
            // success
        }
    }
}
