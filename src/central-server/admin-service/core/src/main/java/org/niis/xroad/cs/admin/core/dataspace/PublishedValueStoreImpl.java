/*
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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.PublishedValueResult;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;
import org.niis.xroad.cs.admin.api.service.PublishedValueStore;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueCandidate.MemberDid;
import org.niis.xroad.cs.admin.core.dataspace.PublishedValueCandidate.SystemPair;
import org.niis.xroad.cs.admin.core.entity.MemberDidEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.UNRELATED_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SECURITY_SERVER_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REJECT_CONNECTOR_PUBLISHED_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

/**
 * Every operation locks the server row before it reads or writes any published value, then validates the
 * values against the server as it is under the lock. Uniqueness is decided here, not by the database: the
 * hash-indexed member DID constraint cannot be an {@code ON CONFLICT} target, so equal resends are told
 * apart from conflicts by comparing the stored value.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PublishedValueStoreImpl implements PublishedValueStore {

    private enum Mode {
        WRITE_ONCE,
        ADMIN_REPLACE
    }

    private final SecurityServerRepository securityServerRepository;
    private final MemberDidRepository memberDidRepository;
    private final XRoadMemberRepository memberRepository;
    private final AuditEventLoggingFacade auditLogging;
    private final EntityManager entityManager;

    @Override
    public PublishedValueResult writeSystemPairOnce(int securityServerId, String systemDid, String dspBaseUrl) {
        return storeSystemPair(securityServerId, new SystemPair(systemDid, dspBaseUrl), Mode.WRITE_ONCE);
    }

    @Override
    public SystemPairCheck checkSystemPair(int securityServerId, String systemDid, String dspBaseUrl) {
        var server = securityServerRepository.findById(securityServerId)
                .orElseThrow(() -> serverNotFound(securityServerId));
        var pair = new SystemPair(systemDid, dspBaseUrl);

        var rejection = new PublishedValueValidator(contextOf(server)).validate(pair);
        if (rejection.isPresent()) {
            return SystemPairCheck.rejected(rejection.get());
        }
        return isStoredPair(server, pair) ? SystemPairCheck.EQUAL : SystemPairCheck.NOT_EQUAL;
    }

    @Override
    public PublishedValueResult replaceSystemPair(int securityServerId, String systemDid, String dspBaseUrl) {
        return storeSystemPair(securityServerId, new SystemPair(systemDid, dspBaseUrl), Mode.ADMIN_REPLACE);
    }

    @Override
    public Map<ClientId, PublishedValueResult> writeMemberDidsOnce(int securityServerId, Map<ClientId, String> memberDids) {
        return storeMemberDids(securityServerId, memberDids, Mode.WRITE_ONCE);
    }

    @Override
    public Map<ClientId, PublishedValueResult> replaceMemberDids(int securityServerId, Map<ClientId, String> memberDids) {
        return storeMemberDids(securityServerId, memberDids, Mode.ADMIN_REPLACE);
    }

    private PublishedValueResult storeSystemPair(int securityServerId, SystemPair pair, Mode mode) {
        var server = lockAndLoad(securityServerId);
        var validator = new PublishedValueValidator(contextOf(server));

        var rejection = validator.validate(pair);
        if (rejection.isPresent()) {
            return PublishedValueResult.rejected(rejection.get());
        }

        var storedDid = server.getSystemDid();
        if (storedDid == null && mode == Mode.ADMIN_REPLACE) {
            return PublishedValueResult.UNCHANGED;
        }
        if (storedDid != null) {
            if (isStoredPair(server, pair)) {
                return PublishedValueResult.UNCHANGED;
            }
            if (mode == Mode.WRITE_ONCE && !validator.isSystemPairStale(storedDid, server.getDspBaseUrl())) {
                return PublishedValueResult.rejected(VALUE_DIFFERS);
            }
        }
        if (securityServerRepository.existsBySystemDidAndIdNot(pair.did(), server.getId())) {
            return heldByAnotherServer(server, null);
        }

        server.setSystemDid(pair.did());
        server.setDspBaseUrl(pair.dspBaseUrl());
        securityServerRepository.saveAndFlush(server);
        return PublishedValueResult.STORED;
    }

    private static boolean isStoredPair(SecurityServerEntity server, SystemPair pair) {
        return Objects.equals(server.getSystemDid(), pair.did()) && Objects.equals(server.getDspBaseUrl(), pair.dspBaseUrl());
    }

    private Map<ClientId, PublishedValueResult> storeMemberDids(int securityServerId, Map<ClientId, String> memberDids, Mode mode) {
        var server = lockAndLoad(securityServerId);
        var validator = new PublishedValueValidator(contextOf(server));

        var results = new LinkedHashMap<ClientId, PublishedValueResult>();
        memberDids.forEach((member, did) -> results.put(member, storeMemberDid(server, validator, member, did, mode)));
        return results;
    }

    private PublishedValueResult storeMemberDid(SecurityServerEntity server, PublishedValueValidator validator,
                                                ClientId member, String did, Mode mode) {
        var rejection = validator.validate(new MemberDid(member, did));
        if (rejection.isPresent()) {
            return PublishedValueResult.rejected(rejection.get());
        }
        var memberEntity = memberRepository.findMember(member);
        if (memberEntity.isEmpty()) {
            return PublishedValueResult.rejected(UNRELATED_MEMBER);
        }

        var stored = memberDidRepository.findBySecurityServerIdAndMemberId(server.getId(), memberEntity.get().getId());
        if (stored.isEmpty() && mode == Mode.ADMIN_REPLACE) {
            return PublishedValueResult.UNCHANGED;
        }
        if (stored.isPresent()) {
            var storedDid = stored.get().getDid();
            if (storedDid.equals(did)) {
                return PublishedValueResult.UNCHANGED;
            }
            if (mode == Mode.WRITE_ONCE && !validator.isMemberDidStale(storedDid)) {
                return PublishedValueResult.rejected(VALUE_DIFFERS);
            }
        }
        if (memberDidRepository.existsByDidAndSecurityServerIdNot(did, server.getId())) {
            return heldByAnotherServer(server, member);
        }
        return writeMemberDid(server, memberEntity.get(), stored, did);
    }

    private PublishedValueResult writeMemberDid(SecurityServerEntity server, XRoadMemberEntity memberEntity,
                                                Optional<MemberDidEntity> stored, String did) {
        var row = stored.orElseGet(() -> new MemberDidEntity(server, memberEntity, did));
        row.setDid(did);
        memberDidRepository.saveAndFlush(row);
        return PublishedValueResult.STORED;
    }

    private SecurityServerEntity lockAndLoad(int securityServerId) {
        if (securityServerRepository.lockForUpdateById(securityServerId).isEmpty()) {
            throw serverNotFound(securityServerId);
        }
        var server = securityServerRepository.findById(securityServerId)
                .orElseThrow(() -> serverNotFound(securityServerId));
        entityManager.refresh(server);
        return server;
    }

    private static NotFoundException serverNotFound(int securityServerId) {
        return new NotFoundException(SECURITY_SERVER_NOT_FOUND.build(String.valueOf(securityServerId)));
    }

    private static PublishedValueContext contextOf(SecurityServerEntity server) {
        var clientMembers = server.getServerClients().stream()
                .map(serverClient -> serverClient.getSecurityServerClient().getIdentifier().getMemberId())
                .collect(toSet());
        return new PublishedValueContext(server.getAddress(), server.getOwner().getIdentifier().getMemberId(), clientMembers);
    }

    private PublishedValueResult heldByAnotherServer(SecurityServerEntity server, ClientId member) {
        var data = new LinkedHashMap<RestApiAuditProperty, Object>();
        data.put(SERVER_CODE, server.getServerCode());
        data.put(OWNER_CLASS, server.getOwner().getIdentifier().getMemberClass());
        data.put(OWNER_CODE, server.getOwner().getIdentifier().getMemberCode());
        if (member != null) {
            data.put(MEMBER_CLASS, member.getMemberClass());
            data.put(MEMBER_CODE, member.getMemberCode());
        }
        auditLogging.auditLogFailWithData(REJECT_CONNECTOR_PUBLISHED_VALUE,
                "Published value rejected: " + DID_HELD_BY_ANOTHER_SERVER, data);
        return PublishedValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER);
    }
}
