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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionOutcome;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.MemberDidRegistrationService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.config.ManagementServiceConfigProperties;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.function.Function;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_REQUEST_TOO_LARGE;

/**
 * Refuses a batch above the configured entry cap as a whole. Otherwise decides, before the request is stored,
 * every entry that does not need the write lock: duplicate entries, entries refused while the server has no SYSTEM
 * values, entries the store refuses, and entries whose DID does not resolve. An entry equal to the stored value
 * skips resolution, since it was resolved when first stored, and is answered by the write under the lock, which
 * validates it again. Holds no transaction, so the resolver call never runs inside one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberDidRegistrationServiceImpl implements MemberDidRegistrationService {

    private final DidResolutionService didResolutionService;
    private final ParticipantValueStore participantValueStore;
    private final SecurityServerRepository securityServerRepository;
    private final ManagementRequestService managementRequestService;
    private final ManagementServiceConfigProperties configProperties;

    @Override
    public MemberDidRegistrationRequest add(MemberDidRegistrationRequest request) {
        int maxEntries = configProperties.getMemberDidRegMaxEntries();
        if (request.getEntries().size() > maxEntries) {
            throw new BadRequestException(MR_REQUEST_TOO_LARGE.build(String.valueOf(maxEntries)));
        }
        request.setSettledResults(settle(request));
        return managementRequestService.add(request);
    }

    private Map<Integer, Result> settle(MemberDidRegistrationRequest request) {
        var entries = request.getEntries();
        var settledByMember = new LinkedHashMap<ClientId, Result>();

        var candidates = singleDidPerMember(entries, settledByMember);
        if (!candidates.isEmpty()) {
            var server = securityServerRepository.findBy(request.getSecurityServerId());
            if (server.isEmpty()) {
                candidates.keySet().forEach(member -> settledByMember.put(member, MemberDidRegistrationOutcome.ownerChanged(member)));
            } else if (server.get().getSystemDid() == null) {
                candidates.keySet().forEach(member ->
                        settledByMember.put(member, MemberDidRegistrationOutcome.systemValuesNotStored(member)));
            } else {
                settledByMember.putAll(settleCandidates(server.get(), candidates));
            }
        }

        var settled = new LinkedHashMap<Integer, Result>();
        for (int i = 0; i < entries.size(); i++) {
            var result = settledByMember.get(entries.get(i).memberId());
            if (result != null) {
                settled.put(i, result);
            }
        }
        return settled;
    }

    /**
     * Settles the members named with more than one distinct DID and returns the DID of every other member.
     */
    private static Map<ClientId, String> singleDidPerMember(List<Entry> entries, Map<ClientId, Result> settledByMember) {
        var didsByMember = new LinkedHashMap<ClientId, SequencedSet<String>>();
        for (var entry : entries) {
            didsByMember.computeIfAbsent(entry.memberId(), member -> new LinkedHashSet<>()).add(entry.did());
        }
        var candidates = new LinkedHashMap<ClientId, String>();
        didsByMember.forEach((member, dids) -> {
            if (dids.size() > 1) {
                settledByMember.put(member, MemberDidRegistrationOutcome.conflictingDuplicate(member));
            } else {
                candidates.put(member, dids.getFirst());
            }
        });
        return candidates;
    }

    private Map<ClientId, Result> settleCandidates(SecurityServerEntity server, Map<ClientId, String> candidates) {
        var settled = new LinkedHashMap<ClientId, Result>();
        var eligible = new LinkedHashMap<ClientId, String>();
        participantValueStore.checkMemberDids(server.getId(), candidates).forEach((member, check) -> {
            if (check.outcome() == ParticipantValueResult.Outcome.REJECTED) {
                settled.put(member, MemberDidRegistrationOutcome.rejected(member, check.rejection(), server.getAddress()));
            } else if (check.outcome() == ParticipantValueResult.Outcome.STORED) {
                eligible.put(member, candidates.get(member));
            }
        });
        if (!eligible.isEmpty()) {
            settled.putAll(unresolved(eligible));
        }
        return settled;
    }

    private Map<ClientId, Result> unresolved(Map<ClientId, String> eligible) {
        var members = new ArrayList<>(eligible.keySet());
        var dids = members.stream().map(eligible::get).toList();
        List<DidResolution> resolutions;
        try {
            resolutions = didResolutionService.resolve(dids);
        } catch (XrdRuntimeException e) {
            log.warn("DID resolution of {} member DIDs failed", dids.size(), e);
            return byMember(members, MemberDidRegistrationOutcome::resolverUnavailable);
        }

        var unresolved = new LinkedHashMap<ClientId, Result>();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            var resolution = i < resolutions.size() ? resolutions.get(i) : null;
            if (resolution == null || !dids.get(i).equals(resolution.did())) {
                unresolved.put(member, MemberDidRegistrationOutcome.resolverUnavailable(member));
            } else if (resolution.outcome() != DidResolutionOutcome.RESOLVED) {
                unresolved.put(member, MemberDidRegistrationOutcome.notResolved(member, resolution));
            }
        }
        return unresolved;
    }

    private static Map<ClientId, Result> byMember(List<ClientId> members, Function<ClientId, Result> answer) {
        var results = new LinkedHashMap<ClientId, Result>();
        members.forEach(member -> results.put(member, answer.apply(member)));
        return results;
    }
}
