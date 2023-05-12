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

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalGroupMemberServiceImplTest {

    private static final String INSTANCE = "CS";
    private static final String MEMBER_CLASS = "MEMBER_CLASS";
    private static final String MEMBER_CODE = "MEMBER-CODE";
    private static final String GROUP_CODE = "global-group-code";

    @Mock
    private GlobalGroupMemberRepository globalGroupMemberRepository;
    @Mock
    private GlobalGroupRepository globalGroupRepository;
    @Mock
    private XRoadMemberRepository xRoadMemberRepository;

    @InjectMocks
    private GlobalGroupMemberServiceImpl globalGroupMemberService;

    private final MemberId memberId = MemberId.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);
    private final MemberIdEntity memberIdEntity = MemberIdEntity.create(memberId);
    @Mock
    private XRoadMemberEntity memberEntityMock;

    @Test
    void addMemberToGlobalGroup() {
        GlobalGroupEntity globalGroupEntity = new GlobalGroupEntity(GROUP_CODE);
        when(xRoadMemberRepository.findMember(memberId)).thenReturn(Option.of(memberEntityMock));
        when(memberEntityMock.getIdentifier()).thenReturn(memberIdEntity);
        when(globalGroupRepository.getByGroupCode(GROUP_CODE))
                .thenReturn(Optional.of(globalGroupEntity));

        final MemberId memberToAdd = MemberId.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);

        globalGroupMemberService.addMemberToGlobalGroup(memberToAdd, GROUP_CODE);

        ArgumentCaptor<GlobalGroupMemberEntity> captor = ArgumentCaptor.forClass(GlobalGroupMemberEntity.class);
        verify(globalGroupMemberRepository).save(captor.capture());

        assertThat(captor.getValue().getGlobalGroup()).isEqualTo(globalGroupEntity);
        assertThat(captor.getValue().getIdentifier()).isEqualTo(memberIdEntity);
    }

    @Test
    void addMemberToGlobalGroupAlreadyMember() {
        GlobalGroupEntity globalGroupEntity = new GlobalGroupEntity(GROUP_CODE);
        when(xRoadMemberRepository.findMember(memberId)).thenReturn(Option.of(memberEntityMock));
        when(memberEntityMock.getIdentifier()).thenReturn(memberIdEntity);
        when(globalGroupRepository.getByGroupCode(GROUP_CODE))
                .thenReturn(Optional.of(globalGroupEntity));

        globalGroupEntity.getGlobalGroupMembers().add(
                new GlobalGroupMemberEntity(globalGroupEntity, MemberIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE)));

        final MemberId memberToAdd = MemberId.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);

        globalGroupMemberService.addMemberToGlobalGroup(memberToAdd, GROUP_CODE);

        verifyNoInteractions(globalGroupMemberRepository);
    }

    @Test
    void removeMemberFromGlobalGroup() {
        GlobalGroupEntity globalGroupEntity = new GlobalGroupEntity(GROUP_CODE);

        final String codeToDelete = "code-to-delete";
        final MemberId memberToDeleteId = MemberId.create(INSTANCE, MEMBER_CLASS, codeToDelete);
        final MemberIdEntity memberToDeleteIdEntity = MemberIdEntity.create(INSTANCE, MEMBER_CLASS, codeToDelete);

        when(xRoadMemberRepository.findMember(memberToDeleteId)).thenReturn(Option.of(memberEntityMock));
        when(memberEntityMock.getIdentifier()).thenReturn(memberToDeleteIdEntity);
        when(globalGroupRepository.getByGroupCode(GROUP_CODE))
                .thenReturn(Optional.of(globalGroupEntity));

        GlobalGroupMemberEntity toBeRemoved = new GlobalGroupMemberEntity(globalGroupEntity,
                MemberIdEntity.create(INSTANCE, MEMBER_CLASS, codeToDelete));

        globalGroupEntity.getGlobalGroupMembers().add(
                new GlobalGroupMemberEntity(globalGroupEntity, MemberIdEntity.create(INSTANCE, MEMBER_CLASS, "code-0")));
        globalGroupEntity.getGlobalGroupMembers().add(toBeRemoved);
        globalGroupEntity.getGlobalGroupMembers().add(
                new GlobalGroupMemberEntity(globalGroupEntity, MemberIdEntity.create(INSTANCE, MEMBER_CLASS, "code-2")));

        globalGroupMemberService.removeMemberFromGlobalGroup(GROUP_CODE, MemberId.create(INSTANCE, MEMBER_CLASS, codeToDelete));

        verify(globalGroupMemberRepository).delete(toBeRemoved);
    }

    @Test
    void removeMemberFromGlobalGroupMemberNotInGroup() {
        GlobalGroupEntity globalGroupEntity = new GlobalGroupEntity(GROUP_CODE);

        when(xRoadMemberRepository.findMember(memberId)).thenReturn(Option.of(memberEntityMock));
        when(memberEntityMock.getIdentifier()).thenReturn(memberIdEntity);
        when(globalGroupRepository.getByGroupCode(GROUP_CODE))
                .thenReturn(Optional.of(globalGroupEntity));

        globalGroupEntity.getGlobalGroupMembers().add(
                new GlobalGroupMemberEntity(globalGroupEntity, MemberIdEntity.create(INSTANCE, MEMBER_CLASS, "code-0")));
        globalGroupEntity.getGlobalGroupMembers().add(
                new GlobalGroupMemberEntity(globalGroupEntity, MemberIdEntity.create(INSTANCE, MEMBER_CLASS, "code-2")));

        globalGroupMemberService.removeMemberFromGlobalGroup(GROUP_CODE, memberId);

        assertThat(globalGroupEntity.getGlobalGroupMembers()).hasSize(2);

        verifyNoInteractions(globalGroupMemberRepository);
    }

}
