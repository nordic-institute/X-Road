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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestView;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.Request;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.dto.SecurityServerAuthenticationCertificateDetails;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.paging.PageDto;
import org.niis.xroad.cs.admin.core.service.managementrequest.ManagementRequestServiceImpl;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.data.domain.PageImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_DELETION_REQUEST;
import static org.niis.xroad.cs.admin.api.domain.Origin.CENTER;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

@ExtendWith(MockitoExtension.class)
class SecurityServerServiceImplTest implements WithInOrder {

    @Mock
    private ManagementRequestServiceImpl managementRequestService;
    @Mock
    private GlobalGroupMemberService groupMemberService;
    @Mock
    private ManagementRequestView managementRequestView;
    @Mock
    private ClientService clientService;
    @Mock
    private SubsystemService subsystemService;
    @Mock
    private SecurityServerRepository securityServerRepository;
    @Mock
    private AuthCertRepository authCertRepository;
    @Mock
    private CertificateConverter certificateConverter;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private SecurityServerMapper securityServerMapper;

    @InjectMocks
    private SecurityServerServiceImpl securityServerService;

    @Mock
    private SecurityServerEntity securityServerEntity;
    @Mock
    private SecurityServer securityServer;
    @Mock
    private SecurityServerId serverId;
    @Mock
    private ClientId clientId;

    @Nested
    @DisplayName("findSecurityServerRegistrationStatus(SecurityServerId serverId)")
    class SecurityServerRegStatusMethod implements WithInOrder {

        @Test
        @DisplayName("should find management status approved")
        void shouldReturnStatusApproved() {
            Page<ManagementRequestView> managementRequestViews = new PageDto<>(new PageImpl<>(List.of(managementRequestView)));
            doReturn(managementRequestViews).when(managementRequestService)
                    .findRequests(any(), any());
            doReturn(ManagementRequestStatus.APPROVED).when(managementRequestView).getStatus();

            ManagementRequestStatus result = securityServerService.findSecurityServerClientRegistrationStatus(serverId, clientId);

            assertNotNull(result);
            assertEquals(ManagementRequestStatus.APPROVED, result);
        }

        @Test
        @DisplayName("should return null if no management request exit")
        void shouldReturnNullWhenRequestNotFound() {
            Page<ManagementRequestView> emptyManagementRequestViews = new PageDto<>(org.springframework.data.domain.Page.empty());
            doReturn(emptyManagementRequestViews).when(managementRequestService)
                    .findRequests(any(), any());

            ManagementRequestStatus result = securityServerService.findSecurityServerClientRegistrationStatus(serverId, clientId);

            assertNull(result);
        }
    }

    @Nested
    class FindSecurityServer {

        @Test
        void find() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.of(securityServerEntity));
            when(securityServerMapper.toTarget(securityServerEntity)).thenReturn(securityServer);

            final Optional<SecurityServer> result = securityServerService.find(serverId);

