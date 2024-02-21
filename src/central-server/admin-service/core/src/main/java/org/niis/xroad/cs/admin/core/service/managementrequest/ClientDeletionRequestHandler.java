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
package org.niis.xroad.cs.admin.core.service.managementrequest;


import io.vavr.control.Option;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.core.entity.ClientDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.DECLINED;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.REVOKED;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_CLIENT_REGISTRATION_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientDeletionRequestHandler implements RequestHandler<ClientDeletionRequest> {

    private final SecurityServerClientRepository<SecurityServerClientEntity> clients;
    private final SecurityServerRepository servers;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<ClientIdEntity> clientIds;
    private final RequestRepository<ClientDeletionRequestEntity> deletionRequests;
    private final ClientRegistrationRequestRepository registrationRequests;
    private final ServerClientRepository serverClientRepository;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(ClientDeletionRequest request) {
        return false;
    }

    @Override
    public ClientDeletionRequest add(ClientDeletionRequest request) {
        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        final ClientIdEntity clientId = clientIds.findOne(ClientIdEntity.ensure(request.getClientId()));

        final Option<SecurityServerEntity> securityServerOpt = servers.findBy(serverId, clientId);

        securityServerOpt
                .peek(server -> deleteSecurityServerClient(server, clientId))
                .onEmpty(() -> tryToRevokePreviousRegistration(serverId, clientId));

        final var requestEntity = new ClientDeletionRequestEntity(request.getOrigin(), serverId, clientId, request.getComments());
        final var persistedRequest = deletionRequests.save(requestEntity);

        return requestMapper.toDto(persistedRequest);
    }

    private void tryToRevokePreviousRegistration(final SecurityServerIdEntity serverId, final ClientIdEntity clientId) {
        var requests = registrationRequests.findBy(serverId, clientId, Set.of(WAITING));
        requests.stream()
                .findFirst()
                .ifPresentOrElse(
                        this::revokeRegistration,
                        () -> whenDeclinedRegistrationNotFoundThenThrowNotFoundException(serverId, clientId)
                );
    }

    private void whenDeclinedRegistrationNotFoundThenThrowNotFoundException(
            final SecurityServerIdEntity serverId, final ClientIdEntity clientId) {
        registrationRequests.findBy(serverId, clientId, Set.of(DECLINED))
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(MR_CLIENT_REGISTRATION_NOT_FOUND));
    }

    private void deleteSecurityServerClient(final SecurityServerEntity securityServer, final ClientIdEntity clientId) {
        clients.findOneBy(clientId).toJavaOptional().ifPresentOrElse(
                client -> securityServer.getServerClients()
                        .stream()
                        .filter(serverClient -> client.equals(serverClient.getSecurityServerClient()))
                        .forEach(serverClientRepository::delete),
                this::mrClientRegistrationNotFound
        );
    }

    private void revokeRegistration(final ClientRegistrationRequestEntity requestEntity) {
        requestEntity.getRequestProcessing().setStatus(REVOKED);
        registrationRequests.save(requestEntity);
    }

    private void mrClientRegistrationNotFound() {
        throw new NotFoundException(MR_CLIENT_REGISTRATION_NOT_FOUND);
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
