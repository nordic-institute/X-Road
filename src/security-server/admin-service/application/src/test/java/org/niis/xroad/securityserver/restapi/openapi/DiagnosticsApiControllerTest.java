/*
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

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.CertificationServiceDiagnostics;
import ee.ria.xroad.common.CertificationServiceStatus;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.OcspResponderStatus;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.Test;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.openapi.model.AddOnStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupEncryptionStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.ConfigurationStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.lazy-initialization=true"})
@WithMockUser(authorities = {"DIAGNOSTICS"})
public class DiagnosticsApiControllerTest extends AbstractApiControllerTestContext {

    private static final OffsetDateTime PREVIOUS_UPDATE = TimeUtils.offsetDateTimeNow().with(LocalTime.of(10, 42));
    private static final OffsetDateTime NEXT_UPDATE = PREVIOUS_UPDATE.plusHours(1);
    private static final OffsetDateTime PREVIOUS_UPDATE_MIDNIGHT = PREVIOUS_UPDATE.with(LocalTime.of(0, 0));
    private static final OffsetDateTime NEXT_UPDATE_MIDNIGHT = PREVIOUS_UPDATE_MIDNIGHT.plusHours(1);
    private static final int ERROR_CODE_UNKNOWN = 999;
    private static final String TSA_URL_1 = "https://tsa1.example.com";
    private static final String CA_NAME_1 = "CN=Xroad Test CA CN, OU=Xroad Test CA OU, O=Xroad Test, C=FI";
    private static final String CA_NAME_2 = "CN=Xroad Test, C=EE";
    private static final String OCSP_URL_1 = "https://ocsp1.example.com";
    private static final String OCSP_URL_2 = "https://ocsp2.example.com";
    private static final String GROUPING_RULE = "none";

    @Autowired
    DiagnosticsApiController diagnosticsApiController;

    @Test
    public void getAddOnDiagnostics() throws Exception {
        when(proxyRpcClient.getAddOnStatus()).thenReturn(new AddOnStatusDiagnostics(true));
        ResponseEntity<AddOnStatus> response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessagelogEnabled());

        when(proxyRpcClient.getAddOnStatus()).thenReturn(new AddOnStatusDiagnostics(false));
        response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getMessagelogEnabled());
    }

    @Test
    public void getBackupEncryptionDiagnostics() throws Exception {
        when(proxyRpcClient.getBackupEncryptionStatus()).thenReturn(
                new BackupEncryptionStatusDiagnostics(true, List.of("keyid")));
        ResponseEntity<BackupEncryptionStatus> response = diagnosticsApiController.getBackupEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getBackupEncryptionStatus());
        assertEquals(1, response.getBody().getBackupEncryptionKeys().size());

        when(proxyRpcClient.getBackupEncryptionStatus()).thenReturn(
                new BackupEncryptionStatusDiagnostics(false, List.of()));
        response = diagnosticsApiController.getBackupEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getBackupEncryptionStatus());
        assertTrue(response.getBody().getBackupEncryptionKeys().isEmpty());
    }

    @Test
    public void getMessageLogEncryptionDiagnostics() throws Exception {
        when(proxyRpcClient.getMessageLogEncryptionStatus()).thenReturn(
                new MessageLogEncryptionStatusDiagnostics(true, true, "none",
                        List.of(new MessageLogArchiveEncryptionMember("memberId", Set.of("key"), false)))
        );
        ResponseEntity<MessageLogEncryptionStatus> response = diagnosticsApiController
                .getMessageLogEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessageLogArchiveEncryptionStatus());
        assertEquals(true, response.getBody().getMessageLogDatabaseEncryptionStatus());
        assertEquals(GROUPING_RULE, response.getBody().getMessageLogGroupingRule());
        assertEquals(1, response.getBody().getMembers().size());

        when(proxyRpcClient.getMessageLogEncryptionStatus()).thenReturn(
                new MessageLogEncryptionStatusDiagnostics(false, false, "none",
                        List.of())
        );
        response = diagnosticsApiController.getMessageLogEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getMessageLogArchiveEncryptionStatus());
        assertEquals(false, response.getBody().getMessageLogDatabaseEncryptionStatus());
        assertEquals(GROUPING_RULE, response.getBody().getMessageLogGroupingRule());
        assertTrue(response.getBody().getMembers().isEmpty());
    }

    @Test
    public void getGlobalConfDiagnosticsSuccess() throws Exception {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);
        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.SUCCESS, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsWaiting() throws Exception {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.ERROR_CODE_UNINITIALIZED, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() throws Exception {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.ERROR_CODE_INTERNAL, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() throws Exception {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow().with(LocalTime.of(0, 0));
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(ERROR_CODE_UNKNOWN, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnostics> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnostics globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatus.UNKNOWN, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsException() throws Exception {
        when(confClientRpcClient.getStatus()).thenThrow(new RuntimeException());
        DeviationAwareRuntimeException exception =
                assertThrows(DeviationAwareRuntimeException.class, diagnosticsApiController::getGlobalConfDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().getCode());
    }

    @Test
    public void getTimestampingServiceDiagnosticsSuccess() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS,
                        PREVIOUS_UPDATE, TSA_URL_1))
        );

        ResponseEntity<Set<TimestampingServiceDiagnostics>> response =
                diagnosticsApiController.getTimestampingServicesDiagnostics();

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
    public void getTimestampingServiceDiagnosticsWaiting() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED,
                        PREVIOUS_UPDATE, TSA_URL_1))
        );

        ResponseEntity<Set<TimestampingServiceDiagnostics>> response =
                diagnosticsApiController.getTimestampingServicesDiagnostics();

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
    public void getTimestampingServiceDiagnosticsFailPreviousUpdateYesterday() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL,
                        PREVIOUS_UPDATE_MIDNIGHT, TSA_URL_1))
        );

        ResponseEntity<Set<TimestampingServiceDiagnostics>> response =
                diagnosticsApiController.getTimestampingServicesDiagnostics();

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
    public void getTimestampingServiceDiagnosticsException() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenThrow(new Exception());
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getTimestampingServicesDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().getCode());
    }

    @Test
    public void getOcspResponderDiagnosticsSuccess() throws Exception {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_1);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, OCSP_URL_1, PREVIOUS_UPDATE, NEXT_UPDATE));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_1, certServiceStatus));
        when(signerProxyFacade.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnostics>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnostics diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.SUCCESS, diagnostics.getOcspResponders().getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertEquals(PREVIOUS_UPDATE, diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsWaiting() throws Exception {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_2);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, OCSP_URL_2, null, NEXT_UPDATE));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_2, certServiceStatus));
        when(signerProxyFacade.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnostics>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnostics diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.ERROR_CODE_OCSP_UNINITIALIZED, diagnostics.getOcspResponders()
                .getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertNull(diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() throws Exception {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_1);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, OCSP_URL_1, null, NEXT_UPDATE_MIDNIGHT));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_1, certServiceStatus));
        when(signerProxyFacade.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnostics>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnostics> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnostics diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatus.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.getOcspResponders()
                .getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertNull(diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() throws Exception {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_2);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_2,
                new OcspResponderStatus(ERROR_CODE_UNKNOWN, OCSP_URL_2, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_2, certServiceStatus));
        when(signerProxyFacade.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

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
        assertEquals(OcspStatus.UNKNOWN, diagnostics.getOcspResponders().getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsException() throws Exception {
        when(signerProxyFacade.getCertificationServiceDiagnostics()).thenThrow(new RuntimeException());
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getOcspRespondersDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().getCode());
    }


    private org.niis.xroad.confclient.proto.DiagnosticsStatus createDiagnosticsStatus(int statusCode,
                                                                                      OffsetDateTime prevUpdate,
                                                                                      OffsetDateTime nextUpdate) {
        return org.niis.xroad.confclient.proto.DiagnosticsStatus.newBuilder()
                .setReturnCode(statusCode)
                .setPrevUpdate(prevUpdate.toInstant().toEpochMilli())
                .setNextUpdate(nextUpdate.toInstant().toEpochMilli())
                .build();
    }
}
