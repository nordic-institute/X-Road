package org.niis.xroad.centralserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.model.PagedSecurityServers;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.centralserver.restapi.converter.SecurityServerIdConverter.entityToIdString;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerService {

    private final SecurityServerRepository securityServerRepository;


    public PagedSecurityServers findSecurityServers(/*TODO: Some parameters here */) {


        List<SecurityServer> foundServers = securityServerRepository.findAllBy(Sort.unsorted());

        PagingMetadata pagingMetadata = new PagingMetadata().totalItems(foundServers.size());
        return new PagedSecurityServers()
                .clients(toModel(foundServers))
                .pagingMetadata(pagingMetadata);
    }


    private static List<org.niis.xroad.centralserver.openapi.model.SecurityServer> toModel(List<SecurityServer> securityServerEntityList) {
        return securityServerEntityList.stream().map(entity -> {
            SecurityServerId serverId = getSecurityServerId(entity);

            return new org.niis.xroad.centralserver.openapi.model.SecurityServer()
                    .xroadId(serverId)
                    .id(entityToIdString(entity));
        }).collect(Collectors.toList());


    }

    private static SecurityServerId getSecurityServerId(SecurityServer entity) {
        SecurityServerId serverId = new SecurityServerId()
                .memberClass(entity.getOwner().getMemberClass().getCode())
                .memberCode(entity.getOwner().getMemberCode())
                .serverCode(entity.getServerCode());
        serverId.setInstanceId(entity.getOwner().getIdentifier().getXRoadInstance());
        serverId.setType(XRoadId.TypeEnum.SERVER);
        return serverId;
    }
}
