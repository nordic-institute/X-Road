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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.dto.ServiceClient;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientIdentifierDto;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.serverconf.impl.entity.AccessRightEntity;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.niis.xroad.serverconf.impl.mapper.AccessRightMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ServiceClientService {

    private final ClientRepository clientRepository;
    private final ServiceService serviceService;
    private final EndpointService endpointService;
    private final AccessRightService accessRightService;
    private final LocalGroupService localGroupService;
    private final ServiceDescriptionService serviceDescriptionService;

    /**
     * Get access right holders (serviceClients) by Client (service owner)
     *
     * The concept of base endpoint is used to find service level access rights in this method.
     * Base endpoint is in other words service (code) level endpoint.
     * Each service has one base endpoint.
     * Base endpoint has method '*' and path '**'.
     *
     * @param ownerId
     * @return
     * @throws ClientNotFoundException if owner was not found
     *
     */
    public List<ServiceClient> getServiceClientsByClient(ClientId ownerId)
            throws ClientNotFoundException {

        ClientEntity owner = clientRepository.getClient(ownerId);
        if (owner == null) {
            throw new ClientNotFoundException("Client " + ownerId.toShortString() + " not found");
        }

        // Filter just acls that are set to base endpoints so they are on service code level
        List<AccessRightEntity> serviceCodeLevelAcls = owner.getAccessRights().stream()
                .filter(acl -> acl.getEndpoint().isBaseEndpoint())
                .collect(Collectors.toList());
        List<AccessRightEntity> distinctAccessRights = distinctAccessRightEntityByXRoadId(serviceCodeLevelAcls);
        return accessRightService.mapAccessRightsToServiceClients(owner, distinctAccessRights);
    }

    // Get unique AccessRightEntity from the given list
    private List<AccessRightEntity> distinctAccessRightEntityByXRoadId(List<AccessRightEntity> acls) {
        HashMap<XRoadId, AccessRightEntity> uniqueServiceClientMap = new HashMap<>();
        for (AccessRightEntity acl : acls) {
            if (!uniqueServiceClientMap.containsKey(acl.getSubjectId())) {
                uniqueServiceClientMap.put(acl.getSubjectId(), acl);
            } else {
                // if there are multiple access right with equal subjectId populate the hashmap
                // with the one with earliest timestamp in rights_given_at
                AccessRightEntity accessRightEntity = uniqueServiceClientMap.get(acl.getSubjectId());
                if (acl.getRightsGiven().before(accessRightEntity.getRightsGiven())) {
                    uniqueServiceClientMap.put(acl.getSubjectId(), acl);
                }
            }
        }
        return new ArrayList<>(uniqueServiceClientMap.values());
    }

    /**
     * Get single service client. Service client may be "obsolete", e.g. global group
     * that has been deleted from global configuration, but still exists in global configuration
     *
     * @param ownerId
     * @param serviceClientId
     * @return
     * @throws ClientNotFoundException if given client is not found
     * @throws ServiceClientNotFoundException if given client or service client being searched is not found
     */
    public ServiceClient getServiceClient(ClientId ownerId, XRoadId serviceClientId)
            throws ClientNotFoundException, ServiceClientNotFoundException {

        List<ServiceClient> serviceClientsByClient = null;
        serviceClientsByClient = getServiceClientsByClient(ownerId);

        return serviceClientsByClient.stream()
                .filter(scDto -> scDto.getSubjectId().equals(serviceClientId))
                .findFirst()
                .orElseThrow(() -> new ServiceClientNotFoundException("Service client not found for client id: "
                        + ownerId.toShortString() + " and service client: " + serviceClientId.toShortString()));
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
    public List<ServiceClient> getServiceClientsByService(ClientId clientId, String fullServiceCode)
            throws ClientNotFoundException, ServiceNotFoundException, EndpointNotFoundException {
        ClientEntity clientEntity = clientRepository.getClient(clientId);
        if (clientEntity == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceEntity serviceEntity = serviceService.getServiceEntityFromClient(clientEntity, fullServiceCode);
        EndpointEntity endpointEntity = endpointService.getServiceBaseEndpointEntity(serviceEntity);

        List<AccessRightEntity> accessRightsByEndpoint = accessRightService.getAccessRightsByEndpoint(clientEntity, endpointEntity);
        return accessRightService.mapAccessRightsToServiceClients(clientEntity, accessRightsByEndpoint);
    }

    /**
     * Get access right holders (serviceClients) for Endpoint
     * @param id
     * @return
     * @throws EndpointNotFoundException    if no endpoint is found with given id
     * @throws ClientNotFoundException      if client attached to endpoint is not found
     */
    public List<ServiceClient> getServiceClientsByEndpoint(Long id)
            throws EndpointNotFoundException, ClientNotFoundException {

        ClientEntity clientEntity = clientRepository.getClientByEndpointId(id);
        EndpointEntity endpointEntity = endpointService.getEndpointEntity(id);

        List<AccessRightEntity> accessRightsByEndpoint = accessRightService.getAccessRightsByEndpoint(clientEntity, endpointEntity);
        return accessRightService.mapAccessRightsToServiceClients(clientEntity, accessRightsByEndpoint);
    }

    /**
     * Get service clients access rights to given client
     *
     * @param ownerId
     * @param serviceClientId
     * @return
     * @throws ClientNotFoundException if given client or service client is not found
     * @throws ServiceClientNotFoundException if given service client being searched is not found
     */
    public List<ServiceClientAccessRightDto> getServiceClientAccessRights(
            ClientId ownerId,
            XRoadId serviceClientId) throws ClientNotFoundException, ServiceClientNotFoundException {

        ClientEntity owner = clientRepository.getClient(ownerId);
        if (owner == null) {
            throw new ClientNotFoundException("Client not found with id: " + ownerId.toShortString());
        }

        // verify that service client exists
        getServiceClient(ownerId, serviceClientId);

        // Filter service clients access rights from the given clients acl-list
        return AccessRightMapper.get().toTargets(owner.getAccessRights()).stream()
                .filter(acl -> acl.getEndpoint().isBaseEndpoint() && acl.getSubjectId().equals(serviceClientId))
                .map(acl -> ServiceClientAccessRightDto.builder()
                        .serviceCode(acl.getEndpoint().getServiceCode())
                        .rightsGiven(FormatUtils.fromDateToOffsetDateTime(acl.getRightsGiven()))
                        .title(serviceDescriptionService.getServiceTitle(owner, acl.getEndpoint().getServiceCode()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Convert ServiceClientIdentifierDto to XRoadId
     *
     * @param dto
     * @return
     * @throws ServiceClientNotFoundException if given dto contains local group that is not found
     */
    public XRoadId.Conf convertServiceClientIdentifierDtoToXroadId(ServiceClientIdentifierDto dto)
            throws ServiceClientNotFoundException {

        if (dto.getXRoadId() == null && dto.getLocalGroupId() == null) {
            // should never happen, as long as dto is from ServiceClientIdentifierConverter
            throw new IllegalArgumentException();
        }

        // Get XRoadId for the given service client
        XRoadId.Conf xRoadId = dto.getXRoadId();
        if (dto.isLocalGroup()) {
            try {
                xRoadId = localGroupService.getLocalGroupIdAsXroadId(dto.getLocalGroupId());
            } catch (LocalGroupNotFoundException e) {
                throw new ServiceClientNotFoundException(e);
            }
        }

        return xRoadId;
    }

}
