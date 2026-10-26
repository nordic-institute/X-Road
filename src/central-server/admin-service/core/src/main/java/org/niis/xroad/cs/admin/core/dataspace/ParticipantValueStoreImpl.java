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
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck.Verdict;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.dataspace.ParticipantValue.MemberDid;
import org.niis.xroad.cs.admin.core.dataspace.ParticipantValue.SystemPair;
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
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MEMBER_NOT_SERVED;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SECURITY_SERVER_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REJECT_CONNECTOR_PUBLISHED_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

/**
 * Writes lock the server row before they read or write any published value, then validate the values
 * against the server as it is under the lock. {@code checkSystemPair} is a dry run that takes no lock and
 * writes nothing; so does {@code checkMemberDids}, which decides each entry exactly as the write does
 * and only leaves out the audit event. Uniqueness is decided here first: the hash-indexed member DID constraint cannot be an
 * {@code ON CONFLICT} target, so equal resends are told apart from conflicts by comparing the stored value.
 * A refusal the store sees under the lock concerns one entry and does not stop the others. When two
 * servers race on the same DID and the database exclusion constraint fires first, the whole batch rolls
 * back; the repeated call is then refused per entry.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantValueStoreImpl implements ParticipantValueStore {

    private enum Mode {
        WRITE_ONCE,
        ADMIN_REPLACE
    }

    private record MemberDidPlan(ParticipantValueResult settled, XRoadMemberEntity memberEntity,
                                 Optional<MemberDidEntity> stored) {

        static MemberDidPlan settled(ParticipantValueResult result) {
            return new MemberDidPlan(result, null, Optional.empty());
        }

        boolean isSettled() {
            return settled != null;
        }
    }

    private final SecurityServerRepository securityServerRepository;
    private final MemberDidRepository memberDidRepository;
    private final XRoadMemberRepository memberRepository;
    private final AuditEventLoggingFacade auditLogging;
    private final EntityManager entityManager;

    @Override
    public ParticipantValueResult writeSystemPairOnce(int securityServerId, String systemDid, String dspBaseUrl) {
        return storeSystemPair(securityServerId, new SystemPair(systemDid, dspBaseUrl), Mode.WRITE_ONCE);
    }

    @Override
    public SystemPairCheck checkSystemPair(int securityServerId, String systemDid, String dspBaseUrl) {
        var server = securityServerRepository.findById(securityServerId)
                .orElseThrow(() -> serverNotFound(securityServerId));
        var pair = new SystemPair(systemDid, dspBaseUrl);

        var check = verdictOf(server, new ParticipantValueValidator(registeredServerOf(server)), pair);
        boolean wouldWrite = check.verdict() == Verdict.ABSENT || check.verdict() == Verdict.STALE_REPLACEABLE;
        if (wouldWrite && isHeldByAnotherServer(server, pair)) {
            return SystemPairCheck.rejected(DID_HELD_BY_ANOTHER_SERVER);
        }
        return check;
    }

    @Override
    public ParticipantValueResult replaceSystemPair(int securityServerId, String systemDid, String dspBaseUrl) {
        return storeSystemPair(securityServerId, new SystemPair(systemDid, dspBaseUrl), Mode.ADMIN_REPLACE);
    }

    @Override
    public Map<ClientId, ParticipantValueResult> writeMemberDidsOnce(int securityServerId, Map<ClientId, String> memberDids) {
        return storeMemberDids(securityServerId, memberDids, Mode.WRITE_ONCE);
    }

    @Override
    public Map<ClientId, ParticipantValueResult> checkMemberDids(int securityServerId, Map<ClientId, String> memberDids) {
        var server = securityServerRepository.findById(securityServerId)
                .orElseThrow(() -> serverNotFound(securityServerId));
        var validator = new ParticipantValueValidator(registeredServerOf(server));

        var results = new LinkedHashMap<ClientId, ParticipantValueResult>();
        memberDids.forEach((member, did) -> results.put(member, checkMemberDid(server, validator, member, did)));
        return results;
    }

    private ParticipantValueResult checkMemberDid(SecurityServerEntity server, ParticipantValueValidator validator,
                                                  ClientId member, String did) {
        var plan = planMemberDid(server, validator, member, did, Mode.WRITE_ONCE);
        if (plan.isSettled()) {
            return plan.settled();
        }
        return isDidHeldByAnotherServer(server, did)
                ? ParticipantValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER)
                : ParticipantValueResult.STORED;
    }

    @Override
    public Map<ClientId, ParticipantValueResult> replaceMemberDids(int securityServerId, Map<ClientId, String> memberDids) {
        return storeMemberDids(securityServerId, memberDids, Mode.ADMIN_REPLACE);
    }

    private ParticipantValueResult storeSystemPair(int securityServerId, SystemPair pair, Mode mode) {
        var server = lockAndLoad(securityServerId);
        var check = verdictOf(server, new ParticipantValueValidator(registeredServerOf(server)), pair);

        var verdict = check.verdict();
        if (verdict == Verdict.REJECTED) {
            return ParticipantValueResult.rejected(check.rejection());
        }
        if (verdict == Verdict.EQUAL || (verdict == Verdict.ABSENT && mode == Mode.ADMIN_REPLACE)) {
            return ParticipantValueResult.UNCHANGED;
        }
        if (verdict == Verdict.VALUE_DIFFERS && mode == Mode.WRITE_ONCE) {
            return ParticipantValueResult.rejected(VALUE_DIFFERS);
        }
        if (isHeldByAnotherServer(server, pair)) {
            return heldByAnotherServer(server, null);
        }

        server.setSystemDid(pair.did());
        server.setDspBaseUrl(pair.dspBaseUrl());
        securityServerRepository.saveAndFlush(server);
        return ParticipantValueResult.STORED;
    }

    private SystemPairCheck verdictOf(SecurityServerEntity server, ParticipantValueValidator validator, SystemPair pair) {
        var rejection = validator.validate(pair);
        if (rejection.isPresent()) {
            return SystemPairCheck.rejected(rejection.get());
        }
        var storedDid = server.getSystemDid();
        if (storedDid == null) {
            return SystemPairCheck.ABSENT;
        }
        if (isStoredPair(server, pair)) {
            return SystemPairCheck.EQUAL;
        }
        return validator.isSystemPairStale(storedDid, server.getDspBaseUrl())
                ? SystemPairCheck.STALE_REPLACEABLE
                : SystemPairCheck.VALUE_DIFFERS;
    }

    private boolean isHeldByAnotherServer(SecurityServerEntity server, SystemPair pair) {
        return securityServerRepository.existsBySystemDidAndIdNot(pair.did(), server.getId());
    }

    private static boolean isStoredPair(SecurityServerEntity server, SystemPair pair) {
        return Objects.equals(server.getSystemDid(), pair.did()) && Objects.equals(server.getDspBaseUrl(), pair.dspBaseUrl());
    }

    private Map<ClientId, ParticipantValueResult> storeMemberDids(int securityServerId, Map<ClientId, String> memberDids, Mode mode) {
        var server = lockAndLoad(securityServerId);
        var validator = new ParticipantValueValidator(registeredServerOf(server));

        var results = new LinkedHashMap<ClientId, ParticipantValueResult>();
        memberDids.forEach((member, did) -> results.put(member, storeMemberDid(server, validator, member, did, mode)));
        return results;
    }

    private ParticipantValueResult storeMemberDid(SecurityServerEntity server, ParticipantValueValidator validator,
                                                ClientId member, String did, Mode mode) {
        var plan = planMemberDid(server, validator, member, did, mode);
        if (plan.isSettled()) {
            return plan.settled();
        }
        if (isDidHeldByAnotherServer(server, did)) {
            return heldByAnotherServer(server, member);
        }
        return writeMemberDid(server, plan.memberEntity(), plan.stored(), did);
    }

    private MemberDidPlan planMemberDid(SecurityServerEntity server, ParticipantValueValidator validator,
                                        ClientId member, String did, Mode mode) {
        var rejection = validator.validate(new MemberDid(member, did));
        if (rejection.isPresent()) {
            return MemberDidPlan.settled(ParticipantValueResult.rejected(rejection.get()));
        }
        var memberEntity = memberRepository.findMember(member);
        if (memberEntity.isEmpty()) {
            return MemberDidPlan.settled(ParticipantValueResult.rejected(MEMBER_NOT_SERVED));
        }

        var stored = memberDidRepository.findBySecurityServerIdAndMemberId(server.getId(), memberEntity.get().getId());
        if (stored.isEmpty() && mode == Mode.ADMIN_REPLACE) {
            return MemberDidPlan.settled(ParticipantValueResult.UNCHANGED);
        }
        if (stored.isPresent()) {
            var storedDid = stored.get().getDid();
            if (storedDid.equals(did)) {
                return MemberDidPlan.settled(ParticipantValueResult.UNCHANGED);
            }
            if (mode == Mode.WRITE_ONCE && !validator.isMemberDidStale(storedDid)) {
                return MemberDidPlan.settled(ParticipantValueResult.rejected(VALUE_DIFFERS));
            }
        }
        return new MemberDidPlan(null, memberEntity.get(), stored);
    }

    private boolean isDidHeldByAnotherServer(SecurityServerEntity server, String did) {
        return memberDidRepository.existsByDidAndSecurityServerIdNot(did, server.getId());
    }

    private ParticipantValueResult writeMemberDid(SecurityServerEntity server, XRoadMemberEntity memberEntity,
                                                Optional<MemberDidEntity> stored, String did) {
        var row = stored.orElseGet(() -> new MemberDidEntity(server, memberEntity, did));
        row.setDid(did);
        memberDidRepository.saveAndFlush(row);
        return ParticipantValueResult.STORED;
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

    private static RegisteredServer registeredServerOf(SecurityServerEntity server) {
        var clientMembers = server.getServerClients().stream()
                .map(serverClient -> serverClient.getSecurityServerClient().getIdentifier().getMemberId())
                .collect(toSet());
        return new RegisteredServer(server.getAddress(), server.getOwner().getIdentifier().getMemberId(), clientMembers);
    }

    private ParticipantValueResult heldByAnotherServer(SecurityServerEntity server, ClientId member) {
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
        return ParticipantValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER);
    }
}
