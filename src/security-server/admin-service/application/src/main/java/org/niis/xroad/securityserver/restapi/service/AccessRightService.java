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

package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalGroupInfo;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.dto.ServiceClient;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.serverconf.impl.entity.AccessRightEntity;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.niis.xroad.serverconf.impl.entity.LocalGroupEntity;
import org.niis.xroad.serverconf.impl.entity.XRoadIdEntity;
import org.niis.xroad.serverconf.impl.mapper.XRoadIdMapper;
import org.niis.xroad.serverconf.model.AccessRight;
import org.niis.xroad.serverconf.model.LocalGroup;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

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

import static java.util.stream.Collectors.groupingBy;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ACCESS_RIGHT_NOT_FOUND;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.DUPLICATE_ACCESS_RIGHT;

/**
 * Service class for handling access rights.
 * This service has several methods that return "access rights holders".
 * This is a synonym for "service clients", and those methods return ServiceClientDtos
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AccessRightService {

    private final GlobalConfProvider globalConfProvider;
    private final ClientRepository clientRepository;
    private final IdentifierService identifierService;
    private final EndpointService endpointService;
    private final AuditDataHelper auditDataHelper;
    private final ServiceDescriptionService serviceDescriptionService;
    private final ClientService clientService;
    private final GlobalConfService globalConfService;
    private final LocalGroupService localGroupService;

    /**
     * Remove AccessRights from a Service
     * <p>
     * Does not really need full service code, and versionless service code would be more logical parameter.
     * But controller cannot currently extract version code from full service code, since we use dot as a separator.
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @throws AccessRightNotFoundException if tried to remove access rights that did not exist for the service
     * @throws ClientNotFoundException      if client with given id was not found
     * @throws ServiceNotFoundException     if service with given fullServicecode, or the base endpoint for it,
     *                                      was not found
     */
    public void deleteSoapServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds)
            throws ClientNotFoundException, AccessRightNotFoundException,
                   ServiceNotFoundException {

        ClientEntity clientEntity = clientService.getLocalClientEntityOrThrowNotFound(clientId);

        EndpointEntity endpointEntity = endpointService.getBaseEndpointEntity(clientEntity, fullServiceCode);

        addAuditData(clientId, subjectIds, endpointEntity.getServiceCode());

        deleteEndpointAccessRights(clientEntity, endpointEntity, XRoadIdMapper.get().toSubjects(subjectIds));
    }

    /**
     * Adds clientId, serviceCodes, and subjectId
     */
    private void addAuditData(ClientId clientId, XRoadId subjectId, Set<String> serviceCodes) {
        auditDataHelper.put(clientId);
        auditDataHelper.put(RestApiAuditProperty.SUBJECT_ID, subjectId.toString());
        auditDataHelper.put(RestApiAuditProperty.SERVICE_CODES, serviceCodes);
    }

    /**
     * Adds clientId, serviceCode, and subjectIds
     */
    private void addAuditData(ClientId clientId, Set<? extends XRoadId> subjectIds, String serviceCode) {
        auditDataHelper.put(clientId);
        auditDataHelper.put(RestApiAuditProperty.SERVICE_CODE, serviceCode);
        if (subjectIds != null) {
            subjectIds.forEach(id -> auditDataHelper.addListPropertyItem(
                    RestApiAuditProperty.SUBJECT_IDS, id.toString()));
        }
    }

    /**
     * Remove access rights from endpoint
     * @param endpointId
     * @param subjectIds
     * @throws EndpointNotFoundException    if endpoint by given id is not found
     * @throws ClientNotFoundException      if client attached to endpoint is not found
     * @throws AccessRightNotFoundException if at least one access right expected is not found
     */
    public void deleteEndpointAccessRights(Long endpointId, Set<? extends XRoadId> subjectIds)
            throws EndpointNotFoundException, ClientNotFoundException, AccessRightNotFoundException {

        ClientEntity clientEntity = clientRepository.getClientByEndpointId(endpointId);
        EndpointEntity endpointEntity = endpointService.getEndpointEntity(endpointId);
        deleteEndpointAccessRights(clientEntity, endpointEntity, XRoadIdMapper.get().toSubjects(subjectIds));
    }

    /**
     * Remove access rights from one endpoint
     * @param clientEntity   clientEntity
     * @param endpointEntity endpointEntity
     * @param subjectIds     subjectIds
     * @throws AccessRightNotFoundException if subjects did not have access rights that were attempted to be deleted
     */
    private void deleteEndpointAccessRights(ClientEntity clientEntity,
                                            EndpointEntity endpointEntity,
                                            Set<XRoadIdEntity> subjectIds)
            throws AccessRightNotFoundException {

        deleteEndpointAccessRights(clientEntity, Collections.singletonList(endpointEntity), subjectIds);
    }

    /**
     * delete access rights of multiple subjectIds from one endpoint, or multiple endpoints for one subject,
     * or multiple-for-multiple.
     * <p>
     * Deleting access rights to multiple endpoints from multiple
     * subjects will probably not be used, but if it is, all endpoints have to exist for all subjects, otherwise
     * exception is thrown.
     * @param clientEntity     clientEntity
     * @param endpointEntities endpointEntities
     * @param subjectIds       subjectIds
     * @throws AccessRightNotFoundException if subjects did not have access rights that were attempted to be deleted
     */
    private void deleteEndpointAccessRights(ClientEntity clientEntity, List<EndpointEntity> endpointEntities,
                                            Set<XRoadIdEntity> subjectIds) throws AccessRightNotFoundException {

        for (EndpointEntity endpointEntity : endpointEntities) {
            // check that all access rights exist and can be deleted
            List<AccessRightEntity> accessRightsToBeRemoved = clientEntity.getAccessRights().stream()
                    .filter(acl -> acl.getEndpoint().getId().equals(endpointEntity.getId())
                            && subjectIds.contains(acl.getSubjectId()))
                    .toList();
            if (accessRightsToBeRemoved.size() != subjectIds.size()) {
                throw new AccessRightNotFoundException("All local service client identifiers + "
                        + subjectIds
                        + " weren't found in the access rights list of the given client: "
                        + clientEntity.getIdentifier());
            }
            clientEntity.getAccessRights().removeAll(accessRightsToBeRemoved);
        }
    }


    /**
     * Adds access rights to SOAP services. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     * <p>
     * Does not really need full service code, and versionless service code would be more logical parameter.
     * But controller cannot currently extract version code from full service code, since we use dot as a separator.
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @return List of {@link ServiceClient AccessRightHolderDtos}
     * @throws ClientNotFoundException        exception
     * @throws ServiceNotFoundException       if service with given fullServicecode, or the base endpoint for it,
     *                                        was not found
     * @throws DuplicateAccessRightException  exception
     * @throws ServiceClientNotFoundException if a service client (local group, global group, or system) matching given
     *                                        subjectId did not exist
     */
    public List<ServiceClient> addSoapServiceAccessRights(
            ClientId clientId,
            String fullServiceCode,
            Set<XRoadId.Conf> subjectIds) throws ClientNotFoundException,
                                                 ServiceNotFoundException,
                                                 DuplicateAccessRightException,
                                                 ServiceClientNotFoundException {

        ClientEntity clientEntity = clientService.getLocalClientEntityOrThrowNotFound(clientId);

        EndpointEntity endpointEntity = endpointService.getBaseEndpointEntity(clientEntity, fullServiceCode);

        addAuditData(clientId, subjectIds, endpointEntity.getServiceCode());

        // Combine subject ids and localgroup ids to a single list of XRoadIds
        return addEndpointAccessRights(clientEntity, endpointEntity, XRoadIdMapper.get().toEntities(subjectIds));
    }

    /**
     * Adds access rights to endpoint. If the provided {@code subjectIds} do not exist in the serverconf db
     * they will first be validated (that they exist in global conf) and then saved into the serverconf db.
     * LocalGroup ids will also be verified and if they don't exist in the serverconf db they will be saved
     * @param endpointId
     * @param subjectIds
     * @return
     * @throws EndpointNotFoundException      endpoint is not found with given id
     * @throws ClientNotFoundException        client for the endpoint is not found (shouldn't happen)
     * @throws ServiceClientNotFoundException if a service client (local group, global group, or system) matching given
     *                                        subjectId did not exist
     * @throws DuplicateAccessRightException  Trying to add duplicate access rights
     */
    public List<ServiceClient> addEndpointAccessRights(Long endpointId, Set<XRoadId.Conf> subjectIds)
            throws EndpointNotFoundException, ClientNotFoundException, ServiceClientNotFoundException, DuplicateAccessRightException {

        EndpointEntity endpointEntity = endpointService.getEndpointEntity(endpointId);

        ClientEntity clientEntity = clientRepository.getClientByEndpointId(endpointId);
        return addEndpointAccessRights(clientEntity, endpointEntity, XRoadIdMapper.get().toEntities(subjectIds));

    }

    /**
     * Add access rights for (possibly) multiple subjects, to a single endpoint.
     * @throws ServiceClientNotFoundException if a service client (local group, global group, or system) matching given
     *                                        subjectId did not exist
     * @throws DuplicateAccessRightException
     */
    private List<ServiceClient> addEndpointAccessRights(
            ClientEntity clientEntity,
            EndpointEntity endpointEntity,
            Set<XRoadIdEntity> subjectIds) throws DuplicateAccessRightException, ServiceClientNotFoundException {

        subjectIds.forEach(this::validateServiceClientObjectType);

        // verify that all subject ids exist
        verifyServiceClientObjectsExist(clientEntity, subjectIds);

        // Get all ids from serverconf db IDENTIFIER table - or add them if they don't exist
        Set<XRoadIdEntity> managedIds = identifierService.getOrPersistXroadIdEntities(subjectIds);

        // Add access rights to endpoint
        addAccessRightsInternal(managedIds, clientEntity, Collections.singletonList(endpointEntity));

        // Create DTOs for returning data
        List<AccessRightEntity> accessRightsByEndpoint = getAccessRightsByEndpoint(clientEntity, endpointEntity);
        return mapAccessRightsToServiceClients(clientEntity, accessRightsByEndpoint);
    }

    /**
     * Add access rights for one subject (service client) to multiple services (serviceCodes)
     * of a client (clientType). Access rights are added only to the base endpoint of given service.
     * @param clientId     id of the client who owns the services
     * @param serviceCodes serviceCodes of the services to add access rights to (without version numbers)
     * @param subjectId    subject (service client) to add access rights for. Can be a local group,
     *                     global group, or a subsystem
     * @return ServiceClientAccessRightDtos that were added for this service client
     * @throws ServiceNotFoundException       if serviceCodes had any codes that were not client's services
     *                                        (did not have base endpoints)
     * @throws ClientNotFoundException        if client matching clientId was not found
     * @throws DuplicateAccessRightException  if trying to add existing access right
     * @throws ServiceClientNotFoundException if a service client (local group, global group, or system) matching given
     *                                        subjectId did not exist
     */
    public List<ServiceClientAccessRightDto> addServiceClientAccessRights(
            ClientId clientId,
            Set<String> serviceCodes,
            XRoadId.Conf subjectId) throws ServiceNotFoundException,
                                           DuplicateAccessRightException,
                                           ClientNotFoundException,
                                           ServiceClientNotFoundException {

        addAuditData(clientId, subjectId, serviceCodes);

        ClientEntity clientEntity = clientService.getLocalClientEntityOrThrowNotFound(clientId);

        validateServiceClientObjectType(subjectId);

        XRoadIdEntity subjectIdEntity = XRoadIdMapper.get().toEntity(subjectId);

        // verify that given service client objects exist, otherwise access cannot be added
        verifyServiceClientObjectsExist(clientEntity, Set.of(subjectIdEntity));

        // prepare params for addAccessRightsInternal
        List<EndpointEntity> baseEndpoints = null;
        try {
            baseEndpoints = endpointService.getServiceBaseEndpointEntities(clientEntity, serviceCodes);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }

        // make sure subject id exists in serverconf db IDENTIFIER table, and use a managed entity
        XRoadIdEntity managedSubjectId = identifierService.getOrPersistXroadIdEntity(subjectIdEntity);

        return addAccessRightsInternal(new HashSet<>(Arrays.asList(managedSubjectId)), clientEntity, baseEndpoints)
                .get(managedSubjectId);
    }

    /**
     * Removes access rights from one subject (service client) to multiple services (serviceCodes)
     * of a client. Access rights are removed from base endpoint and also from non-base endpoints with
     * given serviceCode.
     * @param clientId     id of the client who owns the services
     * @param serviceCodes serviceCodes of the services to remove access rights to (without version numbers)
     * @param subjectId    subject (service client) to remove access rights from. Can be a local group,
     *                     global group, or a subsystem
     * @throws AccessRightNotFoundException if trying to remove (any) access rights that did not exist
     * @throws ClientNotFoundException      if client matching clientId was not found
     * @throws ServiceNotFoundException     if given client did not have services with given serviceCodes
     */
    public void deleteServiceClientAccessRights(ClientId clientId,
                                                Set<String> serviceCodes,
                                                XRoadId subjectId) throws AccessRightNotFoundException,
                                                                          ClientNotFoundException,
                                                                          ServiceNotFoundException {

        addAuditData(clientId, subjectId, serviceCodes);

        ClientEntity clientEntity = clientService.getLocalClientEntityOrThrowNotFound(clientId);

        validateServiceClientObjectType(subjectId);

        // first delete base endpoint access rights. These all need to exist, otherwise AccessRightNotFoundException
        List<EndpointEntity> baseEndpoints = null;
        try {
            baseEndpoints = endpointService.getServiceBaseEndpointEntities(clientEntity, serviceCodes);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }
        Set<XRoadIdEntity> subjectIds = new HashSet<>(Collections.singletonList(XRoadIdMapper.get().toEntity(subjectId)));
        deleteEndpointAccessRights(clientEntity, baseEndpoints, subjectIds);

        // then delete all non-base endpoint access rights, for this subject. If there's none, that's fine
        List<EndpointEntity> allEndpoints = endpointService.getServiceEndpointEntities(clientEntity, serviceCodes);
        List<AccessRightEntity> remainingAccessRights = getEndpointAccessRights(clientEntity, allEndpoints, subjectIds);
        if (!remainingAccessRights.isEmpty()) {
            Set<EndpointEntity> endpointsWithAccessRights = remainingAccessRights.stream()
                    .map(AccessRightEntity::getEndpoint)
                    .collect(Collectors.toSet());
            deleteEndpointAccessRights(clientEntity, new ArrayList<>(endpointsWithAccessRights), subjectIds);
        }
    }

    /**
     * Get client's acl entries that match endpoints and subjects
     */
    private List<AccessRightEntity> getEndpointAccessRights(ClientEntity clientEntity, List<EndpointEntity> endpointEntities,
                                                            Set<XRoadIdEntity> subjectIds) {

        List<Long> endpointIds = endpointEntities.stream().map(EndpointEntity::getId).toList();
        return clientEntity.getAccessRights().stream()
                .filter(acl -> endpointIds.contains(acl.getEndpoint().getId())
                        && subjectIds.contains(acl.getSubjectId()))
                .collect(Collectors.toList());
    }

    /**
     * Check that subjectId has correct Object type
     * @param subjectId
     */
    private void validateServiceClientObjectType(XRoadId subjectId) {
        if (subjectId == null) {
            throw new IllegalArgumentException("missing subjectId");
        }
        XRoadObjectType objectType = subjectId.getObjectType();
        if (!isValidServiceClientType(objectType)) {
            throw new IllegalArgumentException("Invalid object type " + objectType);
        }
    }


    private boolean isValidServiceClientType(XRoadObjectType objectType) {
        return objectType == XRoadObjectType.SUBSYSTEM
                || objectType == XRoadObjectType.GLOBALGROUP
                || objectType == XRoadObjectType.LOCALGROUP;
    }

    /**
     * Get access right holders (serviceClients) for endpoint
     * @param clientEntity        clientEntity
     * @param accessRightEntities accessRightEntities
     * @return List<ServiceClient>
     */
    public List<ServiceClient> mapAccessRightsToServiceClients(ClientEntity clientEntity, List<AccessRightEntity> accessRightEntities) {
        Map<String, LocalGroupEntity> localGroupMap = new HashMap<>();
        clientEntity.getLocalGroups().forEach(localGroupEntity -> localGroupMap.put(localGroupEntity.getGroupCode(), localGroupEntity));

        return accessRightEntities.stream()
                .map((accessRightEntity -> accessRightEntityToServiceClientDto(accessRightEntity, localGroupMap)))
                .collect(Collectors.toList());
    }

    /**
     * Makes an {@link ServiceClient} out of {@link AccessRight}
     * @param accessRightEntity The accessRightEntity to convert from
     * @param localGroupMap     A Map containing {@link LocalGroup LocalGroup} mapped by
     *                          their corresponding {@link LocalGroup#getGroupCode()}
     * @return ServiceClient
     */
    ServiceClient accessRightEntityToServiceClientDto(AccessRightEntity accessRightEntity,
                                                      Map<String, LocalGroupEntity> localGroupMap) {
        ServiceClient serviceClient = new ServiceClient();
        XRoadId subjectId = accessRightEntity.getSubjectId();
        serviceClient.setRightsGiven(
                FormatUtils.fromDateToOffsetDateTime(accessRightEntity.getRightsGiven()));
        serviceClient.setSubjectId(subjectId);
        switch (subjectId.getObjectType()) {
            case XRoadObjectType.LOCALGROUP -> {
                LocalGroupId localGroupId = (LocalGroupId) subjectId;
                LocalGroupEntity localGroupEntity = localGroupMap.get(localGroupId.getGroupCode());
                serviceClient.setLocalGroupId(localGroupEntity.getId().toString());
                serviceClient.setLocalGroupCode(localGroupEntity.getGroupCode());
                serviceClient.setLocalGroupDescription(localGroupEntity.getDescription());
            }
            case XRoadObjectType.GLOBALGROUP -> {
                GlobalGroupId globalGroupId = (GlobalGroupId) subjectId;
                serviceClient.setGlobalGroupDescription(globalConfProvider.getGlobalGroupDescription(globalGroupId));
            }
            case XRoadObjectType.SUBSYSTEM -> serviceClient.setSubsystemName(globalConfProvider.getSubsystemName((ClientId) subjectId));
            case XRoadObjectType.MEMBER -> serviceClient.setMemberName(globalConfProvider.getMemberName((ClientId) subjectId));
            case null, default -> {
            }
        }
        return serviceClient;
    }

    /**
     * Get access rights of an endpoint
     * @param clientEntity   clientEntity
     * @param endpointEntity endpointEntity
     * @return List<AccessRightEntity>
     */
    List<AccessRightEntity> getAccessRightsByEndpoint(ClientEntity clientEntity, EndpointEntity endpointEntity) {
        return clientEntity.getAccessRights().stream()
                .filter(accessRightEntity -> accessRightEntity.getEndpoint().getId().equals(endpointEntity.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Add access rights for (possibly) multiple subjects, to (possibly) multiple endpoints.
     * <p>
     * This method is not intended for use from outside, but is package protected for tests.
     * <p>
     * Note that subjectIds need to be managed entities.
     * @param subjectIds   *managed* access rights subjects to grant access for, "service clients"
     * @param clientEntity endpoint owner
     * @param endpoints    endpoints to add access rights to
     * @return map, key = subjectId (service client), value = list of access rights added for the subject
     * @throws DuplicateAccessRightException if trying to add existing access right
     */
    Map<XRoadIdEntity, List<ServiceClientAccessRightDto>> addAccessRightsInternal(Set<XRoadIdEntity> subjectIds,
                                                                                  ClientEntity clientEntity,
                                                                                  List<EndpointEntity> endpoints)
            throws DuplicateAccessRightException {
        Date now = new Date();

        if (subjectIds == null || subjectIds.isEmpty()) {
            throw new IllegalArgumentException("missing subjectIds");
        }
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("missing endpoints");
        }

        Map<XRoadIdEntity, List<ServiceClientAccessRightDto>> addedAccessRights = new HashMap<>();

        for (EndpointEntity endpoint : endpoints) {
            for (XRoadIdEntity subjectId : subjectIds) {
                ServiceClientAccessRightDto dto = addAccessRightInternal(clientEntity, now, endpoint, subjectId);
                addedAccessRights.computeIfAbsent(subjectId, k -> new ArrayList<>()).add(dto);
            }
        }
        clientRepository.merge(clientEntity);

        return addedAccessRights;
    }

    /**
     * Add access right for a single subject (subjectId), to a single endpoint (endpoint)
     * that belongs to client. Assumes parameters are already validated, service client objects
     * for subjectId exist
     * @param clientEntity   clientEntity
     * @param rightsGiven    rightsGiven
     * @param endpointEntity endpointEntity
     * @param subjectId      subjectId
     * @return ServiceClientAccessRightDto
     * @throws DuplicateAccessRightException if access right already exists
     */
    private ServiceClientAccessRightDto addAccessRightInternal(ClientEntity clientEntity, Date rightsGiven,
                                                               EndpointEntity endpointEntity, XRoadIdEntity subjectId)
            throws DuplicateAccessRightException {

        // some sanity checks for the parameters. These should be covered already in earlier methods,
        // but add some extra defensive programming
        // Throw runtime exceptions if these checks fail
        validateServiceClientObjectType(subjectId);

        List<LocalGroupEntity> clientLocalGroups = clientEntity.getLocalGroups();

        if (subjectId.getObjectType() == XRoadObjectType.LOCALGROUP) {
            LocalGroupId localGroupId = (LocalGroupId) subjectId;
            boolean localGroupNotFound = clientLocalGroups.stream()
                    .noneMatch(localGroupEntity -> localGroupEntity.getGroupCode()
                            .equals(localGroupId.getGroupCode()));
            if (localGroupNotFound) {
                String errorMsg = String.format("LocalGroup with the groupCode %s does not belong to client %s",
                        subjectId.toShortString(), clientEntity.getIdentifier().toShortString());
                throw new IllegalStateException(errorMsg);
            }
        }

        // list endpoints, which this subject / service client has already been granted access to
        Set<EndpointEntity> existingAccessibleEndpoints = clientEntity.getAccessRights().stream()
                .filter(accessRightEntity -> accessRightEntity.getSubjectId().equals(subjectId))
                .map(AccessRightEntity::getEndpoint)
                .collect(Collectors.toSet());

        if (existingAccessibleEndpoints.contains(endpointEntity)) {
            throw new DuplicateAccessRightException("Subject " + subjectId.toShortString()
                    + " already has an access right for endpoint " + endpointEntity.getId());
        }

        AccessRightEntity newAccessRight = new AccessRightEntity();
        newAccessRight.setEndpoint(endpointEntity);
        newAccessRight.setSubjectId(subjectId);
        newAccessRight.setRightsGiven(rightsGiven);
        clientEntity.getAccessRights().add(newAccessRight);

        // return a dto
        return ServiceClientAccessRightDto.builder()
                .serviceCode(endpointEntity.getServiceCode())
                .rightsGiven(FormatUtils.fromDateToOffsetDateTime(rightsGiven))
                .title(serviceDescriptionService.getServiceTitle(clientEntity, endpointEntity.getServiceCode()))
                .build();
    }

    /**
     * If access right was not found
     */
    public static class AccessRightNotFoundException extends NotFoundException {
        public AccessRightNotFoundException(String s) {
            super(s, ACCESS_RIGHT_NOT_FOUND.build());
        }
    }

    /**
     * If duplicate access right was found
     */
    public static class DuplicateAccessRightException extends ConflictException {
        public DuplicateAccessRightException(String msg) {
            super(msg, DUPLICATE_ACCESS_RIGHT.build());
        }

    }

    /**
     * Find access right holder (serviceClient) candidates by search terms
     * @param clientId
     * @param subjectType                  search term for subjectType. Null or empty value is considered a match
     * @param memberNameOrGroupDescription search term for memberName or groupDescription (depending on subject's type).
     *                                     Null or empty value is considered a match
     * @param instance                     search term for instance. Null or empty value is considered a match
     * @param memberClass                  search term for memberClass. Null or empty value is considered a match
     * @param memberGroupCode              search term for memberCode or groupCode (depending on subject's type).
     *                                     Null or empty value is considered a match
     * @param subsystemCode                search term for subsystemCode. Null or empty value is considered a match
     * @return A List of {@link ServiceClient serviceClients} or an empty List if nothing is found
     * @throws ClientNotFoundException if client with given id was not found
     */
    public List<ServiceClient> findAccessRightHolderCandidates(ClientId clientId,
                                                               String memberNameOrGroupDescription,
                                                               XRoadObjectType subjectType,
                                                               String instance,
                                                               String memberClass,
                                                               String memberGroupCode,
                                                               String subsystemCode) throws ClientNotFoundException {
        List<ServiceClient> dtos = new ArrayList<>();

        // get client
        ClientEntity client = clientService.getLocalClientEntityOrThrowNotFound(clientId);

        // get global members
        List<ServiceClient> globalMembers = getGlobalMembersAsDtos();
        if (!globalMembers.isEmpty()) {
            dtos.addAll(globalMembers);
        }

        // get global groups
        List<ServiceClient> globalGroups = getGlobalGroupsAsDtos(instance);
        if (!globalMembers.isEmpty()) {
            dtos.addAll(globalGroups);
        }

        // get local groups
        List<ServiceClient> localGroups = getLocalGroupsAsDtos(client.getLocalGroups());
        if (!localGroups.isEmpty()) {
            dtos.addAll(localGroups);
        }

        Predicate<ServiceClient> matchingSearchTerms = buildSubjectSearchPredicate(subjectType,
                memberNameOrGroupDescription, instance, memberClass, memberGroupCode, subsystemCode);

        return dtos.stream()
                .filter(matchingSearchTerms)
                .collect(Collectors.toList());
    }

    private List<ServiceClient> getLocalGroupsAsDtos(List<LocalGroupEntity> localGroupEntities) {
        return localGroupEntities.stream()
                .map(localGroup -> {
                    ServiceClient serviceClient = new ServiceClient();
                    serviceClient.setLocalGroupId(localGroup.getId().toString());
                    serviceClient.setLocalGroupCode(localGroup.getGroupCode());
                    serviceClient.setSubjectId(LocalGroupId.Conf.create(localGroup.getGroupCode()));
                    serviceClient.setLocalGroupDescription(localGroup.getDescription());
                    return serviceClient;
                }).collect(Collectors.toList());
    }

    private List<ServiceClient> getGlobalMembersAsDtos() {
        return globalConfProvider.getMembers().stream()
                .map(memberInfo -> {
                    ServiceClient serviceClient = new ServiceClient();
                    serviceClient.setSubjectId(memberInfo.id());
                    serviceClient.setMemberName(memberInfo.name());
                    serviceClient.setSubsystemName(memberInfo.subsystemName());
                    return serviceClient;
                })
                .collect(Collectors.toList());
    }

    private List<ServiceClient> getGlobalGroupsAsDtos(String instance) {
        List<ServiceClient> globalGroups = new ArrayList<>();
        Set<String> globalGroupInstances = globalConfProvider.getInstanceIdentifiers();
        List<GlobalGroupInfo> globalGroupInfos = null;
        // core throws CodedException if nothing is found for the provided instance/instances
        try {
            if (!StringUtils.isEmpty(instance)) {
                List<String> globalGroupInstancesMatchingSearch = globalGroupInstances.stream()
                        .filter(s -> s.contains(instance))
                        .toList();
                if (!globalGroupInstancesMatchingSearch.isEmpty()) {
                    globalGroupInfos = globalConfProvider
                            .getGlobalGroups(globalGroupInstancesMatchingSearch.toArray(new String[]{}));
                }
            } else {
                globalGroupInfos = globalConfProvider.getGlobalGroups();
            }
        } catch (CodedException e) {
            // no GlobalGroups found for the provided instance -> GlobalGroups are just ignored in the results
        }
        if (globalGroupInfos != null && !globalGroupInfos.isEmpty()) {
            globalGroupInfos.forEach(globalGroupInfo -> {
                ServiceClient serviceClient = new ServiceClient();
                serviceClient.setSubjectId(globalGroupInfo.id());
                serviceClient.setGlobalGroupDescription(globalGroupInfo.description());
                globalGroups.add(serviceClient);
            });
        }
        return globalGroups;
    }

    /**
     * Composes a {@link Predicate} that will be used to filter {@link ServiceClient ServiceClients}
     * against the given search terms. The given ServiceClientDto has a {@link ServiceClient#getSubjectId()}
     * which can be of type {@link GlobalGroupId}, {@link LocalGroupId} or {@link ClientId}. When evaluating the
     * Predicate the type of the Subject will be taken in account for example when testing if the search term
     * {@code memberGroupCode} matches
     * @param subjectType                  search term for subjectType. Null or empty value is considered a match
     * @param memberNameOrGroupDescription search term for memberName or groupDescription (depending on subject's type).
     *                                     Null or empty value is considered a match
     * @param instance                     search term for instance. Null or empty value is considered a match
     * @param memberClass                  search term for memberClass. Null or empty value is considered a match
     * @param memberGroupCode              search term for memberCode or groupCode (depending on subject's type).
     *                                     Null or empty value is considered a match
     * @param subsystemCode                search term for subsystemCode. Null or empty value is considered a match
     * @return Predicate
     */
    private Predicate<ServiceClient> buildSubjectSearchPredicate(XRoadObjectType subjectType,
                                                                 String memberNameOrGroupDescription,
                                                                 String instance,
                                                                 String memberClass,
                                                                 String memberGroupCode,
                                                                 String subsystemCode) {
        // Start by assuming the search is a match. If there are no search terms --> return all
        Predicate<ServiceClient> searchPredicate = accessRightHolderDto -> true;

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

    private Predicate<ServiceClient> getSubjectMemberOrGroupCodePredicate(String memberGroupCode) {
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

    private Predicate<ServiceClient> getSubjectSubsystemCodePredicate(String subsystemCode) {
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

    private Predicate<ServiceClient> getSubjectMemberClassPredicate(String memberClass) {
        return dto -> {
            XRoadId xRoadId = dto.getSubjectId();
            if (xRoadId instanceof ClientId) {
                String clientMemberClass = ((ClientId) xRoadId).getMemberClass();
                return memberClass.equalsIgnoreCase(clientMemberClass);
            } else {
                return false;
            }
        };
    }

    private Predicate<ServiceClient> getSubjectInstancePredicate(String instance) {
        return dto -> {
            XRoadId xRoadId = dto.getSubjectId();
            // In case the Subject is a LocalGroup: LocalGroups do not have explicit X-Road instances
            // -> always return true
            if (xRoadId instanceof LocalGroupId) {
                return true;
            } else {
                return instance.equalsIgnoreCase(dto.getSubjectId().getXRoadInstance());
            }
        };
    }

    private Predicate<ServiceClient> getMemberNameOrGroupDescriptionPredicate(String memberNameOrGroupDescription) {
        return dto -> {
            String memberName = dto.getMemberName();
            String subsystemName = dto.getSubsystemName();
            String localGroupDescription = dto.getLocalGroupDescription();
            String globalGroupDescription = dto.getGlobalGroupDescription();
            return StringUtils.containsIgnoreCase(memberName, memberNameOrGroupDescription)
                    || StringUtils.containsIgnoreCase(subsystemName, memberNameOrGroupDescription)
                    || StringUtils.containsIgnoreCase(localGroupDescription, memberNameOrGroupDescription)
                    || StringUtils.containsIgnoreCase(globalGroupDescription, memberNameOrGroupDescription);
        };
    }

    /**
     * Verify that service client objects identified by given XRoadIds do exist.
     * Criteria in detail:
     * - subsystem is registered in global configuration
     * - global group exists in global configuration
     * - local group exists and belongs to given client
     * @param clientEntity owner of (possible) local groups
     * @param serviceClientIds service client ids to check
     * @throws ServiceClientNotFoundException if some service client objects could not be found
     */
    private void verifyServiceClientObjectsExist(ClientEntity clientEntity, Set<XRoadIdEntity> serviceClientIds)
            throws ServiceClientNotFoundException {
        Map<XRoadObjectType, List<XRoadIdEntity>> idsPerType = serviceClientIds.stream()
                .collect(groupingBy(XRoadIdEntity::getObjectType));
        for (XRoadObjectType type : idsPerType.keySet()) {
            if (!isValidServiceClientType(type)) {
                throw new ServiceClientNotFoundException("Invalid service client subject object type " + type);
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.GLOBALGROUP)) {
            if (!globalConfService.globalGroupsExist(idsPerType.get(XRoadObjectType.GLOBALGROUP))) {
                throw new ServiceClientNotFoundException();
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.SUBSYSTEM)) {
            if (!globalConfService.clientsExist(idsPerType.get(XRoadObjectType.SUBSYSTEM))) {
                throw new ServiceClientNotFoundException();
            }
        }
        if (idsPerType.containsKey(XRoadObjectType.LOCALGROUP)) {
            if (!localGroupService.localGroupsExist(clientEntity, idsPerType.get(XRoadObjectType.LOCALGROUP))) {
                throw new ServiceClientNotFoundException();
            }
        }
    }
}
