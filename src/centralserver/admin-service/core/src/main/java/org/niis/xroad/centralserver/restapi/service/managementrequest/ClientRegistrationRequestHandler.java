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
package org.niis.xroad.centralserver.restapi.service.managementrequest;

import ee.ria.xroad.common.SystemProperties;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.ClientRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ClientIdMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.ClientRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.EnumSet;
import java.util.List;

import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.WAITING;

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
    private final RequestMapper requestMapper;
    private final ClientIdMapper clientIdMapper;

    @Override
    public boolean canAutoApprove(ClientRegistrationRequest request) {
        return (SystemProperties.getCenterAutoApproveClientRegRequests()
                || request.getProcessingStatus().equals(SUBMITTED_FOR_APPROVAL))
                && request.getOrigin() == Origin.SECURITY_SERVER
                && servers.count(request.getSecurityServerId()) > 0
                && members.findMember(request.getClientId()).isDefined();
    }

    @Override
    public ClientRegistrationRequest add(ClientRegistrationRequest request) {
        var requestEntity = requestMapper.fromDto(request);

        SecurityServerIdEntity serverId = requestEntity.getSecurityServerId();
        ClientIdEntity clientId = requestEntity.getClientId();
        Origin origin = requestEntity.getOrigin();

        MemberIdEntity ownerId = serverId.getOwner();
        if (ownerId.equals(clientId)) {
            throw new ValidationFailureException(ErrorMessage.MANAGEMENT_REQUEST_CANNOT_REGISTER_OWNER);
        }

        if (Origin.CENTER.equals(origin)) {
            XRoadMemberEntity owner = members.findOneBy(ownerId).getOrElseThrow(
                    () -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND,
                            ownerId.toString()));

            servers.findByOwnerAndServerCode(owner, serverId.getServerCode()).getOrElseThrow(
                    () -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND,
                            serverId.toString()));

            members.findMember(clientId).getOrElseThrow(() ->
                    new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_MEMBER_NOT_FOUND,
                            request.getClientId().toString()));
        }

        servers.findBy(serverId, clientId).map(s -> {
            //fixme wrong error code
            throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_ALREADY_REGISTERED);
        });

        SecurityServerIdEntity storedServerId = serverIds.findOrCreate(serverId);
        ClientIdEntity storedClientId = clientIds.findOrCreate(clientId);

        List<ClientRegistrationRequestEntity> pending = clientRegRequests.findBy(storedServerId, storedClientId,
                EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING));

        ClientRegistrationRequestEntity req;
        switch (pending.size()) {
            case 0:
                req = new ClientRegistrationRequestEntity(origin, storedServerId, storedClientId);
                break;
            case 1:
                ClientRegistrationRequestEntity anotherReq = pending.get(0);
                if (anotherReq.getOrigin().equals(request.getOrigin())) {
                    throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS,
                            String.valueOf(anotherReq.getId()));
                }
                req = new ClientRegistrationRequestEntity(origin, anotherReq);
                req.setProcessingStatus(SUBMITTED_FOR_APPROVAL);
                break;
            default:
                throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS);

        }

        var persistedRequest = clientRegRequests.save(req);
        return requestMapper.toDto(persistedRequest);
    }

    @Override
    public ClientRegistrationRequest approve(ClientRegistrationRequest request) {
        if (!EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING).contains(request.getRequestProcessing().getStatus())) {
            throw new ValidationFailureException(ErrorMessage.MANAGEMENT_REQUEST_INVALID_STATE_FOR_APPROVAL,
                    String.valueOf(request.getId()));
        }

        SecurityServerEntity server = servers.findBy(request.getSecurityServerId())
                .getOrElseThrow(() -> new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND));

        XRoadMemberEntity clientMember = members.findMember(request.getClientId()).getOrElseThrow(() ->
                new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_MEMBER_NOT_FOUND,
                        request.getClientId().toString()));

        SecurityServerClientEntity client;
        switch (request.getClientId().getObjectType()) {
            case MEMBER:
                client = clientMember;
                break;
            case SUBSYSTEM:
                // create new subsystem if necessary
                client = clients
                        .findOneBy(request.getClientId())
                        .getOrElse(() -> clients.save(new SubsystemEntity(clientMember, clientIdMapper.fromDto(request.getClientId()))));
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
}
