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

import static org.niis.xroad.restapi.service.SecurityHelper.verifyAuthority;

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

    @Autowired
    public AccessRightService(LocalGroupRepository localGroupRepository, GlobalConfFacade globalConfFacade,
            ClientRepository clientRepository, ServiceService serviceService, IdentifierService identifierService,
            GlobalConfService globalConfService, EndpointRepository endpointRepository) {
        this.localGroupRepository = localGroupRepository;
        this.globalConfFacade = globalConfFacade;
        this.clientRepository = clientRepository;
        this.serviceService = serviceService;
        this.identifierService = identifierService;
        this.globalConfService = globalConfService;
        this.endpointRepository = endpointRepository;
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
     * Get access right holders by Service
     * @param clientId
     * @param fullServiceCode
     * @return
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     */
    public List<AccessRightHolderDto> getAccessRightHoldersByService(ClientId clientId, String fullServiceCode)
            throws ClientNotFoundException, ServiceNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);

        List<AccessRightHolderDto> accessRightHolderDtos = new ArrayList<>();

        Map<String, LocalGroupType> localGroupMap = new HashMap<>();

        clientType.getLocalGroup().forEach(localGroupType -> localGroupMap.put(localGroupType.getGroupCode(),
                localGroupType));

        clientType.getAcl().forEach(accessRightType -> {
            EndpointType endpoint = accessRightType.getEndpoint();
            if (endpoint.getServiceCode().equals(serviceType.getServiceCode())
                    && endpoint.getMethod().equals(EndpointType.ANY_METHOD)
                    && endpoint.getPath().equals(EndpointType.ANY_PATH)) {
                AccessRightHolderDto accessRightHolderDto = accessRightTypeToDto(accessRightType, localGroupMap);
                accessRightHolderDtos.add(accessRightHolderDto);
            }
        });

        return accessRightHolderDtos;
    }

    /**
     * Get access right holders for Endpoint
     *
     * @param id
     * @return
     * @throws EndpointService.EndpointNotFoundException    if no endpoint is found with given id
     * @throws ClientNotFoundException                      if client attached to endpoint is not found
     */
    public List<AccessRightHolderDto> getAccessRightHoldersByEndpoint(Long id)
            throws EndpointService.EndpointNotFoundException, ClientNotFoundException {
        verifyAuthority("VIEW_ENDPOINT_ACL");

        ClientType clientType = clientRepository.getClientByEndpointId(id);
        if (clientType == null) {
            throw new ClientNotFoundException("Client not found for endpoint with id: " + id.toString());
        }

        List<AccessRightHolderDto> accessRightHolderDtos = new ArrayList<>();
        Map<String, LocalGroupType> localGroupMap = new HashMap<>();
        clientType.getLocalGroup().forEach(localGroupType -> localGroupMap.put(localGroupType.getGroupCode(),
                localGroupType));
        clientType.getAcl().forEach(accessRightType -> {
            if (accessRightType.getEndpoint().getId().equals(id)) {
                AccessRightHolderDto accessRightHolderDto = accessRightTypeToDto(accessRightType, localGroupMap);
                accessRightHolderDtos.add(accessRightHolderDto);
            }
        });

        return accessRightHolderDtos;
    }


    /**
     * Remove AccessRights from a Service
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws AccessRightNotFoundException if attempted to delete access right that did not exist for the service
     */
    private void deleteSoapServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds)
            throws ClientNotFoundException, AccessRightNotFoundException, ServiceNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);

        List<AccessRightType> accessRightsToBeRemoved = clientType.getAcl()
                .stream()
                .filter(accessRightType ->
                        accessRightType.getEndpoint().getServiceCode().equals(serviceType.getServiceCode())
                            && subjectIds.contains(accessRightType.getSubjectId())
                            && accessRightType.getEndpoint().getMethod().equals(EndpointType.ANY_METHOD)
                            && accessRightType.getEndpoint().getPath().equals(EndpointType.ANY_PATH))
                .collect(Collectors.toList());

        if (accessRightsToBeRemoved.size() != subjectIds.size()) {
            throw new AccessRightNotFoundException();
        }

        clientType.getAcl().removeAll(accessRightsToBeRemoved);

        clientRepository.saveOrUpdate(clientType);
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
     */
    public void deleteSoapServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws LocalGroupNotFoundException, ClientNotFoundException,
            AccessRightNotFoundException, ServiceNotFoundException {
        Set<XRoadId> idsToDelete = new HashSet<>();
        if (localGroupIds != null) {
            idsToDelete.addAll(getLocalGroupsAsXroadIds(localGroupIds));
        }
        if (subjectIds != null) {
            idsToDelete.addAll(subjectIds);
        }
        deleteSoapServiceAccessRights(clientId, fullServiceCode, idsToDelete);
    }

    /**
     * Remove access rights from endpoint
     *
     * @param endpointId
     * @param subjectIds
     * @param localGroupIds
     * @throws LocalGroupNotFoundException                  if localgroups is not found
     * @throws EndpointService.EndpointNotFoundException    if endpoint by given id is not found
     * @throws ClientNotFoundException                      if client attached to endpoint is not found
     * @throws AccessRightNotFoundException                 if at least one access right expected is not found
     */
    public void deleteEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds, Set<Long> localGroupIds)
        throws LocalGroupNotFoundException, EndpointService.EndpointNotFoundException,
            ClientNotFoundException, AccessRightNotFoundException {
        verifyAuthority("EDIT_ENDPOINT_ACL");

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client not found for endpoint with id: " + endpointId.toString());
        }
        Set<XRoadId> idsToDelete = new HashSet<>();
        if (localGroupIds != null) {
            idsToDelete.addAll(getLocalGroupsAsXroadIds(localGroupIds));
        }
        if (subjectIds != null) {
            idsToDelete.addAll(subjectIds);
        }

        List<AccessRightType> accessRightsToBeRemoved = clientType.getAcl().stream()
                .filter(acl -> acl.getEndpoint().getId().equals(endpointId) && idsToDelete.contains(acl.getSubjectId()))
                .collect(Collectors.toList());
        if (accessRightsToBeRemoved.size() != idsToDelete.size()) {
            throw new AccessRightNotFoundException();
        }
        clientType.getAcl().removeAll(accessRightsToBeRemoved);
        clientRepository.saveOrUpdate(clientType);
    }

    /**
     * Add access rights to SOAP services.
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds must be persistent objects
     * @return List of {@link AccessRightHolderDto AccessRightHolderDtos}
     * @throws ClientNotFoundException
     * @throws ServiceNotFoundException
     * @throws EndpointNotFoundException
     */
    private List<AccessRightHolderDto> addSoapServiceAccessRights(ClientId clientId, String fullServiceCode,
            Set<XRoadId> subjectIds) throws ClientNotFoundException, ServiceNotFoundException,
            DuplicateAccessRightException, EndpointNotFoundException {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }
        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);

        // Get matching endpoint from client. This should never throw with SOAP services
        EndpointType endpoint = getEndpoint(clientType, serviceType, EndpointType.ANY_METHOD, EndpointType.ANY_PATH)
                .orElseThrow(() -> new EndpointNotFoundException(fullServiceCode));

        addAccessRights(subjectIds, clientType, endpoint);

        return getAccessRightHolderDtosForEndpoint(clientType, endpoint);
    }

    private List<AccessRightHolderDto> getAccessRightHolderDtosForEndpoint(ClientType clientType,
            EndpointType endpoint) {
        Map<String, LocalGroupType> localGroupMap = new HashMap<>();

        clientType.getLocalGroup().forEach(localGroupType -> localGroupMap.put(localGroupType.getGroupCode(),
                localGroupType));

        List<AccessRightHolderDto> accessRightHolderDtos = new ArrayList<>();

        clientType.getAcl().forEach(accessRightType -> {
            if (accessRightType.getEndpoint().getId().equals(endpoint.getId())) {
                AccessRightHolderDto accessRightHolderDto = accessRightTypeToDto(accessRightType, localGroupMap);
                accessRightHolderDtos.add(accessRightHolderDto);
            }
        });

        return accessRightHolderDtos;
    }

    /**
     * Add access rights to given endpoint
     *
     * @param subjectIds
     * @param clientType
     * @param endpoint
     * @throws DuplicateAccessRightException    if trying to add existing access right
     */
    private void addAccessRights(Set<XRoadId> subjectIds, ClientType clientType, EndpointType endpoint)
            throws DuplicateAccessRightException {
        Date now = new Date();

        for (XRoadId subjectId : subjectIds) {
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
     * Adds access rights to SOAP services. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @param localGroupIds
     * @return List of {@link AccessRightHolderDto AccessRightHolderDtos}
     * @throws LocalGroupNotFoundException
     * @throws ClientNotFoundException
     * @throws ServiceNotFoundException
     * @throws EndpointNotFoundException
     */
    public List<AccessRightHolderDto> addSoapServiceAccessRights(ClientId clientId, String fullServiceCode,
            Set<XRoadId> subjectIds, Set<Long> localGroupIds) throws LocalGroupNotFoundException,
            ClientNotFoundException, ServiceNotFoundException, DuplicateAccessRightException,
            IdentifierNotFoundException, EndpointNotFoundException {
        Set<XRoadId> txSubjects = mergeSubjectIdsWithLocalgroups(subjectIds, localGroupIds);
        return addSoapServiceAccessRights(clientId, fullServiceCode, txSubjects);
    }

    private Set<XRoadId> mergeSubjectIdsWithLocalgroups(Set<XRoadId> subjectIds, Set<Long> localGroupIds)
            throws IdentifierNotFoundException, LocalGroupNotFoundException {
        // Get persistent entities in order to change relations
        Set<XRoadId> txSubjects = new HashSet<>();
        if (subjectIds != null && subjectIds.size() > 0) {
            txSubjects.addAll(getOrPersistSubsystemIds(subjectIds.stream()
                    .filter(xRoadId -> xRoadId.getObjectType() == XRoadObjectType.SUBSYSTEM)
                    .collect(Collectors.toSet())));
            txSubjects.addAll(getOrPersistGlobalGroupIds(subjectIds.stream()
                    .filter(xRoadId -> xRoadId.getObjectType() == XRoadObjectType.GLOBALGROUP)
                    .collect(Collectors.toSet())));
        }
        if (localGroupIds != null && localGroupIds.size() > 0) {
            Set<XRoadId> localGroupXroadIds = getLocalGroupsAsXroadIds(localGroupIds);
            // Get LocalGroupIds from serverconf db - or save them if they don't exist
            Set<XRoadId> txLocalGroupXroadIds = identifierService.getOrPersistXroadIds(localGroupXroadIds);
            txSubjects.addAll(txLocalGroupXroadIds);
        }
        return txSubjects;
    }

    /**
     * Add new access rights to endpoint
     *
     * @param endpointId
     * @param subjectIds
     * @param localGroupIds
     * @return
     * @throws EndpointService.EndpointNotFoundException endpoint is not found with given id
     * @throws ClientNotFoundException                   client for the endpoint is not found (shouldn't happen)
     * @throws IdentifierNotFoundException
     * @throws LocalGroupNotFoundException
     * @throws DuplicateAccessRightException
     */
    public List<AccessRightHolderDto> addEndpointAccessRights(Long endpointId, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) throws EndpointService.EndpointNotFoundException, ClientNotFoundException,
            IdentifierNotFoundException, LocalGroupNotFoundException, DuplicateAccessRightException {
        verifyAuthority("EDIT_ENDPOINT_ACL");

        EndpointType endpointType = endpointRepository.getEndpoint(endpointId);
        if (endpointType == null) {
            throw new EndpointService.EndpointNotFoundException(endpointId.toString());
        }

        ClientType clientType = clientRepository.getClientByEndpointId(endpointId);
        if (clientType == null) {
            throw new ClientNotFoundException("Client not found for endpoint with id: " + endpointId.toString());
        }

        // Combine subject ids and localgroup ids to a single list of XRoadIds
        Set<XRoadId> subjectIdsToBeAdded = mergeSubjectIdsWithLocalgroups(subjectIds, localGroupIds);

        // Add access rights to endpoint
        addAccessRights(subjectIdsToBeAdded, clientType, endpointType);

        // Create DTOs for returning data
        return getAccessRightHolderDtosForEndpoint(clientType, endpointType);
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
     * If endpoint was not found
     */
    public static class EndpointNotFoundException extends NotFoundException {
        public static final String ERROR_ENDPOINT_NOT_FOUND = "endpoint_not_found";
        private static final String MESSAGE = "Endpoint not found for service: %s";

        public EndpointNotFoundException(String fullServiceName) {
            super(String.format(MESSAGE, fullServiceName), new ErrorDeviation(ERROR_ENDPOINT_NOT_FOUND,
                    fullServiceName));
        }

    }

    /**
     * If access right was not found
     */
    public static class AccessRightNotFoundException extends NotFoundException {
        public static final String ERROR_ACCESSRIGHT_NOT_FOUND = "accessright_not_found";

        public AccessRightNotFoundException() {
            super(new ErrorDeviation(ERROR_ACCESSRIGHT_NOT_FOUND));
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
