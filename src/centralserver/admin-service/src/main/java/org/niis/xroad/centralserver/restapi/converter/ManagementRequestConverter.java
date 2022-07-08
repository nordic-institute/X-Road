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
package org.niis.xroad.centralserver.restapi.converter;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.openapi.model.ClientDeletionRequest;
import org.niis.xroad.centralserver.openapi.model.ClientId;
import org.niis.xroad.centralserver.openapi.model.ClientRegistrationRequest;
import org.niis.xroad.centralserver.openapi.model.ManagementRequest;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOrigin;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestStatus;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestType;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestsFilter;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.dto.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ClientDeletionRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ClientRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestInfoDto;
import org.niis.xroad.centralserver.restapi.repository.ManagementRequestViewRepository;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ManagementRequestConverter {
    private final SecurityServerIdConverter securityServerIdConverter;

    public ManagementRequestDto convert(ManagementRequest request) {
        if (request instanceof AuthenticationCertificateRegistrationRequest) {
            var r = (AuthenticationCertificateRegistrationRequest) request;
            return new AuthenticationCertificateRegistrationRequestDto(
                    Origin.valueOf(r.getOrigin().name()),
                    convert(r.getSecurityServerId()).orElse(null),
                    r.getAuthenticationCertificate(),
                    r.getServerAddress());
        }

        if (request instanceof ClientRegistrationRequest) {
            var r = (ClientRegistrationRequest) request;
            return new ClientRegistrationRequestDto(
                    Origin.valueOf(r.getOrigin().name()),
                    convert(r.getSecurityServerId()).orElse(null),
                    convert(r.getClientId()));

        }

        if (request instanceof ClientDeletionRequest) {
            var r = (ClientDeletionRequest) request;
            return new ClientDeletionRequestDto(
                    Origin.valueOf(r.getOrigin().name()),
                    convert(r.getSecurityServerId()).orElse(null),
                    convert(r.getClientId()));

        }

        throw new BadRequestException("Unknown request type");
    }

    public ManagementRequestViewRepository.Criteria convert(ManagementRequestsFilter filter) {
        return ManagementRequestViewRepository.Criteria.builder()
                .origin(convert(filter.getOrigin()))
                .types(convert(filter.getTypes()))
                .status(convert(filter.getStatus()))
                .serverId(convert(filter.getServerId()).orElse(null))
                .build();
    }

    private List<org.niis.xroad.centralserver.restapi.domain.ManagementRequestType> convert(
            final List<ManagementRequestType> types) {
        return Optional.ofNullable(types)
                .map(managementRequestTypes -> managementRequestTypes.stream()
                        .map(this::convert)
                        .collect(Collectors.toList()))
                .orElseGet(Collections::emptyList);
    }

    private ee.ria.xroad.common.identifier.ClientId convert(ClientId id) {
        return ee.ria.xroad.common.identifier.ClientId.create(
                id.getInstanceId(),
                id.getMemberClass(),
                id.getMemberCode(),
                id.getSubsystemCode()
        );
    }

    public ManagementRequest convert(ManagementRequestInfoDto dto) {
        var info = new ManagementRequest();
        info.setId(dto.getId());
        info.setType(ManagementRequestType.valueOf(dto.getType().name()));
        info.setOrigin(ManagementRequestOrigin.valueOf(dto.getOrigin().name()));
        info.setStatus(dto.getStatus() == null ? null : ManagementRequestStatus.valueOf(dto.getStatus().name()));
        info.setSecurityServerOwner(dto.getServerOwnerName());
        info.setSecurityServerId(convert(dto.getServerId()));
        info.setCreatedAt(dto.getCreatedAt().atOffset(ZoneOffset.UTC));
        return info;
    }

    public org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus convert(ManagementRequestStatus status) {
        return status == null ? null
                : org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus.valueOf(status.name());
    }

    public ManagementRequest convert(ManagementRequestDto dto) {
        ManagementRequest response;

        if (dto instanceof AuthenticationCertificateRegistrationRequestDto) {
            response = new AuthenticationCertificateRegistrationRequest()
                    .authenticationCertificate(((AuthenticationCertificateRegistrationRequestDto) dto).getAuthCert())
                    .serverAddress(((AuthenticationCertificateRegistrationRequestDto) dto).getAddress());
        } else if (dto instanceof ClientRegistrationRequestDto) {
            response = new ClientRegistrationRequest()
                    .clientId(convert(((ClientRegistrationRequestDto) dto).getClientId()));
        } else {
            throw new BadRequestException("Unknown management request type");
        }

        return response
                .id(dto.getId())
                .type(ManagementRequestType.valueOf(dto.getType().name()))
                .origin(ManagementRequestOrigin.valueOf(dto.getOrigin().name()))
                .status(ManagementRequestStatus.valueOf(dto.getStatus().name()))
                .securityServerId(convert(dto.getServerId()));
    }

    private ClientId convert(ee.ria.xroad.common.identifier.ClientId id) {
        var clientId = new ClientId();
        clientId.subsystemCode(id.getSubsystemCode())
                .memberCode(id.getMemberCode())
                .memberClass(id.getMemberClass())
                .instanceId(id.getXRoadInstance())
                .type(XRoadId.TypeEnum.valueOf(id.getObjectType().name()));
        return clientId;
    }

    private String convert(ee.ria.xroad.common.identifier.SecurityServerId id) {
        return securityServerIdConverter.convertId(id);
    }

    private Optional<ee.ria.xroad.common.identifier.SecurityServerId> convert(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(securityServerIdConverter.convertId(id));
    }

    public List<ManagementRequest> convert(Collection<ManagementRequestInfoDto> content) {
        var result = new ArrayList<ManagementRequest>(content.size());
        for (var dto : content) {
            result.add(convert(dto));
        }
        return result;
    }

    public Origin convert(ManagementRequestOrigin origin) {
        return origin == null ? null : Origin.valueOf(origin.name());
    }

    public org.niis.xroad.centralserver.restapi.domain.ManagementRequestType convert(ManagementRequestType type) {
        return type == null ? null
                : org.niis.xroad.centralserver.restapi.domain.ManagementRequestType.valueOf(type.name());
    }
}
