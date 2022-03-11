package org.niis.xroad.centralserver.restapi.converter;

import org.niis.xroad.centralserver.openapi.model.PagedSecurityServers;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.dto.FoundSecurityServersWithTotalsDto;
import org.niis.xroad.centralserver.restapi.dto.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;

import java.util.stream.Collectors;


public class SecurityServerConverter {

    public String convertToId(SecurityServer serverEntity) {

        return serverEntity.getOwner().getIdentifier().getXRoadInstance() + ':' + serverEntity.getOwner().getMemberClass().getCode() + ':' + serverEntity.getOwner().getMemberCode() + ':' + serverEntity.getServerCode();
    }

    public String convertToId(SecurityServerDto securityServerDto) {
        return securityServerDto.getInstanceId() + ':' + securityServerDto.getMemberClass() + ':' + securityServerDto.getMemberCode() + ':' + securityServerDto.getServerCode() + ':';
    }

    public SecurityServerDto convert(SecurityServer server) {
        SecurityServerId serverId = getSecurityServerId(server);
        return SecurityServerDto.builder().id(convertToId(server)).instanceId(serverId.getInstanceId()).memberClass(serverId.getMemberClass()).memberCode(serverId.getMemberCode()).serverCode(serverId.getServerCode()).build();
    }

    public PagedSecurityServers convert(FoundSecurityServersWithTotalsDto dto) {
        return new PagedSecurityServers()
                .pagingMetadata(new PagingMetadata()
                        .totalItems(dto.getTotalCount()))
                .clients(dto.getServerDtoList().stream().map(securityServerDto -> {
                    SecurityServerId serverId = getSecurityServerId(securityServerDto);
                    return new org.niis.xroad.centralserver.openapi.model.SecurityServer()
                            .xroadId(serverId)
                            .id(convertToId(securityServerDto));
                }).collect(Collectors.toUnmodifiableList()));
    }

    private SecurityServerId getSecurityServerId(SecurityServer entity) {
        SecurityServerId serverId = new SecurityServerId().memberClass(entity.getOwner().getMemberClass().getCode()).memberCode(entity.getOwner().getMemberCode()).serverCode(entity.getServerCode());
        serverId.setInstanceId(entity.getOwner().getIdentifier().getXRoadInstance());
        serverId.setType(XRoadId.TypeEnum.SERVER);
        return serverId;
    }

    private SecurityServerId getSecurityServerId(SecurityServerDto serverDto) {
        SecurityServerId serverId = new SecurityServerId().memberClass(serverDto.getMemberClass()).memberCode(serverDto.getMemberCode()).serverCode(serverDto.getServerCode());

        serverId.setInstanceId(serverDto.getInstanceId());
        serverId.type(XRoadId.TypeEnum.SERVER);
        return serverId;
    }
}
