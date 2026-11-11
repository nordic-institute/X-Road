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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.service.MemberDidCheckService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class MemberDidCheckServiceImpl implements MemberDidCheckService {

    private final XRoadMemberRepository xRoadMemberRepository;
    private final SecurityServerRepository securityServerRepository;
    private final ParticipantValueStore participantValueStore;
    private final DidBatchResolution didBatchResolution;

    @Override
    public DidCheck check(SecurityServerId serverId, ClientId memberId, String memberDid) {
        if (xRoadMemberRepository.count(memberId) == 0) {
            throw new NotFoundException(MEMBER_NOT_FOUND.build());
        }
        var serverRowId = securityServerRepository.findBy(serverId)
                .map(SecurityServerEntity::getId)
                .orElseThrow(() -> new SecurityServerNotFoundException(serverId));

        var verdict = participantValueStore.checkMemberDidReplacements(serverRowId, Map.of(memberId, memberDid)).get(memberId);
        if (verdict.outcome() == ParticipantValueResult.Outcome.REJECTED) {
            return DidCheck.refused(verdict.rejection());
        }
        return didBatchResolution.resolve(List.of(memberDid)).getFirst().toDidCheck();
    }
}
