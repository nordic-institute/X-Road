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

import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_AUTH_CERTIFICATE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_SERVER_NOT_FOUND;

/**
 * Checks that an authentication certificate is registered to a given Security Server. Runs in its own
 * transaction so that callers without one can use it.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthCertServerCheck {
    private final AuthCertRepository authCertRepository;
    private final SecurityServerRepository securityServerRepository;

    /**
     * Checks that the certificate is an authentication certificate of the named server.
     *
     * @param serverId    the identifier of the server
     * @param certificate the DER-encoded authentication certificate
     * @return the server the certificate belongs to
     * @throws NotFoundException when the certificate and the server are both unknown
     * @throws ConflictException when the certificate is unknown; with the fixable outcome when the named server
     *                           does not exist and the certificate belongs to the server that has the same
     *                           instance and server code under another owner; with the final outcome when the
     *                           certificate belongs to any other server
     */
    public CheckedServer requireServer(SecurityServerId serverId, byte[] certificate) {
        var named = securityServerRepository.findBy(serverId);
        var authCert = authCertRepository.findByCert(certificate);
        if (authCert.isEmpty()) {
            if (named.isEmpty()) {
                throw new NotFoundException(MR_SERVER_NOT_FOUND.build(serverId.toString()));
            }
            throw new ConflictException(MR_INVALID_AUTH_CERTIFICATE.build());
        }

        var certificateServer = authCert.get().getSecurityServer();
        if (named.isPresent() && named.get().getId() == certificateServer.getId()) {
            return new CheckedServer(certificateServer.getId(), certificateServer.getAddress());
        }
        if (named.isEmpty() && isSameServerUnderAnotherOwner(serverId, certificateServer)) {
            throw SystemDidRegistrationOutcome.ownerChanged().toException();
        }
        throw SystemDidRegistrationOutcome.certificateOfAnotherServer().toException();
    }

    private static boolean isSameServerUnderAnotherOwner(SecurityServerId serverId, SecurityServerEntity certificateServer) {
        return certificateServer.getServerId().getXRoadInstance().equals(serverId.getXRoadInstance())
                && certificateServer.getServerCode().equals(serverId.getServerCode());
    }

    /**
     * @param id      the id of the server row
     * @param address the registered address of the server
     */
    public record CheckedServer(int id, String address) {
    }
}
