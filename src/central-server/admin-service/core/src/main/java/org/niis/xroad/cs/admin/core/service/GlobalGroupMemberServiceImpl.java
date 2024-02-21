/*
 * The MIT License
 * <p>
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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMemberView;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.paging.PageRequestDto;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.core.converter.PageConverter;
import org.niis.xroad.cs.admin.core.converter.PageRequestDtoConverter;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberViewMapper;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMembersViewRepository;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.GLOBAL_GROUP_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.OWNERS_GLOBAL_GROUP_MEMBER_CANNOT_BE_DELETED;

@Service
@Transactional
@RequiredArgsConstructor
public class GlobalGroupMemberServiceImpl implements GlobalGroupMemberService {

    private final GlobalGroupMemberRepository globalGroupMemberRepository;
    private final GlobalGroupRepository globalGroupRepository;
    private final XRoadMemberRepository xRoadMemberRepository;
    private final GlobalGroupMembersViewRepository globalGroupMembersViewRepository;
    private final GlobalGroupService globalGroupService;
    private final GlobalGroupMemberMapper globalGroupMemberMapper;
    private final GlobalGroupMemberViewMapper globalGroupMemberViewMapper;
    private final PageRequestDtoConverter pageRequestDtoConverter;
    private final PageConverter pageConverter;
    private final AuditDataHelper auditDataHelper;
    private final StableSortHelper stableSortHelper;

    @Override
    public Page<GlobalGroupMemberView> find(GlobalGroupMemberService.Criteria criteria, PageRequestDto pageRequest) {
        var pageable = stableSortHelper.addSecondaryIdSort(pageRequestDtoConverter.convert(pageRequest));

        var result = globalGroupMembersViewRepository.findAll(criteria, pageable)
                .map(globalGroupMemberViewMapper::toTarget);
        return pageConverter.convert(result);
    }

    @Override
    public List<GlobalGroupMember> findByGroupCode(String groupCode) {
        return globalGroupMemberRepository.findByGlobalGroupGroupCode(groupCode).stream()
                .map(globalGroupMemberMapper::toTarget)
                .collect(toList());
    }

    @Override
    public void addMemberToGlobalGroup(MemberId memberId, String groupCode) {
        final XRoadMemberEntity memberEntity = getMemberIdEntity(memberId);
        final GlobalGroupEntity globalGroupEntity = getGlobalGroupEntity(groupCode);
        if (!isMemberAlreadyInGroup(globalGroupEntity, memberEntity)) {
            final var globalGroupMemberEntity = new GlobalGroupMemberEntity(globalGroupEntity, memberEntity.getIdentifier());

            globalGroupMemberRepository.save(globalGroupMemberEntity);
        }
    }

    @Override
    public void removeMemberFromGlobalGroup(String groupCode, String memberId) {
        final var globalGroup = getGlobalGroupEntity(groupCode);
        final var globalGroupMemberEntity = globalGroup.getGlobalGroupMembers().stream()
                .filter(groupMember -> groupMember.getIdentifier().asEncodedId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND, memberId));

        auditDataHelper.put(RestApiAuditProperty.CODE, globalGroup.getGroupCode());
        auditDataHelper.put(RestApiAuditProperty.DESCRIPTION, globalGroup.getDescription());
        auditDataHelper.put(RestApiAuditProperty.MEMBER_IDENTIFIERS, singletonList(globalGroupMemberEntity.getIdentifier().asEncodedId()));

        globalGroupService.verifyCompositionEditability(globalGroup.getGroupCode(), OWNERS_GLOBAL_GROUP_MEMBER_CANNOT_BE_DELETED);

        globalGroupMemberRepository.delete(globalGroupMemberEntity);
    }

    @Override
    public void removeMemberFromGlobalGroup(String groupCode, MemberId memberId) {
        final XRoadMemberEntity memberEntity = getMemberIdEntity(memberId);
        final GlobalGroupEntity globalGroupEntity = getGlobalGroupEntity(groupCode);

        final Optional<GlobalGroupMemberEntity> globalGroupMember = globalGroupEntity.getGlobalGroupMembers().stream()
                .filter(groupMember -> groupMember.getIdentifier().equals(memberEntity.getIdentifier()))
                .findFirst();

        globalGroupMember.ifPresent(globalGroupMemberRepository::delete);
    }

    @Override
    public void removeClientFromGlobalGroups(ClientId clientId) {
        globalGroupMemberRepository.findMemberGroups(clientId)
                .forEach(globalGroupMemberRepository::delete);
    }

    private XRoadMemberEntity getMemberIdEntity(MemberId memberId) {
        return xRoadMemberRepository.findMember(memberId)
                .getOrElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));
    }

    private GlobalGroupEntity getGlobalGroupEntity(String groupCode) {
        return globalGroupRepository.getByGroupCode(groupCode)
                .orElseThrow(() -> new NotFoundException(GLOBAL_GROUP_NOT_FOUND));
    }

    private boolean isMemberAlreadyInGroup(GlobalGroupEntity globalGroupEntity, XRoadMemberEntity memberEntity) {
        return globalGroupEntity.getGlobalGroupMembers().stream()
                .anyMatch(groupMember -> groupMember.getIdentifier().equals(memberEntity.getIdentifier()));
    }
}
