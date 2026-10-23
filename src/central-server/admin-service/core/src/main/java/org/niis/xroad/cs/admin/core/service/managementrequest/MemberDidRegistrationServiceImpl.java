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
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.MemberDidRegistrationService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_DUPLICATE_MEMBER;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberDidRegistrationServiceImpl implements MemberDidRegistrationService {

    static final String RESOLVER_UNAVAILABLE = "RESOLVER_UNAVAILABLE";

    private final DidResolutionService didResolutionService;
    private final ManagementRequestService managementRequestService;

    @Override
    public MemberDidRegistrationRequest add(MemberDidRegistrationRequest request) {
        requireDistinctMembers(request.getEntries());
        request.setSettledResults(unresolvedResults(request.getEntries()));
        return managementRequestService.add(request);
    }

    private static void requireDistinctMembers(List<Entry> entries) {
        var members = new HashSet<ClientId>();
        for (var entry : entries) {
            if (!members.add(entry.memberId())) {
                throw new BadRequestException(MR_DID_DUPLICATE_MEMBER.build(entry.memberId().toString()));
            }
        }
    }

    private Map<Integer, Result> unresolvedResults(List<Entry> entries) {
        var dids = entries.stream().map(Entry::did).toList();
        List<DidResolution> resolutions;
        try {
            resolutions = didResolutionService.resolve(dids);
        } catch (XrdRuntimeException e) {
            log.warn("DID resolution of {} member DIDs failed", dids.size(), e);
            return allNotResolved(entries);
        }

        var settled = new LinkedHashMap<Integer, Result>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var resolution = i < resolutions.size() ? resolutions.get(i) : null;
            if (resolution == null || !entry.did().equals(resolution.did())) {
                settled.put(i, notResolved(entry, RESOLVER_UNAVAILABLE));
            } else if (resolution.outcome() != DidResolutionOutcome.RESOLVED) {
                var reason = resolution.reason() == null ? RESOLVER_UNAVAILABLE : resolution.reason().name();
                settled.put(i, notResolved(entry, reason));
            }
        }
        return settled;
    }

    private static Map<Integer, Result> allNotResolved(List<Entry> entries) {
        var settled = new LinkedHashMap<Integer, Result>();
        for (int i = 0; i < entries.size(); i++) {
            settled.put(i, notResolved(entries.get(i), RESOLVER_UNAVAILABLE));
        }
        return settled;
    }

    private static Result notResolved(Entry entry, String reason) {
        return new Result(entry.memberId(), Status.NOT_RESOLVED, reason);
    }
}
