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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
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
 * writes nothing; so does {@code checkMemberDids}, which runs the same per-entry decision as the write and
 * leaves out the write, the lock and the audit event. Uniqueness is decided here first: the hash-indexed member DID constraint cannot be an
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

    private enum Effect {
        APPLY,
        DRY_RUN
    }

    private sealed interface MemberDidPlan {

        record Settled(ParticipantValueResult result) implements MemberDidPlan {
        }

        sealed interface Writable extends MemberDidPlan {
            MemberDidEntity rowFor(SecurityServerEntity server, String did);
        }

        record Insert(XRoadMemberEntity member) implements Writable {
            @Override
            public MemberDidEntity rowFor(SecurityServerEntity server, String did) {
                return new MemberDidEntity(server, member, did);
            }
        }

        record Replace(MemberDidEntity row) implements Writable {
            @Override
            public MemberDidEntity rowFor(SecurityServerEntity server, String did) {
                row.setDid(did);
                return row;
            }
        }
    }

    private record MemberKey(String instance, String memberClass, String memberCode) {
        static MemberKey of(ClientId id) {
            return new MemberKey(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode());
        }
    }

    private record MemberDidBatch(Map<MemberKey, XRoadMemberEntity> members, Map<Long, MemberDidEntity> storedByMemberId,
                                  Set<String> didsHeldElsewhere) {
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
        return decideMemberDids(server, memberDids, Mode.WRITE_ONCE, Effect.DRY_RUN);
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
        return decideMemberDids(lockAndLoad(securityServerId), memberDids, mode, Effect.APPLY);
    }

    private Map<ClientId, ParticipantValueResult> decideMemberDids(SecurityServerEntity server, Map<ClientId, String> memberDids,
                                                                   Mode mode, Effect effect) {
        var validator = new ParticipantValueValidator(registeredServerOf(server));
        var batch = loadBatch(server, memberDids);

        var results = new LinkedHashMap<ClientId, ParticipantValueResult>();
        memberDids.forEach((member, did) -> results.put(member, decideMemberDid(server, validator, batch, member, did, mode, effect)));
        return results;
    }

    private MemberDidBatch loadBatch(SecurityServerEntity server, Map<ClientId, String> memberDids) {
        if (memberDids.isEmpty()) {
            return new MemberDidBatch(Map.of(), new HashMap<>(), Set.of());
        }
        var members = memberRepository.findMembers(memberDids.keySet()).stream()
                .collect(toMap(entity -> MemberKey.of(entity.getIdentifier()), identity()));
        var memberIds = members.values().stream().map(XRoadMemberEntity::getId).collect(toSet());
        var storedByMemberId = new HashMap<Long, MemberDidEntity>();
        if (!memberIds.isEmpty()) {
            memberDidRepository.findBySecurityServerIdAndMemberIdIn(server.getId(), memberIds)
                    .forEach(row -> storedByMemberId.put(row.getMember().getId(), row));
        }
        var dids = memberDids.values().stream().filter(Objects::nonNull).collect(toSet());
        Set<String> didsHeldElsewhere = dids.isEmpty()
                ? Set.of()
                : new HashSet<>(memberDidRepository.findDidsHeldByOtherServers(dids, server.getId()));
        return new MemberDidBatch(members, storedByMemberId, didsHeldElsewhere);
    }

    private ParticipantValueResult decideMemberDid(SecurityServerEntity server, ParticipantValueValidator validator,
                                                   MemberDidBatch batch, ClientId member, String did, Mode mode, Effect effect) {
        return switch (planMemberDid(validator, batch, member, did, mode)) {
            case MemberDidPlan.Settled settled -> settled.result();
            case MemberDidPlan.Writable writable -> decideWrite(server, batch, member, did, writable, effect);
        };
    }

    private ParticipantValueResult decideWrite(SecurityServerEntity server, MemberDidBatch batch, ClientId member, String did,
                                               MemberDidPlan.Writable plan, Effect effect) {
        if (batch.didsHeldElsewhere().contains(did)) {
            return effect == Effect.APPLY
                    ? heldByAnotherServer(server, member)
                    : ParticipantValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER);
        }
        if (effect == Effect.APPLY) {
            var row = memberDidRepository.saveAndFlush(plan.rowFor(server, did));
            batch.storedByMemberId().put(row.getMember().getId(), row);
        }
        return ParticipantValueResult.STORED;
    }

    private MemberDidPlan planMemberDid(ParticipantValueValidator validator, MemberDidBatch batch,
                                        ClientId member, String did, Mode mode) {
        var rejection = validator.validate(new MemberDid(member, did));
        if (rejection.isPresent()) {
            return new MemberDidPlan.Settled(ParticipantValueResult.rejected(rejection.get()));
        }
        var memberEntity = batch.members().get(MemberKey.of(member));
        if (memberEntity == null) {
            return new MemberDidPlan.Settled(ParticipantValueResult.rejected(MEMBER_NOT_SERVED));
        }

        var stored = batch.storedByMemberId().get(memberEntity.getId());
        if (stored == null) {
            return mode == Mode.ADMIN_REPLACE
                    ? new MemberDidPlan.Settled(ParticipantValueResult.UNCHANGED)
                    : new MemberDidPlan.Insert(memberEntity);
        }
        var storedDid = stored.getDid();
        if (storedDid.equals(did)) {
            return new MemberDidPlan.Settled(ParticipantValueResult.UNCHANGED);
        }
        if (mode == Mode.WRITE_ONCE && !validator.isMemberDidStale(storedDid)) {
            return new MemberDidPlan.Settled(ParticipantValueResult.rejected(VALUE_DIFFERS));
        }
        return new MemberDidPlan.Replace(stored);
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
