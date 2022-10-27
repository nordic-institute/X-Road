/**
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SecurityServerClient;
import org.niis.xroad.cs.admin.api.dto.MemberCreationRequest;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientNameEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientNameRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_EXISTS;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest implements WithInOrder {
    private static final String MEMBER_CLASS = "CLASS";

    @Mock
    private XRoadMemberRepository xRoadMemberRepository;
    @Mock
    private SecurityServerClientNameRepository securityServerClientNameRepository;
    @Mock
    private GlobalGroupMemberRepository globalGroupMemberRepository;
    @Mock
    private MemberClassRepository memberClassRepository;
    @Mock
    private SecurityServerMapper securityServerMapper;

    @Spy
    private ClientIdMapper clientIdMapper = new ClientIdMapperImpl();
    @Spy
    @InjectMocks
    private SecurityServerClientMapper securityServerClientMapper = new SecurityServerClientMapperImpl();
    @Spy
    @InjectMocks
    private GlobalGroupMemberMapper globalGroupMemberMapper = new GlobalGroupMemberMapperImpl();

    @InjectMocks
    private MemberServiceImpl memberService;

    @Nested
    @DisplayName("add(Client clientDto)")
    class AddMethod implements WithInOrder {
        private final String memberName = "member name";
        private final MemberId memberId = MemberId.create("TEST", MEMBER_CLASS, "MEMBER");

        @Test
        @DisplayName("should create client when not already present")
        void shouldCreateClientWhenNotAlreadyPresent() {
            when(xRoadMemberRepository.findOneBy(memberId)).thenReturn(Option.none());
            when(xRoadMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            when(memberClassRepository.findByCode(MEMBER_CLASS))
                    .thenReturn(Option.of(new MemberClassEntity(MEMBER_CLASS, "")));

            SecurityServerClient result = memberService.add(new MemberCreationRequest(memberName, MEMBER_CLASS, memberId));

            assertEquals("MEMBER", result.getIdentifier().getMemberCode());
            ArgumentCaptor<SecurityServerClientNameEntity> captor = ArgumentCaptor.forClass(SecurityServerClientNameEntity.class);

            verify(xRoadMemberRepository).findOneBy(memberId);

            verify(xRoadMemberRepository).save(any());
            verify(securityServerClientNameRepository).save(captor.capture());

            assertThat(captor.getValue().getName()).isEqualTo(memberName);
            assertThat(captor.getValue().getIdentifier()).isEqualTo(memberId);
        }

        @Test
        @DisplayName("should not create client when already present")
        void shouldNotCreateClientWhenAlreadyPresent() {
            XRoadMemberEntity presentSecurityServerClient = mock(XRoadMemberEntity.class);

            when(xRoadMemberRepository.findOneBy(memberId)).thenReturn((Option.of(presentSecurityServerClient)));

            String clientIdentifier = memberId.toShortString();

            Executable testable = () -> memberService.add(new MemberCreationRequest(memberName, MEMBER_CLASS, memberId));

            EntityExistsException exception = assertThrows(EntityExistsException.class, testable);
            assertEquals(MEMBER_EXISTS.getDescription(), exception.getMessage());
            assertThat(exception.getErrorDeviation().getMetadata())
                    .hasSize(1)
                    .containsExactly(clientIdentifier);

            verify(xRoadMemberRepository).findOneBy(memberId);
            verify(xRoadMemberRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findMember(ClientId clientId)")
    class FindMember implements WithInOrder {

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;

        @Test
        @DisplayName("Should find client from xRoadMemberRepository")
        void shouldFindClient() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            var result = memberService.findMember(clientId);

            assertTrue(result.isDefined());
        }
    }

    @Nested
    @DisplayName("deleteMember(ClientId clientId)")
    class DeleteMember implements WithInOrder {
        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;

        @Test
        @DisplayName("Should delete client from xRoadMemberRepository")
        void shouldDeleteClient() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            memberService.delete(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
                inOrder.verify(xRoadMemberRepository).delete(any());
            });
        }

        @Test
        @DisplayName("Should not delete client when it's non-existent")
        void shouldThrowExceptionWhenClientNotFound() {
            doReturn(Option.none()).when(xRoadMemberRepository).findMember(clientId);

            Executable testable = () -> memberService.delete(clientId);

            NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
            assertEquals(ErrorMessage.MEMBER_NOT_FOUND.getDescription(), actualThrown.getMessage());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
            });
        }
    }

    @Nested
    @DisplayName("updateMemberName(clientId, newName)")
    class UpdateName implements WithInOrder {

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;
        @Mock
        private SecurityServerClientNameEntity securityServerClientName;

        @Test
        @DisplayName("Should set new name")
        void shouldUpdateName() {
            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);
            doReturn(Set.of(securityServerClientName)).when(securityServerClientNameRepository).findByIdentifierIn(any());

            var result = memberService.updateMemberName(clientId, "new name");

            assertTrue(result.isDefined());
            verify(xRoadMember).setName("new name");
            verify(securityServerClientName).setName("new name");
        }
    }

    @Nested
    @DisplayName("findMemberGlobalGroups(ClientId memberId)")
    class FindMemberGlobalGroups implements WithInOrder {

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Test
        void shouldReturnMemberGlobalGroups() {
            final GlobalGroupMemberEntity memberGroup = new GlobalGroupMemberEntity(new GlobalGroupEntity("groupCode"), clientId);
            doReturn(List.of(memberGroup)).when(globalGroupMemberRepository).findMemberGroups(clientId);

            final List<GlobalGroupMember> result = memberService.getMemberGlobalGroups(clientId);

            assertEquals(1, result.size());
            assertEquals("groupCode", result.iterator().next().getGlobalGroup().getGroupCode());
        }
    }

    @Nested
    @DisplayName("getMemberOwnedServers(ClientId memberId)")
    class GetMemberOwnedServers implements WithInOrder {
        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;


        @Test
        void shouldReturnMemberOwnedServers() {
            var ss0 = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS0");
            var ss1 = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS1");
            var securityServersMock = Set.of(ss0, ss1);

            when(securityServerMapper.toTarget(ss0)).thenReturn(new SecurityServer(null, "SS0"));
            when(securityServerMapper.toTarget(ss1)).thenReturn(new SecurityServer(null, "SS1"));

            doReturn(Option.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);
            doReturn(securityServersMock).when(xRoadMember).getOwnedServers();

            final Set<SecurityServer> result = memberService.getMemberOwnedServers(clientId);


            verify(xRoadMemberRepository).findMember(clientId);
            verify(xRoadMember, times(1)).getOwnedServers();

            assertEquals(securityServersMock.size(), result.size());

            Assertions.assertThat(result.stream()
                            .map(SecurityServer::getServerCode).collect(Collectors.toList()))
                    .hasSameElementsAs(List.of("SS0", "SS1"));
        }

        @Test
        void shouldReturnEmptySetWhenMemberNotFound() {
            doReturn(Option.none()).when(xRoadMemberRepository).findMember(clientId);

            final Set<SecurityServer> result = memberService.getMemberOwnedServers(clientId);

            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMemberRepository).findMember(clientId);
            });
            assertTrue(CollectionUtils.isEmpty(result));
        }
    }
}
