/*
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.controller.ServiceClientHelper;
import org.niis.xroad.securityserver.restapi.converter.EndpointConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientConverter;
import org.niis.xroad.securityserver.restapi.dto.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientsDto;
import org.niis.xroad.securityserver.restapi.service.AccessRightService;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
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

    @Override
    @PreAuthorize("hasAuthority('VIEW_ENDPOINT')")
    public ResponseEntity<EndpointDto> getEndpoint(String id) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        EndpointDto endpointDto = endpointConverter.convert(endpointService.getEndpoint(endpointId));
        return new ResponseEntity<>(endpointDto, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_ENDPOINT')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_REST_ENDPOINT)
    public ResponseEntity<Void> deleteEndpoint(String id) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            endpointService.deleteEndpoint(endpointId);
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_OPENAPI3_ENDPOINT')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_REST_ENDPOINT)
    public ResponseEntity<EndpointDto> updateEndpoint(String id, EndpointUpdateDto endpointUpdateDto) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        EndpointDto endpointDto;
        try {
            String method = endpointUpdateDto.getMethod().toString();
            endpointDto = endpointConverter.convert(endpointService.updateEndpoint(endpointId,
                    method, endpointUpdateDto.getPath()));
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        }

        return new ResponseEntity<>(endpointDto, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_ENDPOINT_ACL')")
    public ResponseEntity<Set<ServiceClientDto>> getEndpointServiceClients(String id) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        List<ServiceClient> serviceClientsByEndpoint;
        try {
            serviceClientsByEndpoint = serviceClientService.getServiceClientsByEndpoint(endpointId);
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        }
        Set<ServiceClientDto> serviceClientDtos = serviceClientConverter
                .convertServiceClientDtos(serviceClientsByEndpoint);
        return new ResponseEntity<>(serviceClientDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ENDPOINT_ACL')")
    public ResponseEntity<Set<ServiceClientDto>> addEndpointServiceClients(String id, ServiceClientsDto serviceClientDtos) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        List<ServiceClient> serviceClientsByEndpoint = null;

        try {
            Set<XRoadId.Conf> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClientDtos);
            serviceClientsByEndpoint = accessRightService.addEndpointAccessRights(endpointId,
                    new HashSet<>(xRoadIds));
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        } catch (ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        }

        Set<ServiceClientDto> resultServiceClientDtos = serviceClientConverter
                .convertServiceClientDtos(serviceClientsByEndpoint);
        return new ResponseEntity<>(resultServiceClientDtos, HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ENDPOINT_ACL')")
    public ResponseEntity<Void> deleteEndpointServiceClients(String id, ServiceClientsDto serviceClientDtos) {
        Long endpointId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            Set<XRoadId.Conf> xRoadIds = serviceClientHelper.processServiceClientXRoadIds(serviceClientDtos);
            accessRightService.deleteEndpointAccessRights(endpointId, xRoadIds);
        } catch (ClientNotFoundException e) {
            throw new ConflictException(e);
        } catch (ServiceClientNotFoundException e) {
            throw new BadRequestException(e);
        }

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
