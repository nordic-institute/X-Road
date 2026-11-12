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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DifferenceSignalsTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final SecurityServerId SERVER = SecurityServerId.Conf.create(MEMBER, "SS0");
    private static final String ADDRESS = "ss0.example.org";
    private static final String BOUND = memberDid("ss0.example.org:7183");
    private static final String OTHER_SAME_HOST = memberDid("ss0.example.org:8443");
    private static final String OTHER_HOST = memberDid("ss1.example.org:7183");

    private static final String SYSTEM_DID = systemDid("ss0.example.org:7183");
    private static final String OTHER_SYSTEM_DID = systemDid("ss0.example.org:8443");
    private static final String BUILT_URL = "https://ss0.example.org:7181/dsp";
    private static final String OTHER_PORT_URL = "https://ss0.example.org:9999/dsp";

    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aSentMemberWhoseCarriedDidDiffersAndIsNotStaleMismatches(PublicationState state) {
        assertThat(signals(ADDRESS, OTHER_SAME_HOST).memberMismatch(row(state, BOUND))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"}, mode = EnumSource.Mode.EXCLUDE)
    void aMemberThatIsNotSentNeverMismatches(PublicationState state) {
        assertThat(signals(ADDRESS, OTHER_SAME_HOST).memberMismatch(row(state, BOUND))).isFalse();
    }

    @Test
    void aMemberWithoutPublicationStateNeverMismatches() {
        assertThat(signals(ADDRESS, OTHER_SAME_HOST).memberMismatch(row(null, BOUND))).isFalse();
    }

    @Test
    void anEqualOrAbsentCarriedDidDoesNotMismatch() {
        var row = row(PublicationState.PUBLISHED, BOUND);

        assertThat(signals(ADDRESS, BOUND).memberMismatch(row)).isFalse();
        assertThat(signals(ADDRESS, null).memberMismatch(row)).isFalse();
    }

    @Test
    void aStaleCarriedDidDoesNotMismatch() {
        assertThat(signals(ADDRESS, OTHER_HOST).memberMismatch(row(PublicationState.PUBLISHED, BOUND))).isFalse();
    }

    @Test
    void anUnknownRegisteredAddressMakesEveryDifferingDidStale() {
        var row = row(PublicationState.PUBLISHED, BOUND);
        var signals = signals(null, OTHER_SAME_HOST);

        assertThat(signals.memberMismatch(row)).isFalse();
        assertThat(signals.compareMemberDid(row)).isEqualTo(GlobalConfComparison.STALE);
    }

    @Test
    void compareMemberDidClassifiesTheCarriedDid() {
        var row = row(PublicationState.NOT_SENT, BOUND);

        assertThat(signals(ADDRESS, null).compareMemberDid(row)).isEqualTo(GlobalConfComparison.ABSENT);
        assertThat(signals(ADDRESS, BOUND).compareMemberDid(row)).isEqualTo(GlobalConfComparison.EQUAL);
        assertThat(signals(ADDRESS, OTHER_SAME_HOST).compareMemberDid(row)).isEqualTo(GlobalConfComparison.DIFFERENT);
        assertThat(signals(ADDRESS, OTHER_HOST).compareMemberDid(row)).isEqualTo(GlobalConfComparison.STALE);
    }

    @Test
    void compareSystemValuesComparesTheCarriedPairWithTheBoundDidAndTheBuiltUrl() {
        var row = systemRow(PublicationState.PUBLISHED);

        assertThat(systemSignals(ADDRESS, null).compareSystemValues(row, BUILT_URL))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.ABSENT, GlobalConfComparison.ABSENT));
        assertThat(systemSignals(ADDRESS, new ServerSystemValues(SYSTEM_DID, BUILT_URL))
                .compareSystemValues(row, BUILT_URL))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.EQUAL, GlobalConfComparison.EQUAL));
        assertThat(systemSignals(ADDRESS, new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL))
                .compareSystemValues(row, BUILT_URL))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.DIFFERENT, GlobalConfComparison.DIFFERENT));
    }

    @Test
    void theSystemPairIsStaleWhenEitherHostDiffers() {
        var row = systemRow(PublicationState.PUBLISHED);
        var otherHostDid = new ServerSystemValues(systemDid("ss1.example.org:7183"), OTHER_PORT_URL);
        var otherHostUrl = new ServerSystemValues(OTHER_SYSTEM_DID, "https://ss1.example.org:7181/dsp");

        assertThat(systemSignals(ADDRESS, otherHostDid).compareSystemValues(row, BUILT_URL))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.STALE, GlobalConfComparison.STALE));
        assertThat(systemSignals(ADDRESS, otherHostUrl).compareSystemValues(row, BUILT_URL))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.STALE, GlobalConfComparison.STALE));
    }

    @Test
    void anUnknownRegisteredAddressMakesTheSystemPairStale() {
        var row = systemRow(PublicationState.PUBLISHED);

        assertThat(systemSignals(null, new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL))
                .compareSystemValues(row, null))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.STALE, GlobalConfComparison.STALE));
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aSentSystemWhoseCarriedValuesDifferAndAreNotStaleMismatchesOnBoth(PublicationState state) {
        var carried = new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL);

        assertThat(systemSignals(ADDRESS, carried).systemMismatch(systemRow(state), () -> BUILT_URL))
                .isEqualTo(new DifferenceSignals.SystemMismatch(true, true));
    }

    @Test
    void aSentSystemMismatchesOnlyOnTheValueThatDiffers() {
        var row = systemRow(PublicationState.PUBLISHED);

        assertThat(systemSignals(ADDRESS, new ServerSystemValues(OTHER_SYSTEM_DID, BUILT_URL))
                .systemMismatch(row, () -> BUILT_URL)).isEqualTo(new DifferenceSignals.SystemMismatch(true, false));
        assertThat(systemSignals(ADDRESS, new ServerSystemValues(SYSTEM_DID, OTHER_PORT_URL))
                .systemMismatch(row, () -> BUILT_URL)).isEqualTo(new DifferenceSignals.SystemMismatch(false, true));
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"}, mode = EnumSource.Mode.EXCLUDE)
    void aSystemThatIsNotSentNeverMismatches(PublicationState state) {
        var carried = new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL);

        assertThat(systemSignals(ADDRESS, carried).systemMismatch(systemRow(state), () -> BUILT_URL))
                .isEqualTo(new DifferenceSignals.SystemMismatch(false, false));
    }

    @Test
    void aSystemWithoutPublicationStateNeverMismatches() {
        var carried = new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL);

        assertThat(systemSignals(ADDRESS, carried).systemMismatch(systemRow(null), () -> BUILT_URL))
                .isEqualTo(new DifferenceSignals.SystemMismatch(false, false));
    }

    @Test
    void aStaleSystemPairDoesNotMismatch() {
        var carried = new ServerSystemValues(systemDid("ss1.example.org:7183"), OTHER_PORT_URL);

        assertThat(systemSignals(ADDRESS, carried).systemMismatch(systemRow(PublicationState.PUBLISHED), () -> BUILT_URL))
                .isEqualTo(new DifferenceSignals.SystemMismatch(false, false));
    }

    @Test
    void hostStatusIsOkWhenEveryActiveBindingIsAtTheRegisteredHost() {
        var members = List.of(row(PublicationState.PUBLISHED, BOUND), row(PublicationState.NOT_SENT, OTHER_SAME_HOST));

        var differences = signals(ADDRESS, null).evaluate(systemRow(PublicationState.PUBLISHED), members,
                () -> null);

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.OK);
        assertThat(differences.hostDiffering()).isEmpty();
    }

    @Test
    void hostStatusIsOkWithoutBindings() {
        assertThat(signals(ADDRESS, null).evaluate(null, List.of(), () -> null).hostStatus())
                .isEqualTo(HostStatus.OK);
    }

    @Test
    void hostStatusDiffersWhenAnActiveMemberBindingIsAtAnotherHost() {
        var atOtherHost = row(PublicationState.NOT_SENT, OTHER_HOST);

        var differences = signals(ADDRESS, null)
                .evaluate(null, List.of(row(PublicationState.PUBLISHED, BOUND), atOtherHost), () -> null);

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.DIFFERS);
        assertThat(differences.hostDiffering()).containsExactly(atOtherHost);
    }

    @Test
    void hostStatusDiffersWhenTheSystemBindingIsAtAnotherHost() {
        var system = systemRow(PublicationState.PUBLISHED);
        system.setDid(systemDid("ss1.example.org:7183"));

        var differences = signals(ADDRESS, null)
                .evaluate(system, List.of(row(PublicationState.PUBLISHED, BOUND)), () -> null);

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.DIFFERS);
        assertThat(differences.hostDiffering()).containsExactly(system);
    }

    @ParameterizedTest
    @ValueSource(strings = {"b\u00fccher.example", "bad host", " "})
    @NullAndEmptySource
    void hostStatusIsUnknownWhenTheRegisteredAddressCannotBeUsed(String registeredAddress) {
        var system = systemRow(PublicationState.PUBLISHED);
        var members = List.of(row(PublicationState.PUBLISHED, OTHER_HOST));
        when(globalConfProvider.getServerSystemValues(SERVER))
                .thenReturn(Optional.of(new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL)));
        Supplier<String> failingUrl = () -> {
            throw new AssertionError("the DSP base URL must not be asked for");
        };
        var signals = signals(registeredAddress, OTHER_SAME_HOST);

        var differences = signals.evaluate(system, members, failingUrl);
        var withoutBindings = signals.evaluate(null, List.of(), failingUrl);

        assertThat(differences).isEqualTo(DifferenceSignals.Differences.UNKNOWN);
        assertThat(withoutBindings).isEqualTo(DifferenceSignals.Differences.UNKNOWN);
    }

    @Test
    void theDspBaseUrlIsAskedForOnlyWhenTheSystemRowIsSent() {
        var asked = new AtomicInteger();
        Supplier<String> dspBaseUrl = () -> {
            asked.incrementAndGet();
            return BUILT_URL;
        };
        var signals = signals(ADDRESS, null);

        signals.evaluate(null, List.of(), dspBaseUrl);
        signals.evaluate(systemRow(PublicationState.NOT_SENT), List.of(), dspBaseUrl);
        assertThat(asked).hasValue(0);

        signals.evaluate(systemRow(PublicationState.SUBMITTED), List.of(), dspBaseUrl);
        assertThat(asked).hasValue(1);
    }

    @Test
    void evaluateCombinesEverySignalFromOneSetOfRows() {
        var system = systemRow(PublicationState.PUBLISHED);
        var mismatching = row(PublicationState.PUBLISHED, BOUND);
        var atOtherHost = row(PublicationState.NOT_SENT, OTHER_HOST);
        atOtherHost.setMemberIdentifier(ClientIdEntityFactory.create(ClientId.Conf.create("DEV", "COM", "333")));
        when(globalConfProvider.getServerSystemValues(SERVER))
                .thenReturn(Optional.of(new ServerSystemValues(OTHER_SYSTEM_DID, BUILT_URL)));
        var signals = signals(ADDRESS, OTHER_SAME_HOST);

        var differences = signals.evaluate(system, List.of(mismatching, atOtherHost), () -> BUILT_URL);

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.DIFFERS);
        assertThat(differences.systemMismatch()).isEqualTo(new DifferenceSignals.SystemMismatch(true, false));
        assertThat(differences.memberMismatches()).containsExactly(MEMBER);
        assertThat(differences.hostDiffering()).containsExactly(atOtherHost);
    }

    @Test
    void evaluateWithoutASystemRowHasNoSystemMismatch() {
        var differences = signals(ADDRESS, null).evaluate(null, List.of(row(PublicationState.PUBLISHED, BOUND)),
                () -> BUILT_URL);

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.OK);
        assertThat(differences.systemMismatch()).isEqualTo(new DifferenceSignals.SystemMismatch(false, false));
        assertThat(differences.memberMismatches()).isEmpty();
        assertThat(differences.hostDiffering()).isEmpty();
    }

    private DifferenceSignals systemSignals(String registeredAddress, ServerSystemValues carried) {
        when(globalConfProvider.getServerSystemValues(SERVER)).thenReturn(Optional.ofNullable(carried));
        return DifferenceSignals.of(SERVER, globalConfProvider, registeredAddress);
    }

    private static DsParticipantEntity systemRow(PublicationState state) {
        var row = new DsParticipantEntity();
        row.setParticipantType(ParticipantType.SYSTEM);
        row.setDid(SYSTEM_DID);
        row.setState(ParticipantState.ACTIVE);
        row.setPublicationState(state);
        return row;
    }

    private static String systemDid(String authority) {
        return ParticipantIdentifierScheme.systemDid(authority).toString();
    }

    private DifferenceSignals signals(String registeredAddress, String carriedDid) {
        when(globalConfProvider.getMemberDid(MEMBER, SERVER)).thenReturn(Optional.ofNullable(carriedDid));
        return DifferenceSignals.of(SERVER, globalConfProvider, registeredAddress);
    }

    private static DsParticipantEntity row(PublicationState state, String did) {
        var row = new DsParticipantEntity();
        row.setParticipantType(ParticipantType.MEMBER);
        row.setMemberIdentifier(ClientIdEntityFactory.create(MEMBER));
        row.setDid(did);
        row.setState(ParticipantState.ACTIVE);
        row.setPublicationState(state);
        return row;
    }

    private static String memberDid(String authority) {
        return ParticipantIdentifierScheme.memberDid(MEMBER, authority).toString();
    }
}
