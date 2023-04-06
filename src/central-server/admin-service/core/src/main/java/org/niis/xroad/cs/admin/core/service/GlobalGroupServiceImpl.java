/**
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

import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.GlobalGroup;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.dto.GlobalGroupUpdateDto;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.api.service.StableSortHelper;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.SystemParameterEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.SystemParameterRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CANNOT_ADD_MEMBER_TO_OWNERS_GROUP;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.GLOBAL_GROUP_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.GLOBAL_GROUP_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.OWNERS_GLOBAL_GROUP_CANNOT_BE_DELETED;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.SECURITY_SERVER_OWNERS_GROUP;

@SuppressWarnings("checkstyle:RegexpSingleline")
@Service
@Transactional
@RequiredArgsConstructor
public class GlobalGroupServiceImpl implements GlobalGroupService {
    private final AuditDataHelper auditDataHelper;
    private final StableSortHelper stableSortHelper;
    private final GlobalGroupRepository globalGroupRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final GlobalGroupMemberRepository globalGroupMemberRepository;
    private final IdentifierRepository<SubsystemIdEntity> subsystemIds;
    private final IdentifierRepository<MemberIdEntity> memberIds;
    private final GlobalGroupMapper globalGroupMapper;
    private final GlobalGroupMemberMapper globalGroupMemberMapper;

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
    public GlobalGroup getGlobalGroup(Integer groupId) {
        return Try.success(findGlobalGroupOrThrowException(groupId))
                .map(globalGroupMapper::toTarget)
                .get();
    }

    @Override
    public void deleteGlobalGroup(Integer groupId) {
        handleInternalDelete(findGlobalGroupOrThrowException(groupId));
    }

    @Override
    public GlobalGroup updateGlobalGroupDescription(GlobalGroupUpdateDto updateDto) {
        GlobalGroupEntity globalGroup = findGlobalGroupOrThrowException(updateDto.getGroupId());
        return handleInternalUpdate(globalGroup, updateDto);
    }

    @Override
    public List<GlobalGroupMember> getGroupMembersFilterModel(Integer groupId) {
        return globalGroupMemberRepository.findByGlobalGroupId(groupId).stream()
                .map(globalGroupMemberMapper::toTarget)
                .collect(toList());
    }

    @Override
    public Page<GlobalGroupMember> findGroupMembers(GlobalGroupService.Criteria criteria, Pageable pageable) {
        return globalGroupMemberRepository.findAll(criteria, stableSortHelper.addSecondaryIdSort(pageable))
                .map(globalGroupMemberMapper::toTarget);
    }

    @Override
    public List<String> addGlobalGroupMembers(Integer groupId, List<String> membersToAdd) {
        final var group = findGlobalGroupOrThrowException(groupId);

        addAuditData(group);
        verifyCompositionEditability(group, CANNOT_ADD_MEMBER_TO_OWNERS_GROUP);
        return membersToAdd.stream()
                .distinct()
                .map(clientId -> Pair.of(clientId, addGlobalGroupMember(clientId, group)))
                .filter(Pair::getValue)
                .map(Pair::getKey)
                .collect(toList());
    }

    private boolean addGlobalGroupMember(String encodedClientId, GlobalGroupEntity group) {
        final ClientIdEntity clientIdEntity;
        final var clientId = clientIdConverter.convertId(encodedClientId);
        if (clientId.getSubsystemCode() == null) {
            clientIdEntity = memberIds.findOrCreate(MemberIdEntity.ensure(clientId));
        } else {
            clientIdEntity = subsystemIds.findOrCreate(SubsystemIdEntity.ensure(clientId));
        }
        auditDataHelper.addListPropertyItem(RestApiAuditProperty.MEMBER_IDENTIFIERS, clientId);
        if (isNotMemberOfGroup(group, clientIdEntity)) {
            var groupMember = new GlobalGroupMemberEntity(group, clientId);
            globalGroupMemberRepository.save(groupMember);
            globalGroupRepository.updateGroupMemberCount(group.getId());
            return true;
        }
        return false;
    }

    private GlobalGroupEntity findGlobalGroupOrThrowException(Integer groupId) {
        return globalGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(GLOBAL_GROUP_NOT_FOUND));
    }

    private void assertGlobalGroupExists(String code) {
        globalGroupRepository.getByGroupCode(code)
                .ifPresent(globalGroup -> {
                    throw new DataIntegrityException(GLOBAL_GROUP_EXISTS, code);
                });
    }

    private void handleInternalDelete(GlobalGroupEntity entity) {
        verifyCompositionEditability(entity, OWNERS_GLOBAL_GROUP_CANNOT_BE_DELETED);
        addAuditData(entity);
        globalGroupRepository.deleteById(entity.getId());
    }

    private GlobalGroup handleInternalUpdate(GlobalGroupEntity globalGroup, GlobalGroupUpdateDto updateDto) {
        globalGroup.setDescription(updateDto.getDescription());

        addAuditData(globalGroup);
        var savedEntity = globalGroupRepository.save(globalGroup);
        return globalGroupMapper.toTarget(savedEntity);
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

    private void verifyCompositionEditability(GlobalGroupEntity group, ErrorMessage errorMessage) {
        List<SystemParameterEntity> ownersGroupCode = systemParameterRepository.findByKey(SECURITY_SERVER_OWNERS_GROUP);
        if (isOwnersGroup(ownersGroupCode, group.getGroupCode())) {
            throw new ValidationFailureException(errorMessage);
        }
    }

    private boolean isNotMemberOfGroup(GlobalGroupEntity globalGroupEntity, ClientIdEntity clientIdEntity) {
        return globalGroupEntity.getGlobalGroupMembers().stream()
                .noneMatch(groupMember -> groupMember.getIdentifier().equals(clientIdEntity));
    }
}
