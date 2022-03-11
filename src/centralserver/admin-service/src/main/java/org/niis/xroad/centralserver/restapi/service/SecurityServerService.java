package org.niis.xroad.centralserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.restapi.converter.SecurityServerConverter;
import org.niis.xroad.centralserver.restapi.dto.FoundSecurityServersWithTotalsDto;
import org.niis.xroad.centralserver.restapi.dto.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerService {

    private final SecurityServerRepository securityServerRepository;

    private final SecurityServerConverter serverConverter = new SecurityServerConverter();

    public FoundSecurityServersWithTotalsDto findSecurityServers(/*TODO: Some parameters here */) {


        List<SecurityServer> foundServers = securityServerRepository.findAllBy(Sort.unsorted());

        PagingMetadata pagingMetadata = new PagingMetadata().totalItems(foundServers.size());
        return new FoundSecurityServersWithTotalsDto(
                toDto(foundServers),
                pagingMetadata.getTotalItems());
    }


    private List<SecurityServerDto> toDto(List<SecurityServer> securityServerEntityList) {
        return securityServerEntityList.stream().map(serverConverter::convert).collect(Collectors.toList());


    }


}
