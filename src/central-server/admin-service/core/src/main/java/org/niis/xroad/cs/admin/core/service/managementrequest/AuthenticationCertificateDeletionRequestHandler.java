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
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.AuthenticationCertificateRegistrationRequestRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.REVOKED;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.WAITING;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_AUTH_CERTIFICATE;

/**
 * Service for handling authentication certificate deletion requests
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthenticationCertificateDeletionRequestHandler implements
        RequestHandler<AuthenticationCertificateDeletionRequest> {
    private final RequestRepository<AuthenticationCertificateDeletionRequestEntity> autCertDeletionRequests;
    private final AuthenticationCertificateRegistrationRequestRepository autCertRegistrationRequests;
    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final AuthCertRepository authCertRepository;
    private final RequestMapper requestMapper;

    public AuthenticationCertificateDeletionRequest add(AuthenticationCertificateDeletionRequest request) {
        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));


        authCertRepository.findByCert(request.getAuthCert())
                .ifPresentOrElse(
                        cert -> deleteAuthCert(serverId, cert),
                        () -> tryToRevokeAuthCertRegistration(serverId, request.getAuthCert())
                );


        final var requestEntity = new AuthenticationCertificateDeletionRequestEntity(request.getOrigin(), serverId,
                request.getAuthCert(), request.getComments());
        var persistedRequest = autCertDeletionRequests.save(requestEntity);
        return requestMapper.toDto(persistedRequest);
    }

    private void tryToRevokeAuthCertRegistration(final SecurityServerIdEntity serverId, final byte[] certificate) {
        autCertRegistrationRequests.findByAuthCertAndStatus(certificate, Set.of(WAITING)).stream()
                .filter(req -> serverId.equals(req.getSecurityServerId()))
                .findFirst()
                .ifPresentOrElse(this::revokeAuthCertRegistration, this::mrInvalidAuthCertificate);
    }

    private void revokeAuthCertRegistration(AuthenticationCertificateRegistrationRequestEntity req) {
        req.getRequestProcessing().setStatus(REVOKED);
        autCertRegistrationRequests.save(req);
    }

    private void deleteAuthCert(final SecurityServerIdEntity serverId, final AuthCertEntity authCert) {
        if (!authCert.getSecurityServer().getServerId().equals(serverId)) {
            throw new SecurityServerNotFoundException(serverId);
        }
        authCertRepository.delete(authCert);
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

    private void mrInvalidAuthCertificate() {
        throw new DataIntegrityException(MR_INVALID_AUTH_CERTIFICATE);
    }
}
