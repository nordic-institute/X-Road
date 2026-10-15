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
package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.core.entity.ClientDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.MemberDidEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.DECLINED;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_CLIENT_REGISTRATION_NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class ClientDeletionRequestHandlerTest {
    @Mock
    private SecurityServerClientRepository<SecurityServerClientEntity> clientRepository;
    @Mock
    private SecurityServerRepository serverRepository;
    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIdRepository;
    @Mock
    private IdentifierRepository<ClientIdEntity> clientIdRepository;
    @Mock
    private RequestRepository<ClientDeletionRequestEntity> deletionRequestRepository;
    @Mock
    private ClientRegistrationRequestRepository registrationRequestRepository;
    @Mock
    private ServerClientRepository serverClientRepository;
    @Mock
    private XRoadMemberRepository members;
    @Mock
    private MemberDidRepository memberDids;
    @Mock
    private RequestMapper requestMapper;
    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");
    private final ClientId subsystemId = SubsystemId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SUBSYSTEM-CODE");
    private ClientDeletionRequestHandler handler;

    @BeforeEach
    void setup() {
        handler = new ClientDeletionRequestHandler(
                clientRepository,
                serverRepository,
                serverIdRepository,
                clientIdRepository,
                deletionRequestRepository,
                registrationRequestRepository,
                serverClientRepository,
                members,
                memberDids,
                requestMapper);
    }

    @Test
    void registrationNotFound() {
        var request = new ClientDeletionRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);

        when(serverIdRepository.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIdRepository.findOne(ClientIdEntityFactory.ensure(subsystemId))).thenReturn(mockClientId);
        when(serverRepository.findBy(mockServerId, mockClientId)).thenReturn(Optional.empty());

        var err = assertThrows(NotFoundException.class, () -> handler.add(request));
        assertEquals(MR_CLIENT_REGISTRATION_NOT_FOUND.code(), err.getErrorDeviation().code());
    }

    @Test
    void existsDeclinedRegistration() {
        var request = new ClientDeletionRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);
        var mockClientDeletionRequest = mock(ClientDeletionRequestEntity.class);
        var mockDeclinedClientRegistrationRequest = mock(ClientRegistrationRequestEntity.class);

        when(serverIdRepository.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIdRepository.findOne(ClientIdEntityFactory.ensure(subsystemId))).thenReturn(mockClientId);
        when(serverRepository.findBy(mockServerId, mockClientId)).thenReturn(Optional.empty());
        when(registrationRequestRepository.findBy(mockServerId, mockClientId, Set.of(WAITING))).thenReturn(List.of());
        when(registrationRequestRepository.findBy(mockServerId, mockClientId, Set.of(DECLINED)))
                .thenReturn(List.of(mockDeclinedClientRegistrationRequest));
        when(deletionRequestRepository.save(isA(ClientDeletionRequestEntity.class))).thenReturn(mockClientDeletionRequest);

        handler.add(request);

        verify(requestMapper).toDto(mockClientDeletionRequest);
    }

    @Nested
    class RemovingAServerClient {
        private static final String MEMBER_DID = "did:web:ss1.example.test:member";
        private static final int SERVER_ROW_ID = 7;
        private static final long MEMBER_ROW_ID = 5L;

        private final MemberIdEntity memberId = MemberIdEntity.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE");
        private final MemberIdEntity ownerId = MemberIdEntity.create("INSTANCE", "MEMBER_CLASS", "OWNER_CODE");

        @Mock
        private SecurityServerIdEntity mockServerId;
        @Mock
        private ClientIdEntity mockClientId;
        @Mock
        private ClientDeletionRequestEntity mockClientDeletionRequest;
        @Mock
        private SecurityServerEntity mockSecurityServer;
        @Mock
        private SecurityServerClientEntity mockClient;
        @Mock
        private ServerClientEntity mockServerClient;
        @Mock
        private XRoadMemberEntity mockMember;
        @Mock
        private XRoadMemberEntity mockOwner;
        @Mock
        private SubsystemEntity mockSubsystem;

        private ClientDeletionRequest request;

        @BeforeEach
        void registeredClient() {
            request = new ClientDeletionRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);
            when(serverIdRepository.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
            when(clientIdRepository.findOne(ClientIdEntityFactory.ensure(subsystemId))).thenReturn(mockClientId);
            when(serverRepository.findBy(mockServerId, mockClientId)).thenReturn(Optional.of(mockSecurityServer));
            when(clientRepository.findOneBy(mockClientId)).thenReturn(Optional.of(mockClient));
            when(mockClient.getId()).thenReturn(1L);
            when(mockServerClient.getSecurityServerClient()).thenReturn(mockClient);
            when(mockSecurityServer.getServerClients()).thenReturn(Set.of(mockServerClient));
            when(mockSecurityServer.getId()).thenReturn(SERVER_ROW_ID);
            lenient().when(mockSecurityServer.getOwner()).thenReturn(mockOwner);
            lenient().when(mockOwner.getIdentifier()).thenReturn(ownerId);
            when(mockClientId.getMemberId()).thenReturn(memberId);
            when(deletionRequestRepository.save(isA(ClientDeletionRequestEntity.class))).thenReturn(mockClientDeletionRequest);
        }

        private void memberWithoutRemainingClients() {
            when(members.findMember(memberId)).thenReturn(Optional.of(mockMember));
            when(mockMember.getId()).thenReturn(MEMBER_ROW_ID);
            when(mockMember.getSubsystems()).thenReturn(Set.of(mockSubsystem));
            when(serverClientRepository.countBySecurityServerAndSecurityServerClientIn(eq(mockSecurityServer), any()))
                    .thenReturn(0L);
        }

        @Test
        void deletesDidRowOfLastClient() {
            memberWithoutRemainingClients();
            var storedDid = new MemberDidEntity(mockSecurityServer, mockMember, MEMBER_DID);
            when(memberDids.findBySecurityServerIdAndMemberId(SERVER_ROW_ID, MEMBER_ROW_ID)).thenReturn(Optional.of(storedDid));

            handler.add(request);

            verify(serverClientRepository).delete(mockServerClient);
            verify(memberDids).delete(storedDid);
        }

        @Test
        void locksServerRowBeforeDeletingAndCountingClients() {
            memberWithoutRemainingClients();
            when(memberDids.findBySecurityServerIdAndMemberId(SERVER_ROW_ID, MEMBER_ROW_ID)).thenReturn(Optional.empty());

            handler.add(request);

            InOrder inOrder = inOrder(serverRepository, serverClientRepository, memberDids);
            inOrder.verify(serverRepository).lockForUpdateById(SERVER_ROW_ID);
            inOrder.verify(serverClientRepository).delete(mockServerClient);
            inOrder.verify(serverClientRepository).countBySecurityServerAndSecurityServerClientIn(eq(mockSecurityServer), any());
            inOrder.verify(memberDids).findBySecurityServerIdAndMemberId(SERVER_ROW_ID, MEMBER_ROW_ID);
        }

        @Test
        void countsTheMemberAndItsSubsystemsTogether() {
            memberWithoutRemainingClients();
            when(memberDids.findBySecurityServerIdAndMemberId(SERVER_ROW_ID, MEMBER_ROW_ID)).thenReturn(Optional.empty());

            handler.add(request);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<SecurityServerClientEntity>> counted = ArgumentCaptor.forClass(Collection.class);
            verify(serverClientRepository).countBySecurityServerAndSecurityServerClientIn(eq(mockSecurityServer), counted.capture());
            assertThat(counted.getValue()).containsExactlyInAnyOrder(mockMember, mockSubsystem);
        }

        @Test
        void keepsDidRowWhileAnyClientOfTheMemberRemains() {
            when(members.findMember(memberId)).thenReturn(Optional.of(mockMember));
            when(mockMember.getSubsystems()).thenReturn(Set.of(mockSubsystem));
            when(serverClientRepository.countBySecurityServerAndSecurityServerClientIn(eq(mockSecurityServer), any()))
                    .thenReturn(1L);

            handler.add(request);

            verifyNoInteractions(memberDids);
        }

        @Test
        void deletesNothingAndDoesNotFailWhenMemberHasNoStoredDid() {
            memberWithoutRemainingClients();
            when(memberDids.findBySecurityServerIdAndMemberId(SERVER_ROW_ID, MEMBER_ROW_ID)).thenReturn(Optional.empty());

            handler.add(request);

            verify(serverClientRepository).delete(mockServerClient);
            verify(memberDids, never()).delete(any());
            verify(deletionRequestRepository).save(isA(ClientDeletionRequestEntity.class));
        }

        @Test
        void skipsTheOwnerWithoutTouchingItsDidRow() {
            when(mockClientId.getMemberId()).thenReturn(ownerId);

            handler.add(request);

            verify(serverClientRepository).delete(mockServerClient);
            verifyNoInteractions(members, memberDids);
            verify(serverClientRepository, never()).countBySecurityServerAndSecurityServerClientIn(any(), any());
        }
    }
}
