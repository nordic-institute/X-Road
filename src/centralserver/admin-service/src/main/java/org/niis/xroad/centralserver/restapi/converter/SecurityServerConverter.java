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

import org.niis.xroad.centralserver.openapi.model.PagedSecurityServers;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.dto.FoundSecurityServersWithTotalsDto;
import org.niis.xroad.centralserver.restapi.dto.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.TimeZone;
import java.util.stream.Collectors;


public class SecurityServerConverter {

    public String convertToId(SecurityServer serverEntity) {

        return serverEntity.getOwner().getIdentifier().getXRoadInstance() + ':'
                + serverEntity.getOwner().getMemberClass().getCode() + ':'
                + serverEntity.getOwner().getMemberCode() + ':'
                + serverEntity.getServerCode();
    }

    public String convertToId(SecurityServerDto securityServerDto) {
        return securityServerDto.getInstanceId() + ':'
                + securityServerDto.getOwnerClass() + ':'
                + securityServerDto.getOwnerCode() + ':'
                + securityServerDto.getServerCode();
    }

    public SecurityServerDto convert(SecurityServer server) {
        SecurityServerId serverId = getSecurityServerId(server);
        return SecurityServerDto.builder()
                .id(convertToId(server))
                .instanceId(serverId.getInstanceId())
                .ownerClass(serverId.getMemberClass())
                .ownerCode(serverId.getMemberCode())
                .ownerName(server.getOwner().getName())
                .serverCode(serverId.getServerCode())
                .serverAddress(server.getAddress())
                .updatedAt(server.getUpdatedAt())
                .createdAt(server.getCreatedAt())
                .build();
    }

    public PagedSecurityServers convert(FoundSecurityServersWithTotalsDto dto,
                                        Pageable pageable) {
        return new PagedSecurityServers()
                .pagingMetadata(new PagingMetadata()
                        .totalItems(dto.getTotalCount())
                        .items(dto.getServerDtoList().size())
                        .limit(pageable.getPageSize())
                        .offset(pageable.getPageNumber())
                )
                .clients(dto.getServerDtoList().stream().map(securityServerDto -> {
                    SecurityServerId serverId = getSecurityServerId(securityServerDto);
                    return new org.niis.xroad.centralserver.openapi.model.SecurityServer()
                            .id(convertToId(securityServerDto))
                            .xroadId(serverId)
                            .owner(securityServerDto.getOwnerName())
                            .serverAddress(securityServerDto.getServerAddress())
                            .updatedAt(OffsetDateTime.ofInstant(securityServerDto.getUpdatedAt(),
                                    TimeZone.getDefault().toZoneId()))
                            .createdAt(OffsetDateTime.ofInstant(securityServerDto.getCreatedAt(), TimeZone.getDefault()
                                    .toZoneId()));

                }).collect(Collectors.toUnmodifiableList()));
    }

    private SecurityServerId getSecurityServerId(SecurityServer entity) {
        SecurityServerId serverId = new SecurityServerId()
                .memberClass(entity.getOwner().getMemberClass().getCode())
                .memberCode(entity.getOwner().getMemberCode())
                .serverCode(entity.getServerCode());
        serverId.setInstanceId(entity.getOwner().getIdentifier().getXRoadInstance());
        serverId.setType(XRoadId.TypeEnum.SERVER);
        return serverId;
    }

    private SecurityServerId getSecurityServerId(SecurityServerDto serverDto) {
        SecurityServerId serverId =
                new SecurityServerId().memberClass(serverDto.getOwnerClass()).memberCode(serverDto.getOwnerCode())
                        .serverCode(serverDto.getServerCode());

        serverId.setInstanceId(serverDto.getInstanceId());
        serverId.type(XRoadId.TypeEnum.SERVER);
        return serverId;
    }

}
