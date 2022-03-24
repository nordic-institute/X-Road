/**
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
package org.niis.xroad.centralserver.restapi.service.managementrequest;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.dto.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.entity.ClientRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.centralserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.EnumSet;

import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.WAITING;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientRegistrationRequestHandler
        implements RequestHandler<ClientRegistrationRequestDto, ClientRegistrationRequest> {

    private final IdentifierRepository<SecurityServerId> serverIds;
    private final IdentifierRepository<ClientId> clientIds;
    private final XRoadMemberRepository members;
    private final SecurityServerClientRepository<SecurityServerClient> clients;
    private final ClientRegistrationRequestRepository clientRegRequests;
    private final SecurityServerRepository servers;

    @Override
    public boolean canAutoApprove(ClientRegistrationRequest request) {
        return (SystemProperties.getCenterAutoApproveClientRegRequests()
                || request.getProcessingStatus().equals(SUBMITTED_FOR_APPROVAL))
                && request.getOrigin() == Origin.SECURITY_SERVER
                && servers.count(SecurityServerRepository.serverIdSpec(request.getSecurityServerId())) > 0
                && members.findMember(request.getClientId()).isPresent();
    }

    @Override
    public ClientRegistrationRequest add(ClientRegistrationRequestDto request) {

        if (request.getServerId().getOwner().equals(request.getClientId())) {
            throw new ValidationFailureException(ErrorMessage.MANAGEMENT_REQUEST_CANNOT_REGISTER_OWNER);
        }

        if (Origin.CENTER.equals(request.getOrigin())) {
            var owner = members.findOneBy(request.getServerId().getOwner()).orElseThrow(
                    () -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND,
                            request.getServerId().getOwner().toString()));

            servers.findByOwnerAndServerCode(owner, request.getServerId().getServerCode()).orElseThrow(
                    () -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND,
                            request.getServerId().toString()));

            members.findMember(request.getClientId()).orElseThrow(() ->
                    new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_MEMBER_NOT_FOUND,
                            request.getClientId().toString()));
        }

        servers.findBy(request.getServerId(), request.getClientId()).ifPresent(s -> {
            //fixme wrong error code
            throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_ALREADY_REGISTERED);
        });

        var serverId = serverIds.merge(request.getServerId());
        var clientId = clientIds.merge(request.getClientId());

        var pending = clientRegRequests.findBy(serverId, clientId,
                EnumSet.of(ManagementRequestStatus.SUBMITTED_FOR_APPROVAL, ManagementRequestStatus.WAITING));

        ClientRegistrationRequest req;
        switch (pending.size()) {
            case 0:
                req = new ClientRegistrationRequest(request.getOrigin(), serverId, clientId);
                break;
            case 1:
                var anotherReq = pending.get(0);
                if (anotherReq.getOrigin().equals(request.getOrigin())) {
                    throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS,
                            String.valueOf(anotherReq.getId()));
                }
                req = new ClientRegistrationRequest(request.getOrigin(), anotherReq);
                req.setProcessingStatus(ManagementRequestStatus.SUBMITTED_FOR_APPROVAL);
                break;
            default:
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS);

        }

        return clientRegRequests.save(req);
    }

    @Override
    public ClientRegistrationRequest approve(ClientRegistrationRequest request) {
        if (!EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING).contains(request.getRequestProcessing().getStatus())) {
            throw new ValidationFailureException(ErrorMessage.MANAGEMENT_REQUEST_INVALID_STATE_FOR_APPROVAL,
                    String.valueOf(request.getId()));
        }

        var server = servers.findOne(SecurityServerRepository.serverIdSpec(request.getSecurityServerId()))
                .orElseThrow(() -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND));

        var clientMember = members.findMember(request.getClientId()).orElseThrow(() ->
                new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_MEMBER_NOT_FOUND,
                        request.getClientId().toString()));

        SecurityServerClient client;
        switch (request.getClientId().getObjectType()) {
            case MEMBER:
                client = clientMember;
                break;
            case SUBSYSTEM:
                // create new subsystem if necessary
                client = clients
                        .findOneBy(request.getClientId())
                        .orElseGet(() -> clients.save(new Subsystem(clientMember, request.getClientId())));
                break;
            default:
                throw new IllegalArgumentException("Invalid client type");
        }
        server.addClient(client);
        request.setProcessingStatus(ManagementRequestStatus.APPROVED);
        return request;
    }

    @Override
    public Class<ClientRegistrationRequest> requestType() {
        return ClientRegistrationRequest.class;
    }

    @Override
    public Class<ClientRegistrationRequestDto> dtoType() {
        return ClientRegistrationRequestDto.class;
    }
}
