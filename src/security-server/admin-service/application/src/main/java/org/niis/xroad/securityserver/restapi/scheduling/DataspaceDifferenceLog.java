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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.Differences;
import org.niis.xroad.securityserver.restapi.service.HostStatus;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Logs when a difference between the binding rows and globalconf appears and when it is gone: a warning for each
 * binding whose DID host is not the registered address, the SYSTEM DID, the DSP base URL and each member DID that
 * differ from the ones globalconf carries, an info line for each that is no longer reported. Keeps the differences
 * of the last evaluation in memory and logs only the change, so a standing difference is logged once for each run
 * of this Security Server. An evaluation with host status {@code UNKNOWN} carries no comparison and leaves the kept
 * differences as they are.
 */
@Slf4j
@Component
public class DataspaceDifferenceLog {

    private static final String BOUND_ONE = "the bound one";
    private static final Comparator<Difference> ORDER = Comparator.comparing(Difference::subject)
            .thenComparing(Difference::comparedWith);

    private Set<Difference> active = Set.of();

    /**
     * @param differences the signals evaluated by the current check
     */
    public synchronized void record(Differences differences) {
        if (differences.hostStatus() == HostStatus.UNKNOWN) {
            log.debug("Dataspace globalconf differences: registered address not usable, nothing to compare");
            return;
        }
        var current = describe(differences);
        current.stream().filter(difference -> !active.contains(difference)).sorted(ORDER)
                .forEach(difference -> log.warn("Dataspace globalconf difference: {} differs from {}",
                        difference.subject(), difference.comparedWith()));
        active.stream().filter(difference -> !current.contains(difference)).sorted(ORDER)
                .forEach(difference -> log.info(
                        "Dataspace globalconf difference: {} is no longer reported as differing from {}",
                        difference.subject(), difference.comparedWith()));
        active = current;
    }

    private static Set<Difference> describe(Differences differences) {
        var described = new HashSet<Difference>();
        differences.hostDiffering().forEach(row -> described.add(
                new Difference("the DID host of " + bindingName(row), "the registered address")));
        if (differences.systemMismatch().systemDid()) {
            described.add(new Difference("the SYSTEM DID carried by globalconf", BOUND_ONE));
        }
        if (differences.systemMismatch().dspBaseUrl()) {
            described.add(new Difference("the DSP base URL carried by globalconf", "the built one"));
        }
        differences.memberMismatches().forEach(member -> described.add(
                new Difference("the member DID carried by globalconf for " + member.asEncodedId(), BOUND_ONE)));
        return described;
    }

    private static String bindingName(DsParticipantEntity row) {
        return row.getParticipantType() == ParticipantType.SYSTEM
                ? "the SYSTEM binding"
                : "the binding of member " + row.getMemberIdentifier().asEncodedId();
    }

    private record Difference(String subject, String comparedWith) {
    }
}
