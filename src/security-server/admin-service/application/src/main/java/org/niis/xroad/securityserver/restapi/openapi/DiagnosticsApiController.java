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
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.globalconf.status.DiagnosticsStatus;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.securityserver.restapi.converter.AddOnStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.BackupEncryptionStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.GlobalConfDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.converter.MessageLogEncryptionStatusConverter;
import org.niis.xroad.securityserver.restapi.converter.OcspResponderDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.converter.TimestampingServiceDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.dto.OcspResponderDiagnosticsStatus;
import org.niis.xroad.securityserver.restapi.exception.ErrorMessage;
import org.niis.xroad.securityserver.restapi.openapi.model.AddOnStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.BackupEncryptionStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.MessageLogEncryptionStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.OcspResponderDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnostics;
import org.niis.xroad.securityserver.restapi.service.DiagnosticService;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticReportService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

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
    private final DiagnosticReportService diagnosticReportService;
    private final GlobalConfDiagnosticConverter globalConfDiagnosticConverter;
    private final TimestampingServiceDiagnosticConverter timestampingServiceDiagnosticConverter;
    private final OcspResponderDiagnosticConverter ocspResponderDiagnosticConverter;
    private final AddOnStatusConverter addOnStatusConverter;

    private final BackupEncryptionStatusConverter backupEncryptionStatusConverter;

    private final MessageLogEncryptionStatusConverter messageLogEncryptionStatusConverter;

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<GlobalConfDiagnostics> getGlobalConfDiagnostics() {
        DiagnosticsStatus status = diagnosticService.queryGlobalConfStatus();
        return new ResponseEntity<>(globalConfDiagnosticConverter.convert(status), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<Set<TimestampingServiceDiagnostics>> getTimestampingServicesDiagnostics() {
        Set<DiagnosticsStatus> statuses = diagnosticService.queryTimestampingStatus();
        return new ResponseEntity<>(timestampingServiceDiagnosticConverter.convert(statuses), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<Set<OcspResponderDiagnostics>> getOcspRespondersDiagnostics() {
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
            throw new ServiceException(ErrorMessage.FAILED_COLLECT_SYSTEM_INFORMATION, e);
        }

    }

    @Override
    @PreAuthorize("hasAnyAuthority('DIAGNOSTICS', 'VIEW_TSPS')")
    public ResponseEntity<AddOnStatus> getAddOnDiagnostics() {
        AddOnStatusDiagnostics addOnStatus = diagnosticService.queryAddOnStatus();
        return new ResponseEntity<>(addOnStatusConverter.convert(addOnStatus), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<BackupEncryptionStatus> getBackupEncryptionDiagnostics() {
        BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics =
                diagnosticService.queryBackupEncryptionStatus();
        return new ResponseEntity<>(backupEncryptionStatusConverter
                .convert(backupEncryptionStatusDiagnostics), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<MessageLogEncryptionStatus> getMessageLogEncryptionDiagnostics() {
        MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics =
                diagnosticService.queryMessageLogEncryptionStatus();
        return new ResponseEntity<>(messageLogEncryptionStatusConverter
                .convert(messageLogEncryptionStatusDiagnostics), HttpStatus.OK);
    }

    private String systemInformationFilename() {
        return "diagnostic-report-%s.json".formatted(FORMATTER.format(LocalDateTime.now()));
    }
}
