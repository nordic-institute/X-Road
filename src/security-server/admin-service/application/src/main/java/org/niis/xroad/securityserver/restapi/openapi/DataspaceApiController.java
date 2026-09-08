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
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceParticipantContextStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceProvisioningStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceTlsCertificateEnrollmentStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DataspaceTlsCertificateEnrollmentStatusDto.EnrollmentMethodEnum;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService.DataspaceStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Data space API controller — read-only provisioning status and DataSpace TLS certificate enrollment status
 * endpoints.
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class DataspaceApiController implements DataspaceApi {

    private final DataspaceProvisioningStatusService dataspaceProvisioningStatusService;
    private final DsTlsCertificateService dsTlsCertificateService;

    @Override
    @PreAuthorize("hasAuthority('VIEW_DATASPACE_STATUS')")
    public ResponseEntity<DataspaceProvisioningStatusDto> getDataspaceProvisioningStatus() {
        DataspaceStatus status = dataspaceProvisioningStatusService.readStatus();
        return new ResponseEntity<>(toDto(status), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_DS_TLS_CERT')")
    public ResponseEntity<DataspaceTlsCertificateEnrollmentStatusDto> getDataspaceTlsCertificateEnrollmentStatus() {
        DsTlsEnrollmentStatus status = dsTlsCertificateService.getEnrollmentStatus();
        return new ResponseEntity<>(toDto(status), HttpStatus.OK);
    }

    private DataspaceTlsCertificateEnrollmentStatusDto toDto(DsTlsEnrollmentStatus status) {
        var dto = new DataspaceTlsCertificateEnrollmentStatusDto(
                status.configured() ? EnrollmentMethodEnum.valueOf(status.method().name()) : EnrollmentMethodEnum.NONE);
        if (status.nextRenewalTime() != null) {
            dto.setNextRenewalTime(status.nextRenewalTime().atOffset(ZoneOffset.UTC));
        }
        dto.setLastError(status.lastError());
        return dto;
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
        dto.setCredentialStatus(DataspaceParticipantContextStatusDto.CredentialStatusEnum.valueOf(ctx.credentialStatus().name()));
        if (ctx.identityStatus() != null) {
            dto.setIdentityStatus(DataspaceParticipantContextStatusDto.IdentityStatusEnum.valueOf(ctx.identityStatus().name()));
        }
        return dto;
    }
}
