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
package org.niis.xroad.cs.admin.rest.api.converter.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.AddressChangeRequest;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDisableRequest;
import org.niis.xroad.cs.admin.api.domain.ClientEnableRequest;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.api.domain.Request;
import org.niis.xroad.cs.admin.api.domain.RequestWithProcessing;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.rest.api.converter.model.ManagementRequestDtoTypeConverter;
import org.niis.xroad.cs.admin.rest.api.converter.model.ManagementRequestOriginDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.model.ManagementRequestStatusConverter;
import org.niis.xroad.cs.openapi.model.AddressChangeRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDisableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestsFilterDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.DtoConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.Fn.self;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_UNKNOWN_TYPE;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementRequestDtoConverter extends DtoConverter<Request, ManagementRequestDto> {

    private final ZoneOffset dtoZoneOffset;

    private final SecurityServerIdConverter securityServerIdMapper;
    private final ManagementRequestOriginDtoConverter originMapper;
    private final ClientIdConverter clientIdConverter;
    private final ManagementRequestStatusConverter statusMapper;
    private final ManagementRequestDtoTypeConverter requestTypeConverter;

    public ManagementRequestDto toDto(Request request) {
        ManagementRequestDto result;

        if (request instanceof AuthenticationCertificateRegistrationRequest) {
            AuthenticationCertificateRegistrationRequest req = (AuthenticationCertificateRegistrationRequest) request;
            result = self(new AuthenticationCertificateRegistrationRequestDto(), self -> {
                self.setServerAddress(req.getAddress());
                self.setAuthenticationCertificate(req.getAuthCert());
            });

        } else if (request instanceof AuthenticationCertificateDeletionRequest req) {
            result = self(new AuthenticationCertificateDeletionRequestDto(), self -> self.setAuthenticationCertificate(req.getAuthCert()));

        } else if (request instanceof ClientRegistrationRequest req) {
            result = self(new ClientRegistrationRequestDto(), self -> self.setClientId(clientIdConverter.convertId(req.getClientId())));

        } else if (request instanceof ClientDeletionRequest req) {
            result = self(new ClientDeletionRequestDto(), self -> self.setClientId(clientIdConverter.convertId(req.getClientId())));

        } else if (request instanceof ClientDisableRequest req) {
            result = self(new ClientDisableRequestDto(), self -> self.setClientId(clientIdConverter.convertId(req.getClientId())));

        } else if (request instanceof ClientEnableRequest req) {
            result = self(new ClientEnableRequestDto(), self -> self.setClientId(clientIdConverter.convertId(req.getClientId())));

        } else if (request instanceof OwnerChangeRequest req) {
            result = self(new OwnerChangeRequestDto(), self -> self.setClientId(clientIdConverter.convertId(req.getClientId())));

        } else if (request instanceof AddressChangeRequest req) {
            result = self(new AddressChangeRequestDto(), self -> self.setServerAddress(req.getServerAddress()));

        } else {
            throw new ValidationFailureException(MR_UNKNOWN_TYPE, request);
        }

        if (request instanceof RequestWithProcessing req) {
            result.status(statusMapper.convert(req.getProcessingStatus()));
        }

        return result.id(request.getId())
                .type(requestTypeConverter.convert(request.getManagementRequestType()))
                .origin(originMapper.convert(request.getOrigin()))
                .securityServerId(securityServerIdMapper.convertId(request.getSecurityServerId()))
                .createdAt(request.getCreatedAt().atOffset(dtoZoneOffset))
                .updatedAt(request.getUpdatedAt().atOffset(dtoZoneOffset));
    }

    public Request fromDto(ManagementRequestDto request) {
        if (request instanceof AuthenticationCertificateRegistrationRequestDto req) {
            return new AuthenticationCertificateRegistrationRequest(
                    originMapper.convert(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()))

                    .setAuthCert(req.getAuthenticationCertificate())
                    .setAddress(req.getServerAddress());


        } else if (request instanceof AuthenticationCertificateDeletionRequestDto req) {
            return new AuthenticationCertificateDeletionRequest(
                    originMapper.convert(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()))
                    .setAuthCert(req.getAuthenticationCertificate());

        } else if (request instanceof ClientRegistrationRequestDto req) {
            return new ClientRegistrationRequest(
                    originMapper.convert(req.getOrigin()),

                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    fromEncodedId(req.getClientId()));

        } else if (request instanceof ClientDeletionRequestDto req) {
            return new ClientDeletionRequest(
                    originMapper.convert(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    fromEncodedId(req.getClientId()));
        } else if (request instanceof ClientDisableRequestDto req) {
            return new ClientDisableRequest(
                        originMapper.convert(req.getOrigin()),
                        securityServerIdMapper.convertId(req.getSecurityServerId()),
                        fromEncodedId(req.getClientId()));
        } else if (request instanceof ClientEnableRequestDto req) {
            return new ClientEnableRequest(
                        originMapper.convert(req.getOrigin()),
                        securityServerIdMapper.convertId(req.getSecurityServerId()),
                        fromEncodedId(req.getClientId()));
        } else if (request instanceof OwnerChangeRequestDto req) {
            return new OwnerChangeRequest(
                    originMapper.convert(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    fromEncodedId(req.getClientId()));

        } else if (request instanceof AddressChangeRequestDto req) {
            return new AddressChangeRequest(
                    originMapper.convert(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    req.getServerAddress());

        } else {
            throw new ValidationFailureException(MR_UNKNOWN_TYPE, request);
        }
    }

    public ClientId fromEncodedId(String encodedId) {
        var clientId = clientIdConverter.convertId(encodedId);
        if (clientId.getSubsystemCode() != null) {
            return SubsystemId.create(
                    clientId.getXRoadInstance(),
                    clientId.getMemberClass(),
                    clientId.getMemberCode(),
                    clientId.getSubsystemCode());
        } else {
            return MemberId.create(
                    clientId.getXRoadInstance(),
                    clientId.getMemberClass(),
                    clientId.getMemberCode());
        }
    }

    public ManagementRequestService.Criteria convert(ManagementRequestsFilterDto filter) {
        return ManagementRequestService.Criteria.builder()
                .query(filter.getQuery())
                .origin(originMapper.convert(filter.getOrigin()))
                .types(convert(filter.getTypes()))
                .status(statusMapper.convert(filter.getStatus()))
                .serverId(convert(filter.getServerId()).orElse(null))
                .build();
    }

    private List<ManagementRequestType> convert(List<ManagementRequestTypeDto> types) {
        return Optional.ofNullable(types)
                .map(managementRequestTypes -> managementRequestTypes.stream()
                        .map(requestTypeConverter::convert)
                        .collect(toList()))
                .orElseGet(Collections::emptyList);
    }

    private Optional<ee.ria.xroad.common.identifier.SecurityServerId> convert(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(securityServerIdMapper.convertId(id));
    }

}
