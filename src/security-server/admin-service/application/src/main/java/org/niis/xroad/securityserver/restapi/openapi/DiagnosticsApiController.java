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
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.ProxyMemory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.ServiceIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.securityserver.restapi.converter.AddOnStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.AuthCertStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.BackupEncryptionStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.GlobalConfDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.converter.GlobalConfStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.MessageLogEncryptionStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.OcspResponderDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.converter.OperationalInfoConverter;
import org.niis.xroad.securityserver.restapi.converter.ProxyMemoryUsageStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.TimestampingServiceDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.AddOnStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CaOcspDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfConnectionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OperationalDataIntervalDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ProxyMemoryUsageStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnosticsDto;
import org.niis.xroad.securityserver.restapi.service.DiagnosticConnectionService;
import org.niis.xroad.securityserver.restapi.service.DiagnosticService;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticReportService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.FAILED_COLLECT_SYSTEM_INFORMATION;

/**
 * diagnostics api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class DiagnosticsApiController implements DiagnosticsApi {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DiagnosticService diagnosticService;
    private final DiagnosticConnectionService diagnosticConnectionService;
    private final DiagnosticReportService diagnosticReportService;
    private final GlobalConfDiagnosticConverter globalConfDiagnosticConverter;
    private final TimestampingServiceDiagnosticConverter timestampingServiceDiagnosticConverter;
    private final OcspResponderDiagnosticConverter ocspResponderDiagnosticConverter;
    private final AddOnStatusConverter addOnStatusConverter;
    private final BackupEncryptionStatusConverter backupEncryptionStatusConverter;
    private final MessageLogEncryptionStatusConverter messageLogEncryptionStatusConverter;
    private final ProxyMemoryUsageStatusConverter proxyMemoryUsageStatusConverter;
    private final OperationalInfoConverter operationalInfoConverter;
    private final ClientIdConverter clientIdConverter;
    private final ServiceIdConverter serviceIdConverter;
    private final AuthCertStatusConverter authCertStatusConverter;
    private final GlobalConfStatusConverter globalConfStatusConverter;

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<GlobalConfDiagnosticsDto> getGlobalConfDiagnostics() {
        DiagnosticsStatus status = diagnosticService.queryGlobalConfStatus();
        return new ResponseEntity<>(globalConfDiagnosticConverter.convert(status), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<Set<TimestampingServiceDiagnosticsDto>> getTimestampingServicesDiagnostics() {
        Set<DiagnosticsStatus> statuses = diagnosticService.queryTimestampingStatus();
        return new ResponseEntity<>(timestampingServiceDiagnosticConverter.convert(statuses), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<Set<CaOcspDiagnosticsDto>> getOcspRespondersDiagnostics() {
        List<OcspResponderDiagnosticsStatus> statuses = diagnosticService.queryOcspResponderStatus();
        return new ResponseEntity<>(ocspResponderDiagnosticConverter.convert(statuses), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('DOWNLOAD_DIAGNOSTICS_REPORT')")
    public ResponseEntity<Resource> downloadDiagnosticsReport() {
        try {
            return ControllerUtil.createAttachmentResourceResponse(diagnosticReportService.collectSystemInformation(),
                    systemInformationFilename());
        } catch (Exception e) {
            throw new InternalServerErrorException(e, FAILED_COLLECT_SYSTEM_INFORMATION.build());
        }

    }

    @Override
    @PreAuthorize("hasAnyAuthority('DIAGNOSTICS', 'VIEW_TSPS')")
    public ResponseEntity<AddOnStatusDto> getAddOnDiagnostics() {
        AddOnStatusDiagnostics addOnStatus = diagnosticService.queryAddOnStatus();
        return new ResponseEntity<>(addOnStatusConverter.convert(addOnStatus), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<BackupEncryptionStatusDto> getBackupEncryptionDiagnostics() {
        BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics =
                diagnosticService.queryBackupEncryptionStatus();
        return new ResponseEntity<>(backupEncryptionStatusConverter
                .convert(backupEncryptionStatusDiagnostics), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<MessageLogEncryptionStatusDto> getMessageLogEncryptionDiagnostics() {
        MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics =
                diagnosticService.queryMessageLogEncryptionStatus();
        return new ResponseEntity<>(messageLogEncryptionStatusConverter
                .convert(messageLogEncryptionStatusDiagnostics), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<ProxyMemoryUsageStatusDto> getProxyMemoryUsage() {
        ProxyMemory proxyMemoryUsage = diagnosticService.queryProxyMemoryUsage();
        return new ResponseEntity<>(proxyMemoryUsageStatusConverter.convert(proxyMemoryUsage), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<List<OperationalDataIntervalDto>> getOperationalDataIntervals(OffsetDateTime recordsFrom,
                                                                                        OffsetDateTime recordsTo,
                                                                                        Integer interval,
                                                                                        String securityServerType,
                                                                                        String memberId,
                                                                                        String serviceId) {
        List<OperationalDataInterval> opDataIntervals = diagnosticService.getOperationalDataIntervals(
                recordsFrom.toInstant().toEpochMilli(),
                recordsTo.toInstant().toEpochMilli(),
                interval,
                securityServerType,
                memberId != null ? clientIdConverter.convertId(memberId) : null,
                serviceId != null ? serviceIdConverter.convertId(serviceId) : null);
        return new ResponseEntity<>(operationalInfoConverter.convert(opDataIntervals), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<ConnectionStatusDto> getAuthCertReqStatus() {
        return new ResponseEntity<>(authCertStatusConverter.convert(diagnosticConnectionService.getAuthCertReqStatus()), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<List<GlobalConfConnectionStatusDto>> getGlobalConfStatus() {
        return new ResponseEntity<>(globalConfStatusConverter.convert(diagnosticConnectionService.getGlobalConfStatus()), HttpStatus.OK);
    }

    private String systemInformationFilename() {
        return "diagnostic-report-%s.json".formatted(FORMATTER.format(LocalDateTime.now()));
    }
}
