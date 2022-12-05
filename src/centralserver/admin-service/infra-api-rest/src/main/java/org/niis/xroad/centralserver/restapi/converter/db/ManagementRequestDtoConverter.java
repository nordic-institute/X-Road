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
package org.niis.xroad.centralserver.restapi.converter.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.centralserver.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestStatusDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestsFilterDto;
import org.niis.xroad.centralserver.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.centralserver.restapi.converter.model.ManagementRequestDtoTypeConverter;
import org.niis.xroad.centralserver.restapi.converter.model.ManagementRequestOriginDtoConverter;
import org.niis.xroad.centralserver.restapi.converter.model.ManagementRequestStatusConverter;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestType;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.OwnerChangeRequest;
import org.niis.xroad.cs.admin.api.domain.Request;
import org.niis.xroad.cs.admin.api.dto.ManagementRequestInfoDto;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.Fn.self;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementRequestDtoConverter extends DtoConverter<Request, ManagementRequestDto> {

    private final ZoneOffset dtoZoneOffset;

    private final SecurityServerIdConverter securityServerIdMapper;
    private final ManagementRequestOriginDtoConverter.Service originMapper;
    private final ClientIdDtoConverter clientIdDtoMapper;
    private final ManagementRequestStatusConverter.Service statusMapper;
    private final ManagementRequestDtoTypeConverter.Service requestTypeConverter;

    public ManagementRequestDto toDto(Request request) {
        ManagementRequestDto result;

        if (request instanceof AuthenticationCertificateRegistrationRequest) {
            AuthenticationCertificateRegistrationRequest req = (AuthenticationCertificateRegistrationRequest) request;
            result = self(new AuthenticationCertificateRegistrationRequestDto(), self -> {
                self.setServerAddress(req.getAddress());
                self.setAuthenticationCertificate(req.getAuthCert());
            });

        } else if (request instanceof AuthenticationCertificateDeletionRequest) {
            AuthenticationCertificateDeletionRequest req = (AuthenticationCertificateDeletionRequest) request;
            result = self(new AuthenticationCertificateDeletionRequestDto(), self -> {
                self.setAuthenticationCertificate(req.getAuthCert());
            });

        } else if (request instanceof ClientRegistrationRequest) {
            ClientRegistrationRequest req = (ClientRegistrationRequest) request;
            result = self(new ClientRegistrationRequestDto(), self -> {
                self.setClientId(clientIdDtoMapper.toDto(req.getClientId()));
            });

        } else if (request instanceof ClientDeletionRequest) {
            ClientDeletionRequest req = (ClientDeletionRequest) request;
            result = self(new ClientDeletionRequestDto(), self -> {
                self.setClientId(clientIdDtoMapper.toDto(req.getClientId()));
            });

        } else if (request instanceof OwnerChangeRequest) {
            OwnerChangeRequest req = (OwnerChangeRequest) request;
            result = self(new OwnerChangeRequestDto(), self -> self.setClientId(clientIdDtoMapper.toDto(req.getClientId())));

        } else {
            throw new BadRequestException("Unknown request type");
        }

        return result.id(request.getId())
                .origin(originMapper.toDto(request.getOrigin()))
                .securityServerId(securityServerIdMapper.convertId(request.getSecurityServerId()))
                .status(statusMapper.toDto(request.getProcessingStatus()))
                .createdAt(request.getCreatedAt().atOffset(dtoZoneOffset))
                .updatedAt(request.getUpdatedAt().atOffset(dtoZoneOffset));
    }

    public Request fromDto(ManagementRequestDto request) {
        if (request instanceof AuthenticationCertificateRegistrationRequestDto) {
            AuthenticationCertificateRegistrationRequestDto req =
                    (AuthenticationCertificateRegistrationRequestDto) request;
            return new AuthenticationCertificateRegistrationRequest(
                    originMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()))

                    .setAuthCert(req.getAuthenticationCertificate())
                    .setAddress(req.getServerAddress());


        } else if (request instanceof AuthenticationCertificateDeletionRequestDto) {
            AuthenticationCertificateDeletionRequestDto req = (AuthenticationCertificateDeletionRequestDto) request;
            return new AuthenticationCertificateDeletionRequest(
                    originMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()))
                    .setAuthCert(req.getAuthenticationCertificate());


        } else if (request instanceof ClientRegistrationRequestDto) {
            ClientRegistrationRequestDto req = (ClientRegistrationRequestDto) request;

            return new ClientRegistrationRequest(
                    originMapper.fromDto(req.getOrigin()),

                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    clientIdDtoMapper.fromDto(req.getClientId()));

        } else if (request instanceof ClientDeletionRequestDto) {
            ClientDeletionRequestDto req = (ClientDeletionRequestDto) request;
            return new ClientDeletionRequest(
                    originMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    clientIdDtoMapper.fromDto(req.getClientId()));

        } else if (request instanceof OwnerChangeRequestDto) {
            OwnerChangeRequestDto req = (OwnerChangeRequestDto) request;
            return new OwnerChangeRequest(
                    originMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    clientIdDtoMapper.fromDto(req.getClientId()));

        } else {
            throw new BadRequestException("Unknown request type");
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

    public ManagementRequestDto convert(ManagementRequestInfoDto dto) {
        var info = new ManagementRequestDto();
        info.setId(dto.getId());
        info.setType(ManagementRequestTypeDto.valueOf(dto.getType().name()));
        info.setOrigin(ManagementRequestOriginDto.valueOf(dto.getOrigin().name()));
        info.setStatus(dto.getStatus() == null ? null : ManagementRequestStatusDto.valueOf(dto.getStatus().name()));
        info.setSecurityServerOwner(dto.getServerOwnerName());
        info.setSecurityServerId(convert(dto.getServerId()));
        info.setCreatedAt(dto.getCreatedAt().atOffset(ZoneOffset.UTC));
        return info;
    }

    private List<ManagementRequestType> convert(List<ManagementRequestTypeDto> types) {
        return Optional.ofNullable(types)
                .map(managementRequestTypes -> managementRequestTypes.stream()
                        .map(requestTypeConverter::convertToA)
                        .collect(Collectors.toList()))
                .orElseGet(Collections::emptyList);
    }

    private Optional<ee.ria.xroad.common.identifier.SecurityServerId> convert(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(securityServerIdMapper.convertId(id));
    }

    private String convert(ee.ria.xroad.common.identifier.SecurityServerId id) {
        return securityServerIdMapper.convertId(id);
    }

}
