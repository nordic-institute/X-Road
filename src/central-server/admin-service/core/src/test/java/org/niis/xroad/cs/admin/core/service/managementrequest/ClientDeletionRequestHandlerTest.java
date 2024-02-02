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

import io.vavr.control.Option;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.core.entity.ClientDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                requestMapper);
    }

    @Test
    void registrationNotFound() {
        var request = new ClientDeletionRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);

        when(serverIdRepository.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIdRepository.findOne(ClientIdEntity.ensure(subsystemId))).thenReturn(mockClientId);
        when(serverRepository.findBy(mockServerId, mockClientId)).thenReturn(Option.none());

        Assertions.assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(MR_CLIENT_REGISTRATION_NOT_FOUND.getDescription());
    }

    @Test
    void existsDeclinedRegistration() {
        var request = new ClientDeletionRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);
        var mockClientDeletionRequest = mock(ClientDeletionRequestEntity.class);
        var mockDeclinedClientRegistrationRequest = mock(ClientRegistrationRequestEntity.class);

        when(serverIdRepository.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIdRepository.findOne(ClientIdEntity.ensure(subsystemId))).thenReturn(mockClientId);
        when(serverRepository.findBy(mockServerId, mockClientId)).thenReturn(Option.none());
        when(registrationRequestRepository.findBy(mockServerId, mockClientId, Set.of(WAITING))).thenReturn(List.of());
        when(registrationRequestRepository.findBy(mockServerId, mockClientId, Set.of(DECLINED)))
                .thenReturn(List.of(mockDeclinedClientRegistrationRequest));
        when(deletionRequestRepository.save(isA(ClientDeletionRequestEntity.class))).thenReturn(mockClientDeletionRequest);

        handler.add(request);

        verify(requestMapper).toDto(mockClientDeletionRequest);
    }
}
