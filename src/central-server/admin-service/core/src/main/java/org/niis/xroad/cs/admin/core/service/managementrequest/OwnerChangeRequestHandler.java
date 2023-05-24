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
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.OwnerChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.OwnerChangeRequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.APPROVED;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_CLIENT_ALREADY_OWNER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_STATE_FOR_APPROVAL;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_MEMBER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_OWNER_MUST_BE_CLIENT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_OWNER_MUST_BE_MEMBER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_CODE_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_NOT_FOUND;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;

@Service
@Transactional
@RequiredArgsConstructor
public class OwnerChangeRequestHandler implements RequestHandler<OwnerChangeRequest> {

    private final XRoadMemberRepository members;
    private final OwnerChangeRequestRepository ownerChangeRequestRepository;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<MemberIdEntity> memberIds;
    private final SecurityServerRepository servers;
    private final ServerClientRepository serverClients;

    private final GlobalGroupMemberService groupMemberService;

    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(OwnerChangeRequest request) {
        return SystemProperties.getCenterAutoApproveOwnerChangeRequests()
                && members.findMember(request.getClientId()).isDefined();
    }

    @Override
    public OwnerChangeRequest add(OwnerChangeRequest request) {
        SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        assertNoOtherSubmittedRequests(serverId);
        validateRequest(request);
        final MemberIdEntity memberIdEntity = memberIds.findOne(MemberIdEntity.create(request.getClientId()));

        OwnerChangeRequestEntity entity = new OwnerChangeRequestEntity(
                request.getOrigin(),
                serverId,
                memberIdEntity,
                request.getComments());

        final OwnerChangeRequestEntity saved = ownerChangeRequestRepository.save(entity);
        return requestMapper.toDto(saved);
    }

    private void validateRequest(OwnerChangeRequest request) {
        // New owner cannot be a subsystem
        if (StringUtils.isNotBlank(request.getClientId().getSubsystemCode())) {
            throw new ValidationFailureException(MR_OWNER_MUST_BE_MEMBER);
        }

        final SecurityServerEntity securityServer = servers.findBy(request.getSecurityServerId())
                .getOrElseThrow(() -> new DataIntegrityException(MR_SERVER_NOT_FOUND,
                        request.getSecurityServerId().toString()));

        // New owner must be registered as a client on the security server
        final boolean clientRegistered = securityServer.getServerClients().stream()
                .anyMatch(serverClient -> ClientId.equals(serverClient.getSecurityServerClient().getIdentifier(), request.getClientId()));
        if (!clientRegistered) {
            throw new ValidationFailureException(MR_OWNER_MUST_BE_CLIENT);
        }

        // Client cannot be the current owner of the security server
        if (ClientId.equals(securityServer.getOwner().getIdentifier(), request.getClientId())) {
            throw new ValidationFailureException(MR_CLIENT_ALREADY_OWNER);
        }

        // Check that server with the new server id does not exist yet
        final long count = servers.count(SecurityServerId.create(request.getClientId(), securityServer.getServerCode()));
        if (count > 0) {
            throw new DataIntegrityException(MR_SERVER_CODE_EXISTS);
        }
    }

    private void assertNoOtherSubmittedRequests(SecurityServerIdEntity serverId) {
        final List<OwnerChangeRequestEntity> pendingRequests =
                ownerChangeRequestRepository.findBy(serverId, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING));
        if (!pendingRequests.isEmpty()) {
            throw new DataIntegrityException(MR_EXISTS);
        }
    }

    @Override
    public OwnerChangeRequest approve(OwnerChangeRequest request) {
        Integer requestId = request.getId();

        final OwnerChangeRequestEntity ownerChangeRequestEntity = ownerChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ValidationFailureException(MR_NOT_FOUND, valueOf(requestId)));

        validateRequestStatus(ownerChangeRequestEntity, EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING));

        final SecurityServerEntity securityServer = servers.findBy(ownerChangeRequestEntity.getSecurityServerId())
                .getOrElseThrow(() -> new DataIntegrityException(MR_SERVER_NOT_FOUND,
                        ownerChangeRequestEntity.getSecurityServerId().toString()));

        final XRoadMemberEntity newOwner = members.findOneBy(ownerChangeRequestEntity.getClientId())
                .getOrElseThrow(() -> new DataIntegrityException(MR_MEMBER_NOT_FOUND,
                        ownerChangeRequestEntity.getClientId().toString()));

        final var currentOwner = securityServer.getOwner();

        securityServer.setOwner(newOwner);
        ensureServerIdCreated(securityServer.getServerId());
        ensureSecurityServerClient(securityServer, currentOwner);
        ensureNotASecurityServerClient(securityServer, newOwner);
        servers.saveAndFlush(securityServer);

        updateGlobalGroups(currentOwner.getIdentifier(), newOwner);

        ownerChangeRequestEntity.setProcessingStatus(APPROVED);
        final OwnerChangeRequestEntity saved = ownerChangeRequestRepository.save(ownerChangeRequestEntity);
        return requestMapper.toDto(saved);
    }

    private void validateRequestStatus(OwnerChangeRequestEntity requestEntity, EnumSet<ManagementRequestStatus> allowedStatuses) {
        if (!allowedStatuses.contains(requestEntity.getProcessingStatus())) {
            throw new ValidationFailureException(MR_INVALID_STATE_FOR_APPROVAL,
                    valueOf(requestEntity.getId()));
        }
    }

    private void ensureServerIdCreated(SecurityServerIdEntity serverId) {
        Optional<SecurityServerIdEntity> existingServerId = serverIds.findOpt(serverId);
        if (existingServerId.isEmpty()) {
            serverIds.saveAndFlush(serverId);
        }
    }

    private void ensureSecurityServerClient(SecurityServerEntity securityServer, XRoadMemberEntity member) {
        boolean isMemberAlreadyClient = securityServer.getServerClients().stream()
                .anyMatch(serverClient -> serverClient.getSecurityServerClient().getIdentifier().equals(member.getIdentifier()));
        if (!isMemberAlreadyClient) {
            serverClients.saveAndFlush(new ServerClientEntity(securityServer, member));
        }
    }

    private void ensureNotASecurityServerClient(SecurityServerEntity securityServer, XRoadMemberEntity member) {
        Set<ServerClientEntity> existingClients = securityServer.getServerClients().stream()
                .filter(serverClient -> serverClient.getSecurityServerClient().getIdentifier().equals(member.getIdentifier()))
                .collect(Collectors.toSet());
        existingClients.forEach(serverClients::delete);
    }

    private void updateGlobalGroups(ClientIdEntity currentOwnerIdentifier, XRoadMemberEntity newOwner) {
        var currentOwner = members.findOneBy(currentOwnerIdentifier)
                .getOrElseThrow(() -> new DataIntegrityException(MR_MEMBER_NOT_FOUND, currentOwnerIdentifier));
        if (currentOwner.getOwnedServers().isEmpty()) {
            groupMemberService.removeMemberFromGlobalGroup(DEFAULT_SECURITY_SERVER_OWNERS_GROUP,
                    MemberId.create(currentOwner.getIdentifier()));
        }
        groupMemberService.addMemberToGlobalGroup(MemberId.create(newOwner.getIdentifier()),
                DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
    }

    @Override
    public Class<OwnerChangeRequest> requestType() {
        return OwnerChangeRequest.class;
    }
}
