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

import ee.ria.xroad.common.SystemProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.converter.TokenInitStatusMapping;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.InitialServerConfDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStatusDto;
import org.niis.xroad.securityserver.restapi.service.InitializationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.INIT_SERVER_CONFIGURATION;

/**
 * Init (Security Server) controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class InitializationApiController implements InitializationApi {
    private final InitializationService initializationService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InitializationStatusDto> getInitializationStatus() {
        InitializationStatus initStatus = initializationService.getSecurityServerInitializationStatus();
        var initializationStatusDto = new InitializationStatusDto();
        initializationStatusDto.setIsAnchorImported(initStatus.isAnchorImported());
        initializationStatusDto.setIsServerCodeInitialized(initStatus.isServerCodeInitialized());
        initializationStatusDto.setIsServerOwnerInitialized(initStatus.isServerOwnerInitialized());
        initializationStatusDto.setSoftwareTokenInitStatus(TokenInitStatusMapping.map(initStatus.getSoftwareTokenInitStatusInfo()));
        initializationStatusDto.setEnforceTokenPinPolicy(SystemProperties.shouldEnforceTokenPinPolicy());
        return new ResponseEntity<>(initializationStatusDto, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = INIT_SERVER_CONFIGURATION)
    public synchronized ResponseEntity<Void> initSecurityServer(InitialServerConfDto initialServerConfDto) {
        String securityServerCode = initialServerConfDto.getSecurityServerCode();
        String ownerMemberClass = initialServerConfDto.getOwnerMemberClass();
        String ownerMemberCode = initialServerConfDto.getOwnerMemberCode();
        String softwareTokenPin = initialServerConfDto.getSoftwareTokenPin();
        boolean ignoreWarnings = Boolean.TRUE.equals(initialServerConfDto.getIgnoreWarnings());
        try {
            initializationService.initialize(securityServerCode, ownerMemberClass, ownerMemberCode, softwareTokenPin,
                    ignoreWarnings);
        } catch (UnhandledWarningsException e) {
            throw new BadRequestException(e);
        }

        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
