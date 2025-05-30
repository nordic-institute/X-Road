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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.api.dto.SubsystemCreationRequest;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerClientMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.SubsystemRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_REGISTERED_AND_CANNOT_BE_DELETED;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_SUBSYSTEM_NAME;

@Service
@Transactional
@RequiredArgsConstructor
public class SubsystemServiceImpl implements SubsystemService {

    private final SubsystemRepository subsystemRepository;
    private final XRoadMemberRepository xRoadMemberRepository;
    private final IdentifierRepository<SubsystemIdEntity> subsystemIds;
    private final GlobalGroupMemberService globalGroupMemberService;
    private final SecurityServerClientMapper subsystemConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    public Subsystem add(SubsystemCreationRequest request) {
        auditDataHelper.put(MEMBER_CLASS, request.getMemberId().getMemberClass());
        auditDataHelper.put(MEMBER_CODE, request.getMemberId().getMemberCode());
        auditDataHelper.put(MEMBER_SUBSYSTEM_CODE, request.getSubsystemId().getSubsystemCode());
        auditDataHelper.put(MEMBER_SUBSYSTEM_NAME, request.getSubsystemName());

        final boolean exists = subsystemRepository.findOneBy(request.getSubsystemId()).isPresent();
        if (exists) {
            throw new ConflictException(SUBSYSTEM_EXISTS.build(request.getSubsystemId().toShortString()));
        }

        var persistedEntity = saveSubsystem(request);
        return subsystemConverter.toDto(persistedEntity);
    }

    private SubsystemEntity saveSubsystem(SubsystemCreationRequest request) {
        var memberEntity = xRoadMemberRepository.findMember(request.getMemberId())
                .orElseThrow(() -> new NotFoundException(
                        MEMBER_NOT_FOUND.build(
                                "code",
                                request.getMemberId().getMemberCode())
                ));
        var subsystemIdEntity = subsystemIds.findOrCreate(SubsystemIdEntity.ensure(request.getSubsystemId()));
        var subsystemEntity = new SubsystemEntity(memberEntity, subsystemIdEntity);
        subsystemEntity.setName(request.getSubsystemName());

        return subsystemRepository.save(subsystemEntity);
    }

    @Override
    public Set<Subsystem> findByMemberIdentifier(ClientId id) {
        return xRoadMemberRepository.findMember(id)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND.build()))
                .getSubsystems().stream().map(subsystemConverter::toDto)
                .collect(toSet());
    }

    @Override
    public Optional<Subsystem> findByIdentifier(ClientId id) {
        return subsystemRepository.findByIdentifier(id).map(subsystemConverter::toDto);
    }

    @Override
    public void deleteSubsystem(ClientId subsystemClientId) {
        auditDataHelper.put(MEMBER_CLASS, subsystemClientId.getMemberClass());
        auditDataHelper.put(MEMBER_CODE, subsystemClientId.getMemberCode());
        auditDataHelper.put(MEMBER_SUBSYSTEM_CODE, subsystemClientId.getSubsystemCode());

        var subsystem = subsystemRepository.findOneBy(subsystemClientId)
                .orElseThrow(() -> new NotFoundException(SUBSYSTEM_NOT_FOUND.build()));

        if (isRegistered(subsystem)) {
            throw new BadRequestException(SUBSYSTEM_REGISTERED_AND_CANNOT_BE_DELETED.build());
        }

        globalGroupMemberService.removeClientFromGlobalGroups(subsystemClientId);
        // other dependant entities are removed by cascading database constraints
        subsystemRepository.deleteById(subsystem.getId());
    }

    @Override
    public Optional<Subsystem> updateSubsystemName(ClientId clientId, String newName) {
        auditDataHelper.put(MEMBER_SUBSYSTEM_NAME, newName);
        auditDataHelper.put(MEMBER_SUBSYSTEM_CODE, newName);
        auditDataHelper.put(MEMBER_CLASS, clientId.getMemberClass());
        auditDataHelper.put(MEMBER_CODE, clientId.getMemberCode());
        return subsystemRepository.findByIdentifier(clientId)
                .map(subsystem -> {
                    subsystem.setName(newName);
                    return subsystem;
                })
                .map(subsystemConverter::toDto);
    }

    private boolean isRegistered(SubsystemEntity subsystem) {
        return !CollectionUtils.isEmpty(subsystem.getServerClients());
    }
}
