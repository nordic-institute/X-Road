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
package org.niis.xroad.centralserver.restapi.dto.converter.db;

import ee.ria.xroad.common.identifier.XRoadObjectType;

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
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestInfoDto;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.model.ManagementRequestOriginDtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.model.ManagementRequestStatusConverter;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.centralserver.restapi.entity.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.ClientDeletionRequest;
import org.niis.xroad.centralserver.restapi.entity.ClientRegistrationRequest;
import org.niis.xroad.centralserver.restapi.entity.Request;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerId;
import org.niis.xroad.centralserver.restapi.repository.ManagementRequestViewRepository;
import org.niis.xroad.restapi.converter.Converters;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

import static ee.ria.xroad.common.util.Fn.self;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementRequestDtoConverter extends DtoConverter<Request, ManagementRequestDto> {

    private final ZoneOffset dtoZoneOffset;

    private final SecurityServerIdConverter securityServerIdMapper;
    private final ManagementRequestOriginDtoConverter.Service originDtoMapper;
    private final ClientIdDtoConverter clientIdDtoMapper;
    private final ManagementRequestStatusConverter.Service statusMapper;

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

        } else {
            throw new BadRequestException("Unknown request type");
        }

        return result.id(request.getId())
                .origin(originDtoMapper.toDto(request.getOrigin()))
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
                    originDtoMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId())
            ).self(self -> {
                self.setAuthCert(req.getAuthenticationCertificate());
                self.setAddress(req.getServerAddress());
            });

        } else if (request instanceof AuthenticationCertificateDeletionRequestDto) {
            AuthenticationCertificateDeletionRequestDto req = (AuthenticationCertificateDeletionRequestDto) request;
            return new AuthenticationCertificateDeletionRequest(
                    originDtoMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId())
            ).self(self -> {
                self.setAuthCert(req.getAuthenticationCertificate());
            });

        } else if (request instanceof ClientRegistrationRequestDto) {
            ClientRegistrationRequestDto req = (ClientRegistrationRequestDto) request;

            return new ClientRegistrationRequest(
                    originDtoMapper.fromDto(req.getOrigin()),

                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    clientIdDtoMapper.fromDto(req.getClientId()));

        } else if (request instanceof ClientDeletionRequestDto) {
            ClientDeletionRequestDto req = (ClientDeletionRequestDto) request;
            return new ClientDeletionRequest(
                    originDtoMapper.fromDto(req.getOrigin()),
                    securityServerIdMapper.convertId(req.getSecurityServerId()),
                    clientIdDtoMapper.fromDto(req.getClientId()));

        } else {
            throw new BadRequestException("Unknown request type");
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public SecurityServerId parseServerId(String id) {
        String separator = String.valueOf(Converters.ENCODED_ID_SEPARATOR);
        if (id == null) return null;
        final String[] parts = id.split(separator, 6);
        if (parts.length != 5 || !XRoadObjectType.SERVER.name().equals(parts[0])) {
            throw new IllegalArgumentException("Invalid security server id");
        }
        return SecurityServerId.create(parts[0], parts[1], parts[2], parts[3]);
    }

    public ManagementRequestViewRepository.Criteria convert(ManagementRequestsFilterDto filter) {
        return ManagementRequestViewRepository.Criteria.builder()
                .origin(convert(filter.getOrigin()))
                .types(convert(filter.getTypes()))
                .status(convert(filter.getStatus()))
                .serverId(convert(filter.getServerId()))
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

}
