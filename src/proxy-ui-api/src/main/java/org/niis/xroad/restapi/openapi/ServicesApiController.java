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

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.converter.GroupConverter;
import org.niis.xroad.restapi.converter.ServiceConverter;
import org.niis.xroad.restapi.exceptions.Error;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.service.ClientService;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * services api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class ServicesApiController implements ServicesApi {

    private final ClientConverter clientConverter;
    private final ClientService clientService;
    private final GlobalConfService globalConfService;
    private final GroupConverter groupConverter;
    private final ServiceConverter serviceConverter;
    private final ServiceService serviceService;

    @Autowired
    public ServicesApiController(ClientConverter clientConverter, ClientService clientService,
            GlobalConfService globalConfService, GroupConverter groupConverter, ServiceConverter serviceConverter,
            ServiceService serviceService) {
        this.clientConverter = clientConverter;
        this.clientService = clientService;
        this.globalConfService = globalConfService;
        this.groupConverter = groupConverter;
        this.serviceConverter = serviceConverter;
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
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<List<ServiceClient>> getServiceAccessRights(String id) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        ClientType clientType = clientService.getClient(clientId);
        if (clientType == null) {
            throw new NotFoundException("Client " + clientId.toShortString() + " not found",
                    new Error(ClientService.CLIENT_NOT_FOUND_ERROR_CODE));
        }
        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);

        List<ServiceClient> serviceClients = new ArrayList<>();
        Map<String, String> localGroupDescMap = new HashMap<>();

        clientType.getLocalGroup().forEach(localGroupType -> localGroupDescMap.put(localGroupType.getGroupCode(),
                localGroupType.getDescription()));

        clientType.getAcl()
                .forEach(accessRightType -> {
                    if (accessRightType.getEndpoint().getServiceCode().equals(serviceType.getServiceCode())) {
                        ServiceClient serviceClient = new ServiceClient();
                        XRoadId subjectId = accessRightType.getSubjectId();

                        switch (subjectId.getObjectType()) {
                            case MEMBER:
                            case SUBSYSTEM:
                                ClientId serviceClientId = (ClientId) subjectId;
                                serviceClient.setName(globalConfService.getMemberName(serviceClientId));
                                serviceClient.setId(clientConverter.convertId(serviceClientId, true));
                                break;
                            case GLOBALGROUP:
                                GlobalGroupId globalGroupId = (GlobalGroupId) subjectId;
                                serviceClient.setName(globalConfService.getGlobalGroupDescription(
                                        globalGroupId));
                                serviceClient.setId(groupConverter.convertId(globalGroupId, true));
                                break;
                            case LOCALGROUP:
                                LocalGroupId localGroupId = (LocalGroupId) subjectId;
                                serviceClient.setName(localGroupDescMap.get(localGroupId.getGroupCode()));
                                serviceClient.setId(groupConverter.convertId(localGroupId, true));
                                break;
                            default:
                                break;
                        }

                        serviceClients.add(serviceClient);
                    }
                });

        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }
}
