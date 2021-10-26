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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.SystemApi;
import org.niis.xroad.centralserver.openapi.model.HighAvailabilityStatus;
import org.niis.xroad.centralserver.openapi.model.SystemStatus;
import org.niis.xroad.centralserver.openapi.model.Version;
import org.niis.xroad.centralserver.restapi.config.HAConfigStatus;
import org.niis.xroad.centralserver.restapi.converter.InitializationStatusConverter;
import org.niis.xroad.centralserver.restapi.service.InitializationService;
import org.niis.xroad.centralserver.restapi.service.SystemParameterService;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.CENTRAL_SERVER_ADDRESS;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.INSTANCE_IDENTIFIER;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SystemApiController implements SystemApi {

    private final SystemParameterService systemParameterService;
    private final InitializationService initializationService;
    private final InitializationStatusConverter initializationStatusConverter;

    @Autowired
    HAConfigStatus currentHaConfigStatus;

    @Override
    @PreAuthorize("hasAuthority('CENTRAL_SERVER_ADDRESS')")
    public ResponseEntity<String> centralServerAddress() {
        return ResponseEntity.ok(systemParameterService.getParameterValue(CENTRAL_SERVER_ADDRESS, ""));
    }

    @Override
    @PreAuthorize("hasAuthority('HIGH_AVAILABILITY_STATUS')")
    public ResponseEntity<HighAvailabilityStatus> highAvailabilityStatus() {
        var highAvailabilityStatus = new HighAvailabilityStatus();
        highAvailabilityStatus.setIsHaConfigured(currentHaConfigStatus.isHaConfigured());
        highAvailabilityStatus.setNodeName(currentHaConfigStatus.getCurrentHaNodeName());
        return ResponseEntity.ok(highAvailabilityStatus);
    }

    @Override
    @PreAuthorize("hasAuthority('INSTANCE_IDENTIFIER')")
    public ResponseEntity<String> instanceidentifier() {
        return ResponseEntity.ok(systemParameterService.getParameterValue(INSTANCE_IDENTIFIER, ""));
    }

    @Override
    @PreAuthorize("hasAuthority('SYSTEM_STATUS')")
    public ResponseEntity<SystemStatus> systemStatus() {
        var systemStatus = new SystemStatus();
        systemStatus.setInitializationStatus(
                initializationStatusConverter.convert(initializationService.getInitializationStatus()));
        systemStatus.setHighAvailabilityStatus(
                new HighAvailabilityStatus()
                        .isHaConfigured(currentHaConfigStatus.isHaConfigured())
                        .nodeName(currentHaConfigStatus.getCurrentHaNodeName()));
        return ResponseEntity.ok(systemStatus);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<Version> systemVersion() {
        return ResponseEntity.ok(new Version().info(ee.ria.xroad.common.Version.XROAD_VERSION));
    }
}
