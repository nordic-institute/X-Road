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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.controller.ServiceClientHelper;
import org.niis.xroad.securityserver.restapi.converter.EndpointConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientIdentifierConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceConverter;
import org.niis.xroad.securityserver.restapi.dto.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceUpdateDto;
import org.niis.xroad.securityserver.restapi.service.AccessRightService;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.EndpointAlreadyExistsException;
import org.niis.xroad.securityserver.restapi.service.EndpointNotFoundException;
import org.niis.xroad.securityserver.restapi.service.InvalidHttpsUrlException;
import org.niis.xroad.securityserver.restapi.service.InvalidUrlException;
import org.niis.xroad.securityserver.restapi.service.ServiceClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceClientService;
import org.niis.xroad.securityserver.restapi.service.ServiceDescriptionService;
import org.niis.xroad.securityserver.restapi.service.ServiceNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceService;
import org.niis.xroad.serverconf.model.Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * services api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ServicesApiController implements ServicesApi {

    private final ServiceConverter serviceConverter;
    private final ServiceClientConverter serviceClientConverter;
    private final EndpointConverter endpointConverter;
    private final ServiceService serviceService;
    private final AccessRightService accessRightService;
    private final ServiceClientHelper serviceClientHelper;
    private final ServiceClientService serviceClientService;

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<ServiceDto> getService(String id) {
        Service service = getServiceFromDb(id);
        ClientId clientId = serviceConverter.parseClientId(id);
        ServiceDto serviceDto = serviceConverter.convert(service, clientId);
        return new ResponseEntity<>(serviceDto, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_SERVICE_PARAMS')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_SERVICE_PARAMS)
    public ResponseEntity<ServiceDto> updateService(String id, ServiceUpdateDto serviceUpdateDto) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        ServiceDto updatedService = null;
        boolean ignoreWarnings = serviceUpdateDto.getIgnoreWarnings();
        try {
            updatedService = serviceConverter.convert(
                    serviceService.updateService(clientId, fullServiceCode,
                            serviceUpdateDto.getUrl(), serviceUpdateDto.getUrlAll(),
                            serviceUpdateDto.getTimeout(), serviceUpdateDto.getTimeoutAll(),
                            Boolean.TRUE.equals(serviceUpdateDto.getSslAuth()), serviceUpdateDto.getSslAuthAll(),
                            ignoreWarnings),
                    clientId);
        } catch (InvalidUrlException | InvalidHttpsUrlException | UnhandledWarningsException e) {
            throw new BadRequestException(e);
        } catch (ClientNotFoundException | ServiceNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceDescriptionService.UrlAlreadyExistsException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(updatedService, HttpStatus.OK);
    }

    private Service getServiceFromDb(String id) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        try {
            return serviceService.getService(clientId, fullServiceCode);
        } catch (ServiceNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SERVICE_ACL')")
    public ResponseEntity<Set<ServiceClientDto>> getServiceServiceClients(String encodedServiceId) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        List<ServiceClient> serviceClients = null;
        try {
            serviceClients = serviceClientService.getServiceClientsByService(clientId, fullServiceCode);
        } catch (ClientNotFoundException | ServiceNotFoundException | EndpointNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        Set<ServiceClientDto> serviceClientDtos = serviceClientConverter.convertServiceClientDtos(serviceClients);
        return new ResponseEntity<>(serviceClientDtos, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('EDIT_SERVICE_ACL')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.REMOVE_SERVICE_ACCESS_RIGHTS)
    public ResponseEntity<Void> deleteServiceServiceClients(String encodedServiceId, ServiceClientsDto serviceClientsDto) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        try {
            Set<XRoadId.Conf> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClientsDto);
            accessRightService.deleteSoapServiceAccessRights(clientId, fullServiceCode, new HashSet<>(xRoadIds));
        } catch (ServiceNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (AccessRightService.AccessRightNotFoundException | ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PreAuthorize("hasAuthority('EDIT_SERVICE_ACL')")
    @Override
    @AuditEventMethod(event = RestApiAuditEvent.ADD_SERVICE_ACCESS_RIGHTS)
    public ResponseEntity<Set<ServiceClientDto>> addServiceServiceClients(String encodedServiceId,
                                                                       ServiceClientsDto serviceClientsDto) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        List<ServiceClient> serviceClients;
        try {
            Set<XRoadId.Conf> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClientsDto);
            serviceClients = accessRightService.addSoapServiceAccessRights(clientId, fullServiceCode, xRoadIds);
        } catch (ClientNotFoundException | ServiceNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        } catch (AccessRightService.DuplicateAccessRightException e) {
            throw new ConflictException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        Set<ServiceClientDto> serviceClientDtos = serviceClientConverter.convertServiceClientDtos(serviceClients);

        return new ResponseEntity<>(serviceClientDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_OPENAPI3_ENDPOINT')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_REST_ENDPOINT)
    public ResponseEntity<EndpointDto> addEndpoint(String id, EndpointDto endpointDto) {
        if (endpointDto.getId() != null) {
            throw new BadRequestException("Passing id for endpoint while creating it is not allowed");
        }

        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);

        EndpointDto resultEndpointDto;
        try {
            resultEndpointDto = endpointConverter.convert(serviceService.addEndpoint(clientId, fullServiceCode,
                    endpointDto.getMethod().toString(), endpointDto.getPath()));
        } catch (ServiceNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (EndpointAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (ServiceDescriptionService.WrongServiceDescriptionException e) {
            throw new BadRequestException(e);
        }
        return ControllerUtil.createCreatedResponse("/api/endpoints/{id}", resultEndpointDto, resultEndpointDto.getId());
    }
}
