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
import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.EndpointRepository;
import org.niis.xroad.restapi.repository.LocalGroupRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * service class for handling access rights
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
    private final EndpointRepository endpointRepository;
    private final EndpointService endpointService;

    @Autowired
    public AccessRightService(LocalGroupRepository localGroupRepository, GlobalConfFacade globalConfFacade,
            ClientRepository clientRepository, ServiceService serviceService, IdentifierService identifierService,
            GlobalConfService globalConfService, EndpointRepository endpointRepository,
            EndpointService endpointService) {
        this.localGroupRepository = localGroupRepository;
        this.globalConfFacade = globalConfFacade;
        this.clientRepository = clientRepository;
        this.serviceService = serviceService;
        this.identifierService = identifierService;
        this.globalConfService = globalConfService;
        this.endpointRepository = endpointRepository;
        this.endpointService = endpointService;
    }

    /**
     * Get AccessRightHolderDtos for given client
     *
     * The concept of base endpoint is used to find service level access rights in this method.
     * Base endpoint is in other words service (code) level endpoint.
     * Each service has one base endpoint.
     * Base endpoint has method '*' and path '**'.
     *
     * @param clientId
     * @return
     * @throws ClientNotFoundException
     */
    public List<AccessRightHolderDto> getAccessRightHoldersByClient(ClientId clientId)
            throws ClientNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        // Filter just acls that are set to base endpoints so they are on service code level
        List<AccessRightType> serviceCodeLevelAcls = clientType.getAcl().stream()
                .filter(acl -> acl.getEndpoint().isBaseEndpoint())
                .collect(Collectors.toList());
        List<AccessRightType> distinctAccessRightTypes = distinctAccessRightTypeByXroadId(serviceCodeLevelAcls);
        return mapAccessRightsToAccessRightHolders(clientType, distinctAccessRightTypes);
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
     * Get access right holders by Service
     * @param clientId
     * @param fullServiceCode
     * @return
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws EndpointNotFoundException if base endpoint for this service is not found from the client
     */
    public List<AccessRightHolderDto> getAccessRightHoldersByService(ClientId clientId, String fullServiceCode)
            throws ClientNotFoundException, ServiceNotFoundException, EndpointNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);
        EndpointType endpointType = endpointService.getServiceBaseEndpoint(serviceType);

        List<AccessRightType> accessRightsByEndpoint = getAccessRightsByEndpoint(clientType, endpointType);
        return mapAccessRightsToAccessRightHolders(clientType, accessRightsByEndpoint);
    }

    /**
     * Get access right holders for Endpoint
     * @param id
     * @return
     * @throws EndpointNotFoundException if no endpoint is found with given id
     * @throws ClientNotFoundException if client attached to endpoint is not found
     */
    public List<AccessRightHolderDto> getAccessRightHoldersByEndpoint(Long id)
            throws EndpointNotFoundException, ClientNotFoundException {

        ClientType clientType = clientRepository.getClientByEndpointId(id);
        EndpointType endpointType = endpointService.getEndpoint(id);

        List<AccessRightType> accessRightsByEndpoint = getAccessRightsByEndpoint(clientType, endpointType);
        return mapAccessRightsToAccessRightHolders(clientType, accessRightsByEndpoint);
    }

    private List<AccessRightType> getAccessRightsByEndpoint(ClientType clientType, EndpointType endpointType) {
        return clientType.getAcl().stream()
                .filter(accessRightType -> accessRightType.getEndpoint().getId().equals(endpointType.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get access rights for endpoint
     * @param clientType
     * @param accessRightTypes
     * @return
     */
    private List<AccessRightHolderDto> mapAccessRightsToAccessRightHolders(ClientType clientType,
            List<AccessRightType> accessRightTypes) {
        Map<String, LocalGroupType> localGroupMap = new HashMap<>();
        clientType.getLocalGroup().forEach(localGroupType -> localGroupMap.put(localGroupType.getGroupCode(),
                localGroupType));

        return accessRightTypes.stream()
                .map((accessRightType -> accessRightTypeToDto(accessRightType, localGroupMap)))
                .collect(Collectors.toList());
    }

    /**
     * Makes an {@link AccessRightHolderDto} out of {@link AccessRightType}
     * @param accessRightType The AccessRightType to convert from
     * @param localGroupMap A Map containing {@link LocalGroupType LocalGroupTypes} mapped by
     * their corresponding {@link LocalGroupType#groupCode}
     * @return
     */
    private AccessRightHolderDto accessRightTypeToDto(AccessRightType accessRightType,
            Map<String, LocalGroupType> localGroupMap) {
        AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
        XRoadId subjectId = accessRightType.getSubjectId();
        accessRightHolderDto.setRightsGiven(
                FormatUtils.fromDateToOffsetDateTime(accessRightType.getRightsGiven()));
        accessRightHolderDto.setSubjectId(subjectId);
        if (subjectId.getObjectType() == XRoadObjectType.LOCALGROUP) {
            LocalGroupId localGroupId = (LocalGroupId) subjectId;
            LocalGroupType localGroupType = localGroupMap.get(localGroupId.getGroupCode());
            accessRightHolderDto.setLocalGroupId(localGroupType.getId().toString());
            accessRightHolderDto.setLocalGroupCode(localGroupType.getGroupCode());
            accessRightHolderDto.setLocalGroupDescription(localGroupType.getDescription());
        }
        return accessRightHolderDto;
    }

    /**
     * Remove AccessRights from a Service
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @param localGroupIds
     * @throws LocalGroupNotFoundException if tried to remove local group access right
     * for a local group that does not exist
     * @throws AccessRightNotFoundException if tried to remove access rights that did not exist for the service
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws EndpointNotFoundException if the base endpoint for the service is not found
     */
    public void deleteSoapServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws LocalGroupNotFoundException,
            ClientNotFoundException, AccessRightNotFoundException, ServiceNotFoundException,
            EndpointNotFoundException {
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
                subjectsToDelete.addAll(getLocalGroupsAsXroadIds(localGroupIds));
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
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @param localGroupIds
     * @return List of {@link AccessRightHolderDto AccessRightHolderDtos}
     * @throws AccessRightNotFoundException
     * @throws ClientNotFoundException
     * @throws ServiceNotFoundException
     * @throws DuplicateAccessRightException
     * @throws IdentifierNotFoundException
     * @throws EndpointNotFoundException
     * @throws LocalGroupNotFoundException
     */
    public List<AccessRightHolderDto> addSoapServiceAccessRights(ClientId clientId, String fullServiceCode,
            Set<XRoadId> subjectIds, Set<Long> localGroupIds) throws AccessRightNotFoundException,
            ClientNotFoundException, ServiceNotFoundException, DuplicateAccessRightException,
            IdentifierNotFoundException, EndpointNotFoundException, LocalGroupNotFoundException {
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
     * @param endpointId
     * @param subjectIds
     * @param localGroupIds
     * @return
     * @throws EndpointNotFoundException endpoint is not found with given id
     * @throws ClientNotFoundException client for the endpoint is not found (shouldn't happen)
     * @throws IdentifierNotFoundException Identifier is not found
     * @throws AccessRightNotFoundException Local group is not found
     * @throws DuplicateAccessRightException Trying to add duplicate access rights
     */
    public List<AccessRightHolderDto> addEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws EndpointNotFoundException, ClientNotFoundException,
            IdentifierNotFoundException, AccessRightNotFoundException, DuplicateAccessRightException,
            LocalGroupNotFoundException {

        EndpointType endpointType = endpointService.getEndpoint(endpointId);

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        return addEndpointAccessRights(clientType, endpointType, subjectIds, localGroupIds);

    }

    private List<AccessRightHolderDto> addEndpointAccessRights(ClientType clientType, EndpointType endpointType,
            Set<XRoadId> subjectIds, Set<Long> localGroupIds) throws IdentifierNotFoundException,
            AccessRightNotFoundException, DuplicateAccessRightException, LocalGroupNotFoundException {

        // Combine subject ids and localgroup ids to a single list of XRoadIds
        Set<XRoadId> subjectIdsToBeAdded = mergeSubjectIdsWithLocalgroups(subjectIds, localGroupIds);

        // Add access rights to endpoint
        addAccessRights(subjectIdsToBeAdded, clientType, endpointType);

        // Create DTOs for returning data
        List<AccessRightType> accessRightsByEndpoint = getAccessRightsByEndpoint(clientType, endpointType);
        return mapAccessRightsToAccessRightHolders(clientType, accessRightsByEndpoint);
    }

    /**
     * Add access rights to given endpoint
     * @param subjectIds
     * @param clientType
     * @param endpoint
     * @throws DuplicateAccessRightException if trying to add existing access right
     */
    private void addAccessRights(Set<XRoadId> subjectIds, ClientType clientType, EndpointType endpoint)
            throws DuplicateAccessRightException, LocalGroupNotFoundException {
        Date now = new Date();

        List<LocalGroupType> clientLocalGroups = clientType.getLocalGroup();

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
            Optional<AccessRightType> existingAccessRight = clientType.getAcl().stream()
                    .filter(accessRightType -> accessRightType.getSubjectId().equals(subjectId))
                    .findFirst();

            if (existingAccessRight.isPresent() && existingAccessRight.get().getEndpoint().equals(endpoint)) {
                throw new DuplicateAccessRightException("Subject " + subjectId.toShortString()
                        + " already has an access right for endpoint " + endpoint.getId());
            }
            AccessRightType newAccessRight = new AccessRightType();
            newAccessRight.setEndpoint(endpoint);
            newAccessRight.setSubjectId(subjectId);
            newAccessRight.setRightsGiven(now);
            clientType.getAcl().add(newAccessRight);
        }

        clientRepository.saveOrUpdate(clientType);
    }

    private Set<XRoadId> mergeSubjectIdsWithLocalgroups(Set<XRoadId> subjectIds, Set<Long> localGroupIds)
            throws IdentifierNotFoundException, AccessRightNotFoundException {
        // Get persistent entities in order to change relations
        Set<XRoadId> txSubjects = new HashSet<>();
        if (subjectIds != null && !subjectIds.isEmpty()) {
            txSubjects.addAll(getOrPersistSubsystemIds(subjectIds.stream()
                    .filter(xRoadId -> xRoadId.getObjectType() == XRoadObjectType.SUBSYSTEM)
                    .collect(Collectors.toSet())));
            txSubjects.addAll(getOrPersistGlobalGroupIds(subjectIds.stream()
                    .filter(xRoadId -> xRoadId.getObjectType() == XRoadObjectType.GLOBALGROUP)
                    .collect(Collectors.toSet())));
        }
        if (localGroupIds != null && localGroupIds.size() > 0) {
            Set<XRoadId> localGroupXroadIds = null;
            try {
                localGroupXroadIds = getLocalGroupsAsXroadIds(localGroupIds);
            } catch (LocalGroupNotFoundException e) {
                throw new AccessRightNotFoundException(e);
            }
            // Get LocalGroupIds from serverconf db - or save them if they don't exist
            Set<XRoadId> txLocalGroupXroadIds = identifierService.getOrPersistXroadIds(localGroupXroadIds);
            txSubjects.addAll(txLocalGroupXroadIds);
        }
        return txSubjects;
    }

    /**
     * Get matching {@link EndpointType endpoint} from {@link ClientType#endpoint client's list of endpoints}.
     * @param clientType
     * @param serviceType
     * @param endpointMethod
     * @param endpointPath
     * @return
     */
    private Optional<EndpointType> getEndpoint(ClientType clientType, ServiceType serviceType, String endpointMethod,
            String endpointPath) {
        return clientType.getEndpoint().stream()
                .filter(endpointType -> endpointType.getServiceCode().equals(serviceType.getServiceCode())
                        && endpointType.getMethod().equals(endpointMethod)
                        && endpointType.getPath().equals(endpointPath))
                .findFirst();
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
     * Verify that all given {@link Long} ids are real, then return them as {@link LocalGroupId LocalGroupIds}
     * @param localGroupIds
     * @return
     * @throws LocalGroupNotFoundException
     */
    private Set<XRoadId> getLocalGroupsAsXroadIds(Set<Long> localGroupIds) throws LocalGroupNotFoundException {
        Set<XRoadId> localGroupXRoadIds = new HashSet<>();
        for (Long groupId : localGroupIds) {
            LocalGroupType localGroup = localGroupRepository.getLocalGroup(groupId); // no need to batch
            if (localGroup == null) {
                throw new LocalGroupNotFoundException("LocalGroup with id " + groupId + " not found");
            }
            localGroupXRoadIds.add(LocalGroupId.create(localGroup.getGroupCode()));
        }
        return localGroupXRoadIds;
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
     * Find access right holders by search terms
     * @param clientId
     * @param subjectType search term for subjectType. Null or empty value is considered a match
     * @param memberNameOrGroupDescription search term for memberName or groupDescription (depending on subject's type).
     * Null or empty value is considered a match
     * @param instance search term for instance. Null or empty value is considered a match
     * @param memberClass search term for memberClass. Null or empty value is considered a match
     * @param memberGroupCode search term for memberCode or groupCode (depending on subject's type).
     * Null or empty value is considered a match
     * @param subsystemCode search term for subsystemCode. Null or empty value is considered a match
     * @return A List of {@link AccessRightHolderDto accessRightHolderDtos} or an empty List if nothing is found
     */
    public List<AccessRightHolderDto> findAccessRightHolders(ClientId clientId, String memberNameOrGroupDescription,
            XRoadObjectType subjectType, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) throws ClientNotFoundException {
        List<AccessRightHolderDto> dtos = new ArrayList<>();

        // get client
        ClientType client = clientRepository.getClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        // get global members
        List<AccessRightHolderDto> globalMembers = getGlobalMembersAsDtos();
        if (globalMembers.size() > 0) {
            dtos.addAll(globalMembers);
        }

        // get global groups
        List<AccessRightHolderDto> globalGroups = getGlobalGroupsAsDtos(instance);
        if (globalMembers.size() > 0) {
            dtos.addAll(globalGroups);
        }

        // get local groups
        List<AccessRightHolderDto> localGroups = getLocalGroupsAsDtos(client.getLocalGroup());
        if (localGroups.size() > 0) {
            dtos.addAll(localGroups);
        }

        Predicate<AccessRightHolderDto> matchingSearchTerms = buildSubjectSearchPredicate(subjectType,
                memberNameOrGroupDescription, instance, memberClass, memberGroupCode, subsystemCode);

        return dtos.stream()
                .filter(matchingSearchTerms)
                .collect(Collectors.toList());
    }

    private List<AccessRightHolderDto> getLocalGroupsAsDtos(List<LocalGroupType> localGroupTypes) {
        return localGroupTypes.stream()
                .map(localGroup -> {
                    AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
                    accessRightHolderDto.setLocalGroupId(localGroup.getId().toString());
                    accessRightHolderDto.setLocalGroupCode(localGroup.getGroupCode());
                    accessRightHolderDto.setSubjectId(LocalGroupId.create(localGroup.getGroupCode()));
                    accessRightHolderDto.setLocalGroupDescription(localGroup.getDescription());
                    return accessRightHolderDto;
                }).collect(Collectors.toList());
    }

    private List<AccessRightHolderDto> getGlobalMembersAsDtos() {
        return globalConfFacade.getMembers().stream()
                .map(memberInfo -> {
                    AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
                    accessRightHolderDto.setSubjectId(memberInfo.getId());
                    accessRightHolderDto.setMemberName(memberInfo.getName());
                    return accessRightHolderDto;
                })
                .collect(Collectors.toList());
    }

    private List<AccessRightHolderDto> getGlobalGroupsAsDtos(String instance) {
        List<AccessRightHolderDto> globalGroups = new ArrayList<>();
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
                AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
                accessRightHolderDto.setSubjectId(globalGroupInfo.getId());
                accessRightHolderDto.setLocalGroupDescription(globalGroupInfo.getDescription());
                globalGroups.add(accessRightHolderDto);
            });
        }
        return globalGroups;
    }

    /**
     * Composes a {@link Predicate} that will be used to filter {@link AccessRightHolderDto AccessRightHolderDtos}
     * against the given search terms. The given AccessRightHolderDto has a {@link AccessRightHolderDto#subjectId}
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
    private Predicate<AccessRightHolderDto> buildSubjectSearchPredicate(XRoadObjectType subjectType,
            String memberNameOrGroupDescription, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) {
        // Start by assuming the search is a match. If there are no search terms --> return all
        Predicate<AccessRightHolderDto> searchPredicate = accessRightHolderDto -> true;

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
