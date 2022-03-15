package org.niis.xroad.centralserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.restapi.converter.SecurityServerConverter;
import org.niis.xroad.centralserver.restapi.dto.FoundSecurityServersWithTotalsDto;
import org.niis.xroad.centralserver.restapi.dto.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public FoundSecurityServersWithTotalsDto findSecurityServers(String q, Pageable pageable ) {




        Page<SecurityServer> foundServers = securityServerRepository.findAllBy(
                SecurityServerRepository.multifieldSearch(q), pageable);

        PagingMetadata pagingMetadata = new PagingMetadata().totalItems((int) foundServers.getTotalElements());
        return new FoundSecurityServersWithTotalsDto(
                toDto(foundServers),
                pagingMetadata.getTotalItems());
    }


    private List<SecurityServerDto> toDto(Page<SecurityServer> securityServerEntityPage) {
        return securityServerEntityPage.get().map(serverConverter::convert).collect(Collectors.toList());


    }


}
