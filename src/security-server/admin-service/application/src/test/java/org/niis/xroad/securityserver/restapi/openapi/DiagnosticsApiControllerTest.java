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

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.BeforeClass;
import org.junit.Test;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
@AutoConfigureWireMock(port = PortNumbers.ADMIN_PORT)
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
    @MockBean
    private DiagnosticReportService diagnosticReportService;

    @BeforeClass
    public static void setUp() {
        // Make them point to Wiremock port
        System.setProperty(SystemProperties.CONFIGURATION_CLIENT_ADMIN_PORT, Integer.toString(PortNumbers.ADMIN_PORT));
        System.setProperty(SystemProperties.SIGNER_ADMIN_PORT, Integer.toString(PortNumbers.ADMIN_PORT));
    }

    @Test
    public void getAddOnDiagnostics() {
        stubForDiagnosticsRequest("/addonstatus", "{\"messageLogEnabled\":true}");
        ResponseEntity<AddOnStatusDto> response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().getMessagelogEnabled());

        stubForDiagnosticsRequest("/addonstatus", "{\"messageLogEnabled\":false}");
        response = diagnosticsApiController.getAddOnDiagnostics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
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
                "{\"returnCode\":" + DiagnosticsErrorCodes.RETURN_SUCCESS + ",\"prevUpdate\":\"" + prevUpdate
                        + "\",\"nextUpdate\":\"" + nextUpdate + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.SUCCESS, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsWaiting() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusHours(1);
        stubForDiagnosticsRequest("/status", "{\"returnCode\":" + DiagnosticsErrorCodes.ERROR_CODE_UNINITIALIZED + ","
                + "\"prevUpdate\":\"" + prevUpdate + "\",\"nextUpdate\":\"" + nextUpdate + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.ERROR_CODE_UNINITIALIZED, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.WAITING, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsFailNextUpdateTomorrow() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);
        stubForDiagnosticsRequest("/status",
                "{\"returnCode\":" + DiagnosticsErrorCodes.ERROR_CODE_INTERNAL + ",\"prevUpdate\":\"" + prevUpdate
                        + "\",\"nextUpdate\":\"" + nextUpdate + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.ERROR_CODE_INTERNAL, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, globalConfDiagnostics.getStatusClass());
        assertEquals(prevUpdate, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(nextUpdate, globalConfDiagnostics.getNextUpdateAt());
    }

    @Test
    public void getGlobalConfDiagnosticsFailPreviousUpdateYesterday() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow().with(LocalTime.of(0, 0));
        final OffsetDateTime nextUpdate = prevUpdate.plusDays(1);
        stubForDiagnosticsRequest("/status",
                "{\"returnCode\":" + ERROR_CODE_UNKNOWN + ",\"prevUpdate\":\"" + prevUpdate + "\",\"nextUpdate\":\""
                        + nextUpdate + "\"}");

        ResponseEntity<GlobalConfDiagnosticsDto> response = diagnosticsApiController.getGlobalConfDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GlobalConfDiagnosticsDto globalConfDiagnostics = response.getBody();
        assertEquals(ConfigurationStatusDto.UNKNOWN, globalConfDiagnostics.getStatusCode());
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
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().getCode());
    }

    @Test
    public void getTimestampingServiceDiagnosticsSuccess() {
        stubForDiagnosticsRequest("/timestampstatus",
                "{\"" + TSA_URL_1 + "\":{\"returnCode\":" + DiagnosticsErrorCodes.RETURN_SUCCESS
                        + ",\"prevUpdate\":\"" + PREVIOUS_UPDATE + "\",\"description\":\"" + TSA_URL_1 + "\"}}");

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
    public void getTimestampingServiceDiagnosticsWaiting() {
        stubForDiagnosticsRequest("/timestampstatus",
                "{\"" + TSA_URL_1 + "\":{\"returnCode\":" + DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED
                        + ",\"prevUpdate\":\"" + PREVIOUS_UPDATE + "\",\"description\":\"" + TSA_URL_1 + "\"}}");

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
    public void getTimestampingServiceDiagnosticsFailPreviousUpdateYesterday() {
        stubForDiagnosticsRequest("/timestampstatus",
                "{\"" + TSA_URL_1 + "\":{\"returnCode\":"
                        + DiagnosticsErrorCodes.ERROR_CODE_MALFORMED_TIMESTAMP_SERVER_URL + ",\"prevUpdate\":\""
                        + PREVIOUS_UPDATE_MIDNIGHT + "\",\"description\":\"" + TSA_URL_1 + "\"}}");

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
    public void getTimestampingServiceDiagnosticsException() {
        stubFor(get(urlEqualTo("/timestampstatus"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));
        DeviationAwareRuntimeException exception = assertThrows(DeviationAwareRuntimeException.class,
                diagnosticsApiController::getTimestampingServicesDiagnostics);
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().getCode());
    }

    @Test
    public void getOcspResponderDiagnosticsSuccess() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_1 + "\":{\"name\":\"" + CA_NAME_1
                        + "\",\"ocspResponderStatusMap\":{\"" + OCSP_URL_1 + "\":{\"status\":"
                        + DiagnosticsErrorCodes.RETURN_SUCCESS + ",\"url\":\""
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
        assertEquals(OcspStatusDto.SUCCESS, diagnostics.getOcspResponders().get(0).getStatusCode());
        assertEquals(DiagnosticStatusClassDto.OK, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertEquals(PREVIOUS_UPDATE, diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsWaiting() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_2 + "\":{\"name\":\"" + CA_NAME_2 + "\","
                        + "\"ocspResponderStatusMap\":{\"" + OCSP_URL_2 + "\":{\"status\":"
                        + DiagnosticsErrorCodes.ERROR_CODE_OCSP_UNINITIALIZED + ",\"url\":\"" + OCSP_URL_2
                        + "\",\"nextUpdate\":\"" + NEXT_UPDATE + "\"}}}}}");

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response =
                diagnosticsApiController.getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_2, diagnostics.getDistinguishedName());
        assertEquals(OcspStatusDto.ERROR_CODE_OCSP_UNINITIALIZED, diagnostics.getOcspResponders()
                .get(0).getStatusCode());
        assertEquals(DiagnosticStatusClassDto.WAITING, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertNull(diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_2, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailNextUpdateTomorrow() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_1 + "\":{\"name\":\"" + CA_NAME_1
                        + "\",\"ocspResponderStatusMap\":{\"" + OCSP_URL_1 + "\":{\"status\":"
                        + DiagnosticsErrorCodes.ERROR_CODE_OCSP_RESPONSE_INVALID + ",\"url\":\"" + OCSP_URL_1
                        + "\",\"nextUpdate\":\"" + NEXT_UPDATE_MIDNIGHT + "\"}}}}}");

        ResponseEntity<Set<OcspResponderDiagnosticsDto>> response = diagnosticsApiController
                .getOcspRespondersDiagnostics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<OcspResponderDiagnosticsDto> diagnosticsSet = response.getBody();
        assertEquals(1, diagnosticsSet.size());
        OcspResponderDiagnosticsDto diagnostics = diagnosticsSet.stream().findFirst().orElse(null);
        assertEquals(1, diagnostics.getOcspResponders().size());
        assertEquals(CA_NAME_1, diagnostics.getDistinguishedName());
        assertEquals(OcspStatusDto.ERROR_CODE_OCSP_RESPONSE_INVALID, diagnostics.getOcspResponders()
                .get(0).getStatusCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, diagnostics.getOcspResponders().get(0).getStatusClass());
        assertNull(diagnostics.getOcspResponders().get(0).getPrevUpdateAt());
        assertEquals(NEXT_UPDATE_MIDNIGHT, diagnostics.getOcspResponders().get(0).getNextUpdateAt());
        assertEquals(OCSP_URL_1, diagnostics.getOcspResponders().get(0).getUrl());
    }

    @Test
    public void getOcspResponderDiagnosticsFailPreviousUpdateYesterday() {
        stubForDiagnosticsRequest("/status",
                "{\"certificationServiceStatusMap\":{\"" + CA_NAME_2 + "\":{\"name\":\"" + CA_NAME_2
                        + "\",\"ocspResponderStatusMap\":{\"" + OCSP_URL_2 + "\":{\"status\":" + ERROR_CODE_UNKNOWN
                        + ",\"url\":\"" + OCSP_URL_2 + "\",\"prevUpdate\":\""
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
        assertEquals(OcspStatusDto.UNKNOWN, diagnostics.getOcspResponders().get(0).getStatusCode());
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
        assertEquals(DeviationCodes.ERROR_DIAGNOSTIC_REQUEST_FAILED, exception.getErrorDeviation().getCode());
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

    private void stubForDiagnosticsRequest(String requestPath, String responseBody) {
        stubFor(get(urlEqualTo(requestPath))
                .willReturn(aResponse().withBody(responseBody)));
    }
}
