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
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.Differences;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals.SystemMismatch;
import org.niis.xroad.securityserver.restapi.service.HostStatus;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DataspaceDifferenceLogTest {

    private static final int MEMBER_COUNT = 12;

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
    void manyMemberDidDifferencesAreOneWarningWithTheChangeTheTotalAndTheFirstTenIds() {
        differenceLog.record(memberMismatches(MEMBER_COUNT));

        var warnings = lines(Level.WARN);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).startsWith("Dataspace globalconf difference appeared: member DID, ")
                .contains("12 new, 12 in total").endsWith("and more");
        assertThat(idsIn(warnings.getFirst())).containsExactlyElementsOf(memberIds(10));
    }

    @Test
    void moreThanTenChangedIdsPutTheFullListOnOneDebugLine() {
        differenceLog.record(memberMismatches(MEMBER_COUNT));

        var debug = lines(Level.DEBUG);
        assertThat(debug).hasSize(1);
        assertThat(debug.getFirst()).contains("changed participants");
        assertThat(idsIn(debug.getFirst())).containsExactlyElementsOf(memberIds(MEMBER_COUNT));
    }

    @Test
    void tenOrFewerChangedIdsWriteNoDebugLine() {
        differenceLog.record(memberMismatches(DataspaceDifferenceLog.MAX_LOGGED_IDS));
        differenceLog.record(memberMismatches(0));

        assertThat(lines(Level.DEBUG)).isEmpty();
        assertThat(lines(Level.WARN)).hasSize(1);
        assertThat(lines(Level.INFO)).hasSize(1);
    }

    @Test
    void everyKindOfDifferenceIsOneWarningAndTheNonListingKindsCarryOnlyTheSubject() {
        differenceLog.record(new Differences(HostStatus.DIFFERS, new SystemMismatch(true, true),
                memberSet(MEMBER_COUNT), hostRows(MEMBER_COUNT)));

        var warnings = lines(Level.WARN);
        assertThat(warnings).containsExactlyInAnyOrder(
                "Dataspace globalconf difference appeared: SYSTEM DID",
                "Dataspace globalconf difference appeared: DSP base URL",
                warningOf("DID host"),
                warningOf("member DID"));
    }

    @Test
    void aClearedKindIsOneInfoLineWithTheClearedCountTheRemainingTotalAndTheFirstTenIds() {
        differenceLog.record(memberMismatches(MEMBER_COUNT));
        appender.list.clear();

        differenceLog.record(memberMismatches(0));

        var infos = lines(Level.INFO);
        assertThat(infos).hasSize(1);
        assertThat(infos.getFirst()).startsWith("Dataspace globalconf difference cleared: member DID, ")
                .contains("12 cleared, 0 in total").endsWith("and more").doesNotContain("differs");
        assertThat(idsIn(infos.getFirst())).containsExactlyElementsOf(memberIds(10));
        assertThat(lines(Level.WARN)).isEmpty();
    }

    @Test
    void aPartialClearReportsWhatRemains() {
        differenceLog.record(memberMismatches(13));
        appender.list.clear();

        differenceLog.record(memberMismatches(11));

        assertThat(lines(Level.INFO)).containsExactly(
                "Dataspace globalconf difference cleared: member DID, 2 cleared, 11 in total: [%s, %s]"
                        .formatted(memberId(11), memberId(12)));
    }

    @Test
    void clearedNonListingKindsCarryOnlyTheSubject() {
        differenceLog.record(new Differences(HostStatus.OK, new SystemMismatch(true, true), Set.of(), List.of()));
        appender.list.clear();

        differenceLog.record(memberMismatches(0));

        assertThat(lines(Level.INFO)).containsExactlyInAnyOrder(
                "Dataspace globalconf difference cleared: SYSTEM DID",
                "Dataspace globalconf difference cleared: DSP base URL");
    }

    @Test
    void anUnchangedSetLogsNothing() {
        differenceLog.record(memberMismatches(MEMBER_COUNT));
        appender.list.clear();

        differenceLog.record(memberMismatches(MEMBER_COUNT));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void onlyTheDifferencesThatAppearedAreNamed() {
        differenceLog.record(memberMismatches(2));
        appender.list.clear();

        differenceLog.record(memberMismatches(3));

        var warnings = lines(Level.WARN);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("member DID, 1 new, 3 in total: [");
        assertThat(idsIn(warnings.getFirst())).containsExactly(memberId(2));
    }

    @Test
    void anEvaluationWithAnUnusableAddressLeavesTheKeptDifferencesAsTheyAre() {
        differenceLog.record(memberMismatches(2));
        differenceLog.record(new Differences(HostStatus.UNKNOWN, new SystemMismatch(false, false), Set.of(), List.of()));
        appender.list.clear();

        differenceLog.record(memberMismatches(2));

        assertThat(appender.list).isEmpty();
    }

    private static String warningOf(String subject) {
        return "Dataspace globalconf difference appeared: %s, 12 new, 12 in total: %s and more"
                .formatted(subject, memberIds(10));
    }

    private static Differences memberMismatches(int count) {
        return new Differences(HostStatus.OK, new SystemMismatch(false, false), memberSet(count), List.of());
    }

    private static Set<ClientId> memberSet(int count) {
        return IntStream.range(0, count).mapToObj(DataspaceDifferenceLogTest::member)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<DsParticipantEntity> hostRows(int count) {
        return IntStream.range(0, count).mapToObj(index -> {
            var row = new DsParticipantEntity();
            row.setParticipantType(ParticipantType.MEMBER);
            row.setMemberIdentifier(ClientIdEntityFactory.create(member(index)));
            return row;
        }).toList();
    }

    private static ClientId member(int index) {
        return ClientId.Conf.create("DEV", "COM", memberCode(index));
    }

    private static String memberCode(int index) {
        return "M%02d".formatted(index);
    }

    private static String memberId(int index) {
        return member(index).asEncodedId();
    }

    private static List<String> memberIds(int count) {
        return IntStream.range(0, count).mapToObj(DataspaceDifferenceLogTest::memberId).toList();
    }

    private List<String> lines(Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static List<String> idsIn(String line) {
        var start = line.indexOf('[');
        var end = line.indexOf(']');
        return List.of(line.substring(start + 1, end).split(", "));
    }
}
