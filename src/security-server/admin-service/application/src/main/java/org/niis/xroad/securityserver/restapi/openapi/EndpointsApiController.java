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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.controller.ServiceClientHelper;
import org.niis.xroad.securityserver.restapi.converter.EndpointConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientIdentifierConverter;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.Endpoint;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClients;
import org.niis.xroad.securityserver.restapi.service.AccessRightService;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.EndpointAlreadyExistsException;
import org.niis.xroad.securityserver.restapi.service.EndpointNotFoundException;
import org.niis.xroad.securityserver.restapi.service.EndpointService;
import org.niis.xroad.securityserver.restapi.service.ServiceClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Endpoints api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class EndpointsApiController implements EndpointsApi {

    private final EndpointService endpointService;
    private final EndpointConverter endpointConverter;
    private final AccessRightService accessRightService;
    private final ServiceClientConverter serviceClientConverter;
    private final ServiceClientHelper serviceClientHelper;
    private final ServiceClientService serviceClientService;

    private static final String NOT_FOUND_ERROR_MSG = "Endpoint not found with id";

    @Override
    @PreAuthorize("hasAuthority('VIEW_ENDPOINT')")
    public ResponseEntity<Endpoint> getEndpoint(String id) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        Endpoint endpoint;
        try {
            endpoint = endpointConverter.convert(endpointService.getEndpoint(endpointId));
        } catch (EndpointNotFoundException e) {
            throw new ResourceNotFoundException(NOT_FOUND_ERROR_MSG + " " + id);
        }
        return new ResponseEntity(endpoint, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_ENDPOINT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_REST_ENDPOINT)
    public ResponseEntity<Void> deleteEndpoint(String id) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            endpointService.deleteEndpoint(endpointId);
        } catch (EndpointNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ClientNotFoundException e) {
            throw new ConflictException("Client not found for the given endpoint with id: " + id);
        } catch (EndpointService.IllegalGeneratedEndpointRemoveException e) {
            throw new BadRequestException("Removing is not allowed for generated endpoint " + id);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_OPENAPI3_ENDPOINT')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_REST_ENDPOINT)
    public ResponseEntity<Endpoint> updateEndpoint(String id, EndpointUpdate endpointUpdate) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        Endpoint ep;
        try {
            String method = endpointUpdate.getMethod() == null ? null : endpointUpdate.getMethod().toString();
            ep = endpointConverter.convert(endpointService.updateEndpoint(endpointId,
                    method, endpointUpdate.getPath()));
        } catch (EndpointNotFoundException e) {
            throw new ResourceNotFoundException(NOT_FOUND_ERROR_MSG + " " + id);
        } catch (EndpointService.IllegalGeneratedEndpointUpdateException e) {
            throw new BadRequestException("Updating is not allowed for generated endpoint " + id);
        } catch (EndpointAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (ClientNotFoundException e) {
            throw new ConflictException("Client not found for the given endpoint with id: " + id);
        }

        return new ResponseEntity<>(ep, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_ENDPOINT_ACL')")
    public ResponseEntity<Set<ServiceClient>> getEndpointServiceClients(String id) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        List<ServiceClientDto> serviceClientsByEndpoint;
        try {
            serviceClientsByEndpoint = serviceClientService.getServiceClientsByEndpoint(endpointId);
        } catch (EndpointNotFoundException e) {
            throw new ResourceNotFoundException(NOT_FOUND_ERROR_MSG + " " + id);
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        }
        Set<ServiceClient> serviceClients = serviceClientConverter
                .convertServiceClientDtos(serviceClientsByEndpoint);
        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ENDPOINT_ACL')")
    public ResponseEntity<Set<ServiceClient>> addEndpointServiceClients(String id, ServiceClients serviceClients) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        List<ServiceClientDto> serviceClientsByEndpoint = null;

        try {
            Set<XRoadId.Conf> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClients);
            serviceClientsByEndpoint = accessRightService.addEndpointAccessRights(endpointId,
                    new HashSet<>(xRoadIds));
        } catch (EndpointNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ClientNotFoundException | AccessRightService.DuplicateAccessRightException  e) {
            throw new ConflictException(e);
        } catch (ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }

        Set<ServiceClient> serviceClientsResult = serviceClientConverter
                .convertServiceClientDtos(serviceClientsByEndpoint);
        return new ResponseEntity<>(serviceClientsResult, HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ENDPOINT_ACL')")
    public ResponseEntity<Void> deleteEndpointServiceClients(String id, ServiceClients serviceClients) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            Set<XRoadId.Conf> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClients);
            accessRightService.deleteEndpointAccessRights(endpointId, xRoadIds);
        } catch (EndpointNotFoundException | AccessRightService.AccessRightNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        } catch (ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}
