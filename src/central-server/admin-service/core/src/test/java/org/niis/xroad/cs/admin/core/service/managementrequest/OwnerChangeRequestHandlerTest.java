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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.OwnerChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.OwnerChangeRequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static ee.ria.xroad.common.SystemProperties.CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.APPROVED;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.cs.admin.api.domain.Origin.CENTER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_CLIENT_ALREADY_OWNER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_OWNER_MUST_BE_CLIENT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_OWNER_MUST_BE_MEMBER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_CODE_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_NOT_FOUND;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;

@ExtendWith(MockitoExtension.class)
class OwnerChangeRequestHandlerTest {

    private static final Integer ID = 123;
    private static final String INSTANCE = "CS";
    private static final String MEMBER_CLASS = "MEMBER-CLASS";
    private static final String MEMBER_CODE = "MEMBER-CODE";

    private final XRoadMemberRepository members = mock(XRoadMemberRepository.class);
    private final OwnerChangeRequestRepository ownerChangeRequestRepository = mock(OwnerChangeRequestRepository.class);
    private final IdentifierRepository<SecurityServerIdEntity> serverIds = mock(IdentifierRepository.class);
    private final IdentifierRepository<MemberIdEntity> memberIds = mock(IdentifierRepository.class);
    private final SecurityServerRepository servers = mock(SecurityServerRepository.class);
    private final ServerClientRepository serverClients = mock(ServerClientRepository.class);
    private final RequestMapper requestMapper = mock(RequestMapper.class);
    private final GlobalGroupMemberService groupMemberService = mock(GlobalGroupMemberService.class);
    @Mock
    private OwnerChangeRequestEntity ownerChangeRequestEntity;
    @Mock
    private OwnerChangeRequestEntity savedOwnerChangeRequestEntity;
    @Mock
    private SecurityServerEntity securityServerEntity;

    @Mock
    private OwnerChangeRequest ownerChangeRequestDto;
    @Mock
    private MemberIdEntity memberIdEntity;
    @Mock
    private XRoadMemberEntity currentOwnerMock;

    private final OwnerChangeRequestHandler ownerChangeRequestHandler = new OwnerChangeRequestHandler(members, ownerChangeRequestRepository,
            serverIds, memberIds, servers, serverClients, groupMemberService, requestMapper);

