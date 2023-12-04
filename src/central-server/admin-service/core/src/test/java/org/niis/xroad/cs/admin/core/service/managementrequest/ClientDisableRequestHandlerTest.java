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
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.api.domain.ClientDisableRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.core.entity.ClientDisableRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import java.util.Set;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientDisableRequestHandlerTest {
    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");
    private final ClientId subsystemId = SubsystemId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SUBSYSTEM-CODE");
    private final ClientId unknownSubsystemId = SubsystemId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SUBSYSTEM-CODE-UNKNOWN");
    @Mock
    private IdentifierRepository<ClientIdEntity> clientIds;
    @Mock
    private SecurityServerRepository servers;
    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Mock
    private RequestRepository<ClientDisableRequestEntity> disableRequests;
    @Mock
    private RequestMapper requestMapper;

    private ClientDisableRequestHandler handler;


    @BeforeEach
    void setup() {
        handler = new ClientDisableRequestHandler(servers, serverIds, clientIds, disableRequests, requestMapper);
    }



    @Test
    void add() {
        var request = new ClientDisableRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);

        var server = mock(SecurityServerEntity.class);
        var serverClient = mock(ServerClientEntity.class);
        var securityServerClient = mock(SecurityServerClientEntity.class);

        var managementRequest = mock(ClientDisableRequestEntity.class);

        when(securityServerClient.getIdentifier()).thenReturn(mockClientId);
        when(serverClient.getSecurityServerClient()).thenReturn(securityServerClient);
        when(server.getServerClients()).thenReturn(Set.of(serverClient));

        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIds.findOne(ClientIdEntity.ensure(subsystemId))).thenReturn(mockClientId);
        when(servers.findBy(mockServerId, mockClientId)).thenReturn(Option.of(server));

        when(disableRequests.save(isA(ClientDisableRequestEntity.class))).thenReturn(managementRequest);

        handler.add(request);

        verify(serverClient).setEnabled(false);
        verify(requestMapper).toDto(managementRequest);
    }

    @Test
    void clientNotFound() {
        var request = new ClientDisableRequest(Origin.SECURITY_SERVER, securityServerId, unknownSubsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);

        var server = mock(SecurityServerEntity.class);

        when(server.getServerClients()).thenReturn(Set.of());
        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIds.findOne(ClientIdEntity.ensure(unknownSubsystemId))).thenReturn(mockClientId);
        when(servers.findBy(mockServerId, mockClientId)).thenReturn(Option.of(server));

        Assertions.assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(DataIntegrityException.class)
                .hasMessage("Security server client not found");
    }


}
