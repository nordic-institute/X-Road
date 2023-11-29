package org.niis.xroad.cs.admin.core.service.managementrequest;

import io.vavr.control.Option;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ClientEnableRequest;
import org.niis.xroad.cs.admin.core.entity.ClientEnableRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
class ClientEnableRequestHandler implements RequestHandler<ClientEnableRequest> {

    private final SecurityServerClientRepository<SecurityServerClientEntity> clients;
    private final SecurityServerRepository servers;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<ClientIdEntity> clientIds;
    private final RequestRepository<ClientEnableRequestEntity> enableRequests;
    private final RequestMapper requestMapper;


    @Override
    public boolean canAutoApprove(ClientEnableRequest request) {
        return false;
    }

    @Override
    public ClientEnableRequest add(ClientEnableRequest request) {
        // TODO validate

        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        final ClientIdEntity clientId = clientIds.findOne(ClientIdEntity.ensure(request.getClientId()));

        final Option<SecurityServerEntity> securityServerOpt = servers.findBy(serverId, clientId);

        securityServerOpt.peek(server -> enableSecurityServerClient(server, clientId));

        final var requestEntity = new ClientEnableRequestEntity(request.getOrigin(), serverId, clientId, request.getComments());
        final var persistedRequest = enableRequests.save(requestEntity);

        return requestMapper.toDto(persistedRequest);
    }

    private void enableSecurityServerClient(SecurityServerEntity server, ClientIdEntity clientId) {
        server.getServerClients().stream()
                .filter(serverClient -> serverClient.getSecurityServerClient().getIdentifier().equals(clientId))
                .forEach(serverClient -> serverClient.setEnabled(true));
    }

    @Override
    public ClientEnableRequest approve(ClientEnableRequest request) {
        return request;
    }

    @Override
    public Class<ClientEnableRequest> requestType() {
        return ClientEnableRequest.class;
    }
}
