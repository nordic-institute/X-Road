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
import org.niis.xroad.cs.admin.api.domain.ClientEnableRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.core.entity.ClientEnableRequestEntity;
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
class ClientEnableRequestHandlerTest {
    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");
    private final ClientId subsystemId = SubsystemId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SUBSYSTEM-CODE");
    private final ClientId unknownSubsystemId = SubsystemId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SUBSYSTEM-CODE-UNKNOWN");

    @Mock
    private SecurityServerRepository servers;
    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Mock
    private IdentifierRepository<ClientIdEntity> clientIds;
    @Mock
    private RequestRepository<ClientEnableRequestEntity> enableRequests;
    @Mock
    private RequestMapper requestMapper;


    private ClientEnableRequestHandler handler;

    @BeforeEach
    void setup() {
        handler = new ClientEnableRequestHandler(servers, serverIds, clientIds, enableRequests, requestMapper);
    }

    @Test
    void add() {
        var request = new ClientEnableRequest(Origin.SECURITY_SERVER, securityServerId, subsystemId);

        var mockServerId = mock(SecurityServerIdEntity.class);
        var mockClientId = mock(ClientIdEntity.class);

        var server = mock(SecurityServerEntity.class);
        var serverClient = mock(ServerClientEntity.class);
        var securityServerClient = mock(SecurityServerClientEntity.class);

        var managementRequest = mock(ClientEnableRequestEntity.class);

        when(securityServerClient.getIdentifier()).thenReturn(mockClientId);
        when(serverClient.getSecurityServerClient()).thenReturn(securityServerClient);
        when(server.getServerClients()).thenReturn(Set.of(serverClient));

        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mockServerId);
        when(clientIds.findOne(ClientIdEntity.ensure(subsystemId))).thenReturn(mockClientId);
        when(servers.findBy(mockServerId, mockClientId)).thenReturn(Option.of(server));

        when(enableRequests.save(isA(ClientEnableRequestEntity.class))).thenReturn(managementRequest);

        handler.add(request);

        verify(serverClient).setEnabled(true);
        verify(requestMapper).toDto(managementRequest);
    }

    @Test
    void clientNotFound() {
        var request = new ClientEnableRequest(Origin.SECURITY_SERVER, securityServerId, unknownSubsystemId);

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
