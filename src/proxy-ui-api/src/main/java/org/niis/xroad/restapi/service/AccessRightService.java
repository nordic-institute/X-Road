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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.restapi.dto.ServiceClientDto;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Service class for handling access rights.
 * This service has several methods that return "access rights holders".
 * This is a synonym for "service clients", and those methods return ServiceClientDtos
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class AccessRightService {

    private final GlobalConfFacade globalConfFacade;
    private final ClientRepository clientRepository;
    private final ServiceService serviceService;
    private final IdentifierService identifierService;
    private final GlobalConfService globalConfService;
    private final EndpointService endpointService;
    private final LocalGroupService localGroupService;
    private final ServiceDescriptionService serviceDescriptionService;

    @Autowired
    public AccessRightService(GlobalConfFacade globalConfFacade,
            ClientRepository clientRepository, ServiceService serviceService, IdentifierService identifierService,
            GlobalConfService globalConfService,
            EndpointService endpointService,
            LocalGroupService localGroupService,
            ServiceDescriptionService serviceDescriptionService) {
        this.globalConfFacade = globalConfFacade;
        this.clientRepository = clientRepository;
        this.serviceService = serviceService;
        this.identifierService = identifierService;
        this.globalConfService = globalConfService;
        this.endpointService = endpointService;
        this.localGroupService = localGroupService;
        this.serviceDescriptionService = serviceDescriptionService;
    }

    /**
     * Remove AccessRights from a Service
     *
     * Does not really need full service code, and versionless service code would be more logical parameter.
     * But controller cannot currently extract version code from full service code, since we use dot as a separator.
     *
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @throws AccessRightNotFoundException if tried to remove access rights that did not exist for the service
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode, or the base endpoint for it,
     * was not found
     * @throws EndpointNotFoundException if the base endpoint for the service is not found
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     */
    public void deleteSoapServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds)
            throws ClientNotFoundException, AccessRightNotFoundException,
            ServiceNotFoundException, IdentifierNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        EndpointType endpointType = getBaseEndpointType(fullServiceCode, clientType);

        deleteEndpointAccessRights(clientType, endpointType, subjectIds);
    }

    /**
     * Get base endpoint for given client and full service code
     * @throws ServiceNotFoundException if no match was found
     */
    private EndpointType getBaseEndpointType(String fullServiceCode, ClientType clientType)
            throws ServiceNotFoundException {
        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);
        try {
            return endpointService.getServiceBaseEndpoint(serviceType);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }
    }

    /**
     * Remove access rights from endpoint
     *
     * @param endpointId
     * @param subjectIds
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     * @throws EndpointNotFoundException if endpoint by given id is not found
     * @throws ClientNotFoundException if client attached to endpoint is not found
     * @throws AccessRightNotFoundException if at least one access right expected is not found
     */
    public void deleteEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds)
            throws EndpointNotFoundException,
            ClientNotFoundException, AccessRightNotFoundException, IdentifierNotFoundException {

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        EndpointType endpointType = endpointService.getEndpoint(endpointId);
        deleteEndpointAccessRights(clientType, endpointType, subjectIds);
    }

    /**
     * Remove access rights from endpoint
     *
     * @param clientType
     * @param endpointType
     * @param subjectIds
     * @throws AccessRightNotFoundException if access right is not found
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     */
    private void deleteEndpointAccessRights(ClientType clientType, EndpointType endpointType, Set<XRoadId> subjectIds)
            throws AccessRightNotFoundException, IdentifierNotFoundException {

        // verify that all subject ids exist
        identifierService.verifyServiceClientIdentifiersExist(clientType, subjectIds);

        // Get all ids from serverconf db IDENTIFIER table - or add them if they don't exist
        Set<XRoadId> managedIds = identifierService.getOrPersistXroadIds(subjectIds);

        deleteEndpointAccessRights(clientType, Collections.singletonList(endpointType), subjectIds);
    }

    /**
     * delete access rights of multiple subjectIds from one endpoint, or multiple endpoints for one subject,
     * or multiple-for-multiple.
     *
     * Deleting access rights to multiple endpoints from multiple
     * subjects will probably not be used, but if it is, all endpoints have to exist for all subjects, otherwise
     * exception is thrown.
     *
     * @param clientType
     * @param endpointTypes
     * @param subjectIds
     * @throws AccessRightNotFoundException if subject did not have access right that was attempted to be deleted
     */
    private void deleteEndpointAccessRights(ClientType clientType, List<EndpointType> endpointTypes,
            Set<XRoadId> subjectIds) throws AccessRightNotFoundException {

        for (EndpointType endpointType: endpointTypes) {
            // check that all access rights exist and can be deleted
            List<AccessRightType> accessRightsToBeRemoved = clientType.getAcl().stream()
                    .filter(acl -> acl.getEndpoint().getId().equals(endpointType.getId())
                            && subjectIds.contains(acl.getSubjectId()))
                    .collect(Collectors.toList());
            if (accessRightsToBeRemoved.size() != subjectIds.size()) {
                throw new AccessRightNotFoundException("All local service client identifiers + "
                        + subjectIds.toString()
                        + " weren't found in the access rights list of the given client: "
                        + clientType.getIdentifier());
            }
            clientType.getAcl().removeAll(accessRightsToBeRemoved);
        }
    }

    /**
     * Adds access rights to SOAP services. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     *
     * Does not really need full service code, and versionless service code would be more logical parameter.
     * But controller cannot currently extract version code from full service code, since we use dot as a separator.
     *
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @return List of {@link ServiceClientDto AccessRightHolderDtos}
     * @throws ClientNotFoundException exception
     * @throws ServiceNotFoundException if service with given fullServicecode, or the base endpoint for it,
     * was not found
     * @throws DuplicateAccessRightException exception
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     */
    public List<ServiceClientDto> addSoapServiceAccessRights(ClientId clientId, String fullServiceCode,
            Set<XRoadId> subjectIds) throws ClientNotFoundException,
            ServiceNotFoundException, DuplicateAccessRightException,
            IdentifierNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        EndpointType endpointType = getBaseEndpointType(fullServiceCode, clientType);

        // Combine subject ids and localgroup ids to a single list of XRoadIds
        return addEndpointAccessRights(clientType, endpointType, subjectIds);
    }

    /**
     * Adds access rights to endpoint. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     *
     * @param endpointId
     * @param subjectIds
     * @return
     * @throws EndpointNotFoundException endpoint is not found with given id
     * @throws ClientNotFoundException client for the endpoint is not found (shouldn't happen)
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     * @throws DuplicateAccessRightException Trying to add duplicate access rights
     */
    public List<ServiceClientDto> addEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds)
            throws EndpointNotFoundException, ClientNotFoundException,
            IdentifierNotFoundException, DuplicateAccessRightException {

        EndpointType endpointType = endpointService.getEndpoint(endpointId);

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        return addEndpointAccessRights(clientType, endpointType, subjectIds);

    }

    /**
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     * @throws DuplicateAccessRightException
     */
    private List<ServiceClientDto> addEndpointAccessRights(ClientType clientType, EndpointType endpointType,
            Set<XRoadId> subjectIds) throws IdentifierNotFoundException,
            DuplicateAccessRightException {

        // verify that all subject ids exist
        identifierService.verifyServiceClientIdentifiersExist(clientType, subjectIds);

        // Get all ids from serverconf db IDENTIFIER table - or add them if they don't exist
        Set<XRoadId> managedIds = identifierService.getOrPersistXroadIds(subjectIds);

        // Add access rights to endpoint
        try {
            addAccessRights(managedIds, clientType, endpointType);
        } catch (LocalGroupNotFoundException e) {
            throw new IdentifierNotFoundException(e);
        }

        // Create DTOs for returning data
        List<AccessRightType> accessRightsByEndpoint = getAccessRightsByEndpoint(clientType, endpointType);
        return mapAccessRightsToServiceClients(clientType, accessRightsByEndpoint);
    }

    /**
     * Add access rights for one subject (service client) to multiple services (serviceCodes)
     * of a client (clientType). Access rights are added only to the base endpoint of given service.
     *
     * @param clientId id of the client who owns the services
     * @param serviceCodes serviceCodes of the services to add access rights to (without version numbers)
     * @param subjectId subject (service client) to add access rights for. Can be a local group,
     *                  global group, or a subsystem
     * @return ServiceClientAccessRightDtos that were added for this service client
     * @throws ServiceNotFoundException if serviceCodes had any codes that were not client's services
     * (did not have base endpoints)
     * @throws ClientNotFoundException if client matching clientId was not found
     * @throws DuplicateAccessRightException if trying to add existing access right
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     */
    public List<ServiceClientAccessRightDto> addServiceClientAccessRights(ClientId clientId, Set<String> serviceCodes,
            XRoadId subjectId) throws ServiceNotFoundException,
            DuplicateAccessRightException, ClientNotFoundException, IdentifierNotFoundException {

        ClientType clientType = validateServiceClientAccessRightsParameters(clientId, subjectId);

        // prepare params for addAccessRightsInternal
        List<EndpointType> baseEndpoints = null;
        try {
            baseEndpoints = endpointService.getServiceBaseEndpoints(clientType, serviceCodes);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }

        // make sure subject id exists in serverconf db IDENTIFIER table, and use a managed entity
        XRoadId managedSubjectId = identifierService.getOrPersistXroadId(subjectId);

        try {
            return addAccessRightsInternal(new HashSet<>(Arrays.asList(managedSubjectId)), clientType, baseEndpoints)
                    .get(managedSubjectId);
        } catch (LocalGroupNotFoundException e) {
            // no need to handle this in more detail than the other service client types
            throw new IdentifierNotFoundException(e);
        }
    }

    /**
     * Removes access rights from one subject (service client) to multiple services (serviceCodes)
     * of a client. Access rights are removed from base endpoint and also from non-base endpoints with
     * given serviceCode.
     *
     * @param clientId id of the client who owns the services
     * @param serviceCodes serviceCodes of the services to remove access rights to (without version numbers)
     * @param subjectId subject (service client) to remove access rights from. Can be a local group,
     *                  global group, or a subsystem
     * @throws AccessRightNotFoundException if trying to remove (any) access rights that did not exist
     * @throws ClientNotFoundException if client matching clientId was not found
     * @throws IdentifierNotFoundException if a service client (local group, global group, or system) matching given
     * subjectId did not exist
     * @throws ServiceNotFoundException if given client did not have services with given serviceCodes
     */
    public void deleteServiceClientAccessRights(ClientId clientId,
            Set<String> serviceCodes, XRoadId subjectId) throws AccessRightNotFoundException, ClientNotFoundException,
            IdentifierNotFoundException, ServiceNotFoundException {

        ClientType clientType = validateServiceClientAccessRightsParameters(clientId, subjectId);

        // first delete base endpoint access rights. These all need to exist, otherwise AccessRightNotFoundException
        List<EndpointType> baseEndpoints = null;
        try {
            baseEndpoints = endpointService.getServiceBaseEndpoints(clientType, serviceCodes);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }
        Set<XRoadId> subjectIds = new HashSet<>(Arrays.asList((subjectId)));
        deleteEndpointAccessRights(clientType, baseEndpoints, subjectIds);

        // then delete all non-base endpoint access rights, for this subject. If there's none, that's fine
        List<EndpointType> allEndpoints = endpointService.getServiceEndpoints(clientType, serviceCodes);
        List<AccessRightType> remainingAccessRights = getEndpointAccessRights(clientType, allEndpoints, subjectIds);
        if (!remainingAccessRights.isEmpty()) {
            Set<EndpointType> endpointsWithAccessRights = remainingAccessRights.stream()
                    .map(a -> a.getEndpoint())
                    .collect(Collectors.toSet());
            deleteEndpointAccessRights(clientType, new ArrayList<>(endpointsWithAccessRights), subjectIds);
        }
    }

    /**
     * Get client's acl entries that match endpointTypes and subjects
     */
    private List<AccessRightType> getEndpointAccessRights(ClientType clientType, List<EndpointType> endpointTypes,
            Set<XRoadId> subjectIds) {

        List<Long> endpointIds = endpointTypes.stream().map(e -> e.getId()).collect(Collectors.toList());
        List<AccessRightType> accessRightsToBeRemoved = clientType.getAcl().stream()
                .filter(acl -> endpointIds.contains(acl.getEndpoint().getId())
                        && subjectIds.contains(acl.getSubjectId()))
                .collect(Collectors.toList());
        return accessRightsToBeRemoved;
    }

    private ClientType validateServiceClientAccessRightsParameters(ClientId clientId, XRoadId subjectId)
            throws ClientNotFoundException, IdentifierNotFoundException {
        // validate params some
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }
        if (subjectId == null) {
            throw new IllegalArgumentException("missing subjectId");
        }
        XRoadObjectType objectType = subjectId.getObjectType();
        if (!isValidServiceClientType(objectType)) {
            throw new IllegalArgumentException("Invalid object type " + objectType);
        }

        // verify that given subjectId exists
        identifierService.verifyServiceClientIdentifiersExist(clientType, new HashSet(Arrays.asList(subjectId)));

        return clientType;
    }


    private boolean isValidServiceClientType(XRoadObjectType objectType) {
        return objectType == XRoadObjectType.SUBSYSTEM
                || objectType == XRoadObjectType.GLOBALGROUP
                || objectType == XRoadObjectType.LOCALGROUP;
    }

    /**
     * Get access right holders (serviceClients) for endpoint
     *
     * @param clientType
     * @param accessRightTypes
     * @return
     */
    List<ServiceClientDto> mapAccessRightsToServiceClients(ClientType clientType,
            List<AccessRightType> accessRightTypes) {
        Map<String, LocalGroupType> localGroupMap = new HashMap<>();
        clientType.getLocalGroup().forEach(localGroupType -> localGroupMap.put(localGroupType.getGroupCode(),
                localGroupType));

        return accessRightTypes.stream()
                .map((accessRightType -> accessRightTypeToServiceClientDto(accessRightType, localGroupMap)))
                .collect(Collectors.toList());
    }

    /**
     * Makes an {@link ServiceClientDto} out of {@link AccessRightType}
     * @param accessRightType The AccessRightType to convert from
     * @param localGroupMap A Map containing {@link LocalGroupType LocalGroupTypes} mapped by
     * their corresponding {@link LocalGroupType#groupCode}
     * @return
     */
    private ServiceClientDto accessRightTypeToServiceClientDto(AccessRightType accessRightType,
            Map<String, LocalGroupType> localGroupMap) {
        ServiceClientDto serviceClientDto = new ServiceClientDto();
        XRoadId subjectId = accessRightType.getSubjectId();
        serviceClientDto.setRightsGiven(
                FormatUtils.fromDateToOffsetDateTime(accessRightType.getRightsGiven()));
        serviceClientDto.setSubjectId(subjectId);
        if (subjectId.getObjectType() == XRoadObjectType.LOCALGROUP) {
            LocalGroupId localGroupId = (LocalGroupId) subjectId;
            LocalGroupType localGroupType = localGroupMap.get(localGroupId.getGroupCode());
            serviceClientDto.setLocalGroupId(localGroupType.getId().toString());
            serviceClientDto.setLocalGroupCode(localGroupType.getGroupCode());
            serviceClientDto.setLocalGroupDescription(localGroupType.getDescription());
        }
        return serviceClientDto;
    }

    /**
     * Get access rights of an endpoint
     *
     * @param clientType
     * @param endpointType
     * @return
     */
    public List<AccessRightType> getAccessRightsByEndpoint(ClientType clientType, EndpointType endpointType) {
        return clientType.getAcl().stream()
                .filter(accessRightType -> accessRightType.getEndpoint().getId().equals(endpointType.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Add access rights for (possibly) multiple subjects, to a given endpoint
     *
     * @param subjectIds access rights subjects to grant access for, "service clients"
     * @param clientType endpoint owner
     * @param endpoint endpoint to add access rights to
     * @return map, key = subjectId (service client), value = list of access rights added for the subject
     * @throws DuplicateAccessRightException if trying to add existing access right
     */
    private Map<XRoadId, List<ServiceClientAccessRightDto>> addAccessRights(Set<XRoadId> subjectIds,
            ClientType clientType, EndpointType endpoint)
            throws DuplicateAccessRightException, LocalGroupNotFoundException {
        List<EndpointType> endpoints = Collections.singletonList(endpoint);
        return addAccessRightsInternal(subjectIds, clientType, endpoints);
    }

    /**
     * Add access rights for (possibly) multiple subjects, to (possibly) multiple endpoints.
     *
     * This method is not intended for use from outside, but is package protected for tests.
     *
     * @param subjectIds access rights subjects to grant access for, "service clients"
     * @param clientType endpoint owner
     * @param endpoints endpoints to add access rights to
     * @return map, key = subjectId (service client), value = list of access rights added for the subject
     * @throws DuplicateAccessRightException if trying to add existing access right
     */
    Map<XRoadId, List<ServiceClientAccessRightDto>> addAccessRightsInternal(Set<XRoadId> subjectIds,
            ClientType clientType, List<EndpointType> endpoints)
            throws DuplicateAccessRightException, LocalGroupNotFoundException {
        Date now = new Date();

        if (subjectIds == null || subjectIds.isEmpty()) {
            throw new IllegalArgumentException("missing subjectIds");
        }
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("missing endpoints");
        }

        Map<XRoadId, List<ServiceClientAccessRightDto>> addedAccessRights = new HashMap<>();

        for (EndpointType endpoint: endpoints) {
            for (XRoadId subjectId : subjectIds) {
                ServiceClientAccessRightDto dto = addAccessRightInternal(clientType, now, endpoint, subjectId);
                List<ServiceClientAccessRightDto> addedAccessRightsForSubject = addedAccessRights
                        .computeIfAbsent(subjectId, k -> new ArrayList<>());
                addedAccessRightsForSubject.add(dto);
            }
        }

        clientRepository.saveOrUpdate(clientType);
        return addedAccessRights;
    }

    /**
     * Add access right for a single subject (subjectId), to a single endpoint (endpoint) that belongs to clientType
     * @param clientType
     * @param rightsGiven
     * @param endpoint
     * @param subjectId
     * @return
     * @throws LocalGroupNotFoundException if local group does not exist for given client
     * @throws DuplicateAccessRightException if access righ already exists
     */
    private ServiceClientAccessRightDto addAccessRightInternal(ClientType clientType, Date rightsGiven,
            EndpointType endpoint, XRoadId subjectId)
            throws LocalGroupNotFoundException, DuplicateAccessRightException {

        // A LocalGroup must belong to this client
        List<LocalGroupType> clientLocalGroups = clientType.getLocalGroup();

        if (subjectId.getObjectType() == XRoadObjectType.LOCALGROUP) {
            LocalGroupId localGroupId = (LocalGroupId) subjectId;
            boolean localGroupNotFound = clientLocalGroups.stream()
                    .noneMatch(localGroupType -> localGroupType.getGroupCode()
                            .equals(localGroupId.getGroupCode()));
            if (localGroupNotFound) {
                String errorMsg = String.format("LocalGroup with the groupCode %s does not belong to client %s",
                        subjectId.toShortString(), clientType.getIdentifier().toShortString());
                throw new LocalGroupNotFoundException(errorMsg);
            }
        }

        // list endpoints, which this subject / service client has already been granted access to
        Set<EndpointType> existingAccessibleEndpoints = clientType.getAcl().stream()
                .filter(accessRightType -> accessRightType.getSubjectId().equals(subjectId))
                .map(accessRightType -> accessRightType.getEndpoint())
                .collect(Collectors.toSet());

        if (existingAccessibleEndpoints.contains(endpoint)) {
            throw new DuplicateAccessRightException("Subject " + subjectId.toShortString()
                    + " already has an access right for endpoint " + endpoint.getId());
        }

        AccessRightType newAccessRight = new AccessRightType();
        newAccessRight.setEndpoint(endpoint);
        newAccessRight.setSubjectId(subjectId);
        newAccessRight.setRightsGiven(rightsGiven);
        clientType.getAcl().add(newAccessRight);

        // return a dto
        ServiceClientAccessRightDto dto = ServiceClientAccessRightDto.builder()
                .serviceCode(endpoint.getServiceCode())
                .rightsGiven(FormatUtils.fromDateToOffsetDateTime(rightsGiven))
                .title(serviceDescriptionService.getServiceTitle(clientType, endpoint.getServiceCode()))
                .build();
        return dto;
    }

    /**
     * If access right was not found
     */
    public static class AccessRightNotFoundException extends NotFoundException {
        public static final String ERROR_ACCESSRIGHT_NOT_FOUND = "accessright_not_found";

        public AccessRightNotFoundException(String s) {
            super(s, new ErrorDeviation(ERROR_ACCESSRIGHT_NOT_FOUND));
        }

        public AccessRightNotFoundException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_ACCESSRIGHT_NOT_FOUND));
        }

    }

    /**
     * If duplicate access right was found
     */
    public static class DuplicateAccessRightException extends ServiceException {

        public static final String ERROR_DUPLICATE_ACCESSRIGHT = "duplicate_accessright";

        public DuplicateAccessRightException(String msg) {
            super(msg, new ErrorDeviation(ERROR_DUPLICATE_ACCESSRIGHT));
        }

    }

    /**
     * Find access right holder (serviceClient) candidates by search terms
     * @param clientId
     * @param subjectType search term for subjectType. Null or empty value is considered a match
     * @param memberNameOrGroupDescription search term for memberName or groupDescription (depending on subject's type).
     * Null or empty value is considered a match
     * @param instance search term for instance. Null or empty value is considered a match
     * @param memberClass search term for memberClass. Null or empty value is considered a match
     * @param memberGroupCode search term for memberCode or groupCode (depending on subject's type).
     * Null or empty value is considered a match
     * @param subsystemCode search term for subsystemCode. Null or empty value is considered a match
     * @return A List of {@link ServiceClientDto serviceClientDtos} or an empty List if nothing is found
     * @throws ClientNotFoundException if client was not found
     */
    public List<ServiceClientDto> findAccessRightHolderCandidates(ClientId clientId,
            String memberNameOrGroupDescription,
            XRoadObjectType subjectType, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) throws ClientNotFoundException {
        List<ServiceClientDto> dtos = new ArrayList<>();

        // get client
        ClientType client = clientRepository.getClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        // get global members
        List<ServiceClientDto> globalMembers = getGlobalMembersAsDtos();
        if (globalMembers.size() > 0) {
            dtos.addAll(globalMembers);
        }

        // get global groups
        List<ServiceClientDto> globalGroups = getGlobalGroupsAsDtos(instance);
        if (globalMembers.size() > 0) {
            dtos.addAll(globalGroups);
        }

        // get local groups
        List<ServiceClientDto> localGroups = getLocalGroupsAsDtos(client.getLocalGroup());
        if (localGroups.size() > 0) {
            dtos.addAll(localGroups);
        }

        Predicate<ServiceClientDto> matchingSearchTerms = buildSubjectSearchPredicate(subjectType,
                memberNameOrGroupDescription, instance, memberClass, memberGroupCode, subsystemCode);

        return dtos.stream()
                .filter(matchingSearchTerms)
                .collect(Collectors.toList());
    }

    private List<ServiceClientDto> getLocalGroupsAsDtos(List<LocalGroupType> localGroupTypes) {
        return localGroupTypes.stream()
                .map(localGroup -> {
                    ServiceClientDto serviceClientDto = new ServiceClientDto();
                    serviceClientDto.setLocalGroupId(localGroup.getId().toString());
                    serviceClientDto.setLocalGroupCode(localGroup.getGroupCode());
                    serviceClientDto.setSubjectId(LocalGroupId.create(localGroup.getGroupCode()));
                    serviceClientDto.setLocalGroupDescription(localGroup.getDescription());
                    return serviceClientDto;
                }).collect(Collectors.toList());
    }

    private List<ServiceClientDto> getGlobalMembersAsDtos() {
        return globalConfFacade.getMembers().stream()
                .map(memberInfo -> {
                    ServiceClientDto serviceClientDto = new ServiceClientDto();
                    serviceClientDto.setSubjectId(memberInfo.getId());
                    serviceClientDto.setMemberName(memberInfo.getName());
                    return serviceClientDto;
                })
                .collect(Collectors.toList());
    }

    private List<ServiceClientDto> getGlobalGroupsAsDtos(String instance) {
        List<ServiceClientDto> globalGroups = new ArrayList<>();
        List<String> globalGroupInstances = globalConfFacade.getInstanceIdentifiers();
        List<GlobalGroupInfo> globalGroupInfos = null;
        // core throws CodedException if nothing is found for the provided instance/instances
        try {
            if (!StringUtils.isEmpty(instance)) {
                List<String> globalGroupInstancesMatchingSearch = globalGroupInstances.stream()
                        .filter(s -> s.contains(instance))
                        .collect(Collectors.toList());
                if (globalGroupInstancesMatchingSearch.size() > 0) {
                    globalGroupInfos = globalConfFacade
                            .getGlobalGroups(globalGroupInstancesMatchingSearch.toArray(new String[] {}));
                }
            } else {
                globalGroupInfos = globalConfFacade.getGlobalGroups();
            }
        } catch (CodedException e) {
            // no GlobalGroups found for the provided instance -> GlobalGroups are just ignored in the results
        }
        if (globalGroupInfos != null && globalGroupInfos.size() > 0) {
            globalGroupInfos.forEach(globalGroupInfo -> {
                ServiceClientDto serviceClientDto = new ServiceClientDto();
                serviceClientDto.setSubjectId(globalGroupInfo.getId());
                serviceClientDto.setLocalGroupDescription(globalGroupInfo.getDescription());
                globalGroups.add(serviceClientDto);
            });
        }
        return globalGroups;
    }

    /**
     * Composes a {@link Predicate} that will be used to filter {@link ServiceClientDto ServiceClientDtos}
     * against the given search terms. The given ServiceClientDto has a {@link ServiceClientDto#getSubjectId()}
     * which can be of type {@link GlobalGroupId}, {@link LocalGroupId} or {@link ClientId}. When evaluating the
     * Predicate the type of the Subject will be taken in account for example when testing if the search term
     * {@code memberGroupCode} matches
     * @param subjectType search term for subjectType. Null or empty value is considered a match
     * @param memberNameOrGroupDescription search term for memberName or groupDescription (depending on subject's type).
     * Null or empty value is considered a match
     * @param instance search term for instance. Null or empty value is considered a match
     * @param memberClass search term for memberClass. Null or empty value is considered a match
     * @param memberGroupCode search term for memberCode or groupCode (depending on subject's type).
     * Null or empty value is considered a match
     * @param subsystemCode search term for subsystemCode. Null or empty value is considered a match
     * @return Predicate
     */
    private Predicate<ServiceClientDto> buildSubjectSearchPredicate(XRoadObjectType subjectType,
            String memberNameOrGroupDescription, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) {
        // Start by assuming the search is a match. If there are no search terms --> return all
        Predicate<ServiceClientDto> searchPredicate = accessRightHolderDto -> true;

        // Ultimately members cannot have access rights to Services -> no members in the Subject search results.
        searchPredicate = searchPredicate.and(dto -> dto.getSubjectId().getObjectType() != XRoadObjectType.MEMBER);

        // add subject type to condition
        if (subjectType != null) {
            searchPredicate = searchPredicate.and(dto -> dto.getSubjectId().getObjectType() == subjectType);
        }
        // Check if the memberName or LocalGroup's description match with the search term
        if (!StringUtils.isEmpty(memberNameOrGroupDescription)) {
            searchPredicate = searchPredicate.and(getMemberNameOrGroupDescriptionPredicate(
                    memberNameOrGroupDescription));
        }
        // Check if the instance of the subject matches with the search term
        if (!StringUtils.isEmpty(instance)) {
            searchPredicate = searchPredicate.and(getSubjectInstancePredicate(instance));
        }
        // Check if the memberClass of the subject matches with the search term
        if (!StringUtils.isEmpty(memberClass)) {
            searchPredicate = searchPredicate.and(getSubjectMemberClassPredicate(memberClass));
        }
        // Check if the subsystemCode of the subject matches with the search term
        if (!StringUtils.isEmpty(subsystemCode)) {
            searchPredicate = searchPredicate.and(getSubjectSubsystemCodePredicate(subsystemCode));
        }
        // Check if the memberCode or groupCode of the subject matches with the search term
        if (!StringUtils.isEmpty(memberGroupCode)) {
            searchPredicate = searchPredicate.and(getSubjectMemberOrGroupCodePredicate(memberGroupCode));
        }
        return searchPredicate;
    }

    private Predicate<ServiceClientDto> getSubjectMemberOrGroupCodePredicate(String memberGroupCode) {
        return dto -> {
            XRoadId xRoadId = dto.getSubjectId();
            if (xRoadId instanceof ClientId) {
                String clientMemberCode = ((ClientId) xRoadId).getMemberCode();
                return StringUtils.containsIgnoreCase(clientMemberCode, memberGroupCode);
            } else if (xRoadId instanceof GlobalGroupId) {
                String globalGroupCode = ((GlobalGroupId) xRoadId).getGroupCode();
                return StringUtils.containsIgnoreCase(globalGroupCode, memberGroupCode);
            } else if (xRoadId instanceof LocalGroupId) {
                String localGroupCode = ((LocalGroupId) xRoadId).getGroupCode();
                return StringUtils.containsIgnoreCase(localGroupCode, memberGroupCode);
            } else {
                return false;
            }
        };
    }

    private Predicate<ServiceClientDto> getSubjectSubsystemCodePredicate(String subsystemCode) {
        return dto -> {
            XRoadId xRoadId = dto.getSubjectId();
            if (xRoadId instanceof ClientId) {
                String clientSubsystemCode = ((ClientId) xRoadId).getSubsystemCode();
                return StringUtils.containsIgnoreCase(clientSubsystemCode, subsystemCode);
            } else {
                return false;
            }
        };
    }

    private Predicate<ServiceClientDto> getSubjectMemberClassPredicate(String memberClass) {
        return dto -> {
            XRoadId xRoadId = dto.getSubjectId();
            if (xRoadId instanceof ClientId) {
                String clientMemberClass = ((ClientId) xRoadId).getMemberClass();
                return StringUtils.containsIgnoreCase(clientMemberClass, memberClass);
            } else {
                return false;
            }
        };
    }

    private Predicate<ServiceClientDto> getSubjectInstancePredicate(String instance) {
        return dto -> {
            XRoadId xRoadId = dto.getSubjectId();
            // In case the Subject is a LocalGroup: LocalGroups do not have explicit X-Road instances
            // -> always return
            if (xRoadId instanceof LocalGroupId) {
                return true;
            } else {
                return StringUtils.containsIgnoreCase(dto.getSubjectId().getXRoadInstance(), instance);
            }
        };
    }

    private Predicate<ServiceClientDto> getMemberNameOrGroupDescriptionPredicate(String memberNameOrGroupDescription) {
        return dto -> {
            String memberName = dto.getMemberName();
            String localGroupDescription = dto.getLocalGroupDescription();
            boolean isMatch = StringUtils.containsIgnoreCase(memberName, memberNameOrGroupDescription)
                    || StringUtils.containsIgnoreCase(localGroupDescription, memberNameOrGroupDescription);
            return isMatch;
        };
    }
}
