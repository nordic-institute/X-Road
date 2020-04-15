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
import org.niis.xroad.restapi.repository.LocalGroupRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

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

    private final LocalGroupRepository localGroupRepository;
    private final GlobalConfFacade globalConfFacade;
    private final ClientRepository clientRepository;
    private final ServiceService serviceService;
    private final IdentifierService identifierService;
    private final GlobalConfService globalConfService;
    private final EndpointService endpointService;
    private final LocalGroupService localGroupService;

    @Autowired
    public AccessRightService(LocalGroupRepository localGroupRepository, GlobalConfFacade globalConfFacade,
            ClientRepository clientRepository, ServiceService serviceService, IdentifierService identifierService,
            GlobalConfService globalConfService,
            EndpointService endpointService,
            LocalGroupService localGroupService) {
        this.localGroupRepository = localGroupRepository;
        this.globalConfFacade = globalConfFacade;
        this.clientRepository = clientRepository;
        this.serviceService = serviceService;
        this.identifierService = identifierService;
        this.globalConfService = globalConfService;
        this.endpointService = endpointService;
        this.localGroupService = localGroupService;
    }

    /**
     * Remove AccessRights from a Service
     *
     * TO DO: maybe better fullServiceCode -> serviceCode
     *
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @param localGroupIds
     * @throws AccessRightNotFoundException if tried to remove access rights that did not exist for the service
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws EndpointNotFoundException if the base endpoint for the service is not found
     */
    public void deleteSoapServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws ClientNotFoundException, AccessRightNotFoundException,
            ServiceNotFoundException, EndpointNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);
        EndpointType endpointType = endpointService.getServiceBaseEndpoint(serviceType);

        deleteEndpointAccessRights(clientType, endpointType, subjectIds, localGroupIds);
    }

    /**
     * Remove access rights from endpoint
     *
     * @param endpointId
     * @param subjectIds
     * @param localGroupIds
     * @throws LocalGroupNotFoundException if localgroups is not found
     * @throws EndpointNotFoundException if endpoint by given id is not found
     * @throws ClientNotFoundException if client attached to endpoint is not found
     * @throws AccessRightNotFoundException if at least one access right expected is not found
     */
    public void deleteEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds, Set<Long> localGroupIds)
            throws EndpointNotFoundException,
            ClientNotFoundException, AccessRightNotFoundException {

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        EndpointType endpointType = endpointService.getEndpoint(endpointId);
        deleteEndpointAccessRights(clientType, endpointType, subjectIds, localGroupIds);
    }

    /**
     * Remove access rights from endpoint
     *
     * @param clientType
     * @param endpointType
     * @param subjectIds
     * @param localGroupIds
     * @throws AccessRightNotFoundException if access right is not found
     */
    private void deleteEndpointAccessRights(ClientType clientType, EndpointType endpointType, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws AccessRightNotFoundException {

        Set<XRoadId> subjectsToDelete = new HashSet<>();
        if (localGroupIds != null) {
            try {
                subjectsToDelete.addAll(localGroupService.getLocalGroupIdsAsXroadIds(localGroupIds));
            } catch (LocalGroupNotFoundException e) {
                throw new AccessRightNotFoundException(e);
            }
        }
        if (subjectIds != null) {
            subjectsToDelete.addAll(subjectIds);
        }

        // Check all local groups are found in the access right list of the client
        List<AccessRightType> accessRightsToBeRemoved = clientType.getAcl().stream()
                .filter(acl -> acl.getEndpoint().getId().equals(endpointType.getId())
                        && subjectsToDelete.contains(acl.getSubjectId()))
                .collect(Collectors.toList());
        if (accessRightsToBeRemoved.size() != subjectsToDelete.size()) {
            throw new AccessRightNotFoundException("All local groups identifiers + " + subjectsToDelete.toString()
                    + " weren't found in the access rights list of the given client: " + clientType.getIdentifier());
        }

        clientType.getAcl().removeAll(accessRightsToBeRemoved);
    }

    /**
     * Adds access rights to SOAP services. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     *
     * TO DO: maybe better fullServiceCode -> serviceCode
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @param localGroupIds
     * @return List of {@link ServiceClientDto AccessRightHolderDtos}
     * @throws ClientNotFoundException exception
     * @throws ServiceNotFoundException exception
     * @throws DuplicateAccessRightException exception
     * @throws IdentifierNotFoundException if subjectIds or localGroupIds identifier was not found
     * @throws EndpointNotFoundException exception
     */
    public List<ServiceClientDto> addSoapServiceAccessRights(ClientId clientId, String fullServiceCode,
                Set<XRoadId> subjectIds, Set<Long> localGroupIds) throws ClientNotFoundException,
            ServiceNotFoundException, DuplicateAccessRightException,
            IdentifierNotFoundException, EndpointNotFoundException {

        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);
        EndpointType endpointType = endpointService.getServiceBaseEndpoint(serviceType);

        // Combine subject ids and localgroup ids to a single list of XRoadIds
        return addEndpointAccessRights(clientType, endpointType, subjectIds, localGroupIds);
    }

    /**
     * Adds access rights to endpoint. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     *
     * @param endpointId
     * @param subjectIds
     * @param localGroupIds
     * @return
     * @throws EndpointNotFoundException endpoint is not found with given id
     * @throws ClientNotFoundException client for the endpoint is not found (shouldn't happen)
     * @throws IdentifierNotFoundException Identifier from subjectIds is not found
     * @throws DuplicateAccessRightException Trying to add duplicate access rights
     */
    public List<ServiceClientDto> addEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws EndpointNotFoundException, ClientNotFoundException,
            IdentifierNotFoundException, DuplicateAccessRightException {

        EndpointType endpointType = endpointService.getEndpoint(endpointId);

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        return addEndpointAccessRights(clientType, endpointType, subjectIds, localGroupIds);

    }

    /**
     * @throws IdentifierNotFoundException if subjectIds or localGroupIds were not found
     * @throws DuplicateAccessRightException
     */
    private List<ServiceClientDto> addEndpointAccessRights(ClientType clientType, EndpointType endpointType,
            Set<XRoadId> subjectIds, Set<Long> localGroupIds) throws IdentifierNotFoundException,
            DuplicateAccessRightException {

        // Combine subject ids and localgroup ids to a single list of XRoadIds
        Set<XRoadId> subjectIdsToBeAdded = mergeSubjectIdsWithLocalgroups(clientType, subjectIds, localGroupIds);

        // Add access rights to endpoint
        try {
            addAccessRights(subjectIdsToBeAdded, clientType, endpointType);
        } catch (LocalGroupNotFoundException e) {
            throw new IdentifierNotFoundException(e);
        }

        // Create DTOs for returning data
        List<AccessRightType> accessRightsByEndpoint = getAccessRightsByEndpoint(clientType, endpointType);
        return mapAccessRightsToServiceClients(clientType, accessRightsByEndpoint);
    }

    /**
     * Add access rights for one subject (service client) to multiple services (serviceCodes)
     * of a client (clientType)
     * TO DO: test
     * TO DO: test service != client's services
     * TO DO: test service client != client's service clients
     * TO DO: access right already exists -> exception
     * @param clientId id of the client who owns the services
     * @param serviceCodes serviceCodes of the services to add access rights to (without version numbers)
     * @param subjectId subject (service client) to add access rights for. Can be a local group,
     *                  global group, or a subsystem
     * @return ServiceClientAccessRightDtos that were added for this service client
     * @throws EndpointNotFoundException if serviceCodes had any codes that were not client's services
     * (did not have base endpoints)
     * @throws ClientNotFoundException if client matching clientId was not found
     * @throws DuplicateAccessRightException if trying to add existing access right
     * @throws IdentifierNotFoundException if service client (local group, global group, or system) matching given
     * subjectId did not exist
     */
    public List<ServiceClientAccessRightDto> addServiceClientAccessRights(ClientId clientId, Set<String> serviceCodes,
            XRoadId subjectId) throws EndpointNotFoundException,
            DuplicateAccessRightException, ClientNotFoundException, IdentifierNotFoundException {

        log.debug("Add access rights to subject", subjectId); // acl_subject_open_services_add

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

        // prepare params for addAccessRightsInternal
        List<EndpointType> baseEndpoints = endpointService.getServiceBaseEndpoints(clientType, serviceCodes);
        Set<XRoadId> subjectIds = new HashSet<>();
        subjectIds.add(subjectId);

        // verify that given subjectId exists
        verifyServiceClientIdentifiersExist(clientType, subjectIds);

        // make sure subject id exists in serverconf db IDENTIFIER table
        subjectIds = identifierService.getOrPersistXroadIds(subjectIds);

        try {
            return addAccessRightsInternal(subjectIds, clientType, baseEndpoints).get(subjectId);
        } catch (LocalGroupNotFoundException e) {
            // no need to handle this in more detail than the other service client types
            throw new IdentifierNotFoundException(e);
        }
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
    public List<ServiceClientDto> mapAccessRightsToServiceClients(ClientType clientType,
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

        List<LocalGroupType> clientLocalGroups = clientType.getLocalGroup();

        if (subjectIds == null || subjectIds.isEmpty()) {
            throw new IllegalArgumentException("missing subjectIds");
        }
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("missing endpoints");
        }

        Map<XRoadId, List<ServiceClientAccessRightDto>> addedAccessRights = new HashMap<>();

        for (EndpointType endpoint: endpoints) {
            for (XRoadId subjectId : subjectIds) {
                // A LocalGroup must belong to this client
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
                newAccessRight.setRightsGiven(now);
                clientType.getAcl().add(newAccessRight);
                List<ServiceClientAccessRightDto> addedAccessRightsForSubject = addedAccessRights
                        .computeIfAbsent(subjectId, k -> new ArrayList<>());
                ServiceClientAccessRightDto dto = ServiceClientAccessRightDto.builder()
                        .serviceCode(endpoint.getServiceCode())
                        .rightsGiven(FormatUtils.fromDateToOffsetDateTime(now))
                        .title(getServiceTitle(clientType, endpoint.getServiceCode()))
                        .build();
                addedAccessRightsForSubject.add(dto);
            }
        }

        clientRepository.saveOrUpdate(clientType);
        return addedAccessRights;
    }

    // TO DO: currently duplicate with ServiceClientService, not sure if ServiceClientService will be refactored,
    // and what is the correct place if both need this
    private String getServiceTitle(ClientType clientType, String serviceCode) {
        ServiceType service = clientType.getServiceDescription().stream()
                .flatMap(sd -> sd.getService().stream())
                .filter(serviceType -> serviceType.getServiceCode().equals(serviceCode))
                .findFirst()
                .get();

        return service == null ? null : service.getTitle();
    }

    /**
     * Go through given service client ids (subjectIds), verify that they match existing items,
     * and ensure that SERVERCONF.IDENTIFIER table has rows for all of them. Return managed
     * entities
     * @param clientType owner client of the (possible) local groups
     * @param subjectIds
     * @param localGroupIds
     * @return
     * @throws IdentifierNotFoundException subjectIds identifier was not found
     */
    private Set<XRoadId> mergeSubjectIdsWithLocalgroups(ClientType clientType, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds)
            throws IdentifierNotFoundException {

        // subjectIds + localGroupIds => transientIds
        Set<XRoadId> transientIds = new HashSet<>();
        if (subjectIds != null) {
            transientIds.addAll(subjectIds);
        }
        if (localGroupIds != null && localGroupIds.size() > 0) {
            try {
                Set<XRoadId> localGroupXroadIds = localGroupService.getLocalGroupIdsAsXroadIds(localGroupIds);
                transientIds.addAll(localGroupXroadIds); // not actually transient, but does not matter
            } catch (LocalGroupNotFoundException e) {
                throw new IdentifierNotFoundException(e);
            }
        }

        // verify that all ids actually exist
        verifyServiceClientIdentifiersExist(clientType, transientIds);

        // Get all ids from serverconf db IDENTIFIER table - or add them if they don't exist
        Set<XRoadId> managedIds = identifierService.getOrPersistXroadIds(transientIds);
        return managedIds;
    }

    /**
     * Verify that service client XRoadIds do exist
     * @param clientType owner of (possible) local groups
     * @param serviceClientIds service client ids to check
     * @return
     * @throws IdentifierNotFoundException if there were identifiers that could not be found
     */
    private void verifyServiceClientIdentifiersExist(ClientType clientType, Set<XRoadId> serviceClientIds)
            throws IdentifierNotFoundException {
        Map<XRoadObjectType, List<XRoadId>> idsPerType = serviceClientIds.stream()
                .collect(groupingBy(XRoadId::getObjectType));
        for (XRoadObjectType type: idsPerType.keySet()) {
            if (!isValidServiceClientType(type)) {
                throw new IllegalArgumentException("Invalid service client subject object type " + type);
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.GLOBALGROUP)) {
            if (!globalConfService.globalGroupIdentifiersExist(idsPerType.get(XRoadObjectType.GLOBALGROUP))) {
                throw new IdentifierNotFoundException();
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.SUBSYSTEM)) {
            if (!globalConfService.clientIdentifiersExist(idsPerType.get(XRoadObjectType.SUBSYSTEM))) {
                throw new IdentifierNotFoundException();
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.LOCALGROUP)) {
            if (!localGroupService.localGroupIdentifiersExist(clientType, idsPerType.get(XRoadObjectType.LOCALGROUP))) {
                throw new IdentifierNotFoundException();
            }
        }
    }


    /**
     * Verify that all identifiers are authentic, then get the existing ones from the local db and persist
     * the not-existing ones. This is a necessary step if we are changing identifier relations (such as adding
     * access rights to services)
     * @param subsystemIds {@link GlobalGroupId} or {@link ClientId}
     * @return List of XRoadIds ({@link GlobalGroupId} or {@link ClientId})
     */
    private Set<XRoadId> getOrPersistSubsystemIds(Set<XRoadId> subsystemIds)
            throws IdentifierNotFoundException {
        // Check that the identifiers exist in globalconf
        // LocalGroups must be verified separately! (they do not exist in globalconf)
        if (!globalConfService.clientIdentifiersExist(subsystemIds)) {
            // This exception should be pretty rare since it only occurs if bogus subjects are found
            throw new IdentifierNotFoundException();
        }
        return identifierService.getOrPersistXroadIds(subsystemIds);
    }

    /**
     * @param globalGroupIds
     * @return
     * @throws IdentifierNotFoundException
     * @see AccessRightService#getOrPersistSubsystemIds(Set)
     */
    private Set<XRoadId> getOrPersistGlobalGroupIds(Set<XRoadId> globalGroupIds)
            throws IdentifierNotFoundException {
        if (!globalConfService.globalGroupIdentifiersExist(globalGroupIds)) {
            throw new IdentifierNotFoundException();
        }
        return identifierService.getOrPersistXroadIds(globalGroupIds);
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
     * against the given search terms. The given ServiceClientDto has a {@link ServiceClientDto#subjectId}
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

        if (subjectType != null) {
            searchPredicate = searchPredicate.and(dto -> dto.getSubjectId().getObjectType() == subjectType);
        }
        // Check if the memberName or LocalGroup's description match with the search term
        if (!StringUtils.isEmpty(memberNameOrGroupDescription)) {
            searchPredicate = searchPredicate.and(dto -> {
                String memberName = dto.getMemberName();
                String localGroupDescription = dto.getLocalGroupDescription();
                boolean isMatch = StringUtils.containsIgnoreCase(memberName, memberNameOrGroupDescription)
                        || StringUtils.containsIgnoreCase(localGroupDescription, memberNameOrGroupDescription);
                return isMatch;
            });
        }
        // Check if the instance of the subject matches with the search term
        if (!StringUtils.isEmpty(instance)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                // In case the Subject is a LocalGroup: LocalGroups do not have explicit X-Road instances
                // -> always return
                if (xRoadId instanceof LocalGroupId) {
                    return true;
                } else {
                    return StringUtils.containsIgnoreCase(dto.getSubjectId().getXRoadInstance(), instance);
                }
            });
        }
        // Check if the memberClass of the subject matches with the search term
        if (!StringUtils.isEmpty(memberClass)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                if (xRoadId instanceof ClientId) {
                    String clientMemberClass = ((ClientId) xRoadId).getMemberClass();
                    return StringUtils.containsIgnoreCase(clientMemberClass, memberClass);
                } else {
                    return false;
                }
            });
        }
        // Check if the subsystemCode of the subject matches with the search term
        if (!StringUtils.isEmpty(subsystemCode)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                if (xRoadId instanceof ClientId) {
                    String clientSubsystemCode = ((ClientId) xRoadId).getSubsystemCode();
                    return StringUtils.containsIgnoreCase(clientSubsystemCode, subsystemCode);
                } else {
                    return false;
                }
            });
        }
        // Check if the memberCode or groupCode of the subject matches with the search term
        if (!StringUtils.isEmpty(memberGroupCode)) {
            searchPredicate = searchPredicate.and(dto -> {
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
            });
        }
        return searchPredicate;
    }
}
