/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.ServiceClientConverter;
import org.niis.xroad.restapi.converter.ServiceConverter;
import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * services api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class ServicesApiController implements ServicesApi {

    private final ServiceConverter serviceConverter;
    private final ServiceClientConverter serviceClientConverter;
    private final ServiceService serviceService;

    @Autowired
    public ServicesApiController(ServiceConverter serviceConverter, ServiceClientConverter serviceClientConverter,
            ServiceService serviceService) {
        this.serviceConverter = serviceConverter;
        this.serviceClientConverter = serviceClientConverter;
        this.serviceService = serviceService;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<Service> getService(String id) {
        ServiceType serviceType = getServiceType(id);
        ClientId clientId = serviceConverter.parseClientId(id);
        Service service = serviceConverter.convert(serviceType, clientId);
        return new ResponseEntity<>(service, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_SERVICE_PARAMS')")
    public ResponseEntity<Service> updateService(String id, ServiceUpdate serviceUpdate) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        Service service = serviceUpdate.getService();
        Service updatedService = serviceConverter.convert(
                serviceService.updateService(clientId, fullServiceCode, service.getUrl(), serviceUpdate.getUrlAll(),
                        service.getTimeout(), serviceUpdate.getTimeoutAll(),
                        Boolean.TRUE.equals(service.getSslAuth()), serviceUpdate.getSslAuthAll()),
                clientId);
        return new ResponseEntity<>(updatedService, HttpStatus.OK);
    }

    private ServiceType getServiceType(String id) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        return serviceService.getService(clientId, fullServiceCode);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SERVICE_ACL')")
    public ResponseEntity<List<ServiceClient>> getServiceAccessRights(String encodedServiceId) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        List<AccessRightHolderDto> accessRightHolderDtos =
                serviceService.getAccessRightHoldersByService(clientId, fullServiceCode);
        List<ServiceClient> serviceClients = serviceClientConverter.convertAccessRightHolderDtos(accessRightHolderDtos);
        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }
}
