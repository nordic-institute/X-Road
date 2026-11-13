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

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.Differences;
import org.niis.xroad.securityserver.restapi.service.HostStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * Logs when a difference between the binding rows and globalconf appears and when it is gone. Every difference is an
 * entry: its kind, followed by the participant when the difference belongs to one. One warning names the entries
 * that appeared, one info line the entries that cleared; each line gives the number of entries and the first
 * {@value #MAX_LOGGED_ENTRIES} of them. Keeps the entries of the last evaluation in memory and logs only the change,
 * so a standing difference is logged once for each run of this Security Server. An evaluation with host status
 * {@code UNKNOWN} carries no comparison and leaves the kept entries as they are.
 */
@Slf4j
@Component
public class DataspaceDifferenceLog {

    private static final int MAX_LOGGED_ENTRIES = 10;

    private Set<String> active = Set.of();

    /**
     * @param differences the signals evaluated by the current check
     */
    public synchronized void record(Differences differences) {
        if (differences.hostStatus() == HostStatus.UNKNOWN) {
            log.debug("Dataspace globalconf differences: registered address not usable, nothing to compare");
            return;
        }
        var current = entries(differences);
        if (current.equals(active)) {
            return;
        }
        var appeared = Sets.difference(current, active);
        if (!appeared.isEmpty()) {
            log.warn("Dataspace globalconf differences appeared, {} new: {}", appeared.size(), firstEntries(appeared));
        }
        var cleared = Sets.difference(active, current);
        if (!cleared.isEmpty()) {
            log.info("Dataspace globalconf differences cleared, {}: {}", cleared.size(), firstEntries(cleared));
        }
        active = current;
    }

    private static Set<String> entries(Differences differences) {
        var entries = new TreeSet<String>();
        differences.hostDiffering().forEach(name -> entries.add("DID host " + name));
        if (differences.systemMismatch().systemDid()) {
            entries.add("SYSTEM DID");
        }
        if (differences.systemMismatch().dspBaseUrl()) {
            entries.add("DSP base URL");
        }
        differences.memberMismatches().forEach(member -> entries.add("member DID " + member.asEncodedId()));
        return entries;
    }

    private static String firstEntries(Set<String> entries) {
        var first = entries.stream().limit(MAX_LOGGED_ENTRIES).toList();
        return first + (entries.size() > first.size() ? " and more" : "");
    }
}
