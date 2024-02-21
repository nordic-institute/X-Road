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
package org.niis.xroad.cs.management.core.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.request.AddressChangeRequestType;
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.ClientRequestType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.client.FeignManagementRequestsApi;
import org.niis.xroad.cs.management.core.api.ManagementRequestService;
import org.niis.xroad.cs.openapi.model.AddressChangeRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDisableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementRequestServiceImpl implements ManagementRequestService {
    private final FeignManagementRequestsApi managementRequestsApi;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final ClientIdConverter clientIdConverter;

    @Override
    public Integer addManagementRequest(ClientRequestType request, ManagementRequestType requestType) {
        var managementRequest = createRequestDto(request, requestType);

        return addManagementRequestInternal(managementRequest);
    }

    @Override
    public Integer addManagementRequest(AuthCertDeletionRequestType request) {
        var managementRequest = new AuthenticationCertificateDeletionRequestDto()
                .authenticationCertificate(request.getAuthCert())
                .type(ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST)
                .origin(ManagementRequestOriginDto.SECURITY_SERVER)
                .securityServerId(securityServerIdConverter.convertId(request.getServer()));

        return addManagementRequestInternal(managementRequest);
    }

    @Override
    public Integer addManagementRequest(AddressChangeRequestType request) {
        var managementRequest = new AddressChangeRequestDto()
                .serverAddress(request.getAddress())
                .type(ManagementRequestTypeDto.ADDRESS_CHANGE_REQUEST)
                .origin(ManagementRequestOriginDto.SECURITY_SERVER)
                .securityServerId(securityServerIdConverter.convertId(request.getServer()));
        return addManagementRequestInternal(managementRequest);
    }

    private Integer addManagementRequestInternal(ManagementRequestDto managementRequest) {
        var result = managementRequestsApi.addManagementRequest(managementRequest);
        if (!result.hasBody()) {
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Empty response");
        } else {
            return Optional.ofNullable(result.getBody())
                    .map(ManagementRequestDto::getId)
                    .orElse(null);
        }
    }

    private ManagementRequestDto createRequestDto(ClientRequestType request, ManagementRequestType requestType) {
        ManagementRequestDto managementRequest = switch (requestType) {
            case CLIENT_REGISTRATION_REQUEST -> new ClientRegistrationRequestDto()
                    .clientId(clientIdConverter.convertId(request.getClient()))
                    .type(ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST);
            case OWNER_CHANGE_REQUEST -> new OwnerChangeRequestDto()
                    .clientId(clientIdConverter.convertId(request.getClient()))
                    .type(ManagementRequestTypeDto.OWNER_CHANGE_REQUEST);
            case CLIENT_DELETION_REQUEST -> new ClientDeletionRequestDto()
                    .clientId(clientIdConverter.convertId(request.getClient()))
                    .type(ManagementRequestTypeDto.CLIENT_DELETION_REQUEST);
            case CLIENT_DISABLE_REQUEST -> new ClientDisableRequestDto()
                    .clientId(clientIdConverter.convertId(request.getClient()))
                    .type(ManagementRequestTypeDto.CLIENT_DISABLE_REQUEST);
            case CLIENT_ENABLE_REQUEST -> new ClientEnableRequestDto()
                    .clientId(clientIdConverter.convertId(request.getClient()))
                    .type(ManagementRequestTypeDto.CLIENT_ENABLE_REQUEST);
            default -> throw new CodedException(X_INVALID_REQUEST, "Unsupported request type %s", requestType);
        };

        managementRequest.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
        managementRequest.setSecurityServerId(securityServerIdConverter.convertId(request.getServer()));
        return managementRequest;
    }
}
