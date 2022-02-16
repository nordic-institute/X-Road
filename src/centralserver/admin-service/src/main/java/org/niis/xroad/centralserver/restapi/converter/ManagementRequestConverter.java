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
package org.niis.xroad.centralserver.restapi.converter;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.niis.xroad.centralserver.openapi.model.AuthenticationCertificateRegistrationRequest;
import org.niis.xroad.centralserver.openapi.model.ManagementRequest;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestInfo;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestOrigin;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestStatus;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestType;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.dto.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestDto;
import org.niis.xroad.centralserver.restapi.dto.ManagementRequestInfoDto;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ManagementRequestConverter {

    public ManagementRequestDto convert(ManagementRequest request) {
        if (request instanceof AuthenticationCertificateRegistrationRequest) {
            var r = (AuthenticationCertificateRegistrationRequest) request;
            return new AuthenticationCertificateRegistrationRequestDto(
                    Origin.valueOf(r.getOrigin().name()),
                    convert(r.getSecurityserverId()),
                    r.getAuthenticationCertificate(),
                    r.getServerAddress());
        }
        throw new IllegalArgumentException("Unknown request type");
    }

    public ManagementRequestInfo convert(ManagementRequestInfoDto dto) {
        var info = new ManagementRequestInfo();
        info.setId(dto.getId());
        info.setType(ManagementRequestType.valueOf(dto.getType().name()));
        info.setOrigin(ManagementRequestOrigin.valueOf(dto.getOrigin().name()));
        info.setStatus(dto.getStatus() == null ? null : ManagementRequestStatus.valueOf(dto.getStatus().name()));
        info.setSecurityserverId(convert(dto.getServerId()));
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
        } else {
            throw new IllegalArgumentException("Unknown management request type");
        }

        return response
                .id(dto.getId())
                .type(ManagementRequestType.valueOf(dto.getType().name()))
                .origin(ManagementRequestOrigin.valueOf(dto.getOrigin().name()))
                .status(ManagementRequestStatus.valueOf(dto.getStatus().name()))
                .securityserverId(convert(dto.getServerId()));
    }

    private SecurityServerId convert(ee.ria.xroad.common.identifier.SecurityServerId id) {
        var serverId = new SecurityServerId();
        serverId.memberClass(id.getMemberClass())
                .memberCode(id.getMemberCode())
                .serverCode(id.getServerCode())
                .instanceId(id.getXRoadInstance())
                .type(XRoadId.TypeEnum.SERVER);
        return serverId;
    }

    private ee.ria.xroad.common.identifier.SecurityServerId convert(SecurityServerId id) {
        return ee.ria.xroad.common.identifier.SecurityServerId.create(
                id.getInstanceId(),
                id.getMemberClass(),
                id.getMemberCode(),
                id.getServerCode());
    }

    public List<ManagementRequestInfo> convert(Collection<ManagementRequestInfoDto> content) {
        var result = new ArrayList<ManagementRequestInfo>(content.size());
        for (var dto : content) {
            result.add(convert(dto));
        }
        return result;
    }

    public Origin convert(ManagementRequestOrigin origin) {
        return origin == null ? null : Origin.valueOf(origin.name());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public ee.ria.xroad.common.identifier.SecurityServerId parseServerId(String id) {
        if (id == null) return null;
        final String[] parts = id.split(":", 6);
        if (parts.length != 5 || XRoadObjectType.SERVER.name().equals(parts[0])) {
            throw new IllegalArgumentException("Invalid security server id");
        }
        return ee.ria.xroad.common.identifier.SecurityServerId.create(parts[0], parts[1], parts[2], parts[3]);
    }

    public org.niis.xroad.centralserver.restapi.domain.ManagementRequestType convert(ManagementRequestType type) {
        return type == null ? null
                : org.niis.xroad.centralserver.restapi.domain.ManagementRequestType.valueOf(type.name());
    }
}
