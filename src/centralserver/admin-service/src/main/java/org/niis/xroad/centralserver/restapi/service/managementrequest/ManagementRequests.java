/**
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
package org.niis.xroad.centralserver.restapi.service.managementrequest;

import org.niis.xroad.centralserver.restapi.dto.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.centralserver.restapi.dto.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ClientDeletionRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestInfoDto;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.ClientDeletionRequest;
import org.niis.xroad.centralserver.restapi.entity.ClientRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.Request;

final class ManagementRequests {

    private ManagementRequests() {
        //Utility class
    }

    static ManagementRequestDto asDto(Request request) {
        if (request instanceof AuthenticationCertificateRegistrationRequest) {
            var req = (AuthenticationCertificateRegistrationRequest) request;
            return new AuthenticationCertificateRegistrationRequestDto(
                    req.getId(),
                    req.getOrigin(),
                    req.getSecurityServerId(),
                    req.getProcessingStatus(),
                    req.getAuthCert(),
                    req.getAddress());
        }

        if (request instanceof AuthenticationCertificateDeletionRequest) {
            var req = (AuthenticationCertificateDeletionRequest) request;
            return new AuthenticationCertificateDeletionRequestDto(
                    req.getId(),
                    req.getOrigin(),
                    req.getSecurityServerId(),
                    req.getProcessingStatus(),
                    req.getAuthCert());
        }

        if (request instanceof ClientRegistrationRequest) {
            var req = (ClientRegistrationRequest) request;
            return new ClientRegistrationRequestDto(
                    req.getId(),
                    req.getOrigin(),
                    req.getSecurityServerId(),
                    req.getProcessingStatus(),
                    req.getClientId());
        }

        if (request instanceof ClientDeletionRequest) {
            var req = (ClientDeletionRequest) request;
            return new ClientDeletionRequestDto(
                    req.getId(),
                    req.getOrigin(),
                    req.getSecurityServerId(),
                    req.getProcessingStatus(),
                    req.getClientId());
        }

        throw new IllegalArgumentException("Unknown request type");
    }

    static ManagementRequestInfoDto asInfoDto(Request req) {
        return new ManagementRequestInfoDto(
                req.getId(),
                req.getType(),
                req.getOrigin(),
                req.getSecurityServerId(),
                req.getProcessingStatus(),
                req.getCreatedAt());
    }
}
