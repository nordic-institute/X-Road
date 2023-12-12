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
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.AddressChangeRequest;
import org.niis.xroad.cs.admin.core.entity.AddressChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_SERVER_ADDRESS;

@Service
@Transactional
@RequiredArgsConstructor
public class AddressChangeRequestHandler implements RequestHandler<AddressChangeRequest> {

    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final RequestRepository<AddressChangeRequestEntity> addressChangeRequests;
    private final SecurityServerRepository securityServerRepository;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(AddressChangeRequest request) {
        return false;
    }

    @Override
    public AddressChangeRequest add(AddressChangeRequest request) {
        validate(request);

        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        final var requestEntity = new AddressChangeRequestEntity(request.getOrigin(), serverId,
                request.getServerAddress());

        securityServerRepository.findBy(serverId)
                .peek(server -> {
                    requestEntity.setComments(formatComment(server.getAddress(), request.getComments()));
                    server.setAddress(request.getServerAddress());
                });
        final AddressChangeRequestEntity savedRequest = addressChangeRequests.save(requestEntity);
        return requestMapper.toDto(savedRequest);
    }

    private String formatComment(String oldAddress, String comment) {
        String newComment = "Changing from '%s'.".formatted(oldAddress);
        if (StringUtils.isNotBlank(comment)) {
            newComment += " " + comment;
        }
        return newComment;
    }

    private void validate(AddressChangeRequest request) {
        if (StringUtils.isBlank(request.getServerAddress())) {
            throw new ValidationFailureException(MR_INVALID_SERVER_ADDRESS);
        }
    }

    @Override
    public AddressChangeRequest approve(AddressChangeRequest request) {
        // nothing to do
        return request;
    }

    @Override
    public Class<AddressChangeRequest> requestType() {
        return AddressChangeRequest.class;
    }
}
