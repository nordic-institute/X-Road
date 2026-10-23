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
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.SystemDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class SystemDidRegistrationRequestHandler implements RequestHandler<SystemDidRegistrationRequest> {

    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final RequestRepository<SystemDidRegistrationRequestEntity> systemDidRegistrationRequests;
    private final AuthCertServerCheck authCertServerCheck;
    private final ParticipantValueStore participantValueStore;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(SystemDidRegistrationRequest request) {
        return false;
    }

    @Override
    public SystemDidRegistrationRequest add(SystemDidRegistrationRequest request) {
        var server = authCertServerCheck.requireServer(request.getSecurityServerId(), request.getCertificate());

        var result = participantValueStore.writeSystemPairOnce(server.id(), request.getSystemDid(), request.getDspBaseUrl());
        if (result.outcome() == ParticipantValueResult.Outcome.REJECTED) {
            throw SystemDidRegistrationOutcome.of(result.rejection(), server.address()).toException();
        }

        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        var saved = systemDidRegistrationRequests.save(new SystemDidRegistrationRequestEntity(request.getOrigin(), serverId));
        return requestMapper.toDto(saved);
    }

    @Override
    public SystemDidRegistrationRequest approve(SystemDidRegistrationRequest request) {
        return request;
    }

    @Override
    public Class<SystemDidRegistrationRequest> requestType() {
        return SystemDidRegistrationRequest.class;
    }
}
