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

import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;

import com.google.protobuf.Timestamp;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.api.OperationalDataIntervalProto;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.openapi.model.AddOnStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClassDto;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfConnectionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OperationalDataIntervalDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticReportService;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.lazy-initialization=true"})
@WithMockUser(authorities = {"DIAGNOSTICS"})
@AutoConfigureWireMock(port = PortNumbers.ADMIN_PORT)
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

    @BeforeClass
    public static void setUp() {
        // Make them point to Wiremock port
        System.setProperty(SystemProperties.CONFIGURATION_CLIENT_ADMIN_PORT, Integer.toString(PortNumbers.ADMIN_PORT));
        System.setProperty(SystemProperties.SIGNER_ADMIN_PORT, Integer.toString(PortNumbers.ADMIN_PORT));
    }

    @Test
    public void getAddOnDiagnostics() {
        stubForDiagnosticsRequest("/addonstatus", "{\"messageLogEnabled\":true,\"opMonitoringEnabled\": true}");
        ResponseEntity<AddOnStatusDto> response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessagelogEnabled());
        assertEquals(true, response.getBody().getOpmonitoringEnabled());

        stubForDiagnosticsRequest("/addonstatus", "{\"messageLogEnabled\":false,\"opMonitoringEnabled\": false}");
        response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getMessagelogEnabled());
        assertEquals(false, response.getBody().getMessagelogEnabled());
    }

    @Test
    public void getBackupEncryptionDiagnostics() {
        stubForDiagnosticsRequest("/backup-encryption-status",
                "{\"backupEncryptionStatus\":true,\"backupEncryptionKeys\":[\"keyid\"]}");
        ResponseEntity<BackupEncryptionStatusDto> response = diagnosticsApiController.getBackupEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getBackupEncryptionStatus());
        assertEquals(1, response.getBody().getBackupEncryptionKeys().size());

        stubForDiagnosticsRequest("/backup-encryption-status",
                "{\"backupEncryptionStatus\":false,\"backupEncryptionKeys\":[]}");
        response = diagnosticsApiController.getBackupEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getBackupEncryptionStatus());
        assertTrue(response.getBody().getBackupEncryptionKeys().isEmpty());
    }

    @Test
    public void getMessageLogEncryptionDiagnostics() {
        stubForDiagnosticsRequest("/message-log-encryption-status",
                "{\"messageLogArchiveEncryptionStatus\":true,\"messageLogDatabaseEncryptionStatus\":true,"
                        + "\"messageLogGroupingRule\":\"none\",\"members\":[{\"memberId\":\"memberId\","
                        + "\"keys\":[\"key\"], \"defaultKeyUsed\":false}]}");
        ResponseEntity<MessageLogEncryptionStatusDto> response = diagnosticsApiController
                .getMessageLogEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessageLogArchiveEncryptionStatus());
        assertEquals(true, response.getBody().getMessageLogDatabaseEncryptionStatus());
        assertEquals(GROUPING_RULE, response.getBody().getMessageLogGroupingRule());
        assertEquals(1, response.getBody().getMembers().size());

        stubForDiagnosticsRequest("/message-log-encryption-status",
                "{\"messageLogArchiveEncryptionStatus\":false,\"messageLogDatabaseEncryptionStatus\":false, "
                        + "\"messageLogGroupingRule\":\"none\",\"members\":[]}");
        response = diagnosticsApiController.getMessageLogEncryptionDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().getMessageLogArchiveEncryptionStatus());
        assertEquals(false, response.getBody().getMessageLogDatabaseEncryptionStatus());
        assertEquals(GROUPING_RULE, response.getBody().getMessageLogGroupingRule());
        assertTrue(response.getBody().getMembers().isEmpty());
    }

    @Test
    public void getGlobalConfDiagnosticsSuccess() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);
        stubForDiagnosticsRequest("/status",
                "{\"status\":\"" + DiagnosticStatus.OK + "\",\"prevUpdate\":\"" + prevUpdate
                        + "\",\"nextUpdate\":\"" + nextUpdate + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(DiagnosticStatusClassDto.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsWaiting() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);
        stubForDiagnosticsRequest("/status", "{\"status\":\"" + DiagnosticStatus.UNINITIALIZED + "\","
                + "\"prevUpdate\":\"" + prevUpdate + "\",\"nextUpdate\":\"" + nextUpdate + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(DiagnosticStatusClassDto.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);
        stubForDiagnosticsRequest("/status",
                "{\"errorCode\":\"" + ErrorCode.INTERNAL_ERROR + "\",\"prevUpdate\":\"" + prevUpdate
                        + "\",\"nextUpdate\":\"" + nextUpdate + "\",\"status\":\"" + DiagnosticStatus.ERROR + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ErrorCode.INTERNAL_ERROR.code(), globalConfDiagnostics.getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow().with(LocalTime.of(0, 0));
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);
        stubForDiagnosticsRequest("/status",
                "{\"prevUpdate\":\"" + prevUpdate + "\",\"nextUpdate\":\""
                        + nextUpdate + "\", \"status\":\"" + DiagnosticStatus.UNKNOWN + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsException() {
        stubFor(get(urlEqualTo("/status"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        DeviationAwareRuntimeException exception =
                assertThrows(DeviationAwareRuntimeException.class, diagnosticsApiController::getGlobalConfDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    public void getTimestampingServiceDiagnosticsSuccess() {
        stubForDiagnosticsRequest("/timestampstatus",
                "{\"" + TSA_URL_1 + "\":{\"status\":\"" + DiagnosticStatus.OK
                        + "\",\"prevUpdate\":\"" + PREVIOUS_UPDATE + "\",\"description\":\"" + TSA_URL_1 + "\"}}");

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
        stubForDiagnosticsRequest("/timestampstatus",
                "{\"" + TSA_URL_1 + "\":{\"status\":\"" + DiagnosticStatus.UNINITIALIZED
                        + "\",\"prevUpdate\":\"" + PREVIOUS_UPDATE + "\",\"description\":\"" + TSA_URL_1 + "\"}}");

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
        stubForDiagnosticsRequest("/timestampstatus",
                "{\"" + TSA_URL_1 + "\":{\"status\":\""
                        + DiagnosticStatus.ERROR + "\",\"prevUpdate\":\"" + PREVIOUS_UPDATE_MIDNIGHT + "\","
                        + "\"errorCode\":\"" + ErrorCode.MALFORMED_TIMESTAMP_SERVER_URL
                        + "\",\"description\":\"" + TSA_URL_1 + "\"}}");

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
        stubFor(get(urlEqualTo("/timestampstatus"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getTimestampingServicesDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    public void getOcspResponderDiagnosticsSuccess() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_1 + "\":{\"name\":\"" + CA_NAME_1
                        + "\",\"ocspResponderStatusMap\":{\"" + OCSP_URL_1 + "\":{\"diagnosticStatus\":\""
                        + DiagnosticStatus.OK + "\",\"url\":\""
                        + OCSP_URL_1 + "\",\"prevUpdate\":\"" + PREVIOUS_UPDATE + "\",\"nextUpdate\":\"" + NEXT_UPDATE
                        + "\"}}}}}");

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(DiagnosticStatusClassDto.OK, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsWaiting() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_2 + "\":{\"name\":\"" + CA_NAME_2 + "\","
                        + "\"ocspResponderStatusMap\":{\"" + OCSP_URL_2 + "\":{\"diagnosticStatus\":\""
                        + DiagnosticStatus.UNINITIALIZED + "\",\"url\":\"" + OCSP_URL_2
                        + "\",\"nextUpdate\":\"" + NEXT_UPDATE + "\"}}}}}");

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(DiagnosticStatusClassDto.WAITING, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertNull(diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_1 + "\":{\"name\":\"" + CA_NAME_1
                        + "\",\"ocspResponderStatusMap\":{\"" + OCSP_URL_1 + "\":{\"diagnosticStatus\":\"" + DiagnosticStatus.ERROR
                        + "\",\"errorCode\":\"" + ErrorCode.OCSP_RESPONSE_PARSING_FAILURE + "\",\"url\":\"" + OCSP_URL_1
                        + "\",\"nextUpdate\":\"" + NEXT_UPDATE_MIDNIGHT + "\"}}}}}");

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(ErrorCode.OCSP_RESPONSE_PARSING_FAILURE.code(), diagnostics.getOcspResponders()
                .get(0).getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertNull(diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_2 + "\":{\"name\":\"" + CA_NAME_2
                        + "\",\"ocspResponderStatusMap\":{\"" + OCSP_URL_2 + "\":{\"diagnosticStatus\":\"" + DiagnosticStatus.UNKNOWN
                        + "\",\"url\":\"" + OCSP_URL_2 + "\",\"prevUpdate\":\""
                        + PREVIOUS_UPDATE_MIDNIGHT + "\",\"nextUpdate\":\"" + NEXT_UPDATE_MIDNIGHT + "\"}}}}}");

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
        assertEquals(DiagnosticStatusClassDto.FAIL, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsException() {
        stubFor(get(urlEqualTo("/status"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getOcspRespondersDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().code());
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_ANCHOR"})
    public void downloadDiagnosticsReportWithoutRequiredAuthorities() throws IOException {
        byte[] bytes = "[{}]".getBytes(StandardCharsets.UTF_8);
        when(diagnosticReportService.collectSystemInformation()).thenReturn(bytes);
        when(systemService.getAnchorFilenameForDownload())
                .thenReturn("configuration_anchor_UTC_2019-04-28_09_03_31.xml");

        assertThatThrownBy(() -> diagnosticsApiController.downloadDiagnosticsReport()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_DIAGNOSTICS_REPORT"})
    public void downloadDiagnosticsReport() throws IOException {
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

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getAuthCertReqStatus() {
        TokenInfo tokenInfo = mock(TokenInfo.class);
        when(tokenInfo.getKeyInfo()).thenReturn(Collections.emptyList());
        when(tokenService.getToken(any())).thenReturn(tokenInfo);

        ResponseEntity<ConnectionStatusDto> response = diagnosticsApiController.getAuthCertReqStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ConnectionStatusDto connectionStatusDto = response.getBody();
        assertNotNull(connectionStatusDto);
        assertEquals(DiagnosticStatusClassDto.FAIL, connectionStatusDto.getStatusClass());
        assertEquals("certificate_not_found", connectionStatusDto.getError().getCode());
        assertEquals("No active auth cert found", connectionStatusDto.getError().getMetadata().getFirst());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getGlobalConfStatus() {
        when(globalConfProvider.findSourceAddresses()).thenReturn(Set.of("one-host"));

        ResponseEntity<List<GlobalConfConnectionStatusDto>> response = diagnosticsApiController.getGlobalConfStatus();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<GlobalConfConnectionStatusDto> globalConfStatuses = response.getBody();
        assertNotNull(globalConfStatuses);
        assertEquals(2, globalConfStatuses.size());
        Set<String> actualUrls = globalConfStatuses.stream()
                .map(GlobalConfConnectionStatusDto::getDownloadUrl)
                .collect(Collectors.toSet());

        Set<String> expectedUrls = Set.of(
                "http://one-host:80/internalconf",
                "https://one-host:443/internalconf"
        );

        assertEquals(expectedUrls, actualUrls);

        globalConfStatuses.forEach(status -> {
            assertEquals(DiagnosticStatusClassDto.FAIL, status.getConnectionStatus().getStatusClass());
            assertEquals("unknown_host", status.getConnectionStatus().getError().getCode());
        });
    }

    private void stubForDiagnosticsRequest(String requestPath, String responseBody) {
        stubFor(get(urlEqualTo(requestPath))
                .willReturn(aResponse().withBody(responseBody)));
    }
}
