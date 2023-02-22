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
package org.niis.xroad.cs.admin.core.service;

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
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.dto.ManagementRequestInfoDto;
import org.niis.xroad.cs.admin.api.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.service.managementrequest.ManagementRequestServiceImpl;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServerServiceImplTest implements WithInOrder {

    @Mock
    private ManagementRequestServiceImpl managementRequestService;

    @Mock
    private ClientService clientService;
    @Mock
    private SecurityServerRepository securityServerRepository;

    @Mock
    private SecurityServerMapper securityServerMapper;

    @Mock
    private AuditDataHelper auditDataHelper;

    @InjectMocks
    private SecurityServerServiceImpl securityServerService;

    @Mock
    private ManagementRequestInfoDto managementRequestInfoDto;
    @Mock
    private SecurityServerEntity securityServerEntity;
    @Mock
    private SecurityServer securityServer;
    @Mock
    private SecurityServerId serverId;

    @Nested
    @DisplayName("findSecurityServerRegistrationStatus(SecurityServerId serverId)")
    class SecurityServerRegStatusMethod implements WithInOrder {

        @Test
        @DisplayName("should find management status approved")
        void shouldReturnStatusApproved() {
            Page<ManagementRequestInfoDto> requestInfoDtos = new PageImpl<>(List.of(managementRequestInfoDto));
            doReturn(requestInfoDtos).when(managementRequestService)
                    .findRequests(any(), any());
            doReturn(ManagementRequestStatus.APPROVED).when(managementRequestInfoDto).getStatus();

            ManagementRequestStatus result = securityServerService.findSecurityServerRegistrationStatus(serverId);

            assertNotNull(result);
            assertEquals(ManagementRequestStatus.APPROVED, result);
        }

        @Test
        @DisplayName("should return null if no management request exit")
        void shouldReturnNullWhenRequestNotFound() {
            Page<ManagementRequestInfoDto> emptyRequestInfoDtos = Page.empty();
            doReturn(emptyRequestInfoDtos).when(managementRequestService)
                    .findRequests(any(), any());

            ManagementRequestStatus result = securityServerService.findSecurityServerRegistrationStatus(serverId);

            assertNull(result);
        }
    }

    @Nested
    class FindSecurityServer {

        @Test
        void find() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.of(securityServerEntity));
            when(securityServerMapper.toTarget(securityServerEntity)).thenReturn(securityServer);

            final Option<SecurityServer> result = securityServerService.find(serverId);

            assertThat(result.get()).isEqualTo(securityServer);
        }

        @Test
        void findShouldReturnEmpty() {
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.none());

            final Option<SecurityServer> result = securityServerService.find(serverId);

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
    class UpdateSecurityServerAddress implements WithInOrder {

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
            verify(auditDataHelper).put(RestApiAuditProperty.SERVER_CODE, serverId.getServerCode());
            verify(auditDataHelper).put(RestApiAuditProperty.OWNER_CODE, ownerId.getMemberCode());
            verify(auditDataHelper).put(RestApiAuditProperty.OWNER_CLASS, ownerId.getMemberClass());
            verify(auditDataHelper).put(RestApiAuditProperty.ADDRESS, newAddress);
            verifyNoMoreInteractions(auditDataHelper);
        }

        @Test
        void securityServerNotFound() {
            String newAddress = "http://localhost:443";
            when(serverId.getOwner()).thenReturn(ownerId);
            when(securityServerRepository.findBy(serverId)).thenReturn(Option.none());

            var result = securityServerService.updateSecurityServerAddress(serverId, newAddress);

            assertThat(result).isEqualTo(Option.none());
            verify(auditDataHelper).put(RestApiAuditProperty.SERVER_CODE, serverId.getServerCode());
            verify(auditDataHelper).put(RestApiAuditProperty.OWNER_CODE, ownerId.getMemberCode());
            verify(auditDataHelper).put(RestApiAuditProperty.OWNER_CLASS, ownerId.getMemberClass());
            verify(auditDataHelper).put(RestApiAuditProperty.ADDRESS, newAddress);
            verifyNoMoreInteractions(auditDataHelper);
            verifyNoInteractions(securityServerMapper);
        }
    }
}