            assertThat(result.get()).isEqualTo(securityServer);
        }

        @Test
        void findShouldReturnEmpty() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.none());

            final Optional<SecurityServer> result = securityServerService.find(serverId);

            assertThat(result.isEmpty()).isTrue();
            verifyNoInteractions(securityServerMapper);
        }
    }

    @Nested
    class FindClients {

        private final int securityServerId = new Random().nextInt();

        @Mock
        private FlattenedSecurityServerClientView securityServerClientView1, securityServerClientView2;
        @Captor
        private ArgumentCaptor<ClientService.SearchParameters> searchParamsCaptor;

        @Test
        void findClients() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.of(securityServerEntity));
            when(securityServerEntity.getId()).thenReturn(securityServerId);
            when(clientService.find(any(ClientService.SearchParameters.class)))
                    .thenReturn(List.of(securityServerClientView1, securityServerClientView2));

            final List<FlattenedSecurityServerClientView> result = securityServerService.findClients(serverId);

            assertThat(result).containsExactlyInAnyOrder(securityServerClientView1, securityServerClientView2);
            verify(clientService).find(searchParamsCaptor.capture());
            assertThat(searchParamsCaptor.getValue().getSecurityServerId()).isEqualTo(securityServerId);
        }

        @Test
        void findClientsShouldThrowExceptionWhenSecurityServerNotFound() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.none());

            assertThatThrownBy(() -> securityServerService.findClients(serverId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Security server not found");

            verifyNoInteractions(clientService);
        }
    }

    @Nested
    class UpdateSecurityServerAddress {

        MemberId ownerId = MemberId.create("UNIT_TEST", "MOCK", "test123");

        @Test
        void shouldUpdateSecurityServerAddress() {
            String newAddress = "http://localhost:443";
            when(serverId.getOwner()).thenReturn(ownerId);
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.of(securityServerEntity));
            when(securityServerMapper.toTarget(securityServerEntity)).thenReturn(securityServer);
            when(securityServer.getAddress()).thenReturn(newAddress);

            var result = securityServerService.updateSecurityServerAddress(serverId, newAddress);

            assertThat(result.get().getAddress()).isEqualTo(newAddress);
            verify(auditDataHelper).put(SERVER_CODE, serverId.getServerCode());
            verify(auditDataHelper).put(OWNER_CODE, ownerId.getMemberCode());
            verify(auditDataHelper).put(OWNER_CLASS, ownerId.getMemberClass());
            verify(auditDataHelper).put(RestApiAuditProperty.ADDRESS, newAddress);
            verifyNoMoreInteractions(auditDataHelper);
        }

        @Test
        void securityServerNotFound() {
            String newAddress = "http://localhost:443";
            when(serverId.getOwner()).thenReturn(ownerId);
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.none());

            var result = securityServerService.updateSecurityServerAddress(serverId, newAddress);

            assertThat(result.isEmpty()).isTrue();
            verify(auditDataHelper).put(SERVER_CODE, serverId.getServerCode());
            verify(auditDataHelper).put(OWNER_CODE, ownerId.getMemberCode());
            verify(auditDataHelper).put(OWNER_CLASS, ownerId.getMemberClass());
            verify(auditDataHelper).put(RestApiAuditProperty.ADDRESS, newAddress);
            verifyNoMoreInteractions(auditDataHelper);
            verifyNoInteractions(securityServerMapper);
        }
    }

    @Nested
    class FindSecurityServerAuthCerts {

        @Test
        void shouldFindSecurityServerAuthCerts() {
            var certificateDetailsMock = new SecurityServerAuthenticationCertificateDetails(1);
            AuthCertEntity authCertMock = new AuthCertEntity();
            authCertMock.setCert("test".getBytes());
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.of(securityServerEntity));
            when(securityServerEntity.getAuthCerts()).thenReturn(Set.of(authCertMock));
            when(certificateConverter.toCertificateDetails(authCertMock)).thenReturn(certificateDetailsMock);
            var result = securityServerService.findAuthCertificates(serverId);
            assertThat(result).containsOnly(certificateDetailsMock);
        }

        @Test
        void securityServerNotFound() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.none());

            assertThrows(NotFoundException.class, () -> securityServerService.findAuthCertificates(serverId));
        }

    }

    @Nested
    class Delete {

        private final SecurityServerId id = SecurityServerId.Conf.create("INSTANCE", "CLASS", "MEMBER", "SERVER-CODE");
        private final SecurityServerIdEntity securityServerIdEntity = SecurityServerIdEntity.create(id);
        private final ClientIdEntity clientIdEntity = MemberIdEntity.create("INSTANCE", "CLASS", "MEMBER");

        @Mock
        private XRoadMemberEntity xRoadMemberEntity;

        @Captor
        private ArgumentCaptor<Request> requestCaptor;

        @Test
        void delete() {
            when(securityServerRepository.findBy(id)).thenReturn(Option.of(securityServerEntity));
            when(securityServerEntity.getServerId()).thenReturn(securityServerIdEntity);
            when(securityServerEntity.getServerClients()).thenReturn(
                    Set.of(serverClientEntity("client-1"), serverClientEntity("client-2")));
            when(securityServerEntity.getAuthCerts()).thenReturn(
                    Set.of(new AuthCertEntity(securityServerEntity, new byte[]{1, 2, 3})));
            when(securityServerEntity.getOwner()).thenReturn(xRoadMemberEntity);
            when(xRoadMemberEntity.getIdentifier()).thenReturn(clientIdEntity);
            when(xRoadMemberEntity.getOwnedServers()).thenReturn(new HashSet<>(Set.of(securityServerEntity)));

            securityServerService.delete(id);

            verifyAudit();

            verify(managementRequestService, times(3)).add(requestCaptor.capture());
            verifyAuthCertDeleteRequest(requestCaptor.getAllValues());
            verifyClientDeletionRequest(requestCaptor.getAllValues(), "client-1");
            verifyClientDeletionRequest(requestCaptor.getAllValues(), "client-2");

            verify(groupMemberService).removeMemberFromGlobalGroup(DEFAULT_SECURITY_SERVER_OWNERS_GROUP,
                    MemberId.create(clientIdEntity));
            verify(securityServerRepository).delete(securityServerEntity);
        }

        private void verifyAuthCertDeleteRequest(List<Request> requests) {
            final AuthenticationCertificateDeletionRequest request = requests.stream()
                    .filter(req -> req instanceof AuthenticationCertificateDeletionRequest)
                    .map(req -> (AuthenticationCertificateDeletionRequest) req)
                    .findFirst().orElseThrow();

            assertThat(request.getOrigin()).isEqualTo(CENTER);
            assertThat(request.getComments()).isEqualTo("SERVER:INSTANCE/CLASS/MEMBER/SERVER-CODE deletion");
            assertThat(request.getSecurityServerId()).isEqualTo(id);
            assertThat(request.getAuthCert()).isEqualTo(new byte[]{1, 2, 3});
        }

        private void verifyClientDeletionRequest(List<Request> requests, String code) {
            final ClientDeletionRequest request = requests.stream()
                    .filter(req -> req instanceof ClientDeletionRequest)
                    .map(req -> (ClientDeletionRequest) req)
                    .filter(req -> code.equals(req.getClientId().getMemberCode()))
                    .findFirst().orElseThrow();

            assertThat(request.getOrigin()).isEqualTo(CENTER);
            assertThat(request.getComments()).isEqualTo("SERVER:INSTANCE/CLASS/MEMBER/SERVER-CODE deletion");
            assertThat(request.getSecurityServerId()).isEqualTo(id);
            assertThat(request.getClientId()).isEqualTo(MemberId.create("INST", "class", code));
        }

        private ServerClientEntity serverClientEntity(String code) {
            final ServerClientEntity entity = new ServerClientEntity();
            entity.setSecurityServer(securityServerEntity);
            entity.setSecurityServerClient(new XRoadMemberEntity("name",
                    ClientId.Conf.create("INST", "class", code),
                    new MemberClassEntity("class", "")));
            return entity;
        }

        @Test
        void deleteShouldThrowSecurityServerNotFound() {
            when(securityServerRepository.findBy(id)).thenReturn(Option.none());

            assertThatThrownBy(() -> securityServerService.delete(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Security server not found");

            verifyAudit();
            verifyNoMoreInteractions(securityServerRepository);
            verifyNoInteractions(managementRequestService, groupMemberService);
        }

        private void verifyAudit() {
            verify(auditDataHelper).put(SERVER_CODE, "SERVER-CODE");
            verify(auditDataHelper).put(OWNER_CODE, "MEMBER");
            verify(auditDataHelper).put(OWNER_CLASS, "CLASS");
        }
    }

    @Nested
    class DeleteAuthCertificate {

        private final Integer certificateId = new Random().nextInt();
        private final byte[] certBytes = new byte[]{1, 2, 3, 4, 5};
        private final String instance = "INSTANCE";
        private final String memberClass = "CLASS";
        private final String memberCode = "CODE";
        private final String serverCode = "SERVER-CODE";
        private final SecurityServerId securityServerId = SecurityServerId.Conf.create(instance, memberClass, memberCode, serverCode);
        private final SecurityServerIdEntity securityServerIdEntity = SecurityServerIdEntity.create(securityServerId);

        @Mock
        private AuthCertEntity authCertEntity;

        @Captor
        private ArgumentCaptor<AuthenticationCertificateDeletionRequest> argCaptor;

        @Test
        void deleteAuthCertificateShouldThrowServerNotFound() {
            when(securityServerRepository.existsBy(securityServerId)).thenReturn(false);

            assertThatThrownBy(() -> securityServerService.deleteAuthCertificate(securityServerId, certificateId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Security server not found");

            verify(auditDataHelper).put(OWNER_CLASS, memberClass);
            verify(auditDataHelper).put(OWNER_CODE, memberCode);
            verify(auditDataHelper).put(RestApiAuditProperty.SERVER_CODE, serverCode);
            verifyNoMoreInteractions(auditDataHelper);

            verifyNoInteractions(authCertRepository, managementRequestService);
        }

        @Test
        void deleteAuthCertificateShouldThrowCertificateNotFound() {
            when(securityServerRepository.existsBy(securityServerId)).thenReturn(true);
            when(authCertRepository.findById(certificateId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> securityServerService.deleteAuthCertificate(securityServerId, certificateId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Authentication certificate not found");

            verify(auditDataHelper).put(OWNER_CLASS, memberClass);
            verify(auditDataHelper).put(OWNER_CODE, memberCode);
            verify(auditDataHelper).put(RestApiAuditProperty.SERVER_CODE, serverCode);
            verifyNoMoreInteractions(auditDataHelper);
            verifyNoInteractions(managementRequestService);
        }

        @Test
        void deleteAuthCertificateShouldThrowCertificateNotFoundWhenCertBelongsToOtherServer() {
            when(securityServerRepository.existsBy(securityServerId)).thenReturn(true);
            when(authCertRepository.findById(certificateId)).thenReturn(Optional.of(authCertEntity));
            when(authCertEntity.getSecurityServer()).thenReturn(securityServerEntity);
            when(securityServerEntity.getServerId()).thenReturn(mock(SecurityServerIdEntity.class));

            assertThatThrownBy(() -> securityServerService.deleteAuthCertificate(securityServerId, certificateId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Authentication certificate not found");

            verify(auditDataHelper).put(OWNER_CLASS, memberClass);
            verify(auditDataHelper).put(OWNER_CODE, memberCode);
            verify(auditDataHelper).put(RestApiAuditProperty.SERVER_CODE, serverCode);
            verifyNoMoreInteractions(auditDataHelper);
            verifyNoInteractions(managementRequestService);
        }

        @Test
        void deleteAuthCertificate() {
            when(securityServerRepository.existsBy(securityServerId)).thenReturn(true);
            when(authCertRepository.findById(certificateId)).thenReturn(Optional.of(authCertEntity));
            when(authCertEntity.getSecurityServer()).thenReturn(securityServerEntity);
            when(securityServerEntity.getServerId()).thenReturn(securityServerIdEntity);
            when(authCertEntity.getCert()).thenReturn(certBytes);

            securityServerService.deleteAuthCertificate(securityServerId, certificateId);

            verify(auditDataHelper).put(OWNER_CLASS, memberClass);
            verify(auditDataHelper).put(OWNER_CODE, memberCode);
            verify(auditDataHelper).put(RestApiAuditProperty.SERVER_CODE, serverCode);
            verify(auditDataHelper).putCertificateHash(certBytes);
            verify(managementRequestService).add(argCaptor.capture());

            final AuthenticationCertificateDeletionRequest certDeletionRequest = argCaptor.getValue();

            assertThat(certDeletionRequest.getAuthCert()).isEqualTo(certBytes);
            assertThat(certDeletionRequest.getManagementRequestType()).isEqualTo(AUTH_CERT_DELETION_REQUEST);
            assertThat(certDeletionRequest.getOrigin()).isEqualTo(CENTER);
            assertThat(certDeletionRequest.getSecurityServerId()).isEqualTo(securityServerId);
        }
    }
}
