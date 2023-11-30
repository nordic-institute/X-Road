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

import ee.ria.xroad.common.SystemProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.converter.TokenInitStatusMapping;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitialServerConf;
import org.niis.xroad.securityserver.restapi.openapi.model.InitializationStatus;
import org.niis.xroad.securityserver.restapi.service.AnchorNotFoundException;
import org.niis.xroad.securityserver.restapi.service.InitializationService;
import org.niis.xroad.securityserver.restapi.service.InvalidCharactersException;
import org.niis.xroad.securityserver.restapi.service.WeakPinException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.INIT_SERVER_CONFIGURATION;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GPG_KEY_GENERATION_INTERRUPTED;

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
    public ResponseEntity<InitializationStatus> getInitializationStatus() {
        InitializationStatusDto initStatus = initializationService.getSecurityServerInitializationStatus();
        var status = new InitializationStatus();
        status.setIsAnchorImported(initStatus.isAnchorImported());
        status.setIsServerCodeInitialized(initStatus.isServerCodeInitialized());
        status.setIsServerOwnerInitialized(initStatus.isServerOwnerInitialized());
        status.setSoftwareTokenInitStatus(TokenInitStatusMapping.map(initStatus.getSoftwareTokenInitStatusInfo()));
        status.setEnforceTokenPinPolicy(SystemProperties.shouldEnforceTokenPinPolicy());
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = INIT_SERVER_CONFIGURATION)
    public synchronized ResponseEntity<Void> initSecurityServer(InitialServerConf initialServerConf) {
        String securityServerCode = initialServerConf.getSecurityServerCode();
        String ownerMemberClass = initialServerConf.getOwnerMemberClass();
        String ownerMemberCode = initialServerConf.getOwnerMemberCode();
        String softwareTokenPin = initialServerConf.getSoftwareTokenPin();
        boolean ignoreWarnings = Boolean.TRUE.equals(initialServerConf.getIgnoreWarnings());
        try {
            initializationService.initialize(securityServerCode, ownerMemberClass, ownerMemberCode, softwareTokenPin,
                    ignoreWarnings);
        } catch (AnchorNotFoundException | InitializationService.ServerAlreadyFullyInitializedException e) {
            throw new ConflictException(e);
        } catch (UnhandledWarningsException | InvalidCharactersException
                 | WeakPinException | InitializationService.InvalidInitParamsException e) {
            throw new BadRequestException(e);
        } catch (InitializationService.SoftwareTokenInitException e) {
            throw new InternalServerErrorException(e);
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(new ErrorDeviation(ERROR_GPG_KEY_GENERATION_INTERRUPTED));
        }

        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
