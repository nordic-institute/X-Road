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

import ee.ria.xroad.common.request.AddressChangeRequestType;
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.ClientRegRequestType;
import ee.ria.xroad.common.request.ClientRenameRequestType;
import ee.ria.xroad.common.request.ClientRequestType;
import ee.ria.xroad.common.request.MaintenanceModeDisableRequestType;
import ee.ria.xroad.common.request.MaintenanceModeEnableRequestType;
import ee.ria.xroad.common.request.MemberDidRegRequestType;
import ee.ria.xroad.common.request.MemberDidRegResultType;

import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ResponsePayload;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestUtil;
import org.niis.xroad.cs.admin.client.FeignManagementRequestsApi;
import org.niis.xroad.cs.management.core.api.ManagementRequestService;
import org.niis.xroad.cs.openapi.model.AddressChangeRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDisableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRenameRequestDto;
import org.niis.xroad.cs.openapi.model.MaintenanceModeDisableRequestDto;
import org.niis.xroad.cs.openapi.model.MaintenanceModeEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationEntryDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationResultDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REQUEST;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementRequestServiceImpl implements ManagementRequestService {
    private final FeignManagementRequestsApi managementRequestsApi;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final ClientIdConverter clientIdConverter;

    @Override
    public ResponsePayload addManagementRequestWithPayload(Object request, ManagementRequestType requestType) {
        var dto = switch (request) {
            case AuthCertDeletionRequestType req -> new AuthenticationCertificateDeletionRequestDto()
                    .authenticationCertificate(req.getAuthCert())
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case AddressChangeRequestType req -> new AddressChangeRequestDto()
                    .serverAddress(req.getAddress())
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case ClientRenameRequestType req -> new ClientRenameRequestDto()
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()))
                    .clientId(clientIdConverter.convertId(req.getClient()))
                    .subsystemName(req.getSubsystemName());
            case ClientRegRequestType req -> new ClientRegistrationRequestDto()
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()))
                    .clientId(clientIdConverter.convertId(req.getClient()))
                    .subsystemName(req.getSubsystemName());
            case MemberDidRegRequestType req -> new MemberDidRegistrationRequestDto()
                    .entries(req.getEntry().stream()
                            .map(entry -> new MemberDidRegistrationEntryDto()
                                    .memberId(clientIdConverter.convertId(entry.getMember()))
                                    .did(entry.getDid()))
                            .toList())
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case MaintenanceModeEnableRequestType req -> new MaintenanceModeEnableRequestDto()
                    .message(req.getMessage())
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case MaintenanceModeDisableRequestType req -> new MaintenanceModeDisableRequestDto()
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case ClientRequestType req when ManagementRequestType.OWNER_CHANGE_REQUEST == requestType -> new OwnerChangeRequestDto()
                    .clientId(clientIdConverter.convertId(req.getClient()))
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case ClientRequestType req when ManagementRequestType.CLIENT_DELETION_REQUEST == requestType -> new ClientDeletionRequestDto()
                    .clientId(clientIdConverter.convertId(req.getClient()))
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case ClientRequestType req when ManagementRequestType.CLIENT_DISABLE_REQUEST == requestType -> new ClientDisableRequestDto()
                    .clientId(clientIdConverter.convertId(req.getClient()))
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            case ClientRequestType req when ManagementRequestType.CLIENT_ENABLE_REQUEST == requestType -> new ClientEnableRequestDto()
                    .clientId(clientIdConverter.convertId(req.getClient()))
                    .securityServerId(securityServerIdConverter.convertId(req.getServer()));
            default -> throw XrdRuntimeException.systemException(INVALID_REQUEST, "Unsupported request type %s", requestType);
        };

        dto.setOrigin(ManagementRequestOriginDto.SECURITY_SERVER);
        dto.setType(switch (requestType) {
            case AUTH_CERT_REGISTRATION_REQUEST, SYSTEM_DID_REGISTRATION_REQUEST ->
                    throw XrdRuntimeException.systemException(INVALID_REQUEST, "Unsupported request type %s", requestType);
            case CLIENT_REGISTRATION_REQUEST -> ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST;
            case OWNER_CHANGE_REQUEST -> ManagementRequestTypeDto.OWNER_CHANGE_REQUEST;
            case CLIENT_DELETION_REQUEST -> ManagementRequestTypeDto.CLIENT_DELETION_REQUEST;
            case AUTH_CERT_DELETION_REQUEST -> ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST;
            case ADDRESS_CHANGE_REQUEST -> ManagementRequestTypeDto.ADDRESS_CHANGE_REQUEST;
            case CLIENT_DISABLE_REQUEST -> ManagementRequestTypeDto.CLIENT_DISABLE_REQUEST;
            case CLIENT_ENABLE_REQUEST -> ManagementRequestTypeDto.CLIENT_ENABLE_REQUEST;
            case CLIENT_RENAME_REQUEST -> ManagementRequestTypeDto.CLIENT_RENAME_REQUEST;
            case MEMBER_DID_REGISTRATION_REQUEST -> ManagementRequestTypeDto.MEMBER_DID_REGISTRATION_REQUEST;
            case MAINTENANCE_MODE_ENABLE_REQUEST -> ManagementRequestTypeDto.MAINTENANCE_MODE_ENABLE_REQUEST;
            case MAINTENANCE_MODE_DISABLE_REQUEST -> ManagementRequestTypeDto.MAINTENANCE_MODE_DISABLE_REQUEST;
        });

        return addManagementRequestInternal(dto);
    }

    private ResponsePayload addManagementRequestInternal(ManagementRequestDto managementRequest) {
        var result = managementRequestsApi.addManagementRequest(managementRequest);
        if (!result.hasBody()) {
            throw XrdRuntimeException.systemInternalError("Empty response");
        }
        var body = result.getBody();
        var requestId = Optional.ofNullable(body).map(ManagementRequestDto::getId).orElse(null);
        if (requestId == null && !answersWithoutRequestId(body)) {
            throw XrdRuntimeException.systemInternalError("Response without request id");
        }
        return new ResponsePayload(requestId, resultElements(body));
    }

    private static boolean answersWithoutRequestId(ManagementRequestDto body) {
        return body instanceof MemberDidRegistrationRequestDto memberDidReg
                && memberDidReg.getResults() != null && !memberDidReg.getResults().isEmpty();
    }

    private List<JAXBElement<?>> resultElements(ManagementRequestDto body) {
        if (body instanceof MemberDidRegistrationRequestDto memberDidReg) {
            return memberDidReg.getResults().stream()
                    .<JAXBElement<?>>map(this::toResultElement)
                    .toList();
        }
        return List.of();
    }

    private JAXBElement<MemberDidRegResultType> toResultElement(MemberDidRegistrationResultDto result) {
        var element = new MemberDidRegResultType();
        element.setMember(clientIdConverter.convertId(result.getMemberId()));
        element.setStatus(result.getStatus().name());
        element.setReason(result.getReason());
        return ManagementRequestUtil.memberDidRegResult(element);
    }

}
