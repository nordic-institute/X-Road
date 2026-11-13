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

import ee.ria.xroad.common.identifier.ClientId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.Differences;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.SystemMismatch;
import org.niis.xroad.securityserver.restapi.service.HostStatus;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DataspaceDifferenceLogTest {

    private static final String HOST_MEMBER = "DEV:COM:HOST";

    private final DataspaceDifferenceLog differenceLog = new DataspaceDifferenceLog();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DataspaceDifferenceLog.class);

    @BeforeEach
    void captureLog() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    void manyAppearedEntriesAreOneWarningWithTheCountAndTheFirstTen() {
        differenceLog.record(differences(Set.of(), true, true, 12));

        assertThat(lines(Level.WARN)).containsExactly(
                "Dataspace globalconf differences appeared, 14 new: [DSP base URL, SYSTEM DID, %s] and more"
                        .formatted(String.join(", ", memberEntries(8))));
        assertThat(lines(Level.INFO)).isEmpty();
    }

    @Test
    void everyKindOfEntryCanBeInTheSameLine() {
        differenceLog.record(differences(Set.of(HOST_MEMBER), true, true, 1));

        assertThat(lines(Level.WARN)).containsExactly(
                "Dataspace globalconf differences appeared, 4 new: [DID host DEV:COM:HOST, DSP base URL, SYSTEM DID,"
                        + " member DID DEV:COM:M00]");
    }

    @Test
    void onlyTheSystemDidFlagIsASystemDidEntry() {
        differenceLog.record(differences(Set.of(), true, false, 0));

        assertThat(lines(Level.WARN)).containsExactly("Dataspace globalconf differences appeared, 1 new: [SYSTEM DID]");
    }

    @Test
    void onlyTheDspBaseUrlFlagIsADspBaseUrlEntry() {
        differenceLog.record(differences(Set.of(), false, true, 0));

        assertThat(lines(Level.WARN)).containsExactly(
                "Dataspace globalconf differences appeared, 1 new: [DSP base URL]");
    }

    @Test
    void aSystemBindingAtAnotherHostIsADidHostEntryNamedSystem() {
        differenceLog.record(differences(Set.of("SYSTEM"), false, false, 0));

        assertThat(lines(Level.WARN)).containsExactly(
                "Dataspace globalconf differences appeared, 1 new: [DID host SYSTEM]");
    }

    @Test
    void manyClearedEntriesAreOneInfoLineWithTheCountAndTheFirstTen() {
        differenceLog.record(differences(Set.of(), true, true, 12));
        appender.list.clear();

        differenceLog.record(differences(Set.of(), false, false, 0));

        assertThat(lines(Level.INFO)).containsExactly(
                "Dataspace globalconf differences cleared, 14: [DSP base URL, SYSTEM DID, %s] and more"
                        .formatted(String.join(", ", memberEntries(8))));
        assertThat(lines(Level.WARN)).isEmpty();
    }

    @Test
    void aClearedDidHostEntryIsNamedInTheInfoLine() {
        differenceLog.record(differences(Set.of("SYSTEM"), false, false, 0));
        appender.list.clear();

        differenceLog.record(differences(Set.of(), false, false, 0));

        assertThat(lines(Level.INFO)).containsExactly("Dataspace globalconf differences cleared, 1: [DID host SYSTEM]");
    }

    @Test
    void aCheckWithAppearedAndClearedEntriesLogsOneLineOfEach() {
        differenceLog.record(differences(Set.of(), true, false, 1));
        appender.list.clear();

        differenceLog.record(differences(Set.of(), false, true, 1));

        assertThat(lines(Level.WARN)).containsExactly(
                "Dataspace globalconf differences appeared, 1 new: [DSP base URL]");
        assertThat(lines(Level.INFO)).containsExactly(
                "Dataspace globalconf differences cleared, 1: [SYSTEM DID]");
    }

    @Test
    void anUnchangedSetLogsNothing() {
        differenceLog.record(differences(Set.of(HOST_MEMBER), true, true, 12));
        appender.list.clear();

        differenceLog.record(differences(Set.of(HOST_MEMBER), true, true, 12));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void anEvaluationWithAnUnusableAddressLogsNothingAboveDebugAndKeepsThePreviousSet() {
        differenceLog.record(differences(Set.of(), false, false, 2));
        appender.list.clear();

        differenceLog.record(Differences.UNKNOWN);
        differenceLog.record(differences(Set.of(), false, false, 2));

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsOnly(Level.DEBUG);
    }

    private static Differences differences(Set<String> hostDiffering, boolean systemDid, boolean dspBaseUrl,
            int memberCount) {
        var members = IntStream.range(0, memberCount).mapToObj(DataspaceDifferenceLogTest::member)
                .collect(Collectors.toUnmodifiableSet());
        return new Differences(HostStatus.DIFFERS, new SystemMismatch(systemDid, dspBaseUrl), members, hostDiffering);
    }

    private static ClientId member(int index) {
        return ClientId.Conf.create("DEV", "COM", "M%02d".formatted(index));
    }

    private static List<String> memberEntries(int count) {
        return IntStream.range(0, count).mapToObj(index -> "member DID " + member(index).asEncodedId()).toList();
    }

    private List<String> lines(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }
}
