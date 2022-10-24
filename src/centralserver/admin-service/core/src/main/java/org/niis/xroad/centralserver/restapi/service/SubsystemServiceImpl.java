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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientNameEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientNameRepository;
import org.niis.xroad.cs.admin.core.repository.SubsystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.transaction.Transactional;

import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.SUBSYSTEM_EXISTS;

@Service
@Transactional
@RequiredArgsConstructor
public class SubsystemServiceImpl implements SubsystemService {

    private final SubsystemRepository subsystemRepository;
    private final MemberService memberService;
    private final SecurityServerClientNameRepository securityServerClientNameRepository;
    private final SecurityServerClientMapper subsystemConverter;


    @Override
    public Subsystem add(Subsystem subsystem) {
        boolean exists = subsystemRepository.findOneBy(subsystem.getIdentifier()).isDefined();
        if (exists) {
            throw new EntityExistsException(SUBSYSTEM_EXISTS, subsystem.getIdentifier().toShortString());
        }

        var subsystemEntity = subsystemConverter.fromDto(subsystem);
        SubsystemEntity saved = subsystemRepository.save(subsystemEntity);
        saveSecurityServerClientName(saved);
        return subsystemConverter.toDto(saved);
    }

    private void saveSecurityServerClientName(SubsystemEntity subsystem) {
        var ssClientName = new SecurityServerClientNameEntity(subsystem.getXroadMember(), subsystem.getIdentifier());
        securityServerClientNameRepository.save(ssClientName);
    }

    @Override
    public Set<Subsystem> findByMemberIdentifier(ClientId id) {
        return memberService.findMember(id)
                .getOrElseThrow(() -> new NotFoundException(ErrorMessage.MEMBER_NOT_FOUND))
                .getSubsystems();
    }

    @Override
    public Optional<Subsystem> findByIdentifier(ClientId id) {
        return subsystemRepository.findByIdentifier(id).map(subsystemConverter::toDto);
    }

    @Override
    public void unregisterSubsystem(ClientId subsystemId, SecurityServerId securityServerId) {
        SubsystemEntity subsystem = subsystemRepository.findOneBy(subsystemId)
                .getOrElseThrow(() -> new NotFoundException(ErrorMessage.SUBSYSTEM_NOT_FOUND));
        ServerClientEntity serverClient = subsystem.getServerClients().stream()
                .filter(sc -> securityServerId.equals(sc.getSecurityServer().getServerId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorMessage.SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER));
        subsystem.getServerClients().remove(serverClient);
    }

    @Override
    public void deleteSubsystem(ClientId subsystemClientId) {
        var subsystem = subsystemRepository.findOneBy(subsystemClientId)
                .getOrElseThrow(() -> new NotFoundException(ErrorMessage.SUBSYSTEM_NOT_FOUND));

        if (isRegistered(subsystem)) {
            throw new ValidationFailureException(ErrorMessage.SUBSYSTEM_REGISTERED_AND_CANNOT_BE_DELETED);
        }

        subsystemRepository.deleteById(subsystem.getId());
    }

    private boolean isRegistered(SubsystemEntity subsystem) {
        return !CollectionUtils.isEmpty(subsystem.getServerClients());
    }
}
