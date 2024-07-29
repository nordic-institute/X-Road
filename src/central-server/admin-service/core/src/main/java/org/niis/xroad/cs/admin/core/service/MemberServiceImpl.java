/*
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.GlobalGroupMember;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.dto.MemberCreationRequest;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.GlobalGroupMemberMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupMemberRepository;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final XRoadMemberRepository xRoadMemberRepository;
    private final MemberClassRepository memberClassRepository;
    private final GlobalGroupMemberRepository globalGroupMemberRepository;
    private final IdentifierRepository<MemberIdEntity> memberIds;
    private final ServerClientRepository serverClientRepository;

    private final GlobalGroupMemberService globalGroupMemberService;

    private final SecurityServerMapper securityServerMapper;
    private final SecurityServerClientMapper securityServerClientMapper;
    private final GlobalGroupMemberMapper globalGroupMemberMapper;
    private final AuditDataHelper auditData;

    @Override
    public XRoadMember add(MemberCreationRequest request) {
        auditData.put(MEMBER_NAME, request.getMemberName());
        auditData.put(MEMBER_CLASS, request.getClientId().getMemberClass());
        auditData.put(MEMBER_CODE, request.getClientId().getMemberCode());

        final boolean exists = xRoadMemberRepository.findOneBy(request.getClientId()).isPresent();
        if (exists) {
            throw new DataIntegrityException(MEMBER_EXISTS, request.getClientId().toShortString());
        }

        return securityServerClientMapper.toDto(saveMember(request));
    }

    private XRoadMemberEntity saveMember(MemberCreationRequest request) {
        var memberClass = memberClassRepository.findByCode(request.getMemberClass())
                .orElseThrow(() -> new NotFoundException(
                        MEMBER_CLASS_NOT_FOUND,
                        "code",
                        request.getMemberClass()
                ));

        var memberIdEntity = memberIds.findOrCreate(MemberIdEntity.ensure(request.getClientId()));

        var entity = new XRoadMemberEntity(
                request.getMemberName(),
                memberIdEntity,
                memberClass);

        return xRoadMemberRepository.save(entity);
    }

    @Override
    public void delete(ClientId clientId) {
        auditData.put(MEMBER_CLASS, clientId.getMemberClass());
        auditData.put(MEMBER_CODE, clientId.getMemberCode());

        XRoadMemberEntity member = xRoadMemberRepository.findMember(clientId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));
        globalGroupMemberService.removeClientFromGlobalGroups(clientId);
        // other dependant entities are removed by cascading database constraints
        xRoadMemberRepository.delete(member);
    }

    @Override
    public Optional<XRoadMember> findMember(ClientId clientId) {
        return xRoadMemberRepository.findMember(clientId)
                .map(securityServerClientMapper::toDto);
    }

    @Override
    public List<GlobalGroupMember> getMemberGlobalGroups(ClientId memberId) {
        return globalGroupMemberRepository.findMemberGroups(memberId)
                .stream().map(globalGroupMemberMapper::toTarget)
                .collect(toList());
    }

    @Override
    public List<SecurityServer> getMemberOwnedServers(ClientId memberId) {
        return xRoadMemberRepository.findMember(memberId).stream()
                .map(XRoadMemberEntity::getOwnedServers)
                .flatMap(Collection::stream)
                .map(securityServerMapper::toTarget)
                .collect(toList());
    }

    @Override
    public List<SecurityServer> getMemberClientOfServers(ClientId memberId) {

        return xRoadMemberRepository.findOneBy(memberId).stream()
                .map(SecurityServerClientEntity::getServerClients)
                .flatMap(Collection::stream)
                .map(ServerClientEntity::getSecurityServer)
                .map(securityServerMapper::toTarget)
                .collect(toList());
    }

    @Override
    public void unregisterMember(ClientId memberId, SecurityServerId securityServerId) {
        auditData.put(SERVER_CODE, securityServerId.getServerCode());
        auditData.put(OWNER_CLASS, securityServerId.getOwner().getMemberClass());
        auditData.put(OWNER_CODE, securityServerId.getOwner().getMemberCode());
        auditData.put(CLIENT_IDENTIFIER, memberId);

        var member = xRoadMemberRepository.findOneBy(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));

        ServerClientEntity serverClient = member.getServerClients().stream()
                .filter(sc -> securityServerId.equals(sc.getSecurityServer().getServerId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER));

        serverClientRepository.delete(serverClient);
    }

    @Override
    public Optional<XRoadMember> updateMemberName(ClientId clientId, String newName) {
        auditData.put(MEMBER_NAME, newName);
        auditData.put(MEMBER_CLASS, clientId.getMemberClass());
        auditData.put(MEMBER_CODE, clientId.getMemberCode());
        return xRoadMemberRepository.findMember(clientId)
                .map(xRoadMember -> {
                    xRoadMember.setName(newName);
                    return xRoadMember;
                })
                .map(securityServerClientMapper::toDto);
    }
}
