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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberEntity;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemEntity;

class DifferenceSignalsTest {

    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("DEV", "COM", "333");
    private static final SecurityServerId SERVER = SecurityServerId.Conf.create(MEMBER, "SS0");
    private static final String ADDRESS = "ss0.example.org";
    private static final String BOUND = memberDid("ss0.example.org:7183");
    private static final String OTHER_SAME_HOST = memberDid("ss0.example.org:8443");
    private static final String OTHER_HOST = memberDid("ss1.example.org:7183");

    private static final String SYSTEM_DID = systemDid("ss0.example.org:7183");
    private static final String OTHER_SYSTEM_DID = systemDid("ss0.example.org:8443");
    private static final String OTHER_HOST_SYSTEM_DID = systemDid("ss1.example.org:7183");
    private static final String BUILT_URL = "https://ss0.example.org:7181/dsp";
    private static final String OTHER_PORT_URL = "https://ss0.example.org:9999/dsp";
    private static final String OTHER_HOST_URL = "https://ss1.example.org:7181/dsp";

    private static final DifferenceSignals.SystemMismatch NONE = new DifferenceSignals.SystemMismatch(false, false);
    private static final DifferenceSignals.SystemMismatch BOTH = new DifferenceSignals.SystemMismatch(true, true);

    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);

    static Stream<Arguments> memberMismatchCases() {
        return Stream.of(
                argumentSet("submitted, differing value", PublicationState.SUBMITTED, OTHER_SAME_HOST, true),
                argumentSet("published, differing value", PublicationState.PUBLISHED, OTHER_SAME_HOST, true),
                argumentSet("not sent", PublicationState.NOT_SENT, OTHER_SAME_HOST, false),
                argumentSet("rejected", PublicationState.REJECTED, OTHER_SAME_HOST, false),
                argumentSet("no publication state", null, OTHER_SAME_HOST, false),
                argumentSet("equal value", PublicationState.PUBLISHED, BOUND, false),
                argumentSet("absent value", PublicationState.PUBLISHED, null, false),
                argumentSet("stale by host", PublicationState.PUBLISHED, OTHER_HOST, false));
    }

    @ParameterizedTest
    @MethodSource("memberMismatchCases")
    void aMemberMismatchesOnlyForASentRowWhoseCarriedDidDiffersAndIsNotStale(
            PublicationState state, String carriedDid, boolean expected) {
        var signals = signals(ADDRESS, carriedDid, null);

        assertThat(signals.memberMismatch(memberEntity(MEMBER, BOUND, state))).isEqualTo(expected);
    }

    static Stream<Arguments> systemMismatchCases() {
        var differing = new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL);
        return Stream.of(
                argumentSet("submitted, differing values", PublicationState.SUBMITTED, differing, BOTH),
                argumentSet("published, differing values", PublicationState.PUBLISHED, differing, BOTH),
                argumentSet("not sent", PublicationState.NOT_SENT, differing, NONE),
                argumentSet("rejected", PublicationState.REJECTED, differing, NONE),
                argumentSet("no publication state", null, differing, NONE),
                argumentSet("equal values", PublicationState.PUBLISHED,
                        new ServerSystemValues(SYSTEM_DID, BUILT_URL), NONE),
                argumentSet("absent values", PublicationState.PUBLISHED, null, NONE),
                argumentSet("DID stale by host", PublicationState.PUBLISHED,
                        new ServerSystemValues(OTHER_HOST_SYSTEM_DID, OTHER_PORT_URL), NONE),
                argumentSet("URL stale by host", PublicationState.PUBLISHED,
                        new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_HOST_URL), NONE),
                argumentSet("DID differs only", PublicationState.PUBLISHED,
                        new ServerSystemValues(OTHER_SYSTEM_DID, BUILT_URL),
                        new DifferenceSignals.SystemMismatch(true, false)),
                argumentSet("URL differs only", PublicationState.PUBLISHED,
                        new ServerSystemValues(SYSTEM_DID, OTHER_PORT_URL),
                        new DifferenceSignals.SystemMismatch(false, true)));
    }

    @ParameterizedTest
    @MethodSource("systemMismatchCases")
    void aSystemMismatchesOnlyForASentRowOnTheValuesThatDifferAndAreNotStale(
            PublicationState state, ServerSystemValues carried, DifferenceSignals.SystemMismatch expected) {
        var signals = signals(ADDRESS, null, carried);

        assertThat(signals.systemMismatch(systemEntity(SYSTEM_DID, state))).isEqualTo(expected);
    }

    @Test
    void anUnknownRegisteredAddressMakesEveryDifferingValueStale() {
        var member = memberEntity(MEMBER, BOUND, PublicationState.PUBLISHED);
        var system = systemEntity(SYSTEM_DID, PublicationState.PUBLISHED);
        var signals = signals(null, OTHER_SAME_HOST, new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL), null);

        assertThat(signals.memberMismatch(member)).isFalse();
        assertThat(signals.compareMemberDid(member)).isEqualTo(GlobalConfComparison.STALE);
        assertThat(signals.compareSystemValues(system))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.STALE, GlobalConfComparison.STALE));
    }

    @Test
    void compareMemberDidClassifiesTheCarriedDid() {
        var row = memberEntity(MEMBER, BOUND, PublicationState.NOT_SENT);

        assertThat(signals(ADDRESS, null, null).compareMemberDid(row)).isEqualTo(GlobalConfComparison.ABSENT);
        assertThat(signals(ADDRESS, BOUND, null).compareMemberDid(row)).isEqualTo(GlobalConfComparison.EQUAL);
        assertThat(signals(ADDRESS, OTHER_SAME_HOST, null).compareMemberDid(row))
                .isEqualTo(GlobalConfComparison.DIFFERENT);
        assertThat(signals(ADDRESS, OTHER_HOST, null).compareMemberDid(row)).isEqualTo(GlobalConfComparison.STALE);
    }

    @Test
    void compareSystemValuesComparesTheCarriedPairWithTheBoundDidAndTheBuiltUrl() {
        var row = systemEntity(SYSTEM_DID, PublicationState.PUBLISHED);

        assertThat(signals(ADDRESS, null, null).compareSystemValues(row))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.ABSENT, GlobalConfComparison.ABSENT));
        assertThat(signals(ADDRESS, null, new ServerSystemValues(SYSTEM_DID, BUILT_URL)).compareSystemValues(row))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.EQUAL, GlobalConfComparison.EQUAL));
        assertThat(signals(ADDRESS, null, new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL))
                .compareSystemValues(row))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.DIFFERENT, GlobalConfComparison.DIFFERENT));
    }

    @Test
    void theSystemPairIsStaleWhenEitherHostDiffers() {
        var row = systemEntity(SYSTEM_DID, PublicationState.PUBLISHED);
        var otherHostDid = new ServerSystemValues(OTHER_HOST_SYSTEM_DID, OTHER_PORT_URL);
        var otherHostUrl = new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_HOST_URL);

        assertThat(signals(ADDRESS, null, otherHostDid).compareSystemValues(row))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.STALE, GlobalConfComparison.STALE));
        assertThat(signals(ADDRESS, null, otherHostUrl).compareSystemValues(row))
                .isEqualTo(new SystemValuesComparison(GlobalConfComparison.STALE, GlobalConfComparison.STALE));
    }

    @Test
    void hostStatusIsOkWhenEveryActiveBindingIsAtTheRegisteredHost() {
        var members = List.of(memberEntity(MEMBER, BOUND, PublicationState.PUBLISHED),
                memberEntity(MEMBER, OTHER_SAME_HOST, PublicationState.NOT_SENT));

        var differences = signals(ADDRESS, null, null)
                .evaluate(systemEntity(SYSTEM_DID, PublicationState.PUBLISHED), members);

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.OK);
        assertThat(differences.hostDiffering()).isEmpty();
    }

    @Test
    void hostStatusIsOkWithoutBindings() {
        assertThat(signals(ADDRESS, null, null).evaluate(null, List.of()).hostStatus())
                .isEqualTo(HostStatus.OK);
    }

    @Test
    void hostStatusDiffersWhenTheSystemBindingIsAtAnotherHost() {
        var system = systemEntity(OTHER_HOST_SYSTEM_DID, null);

        var differences = signals(ADDRESS, null, null)
                .evaluate(system, List.of(memberEntity(MEMBER, BOUND, PublicationState.PUBLISHED)));

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.DIFFERS);
        assertThat(differences.hostDiffering()).containsExactly("SYSTEM");
    }

    @ParameterizedTest
    @ValueSource(strings = {"b\u00fccher.example", "bad host", " "})
    @NullAndEmptySource
    void hostStatusIsUnknownWhenTheRegisteredAddressCannotBeUsed(String registeredAddress) {
        var system = systemEntity(SYSTEM_DID, PublicationState.PUBLISHED);
        var members = List.of(memberEntity(MEMBER, OTHER_HOST, PublicationState.PUBLISHED));
        var signals = signals(registeredAddress, OTHER_SAME_HOST,
                new ServerSystemValues(OTHER_SYSTEM_DID, OTHER_PORT_URL), BUILT_URL);

        var differences = signals.evaluate(system, members);
        var withoutBindings = signals.evaluate(null, List.of());

        assertThat(differences).isEqualTo(DifferenceSignals.Differences.UNKNOWN);
        assertThat(withoutBindings).isEqualTo(DifferenceSignals.Differences.UNKNOWN);
    }

    @Test
    void evaluateCombinesEverySignalFromOneSetOfRows() {
        var system = systemEntity(SYSTEM_DID, PublicationState.PUBLISHED);
        var mismatching = memberEntity(MEMBER, BOUND, PublicationState.PUBLISHED);
        var atOtherHost = memberEntity(OTHER_MEMBER, OTHER_HOST, PublicationState.NOT_SENT);
        var signals = signals(ADDRESS, OTHER_SAME_HOST, new ServerSystemValues(OTHER_SYSTEM_DID, BUILT_URL), BUILT_URL);

        var differences = signals.evaluate(system, List.of(mismatching, atOtherHost));

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.DIFFERS);
        assertThat(differences.systemMismatch()).isEqualTo(new DifferenceSignals.SystemMismatch(true, false));
        assertThat(differences.memberMismatches()).containsExactly(MEMBER);
        assertThat(differences.hostDiffering()).containsExactly("DEV:COM:333");
    }

    @Test
    void evaluateWithoutASystemRowHasNoSystemMismatch() {
        var differences = signals(ADDRESS, null, null)
                .evaluate(null, List.of(memberEntity(MEMBER, BOUND, PublicationState.PUBLISHED)));

        assertThat(differences.hostStatus()).isEqualTo(HostStatus.OK);
        assertThat(differences.systemMismatch()).isEqualTo(NONE);
        assertThat(differences.memberMismatches()).isEmpty();
        assertThat(differences.hostDiffering()).isEmpty();
    }

    private DifferenceSignals signals(String registeredAddress, String carriedMemberDid,
            ServerSystemValues carriedSystem) {
        return signals(registeredAddress, carriedMemberDid, carriedSystem, BUILT_URL);
    }

    private DifferenceSignals signals(String registeredAddress, String carriedMemberDid,
            ServerSystemValues carriedSystem, String dspBaseUrl) {
        when(globalConfProvider.getMemberDid(MEMBER, SERVER)).thenReturn(Optional.ofNullable(carriedMemberDid));
        when(globalConfProvider.getServerSystemValues(SERVER)).thenReturn(Optional.ofNullable(carriedSystem));
        return DifferenceSignals.of(SERVER, globalConfProvider, registeredAddress, dspBaseUrl);
    }

    private static String systemDid(String authority) {
        return ParticipantIdentifierScheme.systemDid(authority).toString();
    }

    private static String memberDid(String authority) {
        return ParticipantIdentifierScheme.memberDid(MEMBER, authority).toString();
    }
}
