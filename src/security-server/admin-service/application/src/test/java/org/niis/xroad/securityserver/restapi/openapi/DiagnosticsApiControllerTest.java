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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.Test;
import org.niis.xroad.confclient.model.DiagnosticsStatus;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.openapi.model.AddOnStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConfigurationStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClassDto;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingStatusDto;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticReportService;
import org.niis.xroad.signer.api.dto.CertificationServiceDiagnostics;
import org.niis.xroad.signer.api.dto.CertificationServiceStatus;
import org.niis.xroad.signer.api.dto.OcspResponderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @MockitoBean
    private DiagnosticReportService diagnosticReportService;

    @Test
    public void getAddOnDiagnostics() throws Exception {
        when(proxyRpcClient.getAddOnStatus()).thenReturn(new AddOnStatusDiagnostics(true));
        ResponseEntity<AddOnStatusDto> response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessagelogEnabled());

        when(proxyRpcClient.getAddOnStatus()).thenReturn(new AddOnStatusDiagnostics(false));
        response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getMessagelogEnabled());
    }

    @Test
    public void getBackupEncryptionDiagnostics() {
        when(backupManagerRpcClient.getEncryptionStatus()).thenReturn(
                new BackupEncryptionStatusDiagnostics(true, List.of("keyid")));
        ResponseEntity<BackupEncryptionStatusDto> response = diagnosticsApiController.getBackupEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getBackupEncryptionStatus());
        assertEquals(1, response.getBody().getBackupEncryptionKeys().size());

        when(backupManagerRpcClient.getEncryptionStatus()).thenReturn(
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
        ResponseEntity<MessageLogEncryptionStatusDto> response = diagnosticsApiController
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
        final Instant prevUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.HOURS);
        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.SUCCESS, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsWaiting() throws Exception {
        final Instant prevUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.HOURS);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.ERROR_CODE_UNINITIALIZED, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() throws Exception {
        final Instant prevUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.DAYS);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.ERROR_CODE_INTERNAL, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() throws Exception {
        final Instant prevUpdate = TimeUtils.offsetDateTimeNow().with(LocalTime.of(0, 0)).toInstant();
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.DAYS);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(ERROR_CODE_UNKNOWN, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.UNKNOWN, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsException() throws Exception {
        when(confClientRpcClient.getStatus()).thenThrow(new RuntimeException());
        DeviationAwareRuntimeException exception =
                assertThrows(DeviationAwareRuntimeException.class, diagnosticsApiController::getGlobalConfDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    public void getTimestampingServiceDiagnosticsSuccess() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS,
                        PREVIOUS_UPDATE, TSA_URL_1))
        );
        ResponseEntity<Set<TimestampingServiceDiagnosticsDto>> response =
                diagnosticsApiController.getTimestampingServicesDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<TimestampingServiceDiagnosticsDto> timestampingServiceDiagnosticsSet = response.getBody();
        assertEquals(1, timestampingServiceDiagnosticsSet.size());
        TimestampingServiceDiagnosticsDto timestampingServiceDiagnostics = timestampingServiceDiagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(TimestampingStatusDto.SUCCESS, timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.OK, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    public void getTimestampingServiceDiagnosticsWaiting() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED,
                        PREVIOUS_UPDATE, TSA_URL_1))
        );

        ResponseEntity<Set<TimestampingServiceDiagnosticsDto>> response =
                diagnosticsApiController.getTimestampingServicesDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<TimestampingServiceDiagnosticsDto> timestampingServiceDiagnosticsSet = response.getBody();
        assertEquals(1, timestampingServiceDiagnosticsSet.size());
        TimestampingServiceDiagnosticsDto timestampingServiceDiagnostics = timestampingServiceDiagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(TimestampingStatusDto.ERROR_CODE_TIMESTAMP_UNINITIALIZED,
                timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.WAITING, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    public void getTimestampingServiceDiagnosticsFailPreviousUpdateYesterday() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL,
                        PREVIOUS_UPDATE_MIDNIGHT, TSA_URL_1))
        );

        ResponseEntity<Set<TimestampingServiceDiagnosticsDto>> response =
                diagnosticsApiController.getTimestampingServicesDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<TimestampingServiceDiagnosticsDto> timestampingServiceDiagnosticsSet = response.getBody();
        assertEquals(1, timestampingServiceDiagnosticsSet.size());
        TimestampingServiceDiagnosticsDto timestampingServiceDiagnostics = timestampingServiceDiagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(TimestampingStatusDto.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL,
                timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    public void getTimestampingServiceDiagnosticsException() throws Exception {
        when(proxyRpcClient.getTimestampingStatus()).thenThrow(new Exception());
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getTimestampingServicesDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    public void getOcspResponderDiagnosticsSuccess() throws Exception {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_1);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, OCSP_URL_1, PREVIOUS_UPDATE, NEXT_UPDATE));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_1, certServiceStatus));
        when(signerRpcClient.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatusDto.SUCCESS, diagnostics.getOcspResponders().getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClassDto.OK, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertEquals(PREVIOUS_UPDATE, diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsWaiting() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_2);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED, OCSP_URL_2, null, NEXT_UPDATE));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_2, certServiceStatus));
        when(signerRpcClient.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(OcspStatusDto.ERROR_CODE_OCSP_UNINITIALIZED, diagnostics.getOcspResponders()
                .getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClassDto.WAITING, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertNull(diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_1);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID, OCSP_URL_1, null, NEXT_UPDATE_MIDNIGHT));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_1, certServiceStatus));
        when(signerRpcClient.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatusDto.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.getOcspResponders()
                .getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertNull(diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_2);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_2,
                new OcspResponderStatus(ERROR_CODE_UNKNOWN, OCSP_URL_2, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT));
        var diagnosticsResponse = new CertificationServiceDiagnostics();
        diagnosticsResponse.update(Map.of(CA_NAME_2, certServiceStatus));
        when(signerRpcClient.getCertificationServiceDiagnostics()).thenReturn(diagnosticsResponse);

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(OcspStatusDto.UNKNOWN, diagnostics.getOcspResponders().getFirst().getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsException() {
        when(signerRpcClient.getCertificationServiceDiagnostics()).thenThrow(new RuntimeException());
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getOcspRespondersDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_ANCHOR"})
    public void downloadDiagnosticsReportWithoutRequiredAuthorities() throws Exception {
        byte[] bytes = "[{}]".getBytes(StandardCharsets.UTF_8);
        when(diagnosticReportService.collectSystemInformation()).thenReturn(bytes);
        when(systemService.getAnchorFilenameForDownload())
                .thenReturn("configuration_anchor_UTC_2019-04-28_09_03_31.xml");

        assertThatThrownBy(() -> diagnosticsApiController.downloadDiagnosticsReport()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_DIAGNOSTICS_REPORT"})
    public void downloadDiagnosticsReport() throws Exception {
        byte[] bytes = "[{}]".getBytes(StandardCharsets.UTF_8);
        when(diagnosticReportService.collectSystemInformation()).thenReturn(bytes);
        when(systemService.getAnchorFilenameForDownload())
                .thenReturn("configuration_anchor_UTC_2019-04-28_09_03_31.xml");

        ResponseEntity<Resource> response = diagnosticsApiController.downloadDiagnosticsReport();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isEqualTo(bytes.length);
    }

    private org.niis.xroad.confclient.proto.DiagnosticsStatus createDiagnosticsStatus(int statusCode,
                                                                                      Instant prevUpdate,
                                                                                      Instant nextUpdate) {
        return org.niis.xroad.confclient.proto.DiagnosticsStatus.newBuilder()
                .setReturnCode(statusCode)
                .setPrevUpdate(prevUpdate.toEpochMilli())
                .setNextUpdate(nextUpdate.toEpochMilli())
                .build();
    }
}
