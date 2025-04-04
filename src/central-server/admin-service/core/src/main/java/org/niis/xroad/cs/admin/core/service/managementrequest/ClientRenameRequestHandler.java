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
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.ClientRenameRequest;
import org.niis.xroad.cs.admin.core.entity.ClientRenameRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.SubsystemRepository;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_SUBSYSTEM_NAME;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_ONLY_SUBSYSTEM_RENAME_ALLOWED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_CLIENT_NOT_FOUND;

@Service
@Transactional
@RequiredArgsConstructor
class ClientRenameRequestHandler implements RequestHandler<ClientRenameRequest> {

    private final SecurityServerRepository servers;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final RequestRepository<ClientRenameRequestEntity> renameRequests;
    private final SubsystemRepository subsystemRepository;
    private final RequestMapper requestMapper;


    @Override
    public boolean canAutoApprove(ClientRenameRequest request) {
        return false;
    }

    @Override
    public ClientRenameRequest add(ClientRenameRequest request) {
        validate(request);
        var subsystemId = ClientId.ensure(request.getClientId());

        final var serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));

        final var subsystem = subsystemRepository.findByIdentifier(subsystemId)
                .orElseThrow(clientNotFound(request.getSecurityServerId().toString(), request.getClientId().toString()));

        servers.findBy(serverId, subsystem.getIdentifier())
                .orElseThrow(clientNotFound(request.getSecurityServerId().toString(), request.getClientId().toString()));

        final var requestEntity = new ClientRenameRequestEntity(
                request.getOrigin(),
                serverId,
                subsystem.getIdentifier(),
                request.getSubsystemName(),
                formatRenameComment(subsystem.getName(), request.getSubsystemName(), request.getComments())
        );

        final var persistedRequest = renameRequests.save(requestEntity);
        subsystem.setName(request.getSubsystemName());

        return requestMapper.toDto(persistedRequest);
    }

    protected static String formatRenameComment(String oldName, String newName, String comment) {
        if (StringUtils.isNotEmpty(newName) && StringUtils.isNotEmpty(oldName) && !StringUtils.equals(oldName, newName)) {
            var newComment = "Changing subsystem name from '%s' to '%s'.".formatted(oldName, newName);
            if (StringUtils.isNotBlank(comment)) {
                newComment += " " + comment;
            }
            return newComment;
        }
        return comment;
    }

    private void validate(ClientRenameRequest request) {
        if (!request.getClientId().isSubsystem()) {
            throw new ValidationFailureException(MR_ONLY_SUBSYSTEM_RENAME_ALLOWED);
        }

        if (StringUtils.isBlank(request.getSubsystemName())) {
            throw new ValidationFailureException(MR_INVALID_SUBSYSTEM_NAME);
        }
    }

    private Supplier<DataIntegrityException> clientNotFound(String securityServerId, String clientId) {
        return () -> new DataIntegrityException(MR_SERVER_CLIENT_NOT_FOUND, securityServerId, clientId);
    }

    @Override
    public ClientRenameRequest approve(ClientRenameRequest request) {
        return request;
    }

    @Override
    public Class<ClientRenameRequest> requestType() {
        return ClientRenameRequest.class;
    }
}
