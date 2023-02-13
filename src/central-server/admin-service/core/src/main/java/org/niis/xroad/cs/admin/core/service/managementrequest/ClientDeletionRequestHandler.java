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
package org.niis.xroad.cs.admin.core.service.managementrequest;


import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.core.entity.ClientDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientDeletionRequestHandler implements RequestHandler<ClientDeletionRequest> {

    private final SecurityServerClientRepository<SecurityServerClientEntity> clients;
    private final SecurityServerRepository servers;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<ClientIdEntity> clientIds;
    private final RequestRepository<ClientDeletionRequestEntity> requests;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(ClientDeletionRequest request) {
        return false;
    }

    @Override
    public ClientDeletionRequest add(ClientDeletionRequest request) {
        final SecurityServerIdEntity serverId = serverIds.findOrCreate(SecurityServerIdEntity.create(request.getSecurityServerId()));
        final ClientIdEntity clientId = clientIds.findOrCreate(ClientIdEntity.ensure(request.getClientId()));

        final var requestEntity = new ClientDeletionRequestEntity(request.getOrigin(), serverId, clientId);

        SecurityServerEntity securityServer = servers.findBy(serverId, clientId).getOrElseThrow(() ->
                new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_CLIENT_REGISTRATION_NOT_FOUND));

        //todo: somewhat inefficient, could also directly delete the association entity
        SecurityServerClientEntity client = clients.findOneBy(clientId).getOrElseThrow(() ->
                new NoSuchElementException("No value present"));
        for (var it = client.getServerClients().iterator(); it.hasNext(); ) {
            var item = it.next();
            if (item.getSecurityServer() == securityServer) {
                it.remove();
                break;
            }
        }

        /*
         * Note. The legacy implementation revokes existing pending registration requests. However, that does
         * not seem right since if a request is pending, there is no registration that could be deleted,
         * and if there is a registration, there can not be pending requests (unless there is a concurrency issue
         * that should be addressed).
         */
        return Option.of(requestEntity)
                .map(RequestEntity::getOrigin)
                .flatMap(origin -> Option.of(serverId)
                        .map(serverIds::findOrCreate)
                        .map(dbSecurityServerId -> new ClientDeletionRequestEntity(origin, dbSecurityServerId, clientId)))
                .map(requests::save)
                .map(requestMapper::toDto)
                .get();
    }

    @Override
    public ClientDeletionRequest approve(ClientDeletionRequest request) {
        //nothing to do.
        return request;
    }

    @Override
    public Class<ClientDeletionRequest> requestType() {
        return ClientDeletionRequest.class;
    }

}
