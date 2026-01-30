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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusV2;
import org.niis.xroad.securityserver.restapi.dto.InitializationStep;
import org.niis.xroad.securityserver.restapi.dto.InitializationStepInfo;
import org.niis.xroad.securityserver.restapi.dto.InitializationStepStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.InitStepResultDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationOverallStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStatusV2Dto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStepDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStepInfoDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStepStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServerConfInitRequestDto;
import org.niis.xroad.securityserver.restapi.openapi.model.SoftTokenInitRequestDto;
import org.niis.xroad.securityserver.restapi.service.InitializationStepService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.INIT_GPG_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.INIT_MLOG_ENCRYPTION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.INIT_SERVER_CONF;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.INIT_SOFTTOKEN;

/**
 * V2 Initialization API controller with granular step tracking.
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class InitializationApiControllerV2 implements InitializationV2Api {

    private final InitializationStepService initializationStepService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InitializationStatusV2Dto> getInitializationStatusV2() {
        InitializationStatusV2 status = initializationStepService.getInitializationStatus();
        return ResponseEntity.ok(convertStatus(status));
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = INIT_SERVER_CONF)
    public ResponseEntity<InitStepResultDto> initializeServerConf(
            ServerConfInitRequestDto serverConfInitRequestDto) {
        InitializationStepInfo result = initializationStepService.executeServerConfStep(
                serverConfInitRequestDto.getSecurityServerCode(),
                serverConfInitRequestDto.getOwnerMemberClass(),
                serverConfInitRequestDto.getOwnerMemberCode(),
                Boolean.TRUE.equals(serverConfInitRequestDto.getIgnoreWarnings()));
        return createStepResultResponse(result);
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = INIT_SOFTTOKEN)
    public ResponseEntity<InitStepResultDto> initializeSoftToken(
            SoftTokenInitRequestDto softTokenInitRequestDto) {
        InitializationStepInfo result = initializationStepService.executeSoftTokenStep(
                softTokenInitRequestDto.getSoftwareTokenPin());
        return createStepResultResponse(result);
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = INIT_GPG_KEY)
    public ResponseEntity<InitStepResultDto> initializeGpgKey() {
        InitializationStepInfo result = initializationStepService.executeGpgKeyStep();
        return createStepResultResponse(result);
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = INIT_MLOG_ENCRYPTION)
    public ResponseEntity<InitStepResultDto> initializeMessageLogEncryption() {
        InitializationStepInfo result = initializationStepService.executeMessageLogEncryptionStep();
        return createStepResultResponse(result);
    }

    private ResponseEntity<InitStepResultDto> createStepResultResponse(InitializationStepInfo result) {
        var dto = new InitStepResultDto(
                mapStep(result.step()),
                mapStepStatus(result.status()),
                result.status() == InitializationStepStatus.COMPLETED);
        dto.setMessage(result.errorMessage());
        dto.setErrorCode(result.errorCode());

        var httpStatus = result.status() == InitializationStepStatus.COMPLETED
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return new ResponseEntity<>(dto, httpStatus);
    }

    private InitializationStatusV2Dto convertStatus(InitializationStatusV2 status) {
        var dto = new InitializationStatusV2Dto(
                mapOverallStatus(status.overallStatus()),
                status.anchorImported(),
                status.steps().stream().map(this::convertStepInfo).toList(),
                status.pendingSteps().stream().map(this::mapStep).toList(),
                status.failedSteps().stream().map(this::mapStep).toList(),
                status.completedSteps().stream().map(this::mapStep).toList(),
                status.fullyInitialized());
        dto.setSecurityServerId(status.securityServerId());
        dto.setTokenPinPolicyEnforced(status.tokenPinPolicyEnforced());
        return dto;
    }

    private InitializationStepInfoDto convertStepInfo(InitializationStepInfo info) {
        var dto = new InitializationStepInfoDto(
                mapStep(info.step()),
                mapStepStatus(info.status()),
                info.retryable());
        if (info.startedAt() != null) {
            dto.setStartedAt(OffsetDateTime.ofInstant(info.startedAt(), ZoneOffset.UTC));
        }
        if (info.completedAt() != null) {
            dto.setCompletedAt(OffsetDateTime.ofInstant(info.completedAt(), ZoneOffset.UTC));
        }
        dto.setErrorMessage(info.errorMessage());
        dto.setErrorCode(info.errorCode());
        return dto;
    }

    private InitializationStepDto mapStep(InitializationStep step) {
        return InitializationStepDto.fromValue(step.name());
    }

    private InitializationStepStatusDto mapStepStatus(InitializationStepStatus status) {
        return InitializationStepStatusDto.fromValue(status.name());
    }

    private InitializationOverallStatusDto mapOverallStatus(InitializationStatusV2.OverallStatus status) {
        return InitializationOverallStatusDto.fromValue(status.name());
    }
}
