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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.MemberServer;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SecurityServerClient;
import org.niis.xroad.cs.admin.api.dto.MemberCreationRequest;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupMemberEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberDidRow;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_EXISTS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_NAME;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {
    private static final String MEMBER_CLASS = "CLASS";

    @Mock
    private XRoadMemberRepository xRoadMemberRepository;
    @Mock
    private GlobalGroupMemberRepository globalGroupMemberRepository;
    @Mock
    private MemberClassRepository memberClassRepository;
    @Mock
    private SecurityServerRepository securityServerRepository;
    @Mock
    private MemberDidRepository memberDidRepository;
    @Mock
    private ParticipantValueStore participantValueStore;
    @Mock
    private IdentifierRepository<MemberIdEntity> memberIds;
    @Mock
    private GlobalGroupMemberService globalGroupMemberService;
    @Mock
    private SecurityServerMapper securityServerMapper;
    @Mock
    private AuditDataHelper auditData;

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
    class AddMethod {
        private final String memberName = "member name";
        private final MemberId memberId = MemberId.create("TEST", MEMBER_CLASS, "MEMBER");
        private final MemberIdEntity memberIdEntity = MemberIdEntity.create(memberId);

        @Test
        @DisplayName("should create client when not already present")
        void shouldCreateClientWhenNotAlreadyPresent() {
            when(memberIds.findOrCreate(memberIdEntity)).thenReturn(memberIdEntity);
            when(xRoadMemberRepository.findOneBy(memberId)).thenReturn(Optional.empty());
            when(xRoadMemberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            when(memberClassRepository.findByCode(MEMBER_CLASS))
                    .thenReturn(Optional.of(new MemberClassEntity(MEMBER_CLASS, "")));

            SecurityServerClient result = memberService
                    .add(new MemberCreationRequest(memberName, MEMBER_CLASS, memberId));

            assertEquals("MEMBER", result.getIdentifier().getMemberCode());

            verify(xRoadMemberRepository).findOneBy(memberId);

            verify(xRoadMemberRepository).save(any());

            verify(auditData).put(MEMBER_NAME, memberName);
            verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            verify(auditData).put(MEMBER_CODE, "MEMBER");
        }

        @Test
        @DisplayName("should not create client when already present")
        void shouldNotCreateClientWhenAlreadyPresent() {
            XRoadMemberEntity presentSecurityServerClient = mock(XRoadMemberEntity.class);

            when(xRoadMemberRepository.findOneBy(memberId)).thenReturn((Optional.of(presentSecurityServerClient)));

            String clientIdentifier = memberId.toShortString();

            Executable testable = () -> memberService
                    .add(new MemberCreationRequest(memberName, MEMBER_CLASS, memberId));

            ConflictException exception = assertThrows(ConflictException.class, testable);
            assertEquals(MEMBER_EXISTS.code(), exception.getErrorDeviation().code());
            assertThat(exception.getErrorDeviation().metadata())
                    .hasSize(1)
                    .containsExactly(clientIdentifier);

            verify(xRoadMemberRepository).findOneBy(memberId);
            verify(xRoadMemberRepository, never()).save(any());

            verify(auditData).put(MEMBER_NAME, memberName);
            verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            verify(auditData).put(MEMBER_CODE, "MEMBER");
        }
    }

    @Nested
    @DisplayName("findMember(ClientId clientId)")
    class FindMember {

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;

        @Test
        @DisplayName("Should find client from xRoadMemberRepository")
        void shouldFindClient() {
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            var result = memberService.findMember(clientId);

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("deleteMember(ClientId clientId)")
    class DeleteMember {
        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;

        @Test
        @DisplayName("Should delete client from xRoadMemberRepository")
        void shouldDeleteClient() {
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            memberService.delete(clientId);

            verify(xRoadMemberRepository).findMember(clientId);
            verify(xRoadMemberRepository).delete(xRoadMember);
            verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            verify(auditData).put(MEMBER_CODE, "MEMBER");
        }

        @Test
        @DisplayName("Should only remove the member from global groups and delete it when it is registered on servers")
        void shouldNotTouchRegistrationsWhenMemberIsRegisteredOnServers() {
            var securityServer = serverWithId(5);
            var subsystem = mock(SubsystemEntity.class);
            lenient().doReturn(Set.of(new ServerClientEntity(securityServer, xRoadMember))).when(xRoadMember).getServerClients();
            lenient().doReturn(Set.of(subsystem)).when(xRoadMember).getSubsystems();
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            memberService.delete(clientId);

            verify(globalGroupMemberService).removeClientFromGlobalGroups(clientId);
            verify(securityServerRepository).lockForUpdateById(5);
            verify(xRoadMemberRepository).findMember(clientId);
            verify(xRoadMemberRepository).delete(xRoadMember);
            verifyNoMoreInteractions(xRoadMemberRepository, securityServerRepository, globalGroupMemberService,
                    globalGroupMemberRepository, memberClassRepository, memberIds);
        }

        @Test
        @DisplayName("Should lock every affected server row in ascending id order before deleting")
        void shouldLockOwnedAndClientServersInIdOrderBeforeDelete() {
            var owned = serverWithId(9);
            var memberClientOf = serverWithId(7);
            var subsystemClientOf = serverWithId(4);
            var subsystem = mock(SubsystemEntity.class);
            doReturn(Set.of(owned)).when(xRoadMember).getOwnedServers();
            doReturn(Set.of(new ServerClientEntity(memberClientOf, xRoadMember), new ServerClientEntity(owned, xRoadMember)))
                    .when(xRoadMember).getServerClients();
            doReturn(Set.of(subsystem)).when(xRoadMember).getSubsystems();
            doReturn(Set.of(new ServerClientEntity(subsystemClientOf, subsystem))).when(subsystem).getServerClients();
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            memberService.delete(clientId);

            InOrder inOrder = inOrder(securityServerRepository, xRoadMemberRepository);
            inOrder.verify(securityServerRepository).lockForUpdateById(4);
            inOrder.verify(securityServerRepository).lockForUpdateById(7);
            inOrder.verify(securityServerRepository).lockForUpdateById(9);
            inOrder.verify(xRoadMemberRepository).delete(xRoadMember);
            verify(securityServerRepository, times(3)).lockForUpdateById(any());
        }

        private SecurityServerEntity serverWithId(int id) {
            var server = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS" + id);
            ReflectionTestUtils.setField(server, "id", id);
            return server;
        }

        @Test
        @DisplayName("Should not delete client when it's non-existent")
        void shouldThrowExceptionWhenClientNotFound() {
            doReturn(Optional.empty()).when(xRoadMemberRepository).findMember(clientId);

            Executable testable = () -> memberService.delete(clientId);

            NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
            assertEquals(ErrorMessage.MEMBER_NOT_FOUND.code(), actualThrown.getErrorDeviation().code());
            verify(xRoadMemberRepository).findMember(clientId);
            verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            verify(auditData).put(MEMBER_CODE, "MEMBER");
        }
    }

    @Nested
    @DisplayName("updateMemberName(clientId, newName)")
    class UpdateName {

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;

        @Test
        @DisplayName("Should set new name")
        void shouldUpdateName() {
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);

            var result = memberService.updateMemberName(clientId, "new name");

            assertTrue(result.isPresent());
            verify(xRoadMember).setName("new name");

            verify(auditData).put(MEMBER_NAME, "new name");
            verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            verify(auditData).put(MEMBER_CODE, "MEMBER");
        }
    }

    @Nested
    @DisplayName("findMemberGlobalGroups(ClientId memberId)")
    class FindMemberGlobalGroups {

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Test
        void shouldReturnMemberGlobalGroups() {
            final GlobalGroupMemberEntity memberGroup = new GlobalGroupMemberEntity(new GlobalGroupEntity("groupCode"),
                    clientId);
            doReturn(List.of(memberGroup)).when(globalGroupMemberRepository).findMemberGroups(clientId);

            final List<GlobalGroupMember> result = memberService.getMemberGlobalGroups(clientId);

            assertEquals(1, result.size());
            assertEquals("groupCode", result.iterator().next().getGlobalGroup().getGroupCode());
        }
    }

    @Nested
    @DisplayName("getMemberOwnedServers(ClientId memberId)")
    class GetMemberOwnedServers {
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

            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);
            doReturn(securityServersMock).when(xRoadMember).getOwnedServers();

            final List<MemberServer> result = memberService.getMemberOwnedServers(clientId);

            verify(xRoadMemberRepository).findMember(clientId);
            verify(xRoadMember).getOwnedServers();

            assertEquals(securityServersMock.size(), result.size());

            Assertions.assertThat(result.stream()
                    .map(memberServer -> memberServer.server().getServerCode()).collect(toList()))
                    .hasSameElementsAs(List.of("SS0", "SS1"));
        }

        @Test
        void shouldAddMemberDidAndStaleFlagOfEachServerWithAStoredDid() {
            var memberRowId = 11L;
            var current = ownedServer("SS0", 1, "ss0.example.org");
            var moved = ownedServer("SS1", 2, "ss1.example.org");
            var withoutDid = ownedServer("SS2", 3, "ss2.example.org");
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findMember(clientId);
            doReturn(Set.of(current, moved, withoutDid)).when(xRoadMember).getOwnedServers();
            doReturn(memberRowId).when(xRoadMember).getId();
            var currentDid = "did:web:ss0.example.org%3A7183:v1:TEST:CLASS:MEMBER";
            var movedDid = "did:web:old.example.org%3A7183:v1:TEST:CLASS:MEMBER";
            when(memberDidRepository.findRowsByMemberId(memberRowId)).thenReturn(List.of(
                    new MemberDidRow(1, "TEST", MEMBER_CLASS, "MEMBER", currentDid),
                    new MemberDidRow(2, "TEST", MEMBER_CLASS, "MEMBER", movedDid)));

            var result = memberService.getMemberOwnedServers(clientId);

            assertThat(result).extracting(m -> m.server().getServerCode(), MemberServer::memberDid, MemberServer::memberDidStale)
                    .containsExactlyInAnyOrder(
                            tuple("SS0", currentDid, false),
                            tuple("SS1", movedDid, true),
                            tuple("SS2", null, null));
            verify(memberDidRepository).findRowsByMemberId(memberRowId);
            verifyNoMoreInteractions(memberDidRepository);
        }

        private SecurityServerEntity ownedServer(String code, int id, String address) {
            var owner = mock(XRoadMemberEntity.class);
            lenient().doReturn(MemberIdEntity.create(MemberId.create("TEST", MEMBER_CLASS, "MEMBER"))).when(owner).getIdentifier();
            var server = new SecurityServerEntity(owner, code);
            server.setAddress(address);
            ReflectionTestUtils.setField(server, "id", id);
            when(securityServerMapper.toTarget(server)).thenReturn(new SecurityServer(null, code));
            return server;
        }

        @Test
        void shouldReturnEmptySetWhenMemberNotFound() {
            doReturn(Optional.empty()).when(xRoadMemberRepository).findMember(clientId);

            final List<MemberServer> result = memberService.getMemberOwnedServers(clientId);

            verify(xRoadMemberRepository).findMember(clientId);
            assertTrue(CollectionUtils.isEmpty(result));
        }
    }

    @Nested
    @DisplayName("getMemberClientOfServers(ClientId memberId)")
    class GetMemberClientOfServers {
        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMember;

        @Test
        void shouldReturnMemberAndSubsystemUsedServers() {
            // Setup servers
            var ss1 = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS1");
            var ss2 = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS2");
            var ss3 = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS3"); // Shared server
            var ss4 = new SecurityServerEntity(mock(XRoadMemberEntity.class), "SS4");

            // Setup member clients
            var memberClient1 = new ServerClientEntity(ss1, xRoadMember);
            var memberClient2 = new ServerClientEntity(ss3, xRoadMember);
            doReturn(Set.of(memberClient1, memberClient2)).when(xRoadMember).getServerClients();

            // Setup subsystem clients
            var subsystem = mock(SubsystemEntity.class);
            var subClient1 = new ServerClientEntity(ss3, subsystem); // Duplicate (ss3)
            var subClient2 = new ServerClientEntity(ss4, subsystem);
            doReturn(Set.of(subClient1, subClient2)).when(subsystem).getServerClients();
            doReturn(Set.of(subsystem)).when(xRoadMember).getSubsystems();

            // Repository mock
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findOneBy(clientId);

            // Mapper mocks
            when(securityServerMapper.toTarget(ss1)).thenReturn(new SecurityServer(null, "SS1"));
            when(securityServerMapper.toTarget(ss3)).thenReturn(new SecurityServer(null, "SS3"));
            when(securityServerMapper.toTarget(ss4)).thenReturn(new SecurityServer(null, "SS4"));

            final List<MemberServer> result = memberService.getMemberClientOfServers(clientId);

            assertEquals(3, result.size());
            Assertions.assertThat(result.stream()
                    .map(memberServer -> memberServer.server().getServerCode()).collect(toList()))
                    .containsExactlyInAnyOrder("SS1", "SS3", "SS4");
        }

        @Test
        void shouldAddMemberDidOfAServerWhereOnlyASubsystemIsClient() {
            var memberRowId = 12L;
            var owner = mock(XRoadMemberEntity.class);
            lenient().doReturn(MemberIdEntity.create(MemberId.create("TEST", MEMBER_CLASS, "OWNER"))).when(owner).getIdentifier();
            var server = new SecurityServerEntity(owner, "SS1");
            server.setAddress("ss1.example.org");
            ReflectionTestUtils.setField(server, "id", 5);
            var subsystem = mock(SubsystemEntity.class);
            doReturn(Set.of(new ServerClientEntity(server, subsystem))).when(subsystem).getServerClients();
            doReturn(Set.of(subsystem)).when(xRoadMember).getSubsystems();
            doReturn(Set.of()).when(xRoadMember).getServerClients();
            doReturn(memberRowId).when(xRoadMember).getId();
            doReturn(Optional.of(xRoadMember)).when(xRoadMemberRepository).findOneBy(clientId);
            when(securityServerMapper.toTarget(server)).thenReturn(new SecurityServer(null, "SS1"));
            var did = "did:web:ss1.example.org%3A7183:v1:TEST:CLASS:MEMBER";
            when(memberDidRepository.findRowsByMemberId(memberRowId))
                    .thenReturn(List.of(new MemberDidRow(5, "TEST", MEMBER_CLASS, "MEMBER", did)));

            var result = memberService.getMemberClientOfServers(clientId);

            assertThat(result).singleElement().satisfies(memberServer -> {
                assertThat(memberServer.memberDid()).isEqualTo(did);
                assertThat(memberServer.memberDidStale()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("editMemberDid()")
    class EditMemberDid {
        private static final int SERVER_ROW_ID = 7;
        private static final String NEW_DID = "did:web:ss.example.org%3A7183:v1:TEST:CLASS:MEMBER";

        private final ClientId clientId = ClientId.Conf.create("TEST", MEMBER_CLASS, "MEMBER");
        private final SecurityServerId serverId = SecurityServerId.Conf.create("TEST", MEMBER_CLASS, "OWNER", "server");

        private void givenServerRow() {
            givenMember();
            var server = mock(SecurityServerEntity.class);
            when(server.getId()).thenReturn(SERVER_ROW_ID);
            when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        }

        private void givenMember() {
            when(xRoadMemberRepository.count(clientId)).thenReturn(1L);
        }

        @Test
        void shouldReplaceWithoutLockingTheServerRow() {
            givenServerRow();
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID)))
                    .thenReturn(Map.of(clientId, ParticipantValueResult.STORED));

            memberService.editMemberDid(serverId, clientId, NEW_DID);

            verify(participantValueStore).replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID));
            verify(securityServerRepository, never()).lockForUpdateBy(any());
            verify(securityServerRepository, never()).lockForUpdateById(any());
        }

        @Test
        void shouldPutAuditData() {
            givenServerRow();
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID)))
                    .thenReturn(Map.of(clientId, ParticipantValueResult.STORED));

            memberService.editMemberDid(serverId, clientId, NEW_DID);

            verify(auditData).put(RestApiAuditProperty.MEMBER_CLASS, MEMBER_CLASS);
            verify(auditData).put(MEMBER_CODE, "MEMBER");
            verify(auditData).put(RestApiAuditProperty.SERVER_CODE, "server");
            verify(auditData).put(RestApiAuditProperty.OWNER_CLASS, MEMBER_CLASS);
            verify(auditData).put(RestApiAuditProperty.OWNER_CODE, "OWNER");
            verify(auditData).put(RestApiAuditProperty.MEMBER_DID, NEW_DID);
        }

        @Test
        void shouldSucceedWhenTheStoredDidIsEqual() {
            givenServerRow();
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID)))
                    .thenReturn(Map.of(clientId, ParticipantValueResult.UNCHANGED));

            assertDoesNotThrow(() -> memberService.editMemberDid(serverId, clientId, NEW_DID));

            verify(participantValueStore).replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID));
            verify(auditData).put(RestApiAuditProperty.MEMBER_DID, NEW_DID);
        }

        @Test
        void shouldFailWhenTheServerDoesNotExist() {
            givenMember();
            when(securityServerRepository.findBy(serverId)).thenReturn(Optional.empty());

            assertThrows(SecurityServerNotFoundException.class, () -> memberService.editMemberDid(serverId, clientId, NEW_DID));

            verify(participantValueStore, never()).replaceMemberDids(anyInt(), any());
        }

        @Test
        void shouldFailAsMemberNotFoundWhenTheMemberDoesNotExist() {
            when(xRoadMemberRepository.count(clientId)).thenReturn(0L);

            var failure = assertThrows(NotFoundException.class, () -> memberService.editMemberDid(serverId, clientId, NEW_DID));

            assertEquals(ErrorMessage.MEMBER_NOT_FOUND.code(), failure.getErrorDeviation().code());
            verify(auditData).put(RestApiAuditProperty.MEMBER_DID, NEW_DID);
            verify(participantValueStore, never()).replaceMemberDids(anyInt(), any());
        }

        @ParameterizedTest
        @CsvSource({
                "NO_STORED_VALUE, member_did_not_stored",
                "WRONG_SCHEME, member_did_wrong_scheme",
                "DID_TOO_LONG, member_did_too_long",
                "NON_ASCII_HOST, member_did_non_ascii_host",
                "UNDECODABLE_DID, member_did_undecodable",
                "NOT_CANONICAL, member_did_not_canonical",
                "HOST_DIFFERS, member_did_host_differs",
                "DID_OF_OTHER_MEMBER, member_did_of_other_member",
                "MEMBER_NOT_SERVED, member_did_member_not_served",
                "UNPARSEABLE_REGISTERED_ADDRESS, member_did_unparseable_address",
                "DID_HELD_BY_ANOTHER_SERVER, member_did_held_by_another_server"})
        void shouldFailWithTheCodeOfTheReasonTheStoreRefusesTheDid(ParticipantValueRejection rejection, String code) {
            givenServerRow();
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID)))
                    .thenReturn(Map.of(clientId, ParticipantValueResult.rejected(rejection)));

            var failure = assertThrows(BadRequestException.class, () -> memberService.editMemberDid(serverId, clientId, NEW_DID));

            assertEquals(code, failure.getErrorDeviation().code());
            verify(auditData).put(RestApiAuditProperty.MEMBER_DID, NEW_DID);
        }

        @ParameterizedTest
        @EnumSource(value = ParticipantValueRejection.class, names = {
                "DSP_BASE_URL_NOT_HTTPS", "MALFORMED_DSP_BASE_URL", "FOREIGN_SYSTEM_DID", "VALUE_DIFFERS"})
        void shouldFailLoudlyForAReasonThatAMemberEditCannotProduce(ParticipantValueRejection rejection) {
            givenServerRow();
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID)))
                    .thenReturn(Map.of(clientId, ParticipantValueResult.rejected(rejection)));

            assertThrows(IllegalStateException.class, () -> memberService.editMemberDid(serverId, clientId, NEW_DID));
        }

        @Test
        void shouldFailAsHeldByAnotherServerWhenTheDatabaseRefusesTheSameDidOnTwoServers() {
            givenServerRow();
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID)))
                    .thenThrow(new DataIntegrityViolationException("member_dids_did_excl"));

            var failure = assertThrows(BadRequestException.class, () -> memberService.editMemberDid(serverId, clientId, NEW_DID));

            assertEquals("member_did_held_by_another_server", failure.getErrorDeviation().code());
        }

        @Test
        void shouldRethrowADatabaseRefusalOfAnotherConstraint() {
            givenServerRow();
            var failure = new DataIntegrityViolationException("member_dids_member_id_fk");
            when(participantValueStore.replaceMemberDids(SERVER_ROW_ID, Map.of(clientId, NEW_DID))).thenThrow(failure);

            var thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> memberService.editMemberDid(serverId, clientId, NEW_DID));

            assertThat(thrown).isSameAs(failure);
        }
    }
}
