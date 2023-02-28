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
package org.niis.xroad.cs.admin.core.service.managementrequest;

import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Arrays;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_AUTH_CERTIFICATE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SECURITY_SERVER_NOT_FOUND;

/**
 * Service for handling authentication certificate deletion requests
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthenticationCertificateDeletionRequestHandler implements
        RequestHandler<AuthenticationCertificateDeletionRequest> {
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final SecurityServerClientRepository<XRoadMemberEntity> members;
    private final RequestRepository<AuthenticationCertificateDeletionRequestEntity> requests;
    private final AuthCertRepository authCerts;
    private final SecurityServerRepository servers;
    private final RequestMapper requestMapper;

    public AuthenticationCertificateDeletionRequest add(AuthenticationCertificateDeletionRequest request) {
        // todo: check findOrCreate
        final SecurityServerIdEntity serverId = serverIds.findOrCreate(SecurityServerIdEntity.create(request.getSecurityServerId()));

        final var requestEntity = new AuthenticationCertificateDeletionRequestEntity(request.getOrigin(), serverId);

        if (!authCerts.existsByCert(request.getAuthCert())) {
            throw new DataIntegrityException(INVALID_AUTH_CERTIFICATE);
        }

        //check prerequisites (member exists)
        XRoadMemberEntity owner = members
                .findOneBy(request.getSecurityServerId().getOwner())
                .getOrElseThrow(() ->
                        new DataIntegrityException(MANAGEMENT_REQUEST_SERVER_OWNER_NOT_FOUND));

        SecurityServerEntity server = servers.findBy(request.getSecurityServerId())
                .getOrElseThrow(() ->
                        new DataIntegrityException(SECURITY_SERVER_NOT_FOUND));

        for (var item : server.getAuthCerts()) {
            if (Arrays.equals(item.getCert(), request.getAuthCert())) {
                authCerts.deleteById(item.getId());
                break;
            }
        }

        return Option.of(requestEntity)
                .map(RequestEntity::getOrigin)
                .flatMap(origin -> Option.of(serverId)
                        .map(serverIds::findOrCreate)
                        .map(dbSecurityServerId -> new AuthenticationCertificateDeletionRequestEntity(origin, dbSecurityServerId)))
                .map(requests::save)
                .map(requestMapper::toDto)
                .get();
    }

    @Override
    public boolean canAutoApprove(AuthenticationCertificateDeletionRequest request) {
        return false;
    }

    @Override
    public AuthenticationCertificateDeletionRequest approve(AuthenticationCertificateDeletionRequest request) {
        //nothing to do.
        return request;
    }

    @Override
    public Class<AuthenticationCertificateDeletionRequest> requestType() {
        return AuthenticationCertificateDeletionRequest.class;
    }
}
