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
import ee.ria.xroad.common.identifier.XRoadId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
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
import java.util.Optional;

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
    private final MemberHelper memberHelper;

    @Override
    public boolean canAutoApprove(ClientRegistrationRequest request) {
        return (SystemProperties.getCenterAutoApproveClientRegRequests()
                || request.getProcessingStatus().equals(SUBMITTED_FOR_APPROVAL))
                && request.getOrigin() == SECURITY_SERVER
                && servers.count(request.getSecurityServerId()) > 0
                && members.findMember(request.getClientId()).isPresent();
    }

    @Override
    public ClientRegistrationRequest add(ClientRegistrationRequest request) {
        final SecurityServerIdEntity serverId = serverIds.findOrCreate(SecurityServerIdEntity.create(request.getSecurityServerId()));
        ClientIdEntity clientId = ClientIdEntity.ensure(request.getClientId());
        final Origin origin = request.getOrigin();

        MemberIdEntity ownerId = serverId.getOwner();
        if (ownerId.equals(clientId)) {
            throw new BadRequestException(MR_CANNOT_REGISTER_OWNER.build());
        }

        if (Origin.CENTER.equals(origin)) {
            XRoadMemberEntity owner = members.findOneBy(ownerId)
                    .orElseThrow(() -> new ConflictException(MR_SERVER_NOT_FOUND.build(ownerId.toString())));

            servers.findByOwnerIdAndServerCode(owner.getId(), serverId.getServerCode())
                    .orElseThrow(() -> new ConflictException(MR_SERVER_NOT_FOUND.build(serverId.toString())));

            members.findMember(clientId)
                    .orElseThrow(() -> new ConflictException(MR_MEMBER_NOT_FOUND.build(request.getClientId().toString())));
        }

        clientId = clientIds.findOrCreate(clientId);
        servers.findBy(serverId, clientId)
                .map(s -> {
                    throw new ConflictException(MR_CLIENT_ALREADY_REGISTERED.build());
                });

        List<ClientRegistrationRequestEntity> pending = clientRegRequests.findBy(
                serverId, clientId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING)
        );

        var oldName = Optional.of(clientId)
                .filter(XRoadId::isSubsystem)
                .flatMap(clients::findOneBy)
                .map(SecurityServerClientEntity::getName)
                .orElse(null);

        var comments = clientId.isSubsystem()
                ? ClientRenameRequestHandler.formatRenameComment(oldName, request.getSubsystemName(), request.getComments())
                : request.getComments();

        ClientRegistrationRequestEntity req;
        switch (pending.size()) {
            case 0:
                req = new ClientRegistrationRequestEntity(origin, serverId, clientId, request.getSubsystemName(), comments);
                break;
            case 1:
                ClientRegistrationRequestEntity anotherReq = pending.getFirst();
                if (anotherReq.getOrigin().equals(request.getOrigin())) {
                    throw new ConflictException(MR_EXISTS.build(anotherReq.getId()));
                }
                req = new ClientRegistrationRequestEntity(origin, comments, anotherReq);
                req.setProcessingStatus(SUBMITTED_FOR_APPROVAL);
                break;
            default:
                throw new ConflictException(MR_EXISTS.build());
        }

        var persistedRequest = clientRegRequests.save(req);
        return requestMapper.toDto(persistedRequest);
    }

    @Override
    public ClientRegistrationRequest approve(ClientRegistrationRequest request) {
        Integer requestId = request.getId();
        final ClientRegistrationRequestEntity clientRegistrationRequest = clientRegRequests.findById(requestId)
                .orElseThrow(() -> new BadRequestException(MR_NOT_FOUND.build(requestId)));

        if (!EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING).contains(clientRegistrationRequest.getProcessingStatus())) {
            throw new BadRequestException(MR_INVALID_STATE_FOR_APPROVAL.build(clientRegistrationRequest.getId()));
        }

        SecurityServerEntity server = servers.findBy(clientRegistrationRequest.getSecurityServerId())
                .orElseThrow(() -> new BadRequestException(MR_SERVER_NOT_FOUND.build()));

        XRoadMemberEntity clientMember = memberHelper.findOrCreate(clientRegistrationRequest.getClientId());

        SecurityServerClientEntity client = switch (clientRegistrationRequest.getClientId().getObjectType()) {
            case MEMBER -> clientMember;
            case SUBSYSTEM -> clients
                    .findOneBy(clientRegistrationRequest.getClientId())
                    .map(entity -> updateSubsystemNameIfNeeded(entity, clientRegistrationRequest.getSubsystemName()))
                    .orElseGet(() -> createSubsystem(clientMember, clientRegistrationRequest));
            default -> throw new IllegalArgumentException("Invalid client type");
        };

        serverClientRepository.saveAndFlush(new ServerClientEntity(server, client));

        servers.saveAndFlush(server);
        clientRegistrationRequest.setProcessingStatus(ManagementRequestStatus.APPROVED);
        var persistedRequest = clientRegRequests.save(clientRegistrationRequest);

        return requestMapper.toDto(persistedRequest);
    }

    private SecurityServerClientEntity updateSubsystemNameIfNeeded(SecurityServerClientEntity subsystem, String subsystemName) {
        if (StringUtils.isEmpty(subsystemName) || Strings.CS.equals(subsystemName, subsystem.getName())) {
            return subsystem;
        } else {
            subsystem.setName(subsystemName);
            return clients.save(subsystem);
        }
    }

    @Override
    public Class<ClientRegistrationRequest> requestType() {
        return ClientRegistrationRequest.class;
    }

    private SecurityServerClientEntity createSubsystem(XRoadMemberEntity clientMember, ClientRegistrationRequestEntity request) {
        return clients.save(new SubsystemEntity(clientMember, request.getClientId(), request.getSubsystemName()));
    }
}
