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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.restapi.dto.ServiceClientDto;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.util.FormatUtils.xRoadIdToEncodedId;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ServiceClientService {

    private final ClientRepository clientRepository;
    private final ServiceService serviceService;
    private final EndpointService endpointService;
    private final AccessRightService accessRightService;

    public ServiceClientService(ClientRepository clientRepository, ServiceService serviceService,
            EndpointService endpointService, AccessRightService accessRightService) {
        this.clientRepository = clientRepository;
        this.serviceService = serviceService;
        this.endpointService = endpointService;
        this.accessRightService = accessRightService;
    }

    /**
     * Get access right holders (serviceClients) by Client (service owner)
     *
     * The concept of base endpoint is used to find service level access rights in this method.
     * Base endpoint is in other words service (code) level endpoint.
     * Each service has one base endpoint.
     * Base endpoint has method '*' and path '**'.
     *
     * @param clientId
     * @return
     * @throws ClientNotFoundException
     *
     */
    public List<ServiceClientDto> getServiceClientsByClient(ClientId clientId)
            throws ClientNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        // Filter just acls that are set to base endpoints so they are on service code level
        List<AccessRightType> serviceCodeLevelAcls = clientType.getAcl().stream()
                .filter(acl -> acl.getEndpoint().isBaseEndpoint())
                .collect(Collectors.toList());
        List<AccessRightType>   distinctAccessRightTypes = distinctAccessRightTypeByXroadId(serviceCodeLevelAcls);
        return accessRightService.mapAccessRightsToServiceClients(clientType, distinctAccessRightTypes);
    }

    // Get unique AccessRightTypes from the given list
    private List<AccessRightType> distinctAccessRightTypeByXroadId(List<AccessRightType> acls) {
        HashMap<XRoadId, AccessRightType> uniqueServiceClientMap = new HashMap<>();
        for (AccessRightType acl : acls) {
            if (!uniqueServiceClientMap.containsKey(acl.getSubjectId())) {
                uniqueServiceClientMap.put(acl.getSubjectId(), acl);
            }
        }
        return new ArrayList(uniqueServiceClientMap.values());
    }

    /**
     * Get access right holders (serviceClients) by Service
     * @param clientId
     * @param fullServiceCode
     * @return
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws EndpointNotFoundException if base endpoint for this service is not found from the client
     */
    public List<ServiceClientDto> getServiceClientsByService(ClientId clientId, String fullServiceCode)
            throws ClientNotFoundException, ServiceNotFoundException, EndpointNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);
        EndpointType endpointType = endpointService.getServiceBaseEndpoint(serviceType);

        List<AccessRightType> accessRightsByEndpoint = accessRightService
                .getAccessRightsByEndpoint(clientType, endpointType);
        return accessRightService.mapAccessRightsToServiceClients(clientType, accessRightsByEndpoint);
    }

    /**
     * Get access right holders (serviceClients) for Endpoint
     * @param id
     * @return
     * @throws EndpointNotFoundException    if no endpoint is found with given id
     * @throws ClientNotFoundException      if client attached to endpoint is not found
     */
    public List<ServiceClientDto> getServiceClientsByEndpoint(Long id)
            throws EndpointNotFoundException, ClientNotFoundException {

        ClientType clientType = clientRepository.getClientByEndpointId(id);
        EndpointType endpointType = endpointService.getEndpoint(id);

        List<AccessRightType> accessRightsByEndpoint = accessRightService
                .getAccessRightsByEndpoint(clientType, endpointType);
        return accessRightService.mapAccessRightsToServiceClients(clientType, accessRightsByEndpoint);
    }

    /**
     * Get service clients access rights to given client
     *
     * @param clientid
     * @param serviceClientId
     * @return
     * @throws ClientNotFoundException
     */
    public List<ServiceClientAccessRightDto> getServiceClientAccessRights(ClientId clientid, String serviceClientId)
            throws ClientNotFoundException {
        ClientType clientType = clientRepository.getClient(clientid);
        if (clientType == null) {
            throw new ClientNotFoundException("Client not found with id: " + clientid.toShortString());
        }

        // Filter subjects access rights from the given clients acl-list
        return clientType.getAcl().stream()
                .filter(acl -> {
                    boolean iseq = xRoadIdToEncodedId(acl.getSubjectId()).equals(serviceClientId);
                    boolean isBaseEndpoint = acl.getEndpoint().isBaseEndpoint();
                    return iseq && isBaseEndpoint;
                })
                .map(acl -> ServiceClientAccessRightDto.builder()
                        .id(serviceClientId)
                        .clientId(clientid.toShortString())
                        .serviceCode(acl.getEndpoint().getServiceCode())
                        .rightsGiven(FormatUtils.fromDateToOffsetDateTime(acl.getRightsGiven()))
                        .title(getServiceTitle(clientType, acl.getEndpoint().getServiceCode()))
                        .build())
                .collect(Collectors.toList());
    }

    private String getServiceTitle(ClientType clientType, String serviceCode) {
        ServiceType service = clientType.getServiceDescription().stream()
                .flatMap(sd -> sd.getService().stream())
                .filter(serviceType -> serviceType.getServiceCode().equals(serviceCode))
                .findFirst()
                .get();

        return service == null ? null : service.getTitle();
    }

}
