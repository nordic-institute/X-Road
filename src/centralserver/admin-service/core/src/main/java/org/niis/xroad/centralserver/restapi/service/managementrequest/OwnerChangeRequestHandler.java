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

package org.niis.xroad.centralserver.restapi.service.managementrequest;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.OwnerChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.OwnerChangeRequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.EnumSet;
import java.util.List;

import static java.lang.String.valueOf;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.APPROVED;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.SUBMITTED_FOR_APPROVAL;
import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_CLIENT_ALREADY_OWNER;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_INVALID_STATE_FOR_APPROVAL;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_MEMBER_NOT_FOUND;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_NOT_FOUND;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_OWNER_MUST_BE_CLIENT;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_OWNER_MUST_BE_MEMBER;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_SERVER_CODE_EXISTS;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MANAGEMENT_REQUEST_SERVER_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
public class OwnerChangeRequestHandler implements RequestHandler<OwnerChangeRequest> {

    private final XRoadMemberRepository members;
    private final OwnerChangeRequestRepository ownerChangeRequestRepository;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final IdentifierRepository<MemberIdEntity> memberIds;
    private final SecurityServerRepository servers;

    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(OwnerChangeRequest request) {
        return SystemProperties.getCenterAutoApproveOwnerChangeRequests()
                && members.findMember(request.getClientId()).isDefined();
    }

    @Override
    public OwnerChangeRequest add(OwnerChangeRequest request) {
        assertNoOtherSubmittedRequests(request);
        validateRequest(request);

        final SecurityServerIdEntity securityServerIdEntity = serverIds.findOrCreate(
                SecurityServerIdEntity.create(request.getSecurityServerId()));
        final MemberIdEntity memberIdEntity = memberIds.findOrCreate(MemberIdEntity.create(request.getClientId()));

        OwnerChangeRequestEntity entity = new OwnerChangeRequestEntity(
                request.getOrigin(),
                securityServerIdEntity,
                memberIdEntity);
        entity.setProcessingStatus(SUBMITTED_FOR_APPROVAL);

        final OwnerChangeRequestEntity saved = ownerChangeRequestRepository.save(entity);
        return requestMapper.toDto(saved);
    }

    private void validateRequest(OwnerChangeRequest request) {
        // New owner cannot be a subsystem
        if (StringUtils.isNotBlank(request.getClientId().getSubsystemCode())) {
            throw new ValidationFailureException(MANAGEMENT_REQUEST_OWNER_MUST_BE_MEMBER);
        }

        final SecurityServerEntity securityServer = servers.findBy(request.getSecurityServerId())
                .getOrElseThrow(() -> new DataIntegrityException(MANAGEMENT_REQUEST_SERVER_NOT_FOUND,
                        request.getSecurityServerId().toString()));


        // New owner must be registered as a client on the security server
        final boolean clientRegistered = securityServer.getServerClients().stream()
                .anyMatch(serverClient -> ClientId.equals(serverClient.getSecurityServerClient().getIdentifier(), request.getClientId()));
        if (!clientRegistered) {
            throw new ValidationFailureException(MANAGEMENT_REQUEST_OWNER_MUST_BE_CLIENT);
        }

        // Client cannot be the current owner of the security server
        if (ClientId.equals(securityServer.getOwner().getIdentifier(), request.getClientId())) {
            throw new ValidationFailureException(MANAGEMENT_REQUEST_CLIENT_ALREADY_OWNER);
        }

        // Check that server with the new server id does not exist yet
        final long count = servers.count(SecurityServerId.create(request.getClientId(), securityServer.getServerCode()));
        if (count > 0) {
            throw new DataIntegrityException(MANAGEMENT_REQUEST_SERVER_CODE_EXISTS);
        }
    }

    private void assertNoOtherSubmittedRequests(OwnerChangeRequest request) {
        final SecurityServerIdEntity securityServerIdEntity = serverIds.findOrCreate(
                SecurityServerIdEntity.create(request.getSecurityServerId()));
        final List<OwnerChangeRequestEntity> pendingRequests = ownerChangeRequestRepository.findBy(securityServerIdEntity,
                EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING));

        if (!pendingRequests.isEmpty()) {
            throw new DataIntegrityException(ErrorMessage.MANAGEMENT_REQUEST_EXISTS);
        }
    }

    @Override
    public OwnerChangeRequest approve(OwnerChangeRequest request) {
        Integer requestId = request.getId();

        final OwnerChangeRequestEntity ownerChangeRequestEntity = ownerChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ValidationFailureException(MANAGEMENT_REQUEST_NOT_FOUND, valueOf(requestId)));

        if (!EnumSet.of(SUBMITTED_FOR_APPROVAL, WAITING).contains(ownerChangeRequestEntity.getProcessingStatus())) {
            throw new ValidationFailureException(MANAGEMENT_REQUEST_INVALID_STATE_FOR_APPROVAL,
                    valueOf(ownerChangeRequestEntity.getId()));
        }

        final SecurityServerEntity securityServer = servers.findBy(ownerChangeRequestEntity.getSecurityServerId())
                .getOrElseThrow(() -> new DataIntegrityException(MANAGEMENT_REQUEST_SERVER_NOT_FOUND,
                        ownerChangeRequestEntity.getSecurityServerId().toString()));

        final XRoadMemberEntity newOwner = members.findOneBy(ownerChangeRequestEntity.getClientId())
                .getOrElseThrow(() -> new DataIntegrityException(MANAGEMENT_REQUEST_MEMBER_NOT_FOUND,
                        ownerChangeRequestEntity.getClientId().toString()));

        newOwner.getOwnedServers().add(securityServer);
        securityServer.setOwner(newOwner);

        members.save(newOwner);
        servers.save(securityServer);


        // TODO FIXME: GLOBAL GROUPS
        // there are no services implemented at the moment

        ownerChangeRequestEntity.setProcessingStatus(APPROVED);
        final OwnerChangeRequestEntity saved = ownerChangeRequestRepository.save(ownerChangeRequestEntity);
        return requestMapper.toDto(saved);
    }

    @Override
    public Class<OwnerChangeRequest> requestType() {
        return OwnerChangeRequest.class;
    }
}
