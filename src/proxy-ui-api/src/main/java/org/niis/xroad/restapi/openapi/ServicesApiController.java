/**
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.controller.ServiceClientHelper;
import org.niis.xroad.restapi.converter.EndpointConverter;
import org.niis.xroad.restapi.converter.ServiceClientConverter;
import org.niis.xroad.restapi.converter.ServiceClientIdentifierConverter;
import org.niis.xroad.restapi.converter.ServiceConverter;
import org.niis.xroad.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.restapi.dto.ServiceClientDto;
import org.niis.xroad.restapi.openapi.model.Endpoint;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceClients;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.service.AccessRightService;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.EndpointAlreadyExistsException;
import org.niis.xroad.restapi.service.EndpointNotFoundException;
import org.niis.xroad.restapi.service.InvalidHttpsUrlException;
import org.niis.xroad.restapi.service.InvalidUrlException;
import org.niis.xroad.restapi.service.ServiceClientNotFoundException;
import org.niis.xroad.restapi.service.ServiceClientService;
import org.niis.xroad.restapi.service.ServiceDescriptionService;
import org.niis.xroad.restapi.service.ServiceNotFoundException;
import org.niis.xroad.restapi.service.ServiceService;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_REST_ENDPOINT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_SERVICE_ACCESS_RIGHTS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_SERVICE_PARAMS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REMOVE_SERVICE_ACCESS_RIGHTS;

/**
 * services api
 */
@Controller
@RequestMapping(ApiUtil.API_V1_PREFIX)
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
    private final ServiceClientSortingComparator serviceClientSortingComparator;

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
    @AuditEventMethod(event = EDIT_SERVICE_PARAMS)
    public ResponseEntity<Service> updateService(String id, ServiceUpdate serviceUpdate) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        Service updatedService = null;
        boolean ignoreWarnings = serviceUpdate.getIgnoreWarnings();
        try {
            updatedService = serviceConverter.convert(
                    serviceService.updateService(clientId, fullServiceCode,
                            serviceUpdate.getUrl(), serviceUpdate.getUrlAll(),
                            serviceUpdate.getTimeout(), serviceUpdate.getTimeoutAll(),
                            Boolean.TRUE.equals(serviceUpdate.getSslAuth()), serviceUpdate.getSslAuthAll(),
                            ignoreWarnings),
                    clientId);
        } catch (InvalidUrlException | InvalidHttpsUrlException | UnhandledWarningsException e) {
            throw new BadRequestException(e);
        } catch (ClientNotFoundException | ServiceNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(updatedService, HttpStatus.OK);
    }

    private ServiceType getServiceType(String id) {
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
    public ResponseEntity<List<ServiceClient>> getServiceServiceClients(String encodedServiceId) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        List<ServiceClientDto> serviceClientDtos = null;
        try {
            serviceClientDtos = serviceClientService.getServiceClientsByService(clientId, fullServiceCode);
        } catch (ClientNotFoundException | ServiceNotFoundException | EndpointNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        List<ServiceClient> serviceClients = serviceClientConverter.convertServiceClientDtos(serviceClientDtos);
        Collections.sort(serviceClients, serviceClientSortingComparator);
        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('EDIT_SERVICE_ACL')")
    @Override
    @AuditEventMethod(event = REMOVE_SERVICE_ACCESS_RIGHTS)
    public ResponseEntity<Void> deleteServiceServiceClients(String encodedServiceId, ServiceClients serviceClients) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        try {
            Set<XRoadId> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClients);
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
    @AuditEventMethod(event = ADD_SERVICE_ACCESS_RIGHTS)
    public ResponseEntity<List<ServiceClient>> addServiceServiceClients(String encodedServiceId,
            ServiceClients serviceClients) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        List<ServiceClientDto> serviceClientDtos;
        try {
            Set<XRoadId> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClients);
            serviceClientDtos = accessRightService.addSoapServiceAccessRights(clientId, fullServiceCode,
                    xRoadIds);
        } catch (ClientNotFoundException | ServiceNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        } catch (AccessRightService.DuplicateAccessRightException e) {
            throw new ConflictException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        List<ServiceClient> serviceClientsResult = serviceClientConverter.convertServiceClientDtos(
                serviceClientDtos);
        Collections.sort(serviceClientsResult, serviceClientSortingComparator);
        return new ResponseEntity<>(serviceClientsResult, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_OPENAPI3_ENDPOINT')")
    @AuditEventMethod(event = ADD_REST_ENDPOINT)
    public ResponseEntity<Endpoint> addEndpoint(String id, Endpoint endpoint) {
        ServiceType serviceType = getServiceType(id);

        if (endpoint.getId() != null) {
            throw new BadRequestException("Passing id for endpoint while creating it is not allowed");
        }
        Endpoint ep;
        try {
            ep = endpointConverter.convert(serviceService.addEndpoint(serviceType,
                    endpoint.getMethod().toString(), endpoint.getPath()));
        } catch (EndpointAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (ServiceDescriptionService.WrongServiceDescriptionTypeException e) {
            throw new BadRequestException(e);
        }
        return ApiUtil.createCreatedResponse("/api/endpoints/{id}", ep, ep.getId());
    }
}
