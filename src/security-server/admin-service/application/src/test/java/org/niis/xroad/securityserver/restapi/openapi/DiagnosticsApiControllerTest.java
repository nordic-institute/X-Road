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
import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.util.TimeUtils;

import com.google.protobuf.Timestamp;
import org.junit.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.api.OperationalDataIntervalProto;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.openapi.model.AddOnStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClassDto;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OperationalDataIntervalDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnosticsDto;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.lazy-initialization=true"})
@WithMockUser(authorities = {"DIAGNOSTICS"})
public class DiagnosticsApiControllerTest extends AbstractApiControllerTestContext {

    private static final OffsetDateTime PREVIOUS_UPDATE = TimeUtils.offsetDateTimeNow().with(LocalTime.of(10, 42));
    private static final OffsetDateTime NEXT_UPDATE = PREVIOUS_UPDATE.plusHours(1);
    private static final OffsetDateTime PREVIOUS_UPDATE_MIDNIGHT = PREVIOUS_UPDATE.with(LocalTime.of(0, 0));
    private static final OffsetDateTime NEXT_UPDATE_MIDNIGHT = PREVIOUS_UPDATE_MIDNIGHT.plusHours(1);
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
    public void getAddOnDiagnostics() {
        when(proxyRpcClient.getAddOnStatus()).thenReturn(new AddOnStatusDiagnostics(true, true));
        ResponseEntity<AddOnStatusDto> response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessagelogEnabled());
        assertEquals(true, response.getBody().getOpmonitoringEnabled());

