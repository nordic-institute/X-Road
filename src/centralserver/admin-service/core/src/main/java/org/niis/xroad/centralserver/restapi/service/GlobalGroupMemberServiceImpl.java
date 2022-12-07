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

package org.niis.xroad.centralserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.service.GroupMemberService;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Optional;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.GLOBAL_GROUP_NOT_FOUND;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
public class GlobalGroupMemberServiceImpl implements GroupMemberService {

    private final GlobalGroupMemberRepository globalGroupMemberRepository;
    private final GlobalGroupRepository globalGroupRepository;
    private final IdentifierRepository<MemberIdEntity> memberIdRepository;

    public void addMemberToGlobalGroup(MemberId memberId, String groupCode) {
        final MemberIdEntity memberIdEntity = getMemberIdEntity(memberId);
        final GlobalGroupEntity globalGroupEntity = getGlobalGroupEntity(groupCode);
        if (!isMemberAlreadyInGroup(globalGroupEntity, memberIdEntity)) {
            final var globalGroupMemberEntity = new GlobalGroupMemberEntity(globalGroupEntity, memberIdEntity);
            globalGroupEntity.getGlobalGroupMembers().add(globalGroupMemberEntity);
            globalGroupMemberRepository.save(globalGroupMemberEntity);
        }
    }

    public void removeMemberFromGlobalGroup(MemberId memberId, String groupCode) {
        final MemberIdEntity memberIdEntity = getMemberIdEntity(memberId);
        final GlobalGroupEntity globalGroupEntity = getGlobalGroupEntity(groupCode);

        final Optional<GlobalGroupMemberEntity> globalGroupMember = globalGroupEntity.getGlobalGroupMembers().stream()
                .filter(groupMember -> groupMember.getIdentifier().equals(memberIdEntity))
                .findFirst();

        if (globalGroupMember.isPresent()) {
            final GlobalGroupMemberEntity entity = globalGroupMember.get();
            globalGroupEntity.getGlobalGroupMembers().remove(entity);
            globalGroupMemberRepository.delete(entity);
        }
    }

    private MemberIdEntity getMemberIdEntity(MemberId memberId) {
        return memberIdRepository.findById((long) memberId.getId())
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));
    }

    private GlobalGroupEntity getGlobalGroupEntity(String groupCode) {
        return globalGroupRepository.getByGroupCode(groupCode)
                .orElseThrow(() -> new NotFoundException(GLOBAL_GROUP_NOT_FOUND));
    }

    private boolean isMemberAlreadyInGroup(GlobalGroupEntity globalGroupEntity, ClientIdEntity clientId) {
        return globalGroupEntity.getGlobalGroupMembers().stream()
                .anyMatch(groupMember -> groupMember.getIdentifier().equals(clientId));
    }
}
