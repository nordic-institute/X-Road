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

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.core.entity.AuthenticationCertificateDeletionRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Arrays;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_AUTH_CERTIFICATE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SECURITY_SERVER_NOT_FOUND;

/**
 * Service for handling authentication certificate deletion requests
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthenticationCertificateDeletionRequestHandler implements
        RequestHandler<AuthenticationCertificateDeletionRequest> {
    private final RequestRepository<AuthenticationCertificateDeletionRequestEntity> requests;
    private final AuthCertRepository authCerts;
    private final SecurityServerRepository servers;
    private final RequestMapper requestMapper;

    public AuthenticationCertificateDeletionRequest add(AuthenticationCertificateDeletionRequest request) {

        SecurityServerId serverId = request.getSecurityServerId();

        final var requestEntity = new AuthenticationCertificateDeletionRequestEntity(request.getOrigin(), serverId);

        if (!authCerts.existsByCert(request.getAuthCert())) {
            throw new DataIntegrityException(INVALID_AUTH_CERTIFICATE);
        }

        SecurityServerEntity server = servers.findBy(serverId)
                .getOrElseThrow(() ->
                        new DataIntegrityException(SECURITY_SERVER_NOT_FOUND));

        for (var item : server.getAuthCerts()) {
            if (Arrays.equals(item.getCert(), request.getAuthCert())) {
                authCerts.deleteById(item.getId());
                break;
            }
        }

        requests.save(requestEntity);

        return requestMapper.toDto(requestEntity);
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