        when(proxyRpcClient.getAddOnStatus()).thenReturn(new AddOnStatusDiagnostics(false, false));
        response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getMessagelogEnabled());
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
    public void getMessageLogEncryptionDiagnostics() {
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
    public void getGlobalConfDiagnosticsSuccess() {
        final Instant prevUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.HOURS);
        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticStatus.OK, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(DiagnosticStatusClassDto.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsWaiting() {
        final Instant prevUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.HOURS);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticStatus.UNINITIALIZED, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(DiagnosticStatusClassDto.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() {
        final Instant prevUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.DAYS);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(ErrorCode.INTERNAL_ERROR, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), globalConfDiagnostics.getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() {
        final Instant prevUpdate = TimeUtils.offsetDateTimeNow().with(LocalTime.of(0, 0)).toInstant();
        final Instant nextUpdate = prevUpdate.plus(1, ChronoUnit.DAYS);

        when(confClientRpcClient.getStatus()).thenReturn(
                createDiagnosticsStatus(DiagnosticStatus.UNKNOWN, prevUpdate, nextUpdate));

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt().toInstant());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt().toInstant());
    }

    @Test
    public void getGlobalConfDiagnosticsException() {
        when(confClientRpcClient.getStatus()).thenThrow(new RuntimeException());
        DeviationAwareRuntimeException exception =
                assertThrows(DeviationAwareRuntimeException.class, diagnosticsApiController::getGlobalConfDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    public void getTimestampingServiceDiagnosticsSuccess() {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticStatus.OK,
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
        assertEquals(DiagnosticStatusClassDto.OK, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    public void getTimestampingServiceDiagnosticsWaiting() {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticStatus.UNINITIALIZED,
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
        assertEquals(DiagnosticStatusClassDto.WAITING, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    public void getTimestampingServiceDiagnosticsFailPreviousUpdateYesterday() {
        when(proxyRpcClient.getTimestampingStatus()).thenReturn(
                Map.of(TSA_URL_1, new DiagnosticsStatus(DiagnosticStatus.ERROR,
                        PREVIOUS_UPDATE_MIDNIGHT, TSA_URL_1, ErrorCode.MALFORMED_TIMESTAMP_SERVER_URL))
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
        assertEquals(ErrorCode.MALFORMED_TIMESTAMP_SERVER_URL.code(),
                timestampingServiceDiagnostics.getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(TSA_URL_1, timestampingServiceDiagnostics.getUrl());
    }

    @Test
    public void getTimestampingServiceDiagnosticsException() {
        when(proxyRpcClient.getTimestampingStatus()).thenThrow(new RuntimeException());
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getTimestampingServicesDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    public void getOcspResponderDiagnosticsSuccess() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_1);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticStatus.OK, OCSP_URL_1, PREVIOUS_UPDATE, NEXT_UPDATE));
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
        assertEquals(DiagnosticStatusClassDto.OK, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertEquals(PREVIOUS_UPDATE, diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsWaiting() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_2);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticStatus.UNINITIALIZED, OCSP_URL_2, null, NEXT_UPDATE));
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
        assertEquals(DiagnosticStatusClassDto.WAITING, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertNull(diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_1);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_1,
                new OcspResponderStatus(DiagnosticStatus.ERROR, OCSP_URL_1, null, NEXT_UPDATE_MIDNIGHT, ErrorCode.OCSP_RESPONSE_PARSING_FAILURE));
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
        assertEquals(ErrorCode.OCSP_RESPONSE_PARSING_FAILURE.code(), diagnostics.getOcspResponders()
                .getFirst().getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, diagnostics.getOcspResponders().getFirst().getStatusClass());
        assertNull(diagnostics.getOcspResponders().getFirst().getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().getFirst().getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().getFirst().getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() {
        var certServiceStatus = new CertificationServiceStatus(CA_NAME_2);
        certServiceStatus.getOcspResponderStatusMap().put(OCSP_URL_2,
                new OcspResponderStatus(DiagnosticStatus.UNKNOWN, OCSP_URL_2, PREVIOUS_UPDATE_MIDNIGHT, NEXT_UPDATE_MIDNIGHT));
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

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getOperationalDataIntervals() {
        List<OperationalDataInterval> dataIntervals = new ArrayList<>();
        OperationalDataIntervalProto.Builder operationalDataIntervalBuilder = OperationalDataIntervalProto.newBuilder();
        operationalDataIntervalBuilder.setTimeIntervalStart(Timestamp.newBuilder()
                .setSeconds(Instant.now().getEpochSecond())
                .setNanos(Instant.now().getNano())
                .build());
        operationalDataIntervalBuilder.setSuccessCount(10L);
        operationalDataIntervalBuilder.setFailureCount(5L);
        OperationalDataInterval operationalDataInterval = new OperationalDataInterval(operationalDataIntervalBuilder.build());
        dataIntervals.add(operationalDataInterval);
        when(opMonitorClient.getOperationalDataIntervals(any(), any(), any(), any(), any(), any()))
                .thenReturn(dataIntervals);

        ResponseEntity<List<OperationalDataIntervalDto>> response =
                diagnosticsApiController.getOperationalDataIntervals(Instant.now().atOffset(ZoneOffset.UTC),
                        Instant.now().atOffset(ZoneOffset.UTC),
                        1,
                        null,
                        null,
                        null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFirst().getIntervalStartTime()).isEqualTo(operationalDataInterval.getIntervalStart()
                .atOffset(ZoneOffset.UTC));
        assertThat(response.getBody().getFirst().getSuccessCount()).isEqualTo(operationalDataInterval.getSuccessCount());
        assertThat(response.getBody().getFirst().getFailureCount()).isEqualTo(operationalDataInterval.getFailureCount());
    }

    private org.niis.xroad.confclient.proto.DiagnosticsStatus createDiagnosticsStatus(DiagnosticStatus status,
                                                                                      Instant prevUpdate,
                                                                                      Instant nextUpdate) {
        return org.niis.xroad.confclient.proto.DiagnosticsStatus.newBuilder()
                .setDiagnosticStatus(status)
                .setPrevUpdate(prevUpdate.toEpochMilli())
                .setNextUpdate(nextUpdate.toEpochMilli())
                .build();
    }

    private org.niis.xroad.confclient.proto.DiagnosticsStatus createDiagnosticsStatus(ErrorCode errorCode,
                                                                                      Instant prevUpdate,
                                                                                      Instant nextUpdate) {
        return org.niis.xroad.confclient.proto.DiagnosticsStatus.newBuilder()
                .setDiagnosticStatus(DiagnosticStatus.ERROR)
                .setPrevUpdate(prevUpdate.toEpochMilli())
                .setNextUpdate(nextUpdate.toEpochMilli())
                .setErrorCode(errorCode)
                .build();
    }
}
