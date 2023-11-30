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

import io.vavr.control.Option;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ClientDisableRequest;
import org.niis.xroad.cs.admin.core.entity.ClientDisableRequestEntity;
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
class ClientDisableRequestHandler implements RequestHandler<ClientDisableRequest> {

    private final SecurityServerClientRepository<SecurityServerClientEntity> clients;
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
        // TODO validate


        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        final ClientIdEntity clientId = clientIds.findOne(ClientIdEntity.ensure(request.getClientId()));

        final Option<SecurityServerEntity> securityServerOpt = servers.findBy(serverId, clientId);

        securityServerOpt.peek(server -> disableSecurityServerClient(server, clientId));

        final var requestEntity = new ClientDisableRequestEntity(request.getOrigin(), serverId, clientId, request.getComments());
        final var persistedRequest = disableRequests.save(requestEntity);

        return requestMapper.toDto(persistedRequest);
    }

    private void disableSecurityServerClient(SecurityServerEntity server, ClientIdEntity clientId) {
        server.getServerClients().stream()
                .filter(serverClient -> serverClient.getSecurityServerClient().getIdentifier().equals(clientId))
                .forEach(serverClient -> serverClient.setEnabled(false));
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