    private final ClientId clientId = MemberId.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);
    private final XRoadMemberEntity xRoadMemberEntity =
            new XRoadMemberEntity("name", clientId, new MemberClassEntity(MEMBER_CLASS, "description"));
    private final ClientIdEntity clientIdEntity = ClientIdEntity.ensure(clientId);
    private final SecurityServerId securityServerId = SecurityServerId.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE, "SERVER-CODE");
    private final SecurityServerIdEntity securityServerIdEntity = SecurityServerIdEntity.create(securityServerId);


    @Test
    void canAutoApproveFalse() {
        System.setProperty(CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS, "true");
        when(members.findMember(clientId)).thenReturn(Option.none());

        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        assertThat(ownerChangeRequestHandler.canAutoApprove(request)).isFalse();
    }

    @Test
    void canAutoApproveFalse2() {
        System.setProperty(CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS, "false");

        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        assertThat(ownerChangeRequestHandler.canAutoApprove(request)).isFalse();

        verifyNoInteractions(members);
    }

    @Test
    void canAutoApproveTrue() {
        System.setProperty(CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS, "true");
        when(members.findMember(clientId)).thenReturn(Option.of(mock(XRoadMemberEntity.class)));

        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        assertThat(ownerChangeRequestHandler.canAutoApprove(request)).isTrue();
    }

    @Test
    void addShouldThrowExceptionWhenPendingRequestsExist() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of(mock(OwnerChangeRequestEntity.class), mock(OwnerChangeRequestEntity.class)));

        assertThatThrownBy(() -> ownerChangeRequestHandler.add(request))
                .isInstanceOf(DataIntegrityException.class)
                .hasMessage(MR_EXISTS.getDescription());
    }

    @Test
    void addShouldThrowExceptionWhenClientIsSubsystem() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);
        request.getClientId().setSubsystemCode("SUBSYSTEM");

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> ownerChangeRequestHandler.add(request))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage(MR_OWNER_MUST_BE_MEMBER.getDescription());
    }

    @Test
    void addShouldThrowExceptionWhenSecurityServerNotExists() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of());

        when(servers.findBy(securityServerId)).thenReturn(Option.none());

        assertThatThrownBy(() -> ownerChangeRequestHandler.add(request))
                .isInstanceOf(DataIntegrityException.class)
                .hasMessage(MR_SERVER_NOT_FOUND.getDescription());
    }

    @Test
    void addShouldThrowExceptionWhenOwnerIsNotSecurityServerClient() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of());

        when(servers.findBy(securityServerId)).thenReturn(Option.of(securityServerEntity));
        Set<ServerClientEntity> mockClients = Set.of(mockServerClientEntity(), mockServerClientEntity());
        when(securityServerEntity.getServerClients()).thenReturn(mockClients);

        assertThatThrownBy(() -> ownerChangeRequestHandler.add(request))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage(MR_OWNER_MUST_BE_CLIENT.getDescription());
    }

    @Test
    void addShouldThrowExceptionWhenClientIsTheCurrentOwner() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of());

        when(servers.findBy(securityServerId)).thenReturn(Option.of(securityServerEntity));
        Set<ServerClientEntity> mockClients = Set.of(
                mockServerClientEntity(MemberIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE)));
        when(securityServerEntity.getServerClients()).thenReturn(mockClients);

        final XRoadMemberEntity ownerMock = mock(XRoadMemberEntity.class);
        final MemberIdEntity ownerId = MemberIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);
        when(ownerMock.getIdentifier()).thenReturn(ownerId);
        when(securityServerEntity.getOwner()).thenReturn(ownerMock);

        assertThatThrownBy(() -> ownerChangeRequestHandler.add(request))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage(MR_CLIENT_ALREADY_OWNER.getDescription());
    }

    @Test
    void addShouldThrowExceptionWhenOtherSecurityServerWithTheSameIdExists() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of());

        when(servers.findBy(securityServerId)).thenReturn(Option.of(securityServerEntity));
        Set<ServerClientEntity> mockClients = Set.of(
                mockServerClientEntity(MemberIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE)));
        when(securityServerEntity.getServerClients()).thenReturn(mockClients);

        final XRoadMemberEntity ownerMock = mock(XRoadMemberEntity.class);
        final MemberIdEntity ownerId = MemberIdEntity.create(INSTANCE, "MEMBER-CLASS-1", "MEMBER-CODE-1");
        when(ownerMock.getIdentifier()).thenReturn(ownerId);
        when(securityServerEntity.getOwner()).thenReturn(ownerMock);
        when(securityServerEntity.getServerCode()).thenReturn("SS");

        when(servers.count(SecurityServerId.create(clientId, "SS"))).thenReturn(1L);

        assertThatThrownBy(() -> ownerChangeRequestHandler.add(request))
                .isInstanceOf(DataIntegrityException.class)
                .hasMessage(MR_SERVER_CODE_EXISTS.getDescription());
    }

    private ServerClientEntity mockServerClientEntity(MemberIdEntity memberId) {
        final SecurityServerClientEntity securityServerClientMock = mock(SecurityServerClientEntity.class);
        when(securityServerClientMock.getIdentifier()).thenReturn(memberId);

        final ServerClientEntity serverClientEntityMock = new ServerClientEntity();
        serverClientEntityMock.setSecurityServer(securityServerEntity);
        serverClientEntityMock.setSecurityServerClient(securityServerClientMock);

        return serverClientEntityMock;

    }

    private ServerClientEntity mockServerClientEntity() {
        return mockServerClientEntity(MemberIdEntity.create("xx", "yy", UUID.randomUUID().toString()));
    }

    @Test
    void add() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);

        when(serverIds.findOne(isA(SecurityServerIdEntity.class))).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestRepository.findBy(securityServerId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)))
                .thenReturn(List.of());

        when(servers.findBy(securityServerId)).thenReturn(Option.of(securityServerEntity));
        Set<ServerClientEntity> mockClients = Set.of(
                mockServerClientEntity(MemberIdEntity.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE)));
        when(securityServerEntity.getServerClients()).thenReturn(mockClients);

        final XRoadMemberEntity ownerMock = mock(XRoadMemberEntity.class);
        final MemberIdEntity ownerId = MemberIdEntity.create(INSTANCE, "MEMBER-CLASS-1", "MEMBER-CODE-1");
        when(ownerMock.getIdentifier()).thenReturn(ownerId);
        when(securityServerEntity.getOwner()).thenReturn(ownerMock);
        when(securityServerEntity.getServerCode()).thenReturn("SS");

        when(servers.count(SecurityServerId.create(clientId, "SS"))).thenReturn(0L);

        when(memberIds.findOne(isA(MemberIdEntity.class))).thenReturn(memberIdEntity);
        when(ownerChangeRequestRepository.save(isA(OwnerChangeRequestEntity.class))).thenReturn(ownerChangeRequestEntity);
        when(requestMapper.toDto(ownerChangeRequestEntity)).thenReturn(ownerChangeRequestDto);

        final OwnerChangeRequest result = ownerChangeRequestHandler.add(request);

        ArgumentCaptor<OwnerChangeRequestEntity> argHandler = ArgumentCaptor.forClass(OwnerChangeRequestEntity.class);
        verify(ownerChangeRequestRepository).save(argHandler.capture());
        assertThat(argHandler.getValue().getOrigin()).isEqualTo(request.getOrigin());
        assertThat(argHandler.getValue().getSecurityServerId()).isEqualTo(securityServerIdEntity);
        assertThat(argHandler.getValue().getClientId()).isEqualTo(memberIdEntity);

        assertThat(argHandler.getValue().getProcessingStatus()).isEqualTo(WAITING);
    }

    @Test
    void approve() {
        final OwnerChangeRequest request = new OwnerChangeRequest(CENTER, securityServerId, clientId);
        request.setId(ID);

        when(ownerChangeRequestRepository.findById(ID)).thenReturn(Optional.of(ownerChangeRequestEntity));
        when(ownerChangeRequestEntity.getProcessingStatus()).thenReturn(WAITING);
        when(ownerChangeRequestEntity.getSecurityServerId()).thenReturn(securityServerIdEntity);
        when(ownerChangeRequestEntity.getClientId()).thenReturn(clientIdEntity);
        when(servers.findBy(securityServerIdEntity)).thenReturn(Option.of(securityServerEntity));
        when(members.findOneBy(clientIdEntity)).thenReturn(Option.of(xRoadMemberEntity));

        when(ownerChangeRequestRepository.save(ownerChangeRequestEntity)).thenReturn(savedOwnerChangeRequestEntity);
        when(requestMapper.toDto(savedOwnerChangeRequestEntity)).thenReturn(ownerChangeRequestDto);

        when(securityServerEntity.getOwner()).thenReturn(currentOwnerMock);

        final Set<SecurityServerEntity> ownedServersMock = mock(HashSet.class);
        final ClientIdEntity currentOwnerIdentifier = MemberIdEntity.create("x", "y", "z");
        when(currentOwnerMock.getOwnedServers()).thenReturn(ownedServersMock);
        when(currentOwnerMock.getIdentifier()).thenReturn(currentOwnerIdentifier);
        when(securityServerEntity.getServerClients())
                .thenReturn(Set.of(new ServerClientEntity(securityServerEntity, xRoadMemberEntity)));
        when(members.findOneBy(currentOwnerMock.getIdentifier())).thenReturn(Option.of(currentOwnerMock));
        when(ownedServersMock.isEmpty()).thenReturn(true);

        final OwnerChangeRequest result = ownerChangeRequestHandler.approve(request);
        assertThat(result).isEqualTo(ownerChangeRequestDto);

        verify(securityServerEntity).setOwner(xRoadMemberEntity);
        verify(serverIds).findOpt(securityServerEntity.getServerId());
        verify(serverIds).saveAndFlush(securityServerEntity.getServerId());

        ArgumentCaptor<ServerClientEntity> argHandler = ArgumentCaptor.forClass(ServerClientEntity.class);
        verify(serverClients).saveAndFlush(argHandler.capture());
        assertThat(argHandler.getValue().getSecurityServer()).isEqualTo(securityServerEntity);
        assertThat(argHandler.getValue().getSecurityServerClient()).isEqualTo(currentOwnerMock);

        verify(serverClients).delete(argHandler.capture());
        assertThat(argHandler.getValue().getSecurityServer()).isEqualTo(securityServerEntity);
        assertThat(argHandler.getValue().getSecurityServerClient()).isEqualTo(xRoadMemberEntity);

        verify(servers).saveAndFlush(securityServerEntity);

        verify(ownerChangeRequestEntity).setProcessingStatus(APPROVED);

        verify(groupMemberService).removeMemberFromGlobalGroup(DEFAULT_SECURITY_SERVER_OWNERS_GROUP, MemberId.create("x", "y", "z"));
        verify(groupMemberService).addMemberToGlobalGroup(
                MemberId.create(xRoadMemberEntity.getIdentifier()), DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
    }

    @Test
    void requestType() {
        assertThat(ownerChangeRequestHandler.requestType()).isEqualTo(OwnerChangeRequest.class);
    }
}
