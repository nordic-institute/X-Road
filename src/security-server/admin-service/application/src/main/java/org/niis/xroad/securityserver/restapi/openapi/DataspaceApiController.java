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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.niis.xroad.securityserver.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceParticipantContextStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceProvisioningStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceTlsCertificateEnrollmentStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceTlsCertificateEnrollmentStatusDto.EnrollmentMethodEnum;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService.DataspaceStatus;
import org.niis.xroad.securityserver.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.service.DsTlsCertificateService.EnrollmentStatusView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UPLOAD_DATASPACE_TLS_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_FILE_NAME;

/**
 * Data space API controller — provisioning status and dataspace TLS certificate endpoints.
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class DataspaceApiController implements DataspaceApi {

    private final DataspaceProvisioningStatusService dataspaceProvisioningStatusService;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('VIEW_DATASPACE_STATUS')")
    public ResponseEntity<DataspaceProvisioningStatusDto> getDataspaceProvisioningStatus() {
        DataspaceStatus status = dataspaceProvisioningStatusService.readStatus();
        return new ResponseEntity<>(toDto(status), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CERT')")
    public ResponseEntity<CertificateDetailsDto> getDataspaceTlsCertificate() {
        var certificate = dsTlsCertificateService.getDataspaceTlsCertificate();
        return ResponseEntity.ok(certificateDetailsConverter.convert(certificate));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CERT')")
    public ResponseEntity<DataspaceTlsCertificateEnrollmentStatusDto> getDataspaceTlsCertificateEnrollmentStatus() {
        EnrollmentStatusView status = dsTlsCertificateService.getEnrollmentStatus();
        return ResponseEntity.ok(toDto(status));
    }

    @Override
    @PreAuthorize("hasAuthority('UPLOAD_DS_TLS_CERT')")
    @AuditEventMethod(event = UPLOAD_DATASPACE_TLS_CERT)
    public ResponseEntity<CertificateDetailsDto> uploadDataspaceTlsCertificate(MultipartFile key, MultipartFile certificate) {
        auditDataHelper.put(CERT_FILE_NAME, certificate.getOriginalFilename());

        byte[] keyBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(key);
        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(certificate);

        var uploaded = dsTlsCertificateService.uploadDataspaceTlsCertificate(keyBytes, certificateBytes);
        return ResponseEntity.ok(certificateDetailsConverter.convert(uploaded));
    }

    private DataspaceProvisioningStatusDto toDto(DataspaceStatus status) {
        var dto = new DataspaceProvisioningStatusDto();
        dto.setEnabled(status.enabled());
        dto.setAuthCertRegistered(status.authCertRegistered());
        dto.setParticipantContexts(toContextDtos(status.participantContexts()));
        return dto;
    }

    private List<DataspaceParticipantContextStatusDto> toContextDtos(List<ParticipantContextStatus> contexts) {
        return contexts.stream().map(this::toContextDto).toList();
    }

    private DataspaceParticipantContextStatusDto toContextDto(ParticipantContextStatus ctx) {
        var dto = new DataspaceParticipantContextStatusDto();
        dto.setParticipantId(ctx.participantId());
        dto.setKind(DataspaceParticipantContextStatusDto.KindEnum.valueOf(ctx.kind().name()));
        dto.setContextCreated(ctx.contextCreated());
        dto.setCredentialStatus(DataspaceParticipantContextStatusDto.CredentialStatusEnum.valueOf(ctx.credentialStatus()));
        return dto;
    }

    private DataspaceTlsCertificateEnrollmentStatusDto toDto(EnrollmentStatusView status) {
        var dto = new DataspaceTlsCertificateEnrollmentStatusDto(EnrollmentMethodEnum.valueOf(status.enrollmentMethod().name()));
        if (status.nextRenewalTime() != null) {
            dto.setNextRenewalTime(OffsetDateTime.ofInstant(status.nextRenewalTime(), ZoneOffset.UTC));
        }
        dto.setLastError(status.lastError());
        return dto;
    }
}
