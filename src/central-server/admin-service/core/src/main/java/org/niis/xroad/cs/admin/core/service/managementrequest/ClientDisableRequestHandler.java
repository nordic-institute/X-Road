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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.cs.admin.api.domain.ClientDisableRequest;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.ClientDisableRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_FORBIDDEN_DISABLE_MANAGEMENT_SERVICE_CLIENT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_CLIENT_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
class ClientDisableRequestHandler implements RequestHandler<ClientDisableRequest> {
    private final SystemParameterService systemParameterService;
    private final SecurityServerRepository servers;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<ClientIdEntity> clientIds;
    private final RequestRepository<ClientDisableRequestEntity> disableRequests;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(ClientDisableRequest request) {
        return false;
    }

    @Override
    public ClientDisableRequest add(ClientDisableRequest request) {
        final var serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        final var clientId = clientIds.findOne(ClientIdEntity.ensure(request.getClientId()));
        var serverClient = findServerClient(serverId, clientId)
                .orElseThrow(() -> new ConflictException(MR_SERVER_CLIENT_NOT_FOUND.build(
                        request.getSecurityServerId(),
                        request.getClientId())));

        if (request.getClientId().equals(systemParameterService.getManagementServiceProviderId())) {
            throw new ConflictException(MR_FORBIDDEN_DISABLE_MANAGEMENT_SERVICE_CLIENT.build(request.getClientId()));
        }

        serverClient.setEnabled(false);

        final var requestEntity = new ClientDisableRequestEntity(request.getOrigin(), serverId, clientId, request.getComments());
        final var persistedRequest = disableRequests.save(requestEntity);

        return requestMapper.toDto(persistedRequest);
    }

    private Optional<ServerClientEntity> findServerClient(SecurityServerIdEntity serverId, ClientIdEntity clientId) {
        return servers.findBy(serverId, clientId)
                .flatMap(securityServer -> securityServer.getServerClients().stream()
                        .filter(serverClient -> serverClient.getSecurityServerClient().getIdentifier().equals(clientId))
                        .findFirst());
    }

    @Override
    public ClientDisableRequest approve(ClientDisableRequest request) {
        return request;
    }

    @Override
    public Class<ClientDisableRequest> requestType() {
        return ClientDisableRequest.class;
    }

}
