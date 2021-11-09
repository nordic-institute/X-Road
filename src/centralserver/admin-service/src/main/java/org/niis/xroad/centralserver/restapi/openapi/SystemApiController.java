/**
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
package org.niis.xroad.centralserver.restapi.openapi;

import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.SystemApi;
import org.niis.xroad.centralserver.openapi.model.HighAvailabilityStatus;
import org.niis.xroad.centralserver.openapi.model.ServerAddressUpdateBody;
import org.niis.xroad.centralserver.openapi.model.SystemStatus;
import org.niis.xroad.centralserver.openapi.model.Version;
import org.niis.xroad.centralserver.restapi.config.HAConfigStatus;
import org.niis.xroad.centralserver.restapi.converter.InitializationStatusConverter;
import org.niis.xroad.centralserver.restapi.service.InitializationService;
import org.niis.xroad.centralserver.restapi.service.SystemParameterService;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SystemApiController implements SystemApi {

    private final InitializationService initializationService;
    private final InitializationStatusConverter initializationStatusConverter;
    private final SystemParameterService systemParameterService;
    private final AuditDataHelper auditDataHelper;
    private final HAConfigStatus currentHaConfigStatus;


    @Override
    @PreAuthorize("hasAuthority('SYSTEM_STATUS')")
    public ResponseEntity<SystemStatus> systemStatus() {
        return getSystemStatusResponseEntity();
    }


    /**
     * PUT /system/status/server-address : update the server address
     *
     * @param serverAddressUpdateBody New central server address (required)
     * @return System status with updated Central Server address (status code 200)
     * or request was invalid (status code 400)
     * or authentication credentials are missing (status code 401)
     * or request has been refused (status code 403)
     * or resource requested does not exist (status code 404)
     * or request specified an invalid format (status code 406)
     * or internal server error (status code 500)
     */
    @Override
    @PreAuthorize("hasAuthority('SYSTEM_STATUS')")
    @AuditEventMethod(event = RestApiAuditEvent.UPDATE_CENTRAL_SERVER_ADDRESS)
    public ResponseEntity<SystemStatus> updateCentralServerAddress(
            @ApiParam(value = "New central server address", required = true) @Validated @RequestBody
                    ServerAddressUpdateBody serverAddressUpdateBody) {
        auditDataHelper.put(RestApiAuditProperty.CENTRAL_SERVER_ADDRESS,
                serverAddressUpdateBody.getCentralServerAddress());
        systemParameterService.updateOrCreateParameter(SystemParameterService.CENTRAL_SERVER_ADDRESS,
                serverAddressUpdateBody.getCentralServerAddress());
        return getSystemStatusResponseEntity();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<Version> systemVersion() {
        return ResponseEntity.ok(new Version().info(ee.ria.xroad.common.Version.XROAD_VERSION));
    }

    private ResponseEntity<SystemStatus> getSystemStatusResponseEntity() {
        var systemStatus = new SystemStatus();
        systemStatus.setInitializationStatus(
                initializationStatusConverter.convert(initializationService.getInitializationStatus()));
        systemStatus.setHighAvailabilityStatus(
                new HighAvailabilityStatus()
                        .isHaConfigured(currentHaConfigStatus.isHaConfigured())
                        .nodeName(currentHaConfigStatus.getCurrentHaNodeName()));
        return ResponseEntity.ok(systemStatus);
    }
}
