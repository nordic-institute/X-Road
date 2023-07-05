/**
 * The MIT License
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

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.service.ManagementServicesService;
import org.niis.xroad.cs.admin.rest.api.converter.ManagementServicesConfigurationMapper;
import org.niis.xroad.cs.openapi.ManagementServicesApi;
import org.niis.xroad.cs.openapi.model.ManagementServicesConfigurationDto;
import org.niis.xroad.cs.openapi.model.RegisterServiceProviderRequestDto;
import org.niis.xroad.cs.openapi.model.ServiceProviderIdDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_SERVICE_PROVIDER_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_MANAGEMENT_SERVICES_PROVIDER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REGISTER_MANAGEMENT_SERVICES_PROVIDER;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class ManagementServicesController implements ManagementServicesApi {
    private final ManagementServicesService managementServicesService;
    private final ManagementServicesConfigurationMapper managementServicesConfigurationMapper;
    private final ClientIdConverter clientIdConverter;
    private final SecurityServerIdConverter securityServerIdConverter;

    @Override
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    public ResponseEntity<ManagementServicesConfigurationDto> getManagementServicesConfiguration() {
        var configuration = managementServicesService.getManagementServicesConfiguration();
        return ResponseEntity.ok(managementServicesConfigurationMapper.toTarget(configuration));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    @AuditEventMethod(event = EDIT_MANAGEMENT_SERVICES_PROVIDER)
    public ResponseEntity<ManagementServicesConfigurationDto> updateManagementServicesConfiguration(
            ServiceProviderIdDto serviceProviderIdDto) {
        var serviceProviderId = serviceProviderIdDto.getServiceProviderId();
        if (!clientIdConverter.isEncodedSubsystemId(serviceProviderId)) {
            throw new ValidationFailureException(INVALID_SERVICE_PROVIDER_ID, serviceProviderId);
        }

        var response = managementServicesService.updateManagementServicesProvider(clientIdConverter.convertId(serviceProviderId));
        return ResponseEntity.ok(managementServicesConfigurationMapper.toTarget(response));
    }

    @Override
    @PreAuthorize("hasAuthority('REGISTER_SERVICE_PROVIDER')")
    @AuditEventMethod(event = REGISTER_MANAGEMENT_SERVICES_PROVIDER)
    public ResponseEntity<ManagementServicesConfigurationDto> registerServiceProvider(RegisterServiceProviderRequestDto request) {
        final SecurityServerId securityServerId = securityServerIdConverter.convertId(request.getSecurityServerId());
        var response = managementServicesService.registerManagementServicesSecurityServer(securityServerId);
        return ResponseEntity.ok(managementServicesConfigurationMapper.toTarget(response));
    }
}
