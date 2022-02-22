package org.niis.xroad.centralserver.restapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ClientService {

    // TO DO: dtos instead of entities?
    // 2: data.sql vs import.sql
    // 3: identifier vs identifiers

    @Autowired
    private SecurityServerClientRepository securityServerClientRepository;

    @Autowired
    private XRoadMemberRepository2 xRoadMemberRepository2;

    /**
     * SecurityServerClientRepository
     */
    public List<SecurityServerClient> findAll() {
        return securityServerClientRepository.findAll();
    }

    /**
     * SecurityServerClientRepository
     * Find by case-insensitive search for name
     * @param nameParam
     * @return
     */
    public List<SecurityServerClient> find(String nameParam) {
        return securityServerClientRepository.findAll(SecurityServerClientRepository.nameHas(nameParam));
    }

    public List<SecurityServerClient> findNameIs(String nameParam) {
        return securityServerClientRepository.findAll(SecurityServerClientRepository.nameIs(nameParam));
    }

    /**
     * XRoadMemberRepository2
     */
    public List<XRoadMember> findAll2() {
        return xRoadMemberRepository2.findAll();
    }

    /**
     * XRoadMemberRepository2
     * Find by case-insensitive search for name
     * @param nameParam
     * @return
     */
    public List<XRoadMember> find2(String nameParam) {
        return xRoadMemberRepository2.findAll(XRoadMemberRepository2.nameHas(nameParam));
    }


}
