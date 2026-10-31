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
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult.Outcome;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DID_ENTRIES;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DID_ENTRY_COUNT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DID_ENTRY_REASON;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DID_ENTRY_STATUS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.INSTANCE_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

/**
 * Locks the Security Server row by its identifier, which also settles that the server still exists under the
 * requesting owner, and keeps the lock until commit. A server missing at the pre-transaction checks is already
 * answered as owner changed for every candidate there; a server the lock does not find because it went missing
 * after them is answered as owner changed for every entry, settled or not, and no DID is stored. Everything
 * after the lock sees the server as it is at that moment.
 *
 * <p>Every call that gets this far stores one request row holding the entries as received and the result of
 * each, whatever the results are, so every answer carries the request id.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MemberDidRegistrationRequestHandler implements RequestHandler<MemberDidRegistrationRequest> {

    private final IdentifierRepository<SecurityServerIdEntity> serverIds;
    private final RequestRepository<MemberDidRegistrationRequestEntity> memberDidRegistrationRequests;
    private final SecurityServerRepository servers;
    private final ParticipantValueStore participantValueStore;
    private final RequestMapper requestMapper;
    private final AuditDataHelper auditData;

    @Override
    public boolean canAutoApprove(MemberDidRegistrationRequest request) {
        return false;
    }

    @Override
    public MemberDidRegistrationRequest add(MemberDidRegistrationRequest request) {
        var results = servers.lockForUpdateBy(request.getSecurityServerId())
                .map(serverRowId -> writeOnce(serverRowId, request))
                .orElseGet(() -> ownerChanged(request.getEntries()));
        var added = storeRequest(request, results);
        audit(request, results);
        return added;
    }

    @Override
    public MemberDidRegistrationRequest approve(MemberDidRegistrationRequest request) {
        return request;
    }

    @Override
    public Class<MemberDidRegistrationRequest> requestType() {
        return MemberDidRegistrationRequest.class;
    }

    private MemberDidRegistrationRequest storeRequest(MemberDidRegistrationRequest request, List<Result> results) {
        var serverId = serverIds.findOrCreate(SecurityServerIdEntity.create(request.getSecurityServerId()));
        var saved = memberDidRegistrationRequests.save(
                new MemberDidRegistrationRequestEntity(request.getOrigin(), serverId, request.getEntries(), results));
        return requestMapper.toDto(saved)
                .setEntries(request.getEntries())
                .setResults(results);
    }

    private List<Result> writeOnce(int serverRowId, MemberDidRegistrationRequest request) {
        return resultsOf(serverRowId, request, storeUnsettled(serverRowId, request));
    }

    private Map<ClientId, ParticipantValueResult> storeUnsettled(int serverRowId, MemberDidRegistrationRequest request) {
        var settled = request.getSettledResults();
        var unsettled = request.getEntries().stream()
                .filter(entry -> !settled.containsKey(entry.memberId()))
                .collect(Collectors.toMap(Entry::memberId, Entry::did, (first, second) -> first, LinkedHashMap::new));
        return unsettled.isEmpty() ? Map.of() : participantValueStore.writeMemberDidsOnce(serverRowId, unsettled);
    }

    private List<Result> resultsOf(int serverRowId, MemberDidRegistrationRequest request, Map<ClientId, ParticipantValueResult> written) {
        var settled = request.getSettledResults();
        var registeredAddress = hasRejection(written) ? registeredAddressOf(serverRowId) : null;
        return request.getEntries().stream()
                .map(entry -> Optional.ofNullable(settled.get(entry.memberId()))
                        .orElseGet(() -> toResult(entry, written.get(entry.memberId()), registeredAddress)))
                .toList();
    }

    private static boolean hasRejection(Map<ClientId, ParticipantValueResult> written) {
        return written.values().stream().anyMatch(result -> result.outcome() == Outcome.REJECTED);
    }

    private String registeredAddressOf(int serverRowId) {
        return servers.findById(serverRowId).map(SecurityServerEntity::getAddress).orElse(null);
    }

    private static Result toResult(Entry entry, ParticipantValueResult written, String registeredAddress) {
        if (written.outcome() == Outcome.REJECTED) {
            return MemberDidRegistrationOutcome.rejected(entry.memberId(), written.rejection(), registeredAddress);
        }
        return MemberDidRegistrationOutcome.stored(entry.memberId());
    }

    private static List<Result> ownerChanged(List<Entry> entries) {
        return entries.stream().map(entry -> MemberDidRegistrationOutcome.ownerChanged(entry.memberId())).toList();
    }

    private void audit(MemberDidRegistrationRequest request, List<Result> results) {
        var server = request.getSecurityServerId();
        auditData.put(INSTANCE_IDENTIFIER, server.getXRoadInstance());
        auditData.put(OWNER_CLASS, server.getMemberClass());
        auditData.put(OWNER_CODE, server.getMemberCode());
        auditData.put(SERVER_CODE, server.getServerCode());
        auditData.put(DID_ENTRY_COUNT, results.size());
        for (var result : results) {
            var item = new LinkedHashMap<RestApiAuditProperty, Object>();
            item.put(MEMBER_CLASS, result.memberId().getMemberClass());
            item.put(MEMBER_CODE, result.memberId().getMemberCode());
            item.put(DID_ENTRY_STATUS, result.status());
            Optional.ofNullable(result.reason()).ifPresent(reason -> item.put(DID_ENTRY_REASON, reason));
            auditData.addListPropertyItem(DID_ENTRIES, item);
        }
    }
}
