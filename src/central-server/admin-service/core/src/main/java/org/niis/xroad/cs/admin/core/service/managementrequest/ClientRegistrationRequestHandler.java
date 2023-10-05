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

import ee.ria.xroad.common.SystemProperties;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;

import static java.lang.String.valueOf;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_CANNOT_REGISTER_OWNER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_CLIENT_ALREADY_REGISTERED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_STATE_FOR_APPROVAL;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_MEMBER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
public class ClientRegistrationRequestHandler implements RequestHandler<ClientRegistrationRequest> {

    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<ClientIdEntity> clientIds;
    private final XRoadMemberRepository members;
    private final SecurityServerClientRepository<SecurityServerClientEntity> clients;
    private final ClientRegistrationRequestRepository clientRegRequests;
    private final SecurityServerRepository servers;
    private final ServerClientRepository serverClientRepository;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(ClientRegistrationRequest request) {
        return (SystemProperties.getCenterAutoApproveClientRegRequests()
                || request.getProcessingStatus().equals(SUBMITTED_FOR_APPROVAL))
                && request.getOrigin() == SECURITY_SERVER
                && servers.count(request.getSecurityServerId()) > 0
                && members.findMember(request.getClientId()).isDefined();
    }

    @Override
    public ClientRegistrationRequest add(ClientRegistrationRequest request) {
        final SecurityServerIdEntity serverId = serverIds.findOrCreate(SecurityServerIdEntity.create(request.getSecurityServerId()));
        ClientIdEntity clientId = ClientIdEntity.ensure(request.getClientId());
        final Origin origin = request.getOrigin();

        MemberIdEntity ownerId = serverId.getOwner();
        if (ownerId.equals(clientId)) {
            throw new ValidationFailureException(MR_CANNOT_REGISTER_OWNER);
        }

        if (Origin.CENTER.equals(origin)) {
            XRoadMemberEntity owner = members.findOneBy(ownerId).getOrElseThrow(
                    () -> new DataIntegrityException(MR_SERVER_NOT_FOUND,
                            ownerId.toString()));

            servers.findByOwnerIdAndServerCode(owner.getId(), serverId.getServerCode()).getOrElseThrow(
                    () -> new DataIntegrityException(MR_SERVER_NOT_FOUND,
                            serverId.toString()));

            members.findMember(clientId).getOrElseThrow(() ->
                    new DataIntegrityException(MR_MEMBER_NOT_FOUND,
                            request.getClientId().toString()));
        }

        clientId = clientIds.findOrCreate(clientId);
        servers.findBy(serverId, clientId).map(s -> {
            throw new DataIntegrityException(MR_CLIENT_ALREADY_REGISTERED);
        });

        List<ClientRegistrationRequestEntity> pending = clientRegRequests.findBy(
                serverId, clientId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)
        );

        ClientRegistrationRequestEntity req;
        switch (pending.size()) {
            case 0:
                req = new ClientRegistrationRequestEntity(origin, serverId, clientId, request.getComments());
                break;
            case 1:
                ClientRegistrationRequestEntity anotherReq = pending.get(0);
                if (anotherReq.getOrigin().equals(request.getOrigin())) {
                    throw new DataIntegrityException(MR_EXISTS, valueOf(anotherReq.getId()));
                }
                req = new ClientRegistrationRequestEntity(origin, request.getComments(), anotherReq);
                req.setProcessingStatus(SUBMITTED_FOR_APPROVAL);
                break;
            default:
                throw new DataIntegrityException(MR_EXISTS);
        }

        var persistedRequest = clientRegRequests.save(req);
        return requestMapper.toDto(persistedRequest);
    }

    @Override
    public ClientRegistrationRequest approve(ClientRegistrationRequest request) {
        Integer requestId = request.getId();
        final ClientRegistrationRequestEntity clientRegistrationRequest = clientRegRequests.findById(requestId)
                .orElseThrow(() -> new ValidationFailureException(MR_NOT_FOUND, valueOf(requestId)));

        if (!EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING).contains(clientRegistrationRequest.getProcessingStatus())) {
            throw new ValidationFailureException(MR_INVALID_STATE_FOR_APPROVAL,
                    valueOf(clientRegistrationRequest.getId()));
        }

        SecurityServerEntity server = servers.findBy(clientRegistrationRequest.getSecurityServerId())
                .getOrElseThrow(() -> new DataIntegrityException(MR_SERVER_NOT_FOUND));

        XRoadMemberEntity clientMember = members.findMember(clientRegistrationRequest.getClientId()).getOrElseThrow(() ->
                new DataIntegrityException(MR_MEMBER_NOT_FOUND,
                        clientRegistrationRequest.getClientId().toString()));

        SecurityServerClientEntity client;
        switch (clientRegistrationRequest.getClientId().getObjectType()) {
            case MEMBER:
                client = clientMember;
                break;
            case SUBSYSTEM:
                // create new subsystem if necessary
                client = clients
                        .findOneBy(clientRegistrationRequest.getClientId())
                        .getOrElse(() -> clients.save(new SubsystemEntity(clientMember, clientRegistrationRequest.getClientId())));
                break;
            default:
                throw new IllegalArgumentException("Invalid client type");
        }

        serverClientRepository.saveAndFlush(new ServerClientEntity(server, client));

        servers.saveAndFlush(server);
        clientRegistrationRequest.setProcessingStatus(ManagementRequestStatus.APPROVED);
        var persistedRequest = clientRegRequests.save(clientRegistrationRequest);

        return requestMapper.toDto(persistedRequest);
    }

    @Override
    public Class<ClientRegistrationRequest> requestType() {
        return ClientRegistrationRequest.class;
    }
}
