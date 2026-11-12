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
package org.niis.xroad.securityserver.restapi.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.Differences;
import org.niis.xroad.securityserver.restapi.service.HostStatus;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Logs when a difference between the binding rows and globalconf appears and when it is gone. There is one line for
 * each kind of difference: the DID host of the bindings, the SYSTEM DID, the DSP base URL and the member DIDs. A
 * warning names the differences that appeared, an info line the ones that cleared. A line that lists participants
 * gives the number that changed, the number now in total for that kind and the first {@value #MAX_LOGGED_IDS}
 * identifiers; when more than that changed, the full list of changed participants goes to debug. Keeps the
 * differences of the last evaluation in memory and logs only the change, so a standing difference is logged once for
 * each run of this Security Server. An evaluation with host status {@code UNKNOWN} carries no comparison and leaves
 * the kept differences as they are.
 */
@Slf4j
@Component
public class DataspaceDifferenceLog {

    static final int MAX_LOGGED_IDS = 10;

    private static final String SYSTEM_ID = "SYSTEM";

    private Map<Kind, Set<String>> active = new EnumMap<>(Kind.class);

    /**
     * @param differences the signals evaluated by the current check
     */
    public synchronized void record(Differences differences) {
        if (differences.hostStatus() == HostStatus.UNKNOWN) {
            log.debug("Dataspace globalconf differences: registered address not usable, nothing to compare");
            return;
        }
        var current = describe(differences);
        for (var kind : Kind.values()) {
            var now = current.getOrDefault(kind, Set.of());
            var before = active.getOrDefault(kind, Set.of());
            logChange(kind, difference(now, before), now.size(), true);
            logChange(kind, difference(before, now), now.size(), false);
        }
        active = current;
    }

    private static Map<Kind, Set<String>> describe(Differences differences) {
        var described = new EnumMap<Kind, Set<String>>(Kind.class);
        differences.hostDiffering().forEach(row -> add(described, Kind.HOST_DID, bindingName(row)));
        if (differences.systemMismatch().systemDid()) {
            add(described, Kind.SYSTEM_DID, SYSTEM_ID);
        }
        if (differences.systemMismatch().dspBaseUrl()) {
            add(described, Kind.DSP_BASE_URL, SYSTEM_ID);
        }
        differences.memberMismatches().forEach(member -> add(described, Kind.MEMBER_DID, member.asEncodedId()));
        return described;
    }

    private static void add(Map<Kind, Set<String>> described, Kind kind, String id) {
        described.computeIfAbsent(kind, _ -> new TreeSet<>()).add(id);
    }

    private static Set<String> difference(Set<String> from, Collection<String> without) {
        var result = new TreeSet<>(from);
        result.removeAll(without);
        return result;
    }

    private static void logChange(Kind kind, Set<String> ids, int total, boolean appeared) {
        if (ids.isEmpty()) {
            return;
        }
        var detail = kind.listsIds
                ? ", %d %s, %d in total: %s".formatted(ids.size(), appeared ? "new" : "cleared", total, firstIds(ids))
                : "";
        if (appeared) {
            log.warn("Dataspace globalconf difference appeared: {}{}", kind.subject, detail);
        } else {
            log.info("Dataspace globalconf difference cleared: {}{}", kind.subject, detail);
        }
        if (kind.listsIds && ids.size() > MAX_LOGGED_IDS) {
            log.debug("Dataspace globalconf difference {}: {}, all changed participants: {}",
                    appeared ? "appeared" : "cleared", kind.subject, ids);
        }
    }

    private static String firstIds(Set<String> ids) {
        var first = ids.stream().limit(MAX_LOGGED_IDS).toList();
        return first + (ids.size() > first.size() ? " and more" : "");
    }

    private static String bindingName(DsParticipantEntity row) {
        return row.getParticipantType() == ParticipantType.SYSTEM
                ? SYSTEM_ID
                : row.getMemberIdentifier().asEncodedId();
    }

    @RequiredArgsConstructor
    private enum Kind {
        HOST_DID("DID host", true),
        SYSTEM_DID("SYSTEM DID", false),
        DSP_BASE_URL("DSP base URL", false),
        MEMBER_DID("member DID", true);

        private final String subject;
        private final boolean listsIds;
    }
}
