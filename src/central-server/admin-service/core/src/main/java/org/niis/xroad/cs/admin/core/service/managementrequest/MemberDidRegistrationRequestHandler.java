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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberDidRegistrationRequestHandler implements RequestHandler<MemberDidRegistrationRequest> {

    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final RequestRepository<MemberDidRegistrationRequestEntity> memberDidRegistrationRequests;
    private final SecurityServerRepository servers;
    private final ParticipantValueStore participantValueStore;
    private final RequestMapper requestMapper;

    @Override
    public boolean canAutoApprove(MemberDidRegistrationRequest request) {
        return false;
    }

    @Override
    public MemberDidRegistrationRequest add(MemberDidRegistrationRequest request) {
        return servers.lockForUpdateBy(request.getSecurityServerId())
                .map(serverRowId -> writeOnceAndRecord(serverRowId, request))
                .orElseGet(() -> request.setResults(
                        answerAll(request.getEntries(), Status.FIXABLE_CONFLICT, SystemDidRegistrationOutcome.OWNER_CHANGED)));
    }

    @Override
    public MemberDidRegistrationRequest approve(MemberDidRegistrationRequest request) {
        return request;
    }

    @Override
    public Class<MemberDidRegistrationRequest> requestType() {
        return MemberDidRegistrationRequest.class;
    }

    private MemberDidRegistrationRequest writeOnceAndRecord(int serverRowId, MemberDidRegistrationRequest request) {
        var results = writeOnce(serverRowId, request);
        final SecurityServerIdEntity serverId = serverIds.findOne(SecurityServerIdEntity.create(request.getSecurityServerId()));
        var saved = memberDidRegistrationRequests.save(new MemberDidRegistrationRequestEntity(request.getOrigin(), serverId));
        return requestMapper.toDto(saved)
                .setEntries(request.getEntries())
                .setResults(results);
    }

    private List<Result> writeOnce(int serverRowId, MemberDidRegistrationRequest request) {
        var settled = request.getSettledResults();
        var unsettled = new LinkedHashMap<ClientId, String>();
        for (int i = 0; i < request.getEntries().size(); i++) {
            if (!settled.containsKey(i)) {
                var entry = request.getEntries().get(i);
                unsettled.put(entry.memberId(), entry.did());
            }
        }
        Map<ClientId, ParticipantValueResult> written = unsettled.isEmpty()
                ? Map.of()
                : participantValueStore.writeMemberDidsOnce(serverRowId, unsettled);

        var results = new ArrayList<Result>(request.getEntries().size());
        for (int i = 0; i < request.getEntries().size(); i++) {
            var entry = request.getEntries().get(i);
            results.add(settled.containsKey(i) ? settled.get(i) : toResult(entry, written.get(entry.memberId())));
        }
        return results;
    }

    private static Result toResult(Entry entry, ParticipantValueResult written) {
        if (written.outcome() == ParticipantValueResult.Outcome.REJECTED) {
            return new Result(entry.memberId(), Status.FIXABLE_CONFLICT, written.rejection().name());
        }
        return new Result(entry.memberId(), Status.STORED, null);
    }

    private static List<Result> answerAll(List<Entry> entries, Status status, String reason) {
        return entries.stream().map(entry -> new Result(entry.memberId(), status, reason)).toList();
    }
}
