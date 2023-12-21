/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;

import io.vavr.control.Try;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;
import org.niis.xroad.cs.admin.api.dto.GlobalGroupUpdateDto;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.GroupMemberCount;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.SystemParameterEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMapper;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.SystemParameterRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CANNOT_ADD_MEMBER_TO_OWNERS_GROUP;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.GLOBAL_GROUP_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.GLOBAL_GROUP_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.OWNERS_GLOBAL_GROUP_CANNOT_BE_DELETED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_NOT_FOUND;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.SECURITY_SERVER_OWNERS_GROUP;

@SuppressWarnings("checkstyle:RegexpSingleline")
@Service
@Transactional
@RequiredArgsConstructor
public class GlobalGroupServiceImpl implements GlobalGroupService {
    private final AuditDataHelper auditDataHelper;
    private final GlobalGroupRepository globalGroupRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final GlobalGroupMemberRepository globalGroupMemberRepository;
    private final IdentifierRepository<SubsystemIdEntity> subsystemIds;
    private final IdentifierRepository<MemberIdEntity> memberIds;
    private final GlobalGroupMapper globalGroupMapper;
    private final ClientIdConverter clientIdConverter;

    @Override
    public List<GlobalGroup> findGlobalGroups() {
        return globalGroupRepository.findAll().stream()
                .map(globalGroupMapper::toTarget)
                .collect(toList());
    }

    @Override
    public GlobalGroup addGlobalGroup(GlobalGroup globalGroup) {
        assertGlobalGroupExists(globalGroup.getGroupCode());

        var globalGroupEntity = new GlobalGroupEntity(globalGroup.getGroupCode());
        globalGroupEntity.setDescription(globalGroup.getDescription());


        globalGroupEntity = globalGroupRepository.save(globalGroupEntity);
        addAuditData(globalGroupEntity);

        return globalGroupMapper.toTarget(globalGroupEntity);
    }

    @Override
    public GlobalGroup getGlobalGroup(String groupCode) {
        return Try.success(findGlobalGroupOrThrowException(groupCode))
                .map(globalGroupMapper::toTarget)
                .get();
    }

    @Override
    public void deleteGlobalGroupMember(String groupCode) {
        handleInternalDelete(findGlobalGroupOrThrowException(groupCode));
    }

    @Override
    public GlobalGroup updateGlobalGroupDescription(GlobalGroupUpdateDto updateDto) {
        GlobalGroupEntity globalGroup = findGlobalGroupOrThrowException(updateDto.getGroupCode());
        return handleInternalUpdate(globalGroup, updateDto);
    }

    @Override
    public List<String> addGlobalGroupMembers(String groupCode, List<String> membersToAdd) {
        final var group = findGlobalGroupOrThrowException(groupCode);

        addAuditData(group);
        verifyCompositionEditability(group.getGroupCode(), CANNOT_ADD_MEMBER_TO_OWNERS_GROUP);
        return membersToAdd.stream()
                .distinct()
                .map(clientId -> Pair.of(clientId, addGlobalGroupMember(clientId, group)))
                .filter(Pair::getValue)
                .map(Pair::getKey)
                .collect(toList());
    }

    @Override
    public int countGroupMembers(String groupCode) {
        return globalGroupRepository.countGroupMembers(groupCode);
    }

    @Override
    public Map<Integer, Long> countGroupMembers() {
        return globalGroupRepository.countGroupMembers().stream()
                .collect(Collectors.toMap(GroupMemberCount::getGroupId, GroupMemberCount::getCount));
    }

    private boolean addGlobalGroupMember(String encodedClientId, GlobalGroupEntity group) {
        final var clientId = clientIdConverter.convertId(encodedClientId);
        final ClientIdEntity clientIdEntity = getClientIdEntity(clientId);

        auditDataHelper.addListPropertyItem(RestApiAuditProperty.MEMBER_IDENTIFIERS, clientId);
        if (isNotMemberOfGroup(group, clientIdEntity)) {
            var groupMember = new GlobalGroupMemberEntity(group, clientId);
            globalGroupMemberRepository.save(groupMember);
            return true;
        }
        return false;
    }

    private ClientIdEntity getClientIdEntity(ClientId.Conf clientId) {
        if (clientId.getSubsystemCode() == null) {
            return memberIds.findOpt(MemberIdEntity.ensure(clientId))
                    .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND, clientId.toString()));
        } else {
            return subsystemIds.findOpt(SubsystemIdEntity.ensure(clientId))
                    .orElseThrow(() -> new NotFoundException(SUBSYSTEM_NOT_FOUND, clientId.toString()));
        }
    }

    private GlobalGroupEntity findGlobalGroupOrThrowException(String groupCode) {
        return globalGroupRepository.getByGroupCode(groupCode)
                .orElseThrow(() -> new NotFoundException(GLOBAL_GROUP_NOT_FOUND));
    }

    private void assertGlobalGroupExists(String code) {
        globalGroupRepository.getByGroupCode(code)
                .ifPresent(globalGroup -> {
                    throw new DataIntegrityException(GLOBAL_GROUP_EXISTS, code);
                });
    }

    private void handleInternalDelete(GlobalGroupEntity entity) {
        verifyCompositionEditability(entity.getGroupCode(), OWNERS_GLOBAL_GROUP_CANNOT_BE_DELETED);
        addAuditData(entity);
        globalGroupRepository.deleteById(entity.getId());
    }


    private GlobalGroup handleInternalUpdate(GlobalGroupEntity globalGroup, GlobalGroupUpdateDto updateDto) {
        globalGroup.setDescription(updateDto.getDescription());

        addAuditData(globalGroup);
        var savedEntity = globalGroupRepository.save(globalGroup);
        return globalGroupMapper.toTarget(savedEntity);
    }

    @Override
    public void verifyCompositionEditability(String groupCode, ErrorMessage errorMessage) {
        List<SystemParameterEntity> ownersGroupCode = systemParameterRepository.findByKey(SECURITY_SERVER_OWNERS_GROUP);
        if (isOwnersGroup(ownersGroupCode, groupCode)) {
            throw new ValidationFailureException(errorMessage);
        }
    }

    private boolean isOwnersGroup(List<SystemParameterEntity> ownersGroupCode, String groupCode) {
        return ownersGroupCode.stream()
                .map(SystemParameterEntity::getValue)
                .anyMatch(code -> code.equalsIgnoreCase(groupCode));
    }

    private void addAuditData(GlobalGroupEntity entity) {
        auditDataHelper.put(RestApiAuditProperty.CODE, entity.getGroupCode());
        auditDataHelper.put(RestApiAuditProperty.DESCRIPTION, entity.getDescription());
    }

    private boolean isNotMemberOfGroup(GlobalGroupEntity globalGroupEntity, ClientIdEntity clientIdEntity) {
        return globalGroupEntity.getGlobalGroupMembers().stream()
                .noneMatch(groupMember -> groupMember.getIdentifier().equals(clientIdEntity));
    }
}
