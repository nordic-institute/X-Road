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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.NotFoundException;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.LocalGroupRepository;
import org.niis.xroad.serverconf.entity.AccessRightEntity;
import org.niis.xroad.serverconf.entity.ClientEntity;
import org.niis.xroad.serverconf.entity.GroupMemberEntity;
import org.niis.xroad.serverconf.entity.LocalGroupEntity;
import org.niis.xroad.serverconf.entity.XRoadIdEntity;
import org.niis.xroad.serverconf.mapper.LocalGroupMapper;
import org.niis.xroad.serverconf.mapper.XRoadIdMapper;
import org.niis.xroad.serverconf.model.LocalGroup;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_DUPLICATE_LOCAL_GROUP_CODE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_LOCAL_GROUP_MEMBER_ALREADY_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_LOCAL_GROUP_MEMBER_NOT_FOUND;

/**
 * LocalGroup service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class LocalGroupService {

    public static final String LOCAL_GROUP_WITH_ID = "LocalGroup with id ";
    public static final String NOT_FOUND = " not found";
    public static final String CLIENT_WITH_ID = "client with id ";

    private final LocalGroupRepository localGroupRepository;
    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final AuditDataHelper auditDataHelper;

    /**
     * Return local group.
     * Local group members are always loaded with Hibernate.init()
     * @param groupId
     * @return the LocalGroupType, or null if not found
     */
    public LocalGroup getLocalGroup(Long groupId) {
        return LocalGroupMapper.get().toTarget(getLocalGroupEntity(groupId));
    }

    private LocalGroupEntity getLocalGroupEntity(Long groupId) {
        LocalGroupEntity localGroupType = localGroupRepository.getLocalGroup(groupId);
        if (localGroupType != null) {
            Hibernate.initialize(localGroupType.getGroupMember());
        }
        return localGroupType;
    }

    /**
     * Edit local group description
     * @return LocalGroupType
     * @throws LocalGroupNotFoundException if local group with given id was not found
     */
    public LocalGroup updateDescription(Long groupId, String description) throws LocalGroupNotFoundException {
        LocalGroupEntity localGroupType = getLocalGroupEntity(groupId);

        if (localGroupType == null) {
            throw new LocalGroupNotFoundException(LOCAL_GROUP_WITH_ID + groupId + NOT_FOUND);
        }
        auditLog(localGroupType, description);

        localGroupType.setDescription(description);
        localGroupType.setUpdated(new Date());
        return LocalGroupMapper.get().toTarget(localGroupType);
    }

    /**
     * audit log client id, group code, group description
     */
    private void auditLog(LocalGroupEntity localGroupType, String description) {
        if (localGroupType != null) {
            auditLogOwnerClientId(localGroupType);
            auditDataHelper.put(RestApiAuditProperty.GROUP_CODE, localGroupType.getGroupCode());
        }
        auditDataHelper.put(RestApiAuditProperty.GROUP_DESCRIPTION, description);
    }

    /**
     * audit log client id. Does lookup query based on local group.
     */
    private void auditLogOwnerClientId(LocalGroupEntity localGroupType) {
        try {
            auditDataHelper.put(clientRepository.getClientByLocalGroup(localGroupType).getIdentifier());
        } catch (ClientNotFoundException e) {
            // unexpected
            throw new RuntimeException("local group was not attached to client", e);
        }
    }

    /**
     * Adds a local group to a client
     * @param id
     * @param localGroupToAdd
     * @throws DuplicateLocalGroupCodeException if local group with given code already exists
     * @throws ClientNotFoundException if client with given id was not found
     */
    public LocalGroup addLocalGroup(ClientId id, LocalGroup localGroupToAdd)
            throws DuplicateLocalGroupCodeException, ClientNotFoundException {
        LocalGroupEntity localGroupTypeToAddEntity = LocalGroupMapper.get().toEntity(localGroupToAdd);


        auditDataHelper.put(id);
        auditDataHelper.put(RestApiAuditProperty.GROUP_CODE, localGroupTypeToAddEntity.getGroupCode());
        auditDataHelper.put(RestApiAuditProperty.GROUP_DESCRIPTION, localGroupTypeToAddEntity.getDescription());

        ClientEntity clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + id + NOT_FOUND);
        }
        Optional<LocalGroupEntity> existingLocalGroupType = clientType.getLocalGroup().stream()
                .filter(localGroupType -> localGroupType.getGroupCode().equals(
                        localGroupTypeToAddEntity.getGroupCode())).findFirst();
        if (existingLocalGroupType.isPresent()) {
            throw new DuplicateLocalGroupCodeException(
                    "local group with code " + localGroupTypeToAddEntity.getGroupCode() + " already added");
        }
        localGroupRepository.persist(localGroupTypeToAddEntity); // explicit persist to get the id to the return value
        clientType.getLocalGroup().add(localGroupTypeToAddEntity);
        return LocalGroupMapper.get().toTarget(localGroupTypeToAddEntity);
    }

    /**
     * Adds a members to LocalGroup
     * @param memberIds
     * @throws MemberAlreadyExistsException if given member already exists in the group
     * @throws LocalGroupNotFoundException if local group with given id was not found
     * @throws LocalGroupMemberNotFoundException if local group member was not found
     */
    public void addLocalGroupMembers(Long groupId, List<ClientId> memberIds) throws MemberAlreadyExistsException,
            LocalGroupNotFoundException, LocalGroupMemberNotFoundException {
        LocalGroupEntity localGroupType = getLocalGroupEntity(groupId);
        if (localGroupType == null) {
            throw new LocalGroupNotFoundException(LOCAL_GROUP_WITH_ID + groupId + NOT_FOUND);
        }

        auditLog(memberIds, localGroupType);

        List<GroupMemberEntity> membersToBeAdded = new ArrayList<>(memberIds.size());
        for (ClientId memberId : memberIds) {
            Optional<ClientEntity> foundMember = clientService.findEntityByClientId(memberId);
            if (!foundMember.isPresent()) {
                throw new LocalGroupMemberNotFoundException(CLIENT_WITH_ID
                        + memberId.toShortString() + NOT_FOUND);
            }
            ClientId clientIdToBeAdded = foundMember.get().getIdentifier();
            boolean isAdded = localGroupType.getGroupMember().stream()
                    .anyMatch(groupMemberType -> groupMemberType.getGroupMemberId().toShortString().trim()
                            .equals(clientIdToBeAdded.toShortString().trim()));
            if (isAdded) {
                throw new MemberAlreadyExistsException("local group member already exists in group");
            }
            GroupMemberEntity groupMemberType = new GroupMemberEntity();
            groupMemberType.setAdded(new Date());
            groupMemberType.setGroupMemberId(XRoadIdMapper.get().toEntity(clientIdToBeAdded));
            membersToBeAdded.add(groupMemberType);
        }
        // do not remove this saveOrUpdateAll - contrary to expectations hibernate does not cascade such
        // one-to-many + many-to-one construct properly
        localGroupRepository.saveOrUpdateAll(membersToBeAdded);
        localGroupType.getGroupMember().addAll(membersToBeAdded);
    }

    /**
     * Deletes a local group
     * @param groupId
     * @throws LocalGroupNotFoundException if local group with given id was not found
     * @throws ClientNotFoundException if client containing local group was not found
     */
    public void deleteLocalGroup(Long groupId) throws LocalGroupNotFoundException, ClientNotFoundException {
        LocalGroupEntity existingLocalGroupType = getLocalGroupEntity(groupId);

        if (existingLocalGroupType == null) {
            throw new LocalGroupNotFoundException(LOCAL_GROUP_WITH_ID + groupId + NOT_FOUND);
        }
        auditLog(existingLocalGroupType, existingLocalGroupType.getDescription());

        XRoadIdEntity xRoadId = XRoadIdMapper.get().toEntity(getLocalGroupIdAsXroadId(groupId));
        ClientEntity clientType = clientRepository.getClientByLocalGroup(existingLocalGroupType);

        deleteAccessRightsByXRoadId(clientType, xRoadId);
        localGroupRepository.delete(existingLocalGroupType);
    }

    /**
     * Removes access rights by XRoadId
     *
     * @param clientType
     * @param xRoadId
     */
    private void deleteAccessRightsByXRoadId(ClientEntity clientType, XRoadIdEntity xRoadId) {
        List<AccessRightEntity> acls = clientType.getAcl().stream()
                .filter(acl -> acl.getSubjectId().equals(xRoadId))
                .toList();
        clientType.getAcl().removeAll(acls);
    }

    /**
     * deletes a member from given local group
     * @param groupId local group id
     * @param items
     * @throws LocalGroupMemberNotFoundException if local group member was not found in the group
     * @throws LocalGroupNotFoundException if local group was not found
     */
    public void deleteGroupMembers(long groupId, List<ClientId> items)
            throws LocalGroupMemberNotFoundException, LocalGroupNotFoundException {
        LocalGroupEntity managedLocalGroup = getLocalGroupEntity(groupId);

        if (managedLocalGroup == null) {
            throw new LocalGroupNotFoundException(LOCAL_GROUP_WITH_ID + groupId + NOT_FOUND);
        }

        auditLog(items, managedLocalGroup);

        List<GroupMemberEntity> membersToBeRemoved = managedLocalGroup.getGroupMember().stream()
                .filter(member -> items.stream()
                        .anyMatch(item -> item.toShortString().trim()
                                .equals(member.getGroupMemberId().toShortString().trim())))
                .collect(Collectors.toList());

        // do not remove members at all if even one of them was not found
        if (membersToBeRemoved.isEmpty() || items.size() != membersToBeRemoved.size()) {
            throw new LocalGroupMemberNotFoundException("the requested group member was not found in local group");
        }
        managedLocalGroup.getGroupMember().removeAll(membersToBeRemoved);
    }

    /**
     * Audit log group owner client id (new lookup), group code, members (to add or remove)
     * @param memberIds
     * @param managedLocalGroup
     */
    private void auditLog(List<ClientId> memberIds, LocalGroupEntity managedLocalGroup) {
        auditLogOwnerClientId(managedLocalGroup);
        auditDataHelper.put(RestApiAuditProperty.GROUP_CODE, managedLocalGroup.getGroupCode());
        List<String> auditLogFormatMemberIds = memberIds.stream()
                .map(item -> item.toString()).collect(Collectors.toList());
        auditDataHelper.put(RestApiAuditProperty.MEMBER_IDENTIFIERS, auditLogFormatMemberIds);
    }

    /**
     * Verify that all given {@link Long} local group (PK) ids are real, then return them as
     * {@link LocalGroupId LocalGroupIds}
     * @param localGroupIds
     * @return
     * @throws LocalGroupNotFoundException if local group with given id was not found in database
     */
    public Set<XRoadId.Conf> getLocalGroupIdsAsXroadIds(Set<Long> localGroupIds) throws LocalGroupNotFoundException {
        Set<XRoadId.Conf> localGroupXRoadIds = new HashSet<>();
        for (Long groupId : localGroupIds) {
            LocalGroupEntity localGroup = localGroupRepository.getLocalGroup(groupId); // no need to batch
            if (localGroup == null) {
                throw new LocalGroupNotFoundException(LOCAL_GROUP_WITH_ID + groupId + NOT_FOUND);
            }
            localGroupXRoadIds.add(LocalGroupId.Conf.create(localGroup.getGroupCode()));
        }
        return localGroupXRoadIds;
    }

    /**
     * Verify that given {@link Long} local group id is real, then return it as {@link LocalGroupId LocalGroupId}
     * @param localGroupId
     * @return
     * @throws LocalGroupNotFoundException if local group with given id was not found in database
     */
    public XRoadId.Conf getLocalGroupIdAsXroadId(long localGroupId) throws LocalGroupNotFoundException {
        Set<Long> ids = new HashSet<>();
        ids.add(localGroupId);
        return getLocalGroupIdsAsXroadIds(ids).iterator().next();
    }

    /**
     * @param clientType local group owner
     * @param identifiers identifiers to check
     * @return whether all the local groups exist in LOCALGROUP table for the given client.
     * Entry in IDENTIFIER table may or may not exist
     */
    public boolean localGroupsExist(ClientEntity clientType, List<? extends XRoadId> identifiers) {
        Set<LocalGroupId> clientsLocalGroupIds = clientType.getLocalGroup().stream()
                .map(localGroup -> LocalGroupId.Conf.create(localGroup.getGroupCode()))
                .collect(Collectors.toSet());
        return clientsLocalGroupIds.containsAll(identifiers);
    }

    /**
     * Thrown when attempt to add member that already exists
     */
    public static class MemberAlreadyExistsException extends ServiceException {
        public MemberAlreadyExistsException(String s) {
            super(s, new ErrorDeviation(ERROR_LOCAL_GROUP_MEMBER_ALREADY_EXISTS));
        }
    }

    /**
     * Thrown when attempt to add member that already exists
     */
    public static class DuplicateLocalGroupCodeException extends ServiceException {
        public DuplicateLocalGroupCodeException(String s) {
            super(s, new ErrorDeviation(ERROR_DUPLICATE_LOCAL_GROUP_CODE));
        }
    }

    /**
     * If local group member was not found
     */
    public static class LocalGroupMemberNotFoundException extends NotFoundException {
        public LocalGroupMemberNotFoundException(String s) {
            super(s, new ErrorDeviation(ERROR_LOCAL_GROUP_MEMBER_NOT_FOUND));
        }
    }
}
