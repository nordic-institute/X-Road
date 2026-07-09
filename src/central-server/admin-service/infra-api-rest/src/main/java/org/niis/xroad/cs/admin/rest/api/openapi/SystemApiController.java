/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.rest.api.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.ConfigurationAnchorService;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.HAClusterStatusService;
import org.niis.xroad.cs.admin.api.service.InitializationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.rest.api.converter.HAClusterNodeDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.model.InitializationStatusDtoConverter;
import org.niis.xroad.cs.openapi.SystemApi;
import org.niis.xroad.cs.openapi.model.CentralServerAddressDto;
import org.niis.xroad.cs.openapi.model.HighAvailabilityClusterNodeDto;
import org.niis.xroad.cs.openapi.model.HighAvailabilityClusterStatusDto;
import org.niis.xroad.cs.openapi.model.HighAvailabilityStatusDto;
import org.niis.xroad.cs.openapi.model.SystemStatusDto;
import org.niis.xroad.cs.openapi.model.VersionDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.openapi.model.HighAvailabilityClusterNodeDto.StatusEnum.OK;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SystemApiController implements SystemApi {

    private final InitializationService initializationService;
    private final SystemParameterService systemParameterService;
    private final ConfigurationAnchorService configurationAnchorService;
    private final ConfigurationService configurationService;
    private final AuditDataHelper auditDataHelper;
    private final HAConfigStatus currentHaConfigStatus;
    private final HAClusterStatusService haClusterStatusService;
    private final InitializationStatusDtoConverter initializationStatusDtoConverter;
    private final HAClusterNodeDtoConverter haClusterNodeDtoConverter;

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<SystemStatusDto> getSystemStatus() {
        return getSystemStatusResponseEntity();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<HighAvailabilityClusterStatusDto> getHighAvailabilityClusterStatus() {
        List<HighAvailabilityClusterNodeDto> haClusterNodes = haClusterStatusService.getHAClusterNodes().stream()
                .map(haClusterNodeDtoConverter::toTarget)
                .collect(toList());
        var haClusterStatus = new HighAvailabilityClusterStatusDto()
                .isHaConfigured(currentHaConfigStatus.isHaConfigured())
                .nodeName(currentHaConfigStatus.getCurrentHaNodeName())
                .nodes(haClusterNodes)
                .allNodesOk(
                        haClusterNodes.stream().allMatch(node -> OK == node.getStatus())
                );
        return ResponseEntity.ok(haClusterStatus);
    }

    /**
     * PUT /system/server-address : update the server address
     *
     * @param centralServerAddress New central server address (required)
     * @return System status with updated Central Server address (status code 200)
     * or request was invalid (status code 400)
     * or authentication credentials are missing (status code 401)
     * or request has been refused (status code 403)
     * or resource requested does not exist (status code 404)
     * or request specified an invalid format (status code 406)
     * or internal server error (status code 500)
     */
    @Override
    @PreAuthorize("hasAuthority('EDIT_CENTRAL_SERVER_ADDRESS')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_CENTRAL_SERVER_ADDRESS)
    public ResponseEntity<SystemStatusDto> updateCentralServerAddress(CentralServerAddressDto centralServerAddress) {
        auditDataHelper.put(RestApiAuditProperty.CENTRAL_SERVER_ADDRESS,
                centralServerAddress.getCentralServerAddress());
        systemParameterService.updateOrCreateParameter(SystemParameterService.CENTRAL_SERVER_ADDRESS,
                centralServerAddress.getCentralServerAddress());

        Arrays.stream(ConfigurationSourceType.values())
                .filter(configurationService::hasSigningKeys)
                .forEach(sourceType -> configurationAnchorService.recreateAnchor(sourceType, false));

        return getSystemStatusResponseEntity();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_VERSION')")
    public ResponseEntity<VersionDto> getSystemVersion() {
        return ResponseEntity.ok(new VersionDto().info(ee.ria.xroad.common.Version.XROAD_VERSION));
    }

    private ResponseEntity<SystemStatusDto> getSystemStatusResponseEntity() {
        var systemStatus = new SystemStatusDto();
        var initStatus = initializationStatusDtoConverter
                .fromDto(initializationService.getInitializationStatus());
        systemStatus.setInitializationStatus(initStatus);
        systemStatus.setHighAvailabilityStatus(
                new HighAvailabilityStatusDto()
                        .isHaConfigured(currentHaConfigStatus.isHaConfigured())
                        .nodeName(currentHaConfigStatus.getCurrentHaNodeName()));
        return ResponseEntity.ok(systemStatus);
    }
}
