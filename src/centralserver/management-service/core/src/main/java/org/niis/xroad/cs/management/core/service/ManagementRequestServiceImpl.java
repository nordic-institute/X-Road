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
package org.niis.xroad.cs.management.core.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.request.ClientRequestType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.client.FeignManagementRequestsApi;
import org.niis.xroad.cs.management.core.api.ManagementRequestService;
import org.springframework.stereotype.Service;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementRequestServiceImpl implements ManagementRequestService {
    private final FeignManagementRequestsApi managementRequestsApi;

    @Override
    public int addManagementRequest(ClientRequestType request, ManagementRequestType requestType) {
        var managementRequest = new ClientRegistrationRequestDto();

        managementRequest.setType(mapRequestType(requestType));
        managementRequest.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
        //TODO fill
        //managementRequest.setClientId(request.getClient());

        var result = managementRequestsApi.addManagementRequest(managementRequest);

        if (!result.hasBody()) {
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, "Empty response");
        } else {
            return result.getBody().getId();
        }


    }

    private ManagementRequestTypeDto mapRequestType(ManagementRequestType requestType) {
        switch (requestType) {
            case AUTH_CERT_REGISTRATION_REQUEST:
                return ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST;
            case CLIENT_REGISTRATION_REQUEST:
                return ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST;
            case OWNER_CHANGE_REQUEST:
                return ManagementRequestTypeDto.OWNER_CHANGE_REQUEST;
            default:
                throw new CodedException(X_INVALID_REQUEST, "Unsupported request type %s", requestType);
        }
    }
}
