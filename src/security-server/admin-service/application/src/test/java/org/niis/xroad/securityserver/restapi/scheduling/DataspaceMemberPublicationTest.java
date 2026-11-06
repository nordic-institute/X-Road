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
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.scheduling.PublicationBackoff.RequestKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DidRegistrationSender;
import org.niis.xroad.securityserver.restapi.service.MemberDidRegistrationSendOutcome;
import org.niis.xroad.securityserver.restapi.service.MemberDidRegistrationSendOutcome.Entry;
import org.niis.xroad.securityserver.restapi.service.SystemDidRegistrationSendOutcome;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnIdentity;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.PublicationState;
import org.niis.xroad.test.MutableClock;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the member DID step of the publication stage the way a reconciler pass does, against in-memory binding
 * rows whose transitions behave like the real conditional update.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspaceMemberPublicationTest {

    private static final String DSP_BASE_URL = "https://ss.example.test:7181/dsp";
    private static final ClientId.Conf OWNER = ClientId.Conf.create("DEV", "COM", "222");
    private static final SecurityServerId.Conf SERVER_ID = SecurityServerId.Conf.create(OWNER, "SS0");
    private static final ParticipantContext SYSTEM_CONTEXT = new ParticipantContext("xrd-ss0", ParticipantKind.SYSTEM, OWNER);

    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceDidAuthority didAuthority;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private DidRegistrationSender didRegistrationSender;
    @Mock
    private OwnSecurityServerResolver ownSecurityServerResolver;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspaceProperties;
    @Mock
    private GlobalConfProvider globalConfProvider;

    private DataspacePublicationStage stage;
    private DataspacePublicationGlobalConfCheck check;
    private MutableClock clock;
    private PublicationBackoff backoff;
    private DsParticipantEntity systemRow;
    private final List<DsParticipantEntity> memberRows = new ArrayList<>();
    private final Set<ParticipantContext> provisioned = new HashSet<>();
    private final Set<ParticipantContext> issued = new HashSet<>();
    private final List<List<String>> requests = new ArrayList<>();
    private MemberDidRegistrationSendOutcome nextOutcome;
    private int maxEntries = Integer.MAX_VALUE;
    private Set<String> unresolvedCodes = Set.of();
    private int batchSize = 100;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
        systemRow = row(0L, null, PublicationState.SUBMITTED);
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        when(readinessPredicates.registeredAuthCertBytes()).thenReturn(Optional.of(new byte[] {1}));
        when(didAuthority.dspBaseUrlIfValid()).thenReturn(Optional.of(DSP_BASE_URL));
        when(ownSecurityServerResolver.identity()).thenReturn(new OwnIdentity.Known(SERVER_ID));
        when(adminServiceProperties.getDataspace()).thenReturn(dataspaceProperties);
        when(dataspaceProperties.getProvisioningBatchSize()).thenAnswer(_ -> batchSize);
        when(dsParticipantRepository.findActiveSystemParticipant()).thenAnswer(_ -> Optional.of(systemRow));
        when(dsParticipantRepository.findActiveMembers(any())).thenAnswer(call -> {
            Set<PublicationState> states = call.getArgument(0);
            return memberRows.stream()
                    .filter(row -> states.contains(row.getPublicationState() == null
                            ? PublicationState.NOT_SENT : row.getPublicationState()))
                    .toList();
        });
        when(clientRepository.getAllLocalClients()).thenAnswer(_ -> provisioned.stream()
                .filter(context -> context.kind() == ParticipantKind.MEMBER)
                .map(context -> registeredClient(context.memberId()))
                .toList());
        when(globalConfProvider.getMemberDid(any(), any())).thenReturn(Optional.empty());
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenAnswer(call -> {
            var row = systemRow.getId().equals(call.getArgument(0)) ? systemRow
                    : memberRows.stream().filter(r -> r.getId().equals(call.getArgument(0))).findFirst().orElseThrow();
            PublicationState expected = call.getArgument(1);
            var current = row.getPublicationState() == null ? PublicationState.NOT_SENT : row.getPublicationState();
            if (current != expected) {
                return false;
            }
            row.setPublicationState(call.getArgument(2));
            row.setPublicationReason(call.getArgument(3));
            return true;
        });
        when(didRegistrationSender.sendMemberDidRegisterRequest(any())).thenAnswer(call -> {
            Map<ClientId.Conf, String> request = call.getArgument(0);
            requests.add(request.keySet().stream().map(ClientId::getMemberCode).toList());
            if (request.size() > maxEntries) {
                return MemberDidRegistrationSendOutcome.requestTooLarge();
            }
            return nextOutcome != null ? nextOutcome : answeredFor(request);
        });

        stage = new DataspacePublicationStage(readinessPredicates, didAuthority, dsParticipantRepository,
                didRegistrationSender, ownSecurityServerResolver, adminServiceProperties);
        backoff = new PublicationBackoff(clock, () -> 1.0);
        stage.setBackoff(backoff);
        check = new DataspacePublicationGlobalConfCheck(dsParticipantRepository, clientRepository, globalConfProvider,
                readinessPredicates, didAuthority, mock(PlatformTransactionManager.class));
        provisioned.add(SYSTEM_CONTEXT);
        issued.add(SYSTEM_CONTEXT);
        host(OWNER.getMemberCode());
    }

    @Test
    void aProvisionedMemberDidIsSentAndSubmittedAfterTheSystemValuesAreSubmitted() {
        var member = addMember("A01");

        pass();

        assertThat(requests).containsExactly(List.of("A01"));
        assertThat(member.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED"})
    void aMemberDidIsSentWhileTheSystemValuesAreSubmittedOrPublished(PublicationState systemState) {
        systemRow.setPublicationState(systemState);
        var member = addMember("A01");

        pass();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = PublicationState.class, names = {"NOT_SENT", "REJECTED"})
    void nothingIsSentBeforeTheSystemValuesAreSubmitted(PublicationState systemState) {
        systemRow.setPublicationState(systemState);
        addMember("A01");

        pass();

        verify(didRegistrationSender, never()).sendMemberDidRegisterRequest(any());
    }

    @Test
    void aSubmittedMemberDidThatGlobalconfCarriesBecomesPublished() {
        var member = addMember("A01");
        pass();
        carryDid(member);

        globalconfCheck();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(requests).hasSize(1);
    }

    @Test
    void aPublishedMemberDidMissingFromGlobalconfIsResetOnceAndSentAgainByTheNextPass() {
        var member = addMember("A01");
        member.setPublicationState(PublicationState.PUBLISHED);

        globalconfCheck();
        assertThat(member.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
        pass();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        assertThat(requests).containsExactly(List.of("A01"));
    }

    @Test
    void aPublishedMemberDidIsNotResetWhenTheMemberIsNoLongerHostedHere() {
        var member = addUnhostedMember("A01");
        member.setPublicationState(PublicationState.PUBLISHED);

        globalconfCheck();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
    }

    @Test
    void aDifferentMemberDidInGlobalconfChangesNothing() {
        var submitted = addMember("A01");
        submitted.setPublicationState(PublicationState.SUBMITTED);
        var published = addMember("A02");
        published.setPublicationState(PublicationState.PUBLISHED);
        when(globalConfProvider.getMemberDid(any(), any())).thenReturn(Optional.of("did:web:other.example.test"));

        globalconfCheck();
        globalconfCheck();

        assertThat(submitted.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        assertThat(published.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
    }

    @Test
    void aFailingSystemValuesCheckDoesNotStopTheMemberDidCheck() {
        var member = addMember("A01");
        member.setPublicationState(PublicationState.PUBLISHED);
        when(dsParticipantRepository.findActiveSystemParticipant()).thenThrow(new IllegalStateException("database unavailable"));

        globalconfCheck();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
    }

    @Test
    void aMemberCheckThatReadsNoHostedMemberTouchesNoBindingRow() {
        var member = addUnhostedMember("A01");
        member.setPublicationState(PublicationState.PUBLISHED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());

        globalconfCheck();

        verify(dsParticipantRepository, never()).findActiveMembers(any());
    }

    @Test
    void nothingIsSentForAMemberNotHostedOnThisServer() {
        var hosted = addMember("A01");
        var notHosted = addUnhostedMember("B01");

        pass();

        assertThat(requests).containsExactly(List.of("A01"));
        assertThat(hosted.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        assertThat(notHosted.getPublicationState()).isNull();
    }

    @Test
    void nothingIsSentWhileTheOwnerContextHasNoIssuedCredential() {
        addMember("A01");
        issued.removeIf(context -> context.kind() == ParticipantKind.MEMBER);

        pass();

        verify(didRegistrationSender, never()).sendMemberDidRegisterRequest(any());
    }

    @Test
    void withoutAnIssuedOwnerCredentialNoBindingRowIsRead() {
        addMember("A01");
        provisioned.remove(SYSTEM_CONTEXT);
        issued.removeIf(context -> context.kind() == ParticipantKind.MEMBER);

        pass();

        verify(dsParticipantRepository, never()).findActiveSystemParticipant();
        verify(dsParticipantRepository, never()).findActiveMembers(any());
    }

    @Test
    void whileTheMemberKindIsPausedNoBindingRowIsRead() {
        addMember("A01");
        nextOutcome = MemberDidRegistrationSendOutcome.sendFailure(new IllegalStateException("refused"));
        pass();
        provisioned.remove(SYSTEM_CONTEXT);
        clearInvocations(dsParticipantRepository);

        pass();

        verify(dsParticipantRepository, never()).findActiveSystemParticipant();
        verify(dsParticipantRepository, never()).findActiveMembers(any());
    }

    @Test
    void nothingIsSentWhenOnlyAnotherMembersCredentialIsIssued() {
        issued.removeIf(context -> context.kind() == ParticipantKind.MEMBER);
        addMember("A01");
        issued.add(memberContext("A01"));

        pass();

        verify(didRegistrationSender, never()).sendMemberDidRegisterRequest(any());
    }

    @Test
    void aMemberWhoseOnlyClientIsASubsystemIsSentThroughItsMemberContext() {
        var subsystem = ClientId.Conf.create("DEV", "COM", "A01", "SUB");
        var member = addUnhostedMember("A01");
        provisioned.add(new ParticipantContext("xrd-ss0-a01", ParticipantKind.MEMBER,
                ClientId.Conf.create(subsystem.getXRoadInstance(), subsystem.getMemberClass(), subsystem.getMemberCode())));

        pass();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @Test
    void aMemberBoundBeforeTheStateExistedIsSentOnTheNextPass() {
        var member = addMember("A01");
        assertThat(member.getPublicationState()).isNull();

        pass();

        assertThat(requests).hasSize(1);
        assertThat(member.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @Test
    void twoHundredFiftyDidsGoOutInThreeRequestsOverThreePasses() {
        IntStream.range(0, 250).forEach(i -> addMember(String.format("M%03d", i)));

        pass();
        assertThat(requests).hasSize(1);
        pass();
        assertThat(requests).hasSize(2);
        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 100, 50);
        assertThat(memberRows).allMatch(row -> row.getPublicationState() == PublicationState.SUBMITTED);
    }

    @Test
    void aBatchSizeOfTwoHundredFiftyGoesOutInOneRequest() {
        batchSize = 250;
        IntStream.range(0, 250).forEach(i -> addMember(String.format("M%03d", i)));

        pass();
        pass();

        assertThat(requests).extracting(List::size).containsExactly(250);
    }

    @Test
    void aBatchWithMixedResultsMovesEachValueToItsOwnState() {
        var stored = addMember("A01");
        var finalConflict = addMember("A02");
        var fixable = addMember("A03");
        var unresolved = addMember("A04");
        nextOutcome = MemberDidRegistrationSendOutcome.answered(5, List.of(
                entry("A01", "STORED", null),
                entry("A02", "FINAL_CONFLICT", "DID_TAKEN"),
                entry("A03", "FIXABLE_CONFLICT", "ADDRESS_MISMATCH"),
                entry("A04", "NOT_RESOLVED", "DID_UNRESOLVABLE")));

        pass();

        assertThat(stored.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        assertThat(finalConflict.getPublicationState()).isEqualTo(PublicationState.REJECTED);
        assertThat(finalConflict.getPublicationReason()).isEqualTo("DID_TAKEN");
        assertThat(fixable.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(fixable.getPublicationReason()).isEqualTo("ADDRESS_MISMATCH");
        assertThat(unresolved.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(unresolved.getPublicationReason()).isEqualTo("DID_UNRESOLVABLE");
    }

    @Test
    void aValueThatStayedNotSentIsPausedWhileTheRestGoOutOnTheNextPass() {
        addMember("A01");
        addMember("A02");
        nextOutcome = MemberDidRegistrationSendOutcome.answered(5, List.of(
                entry("A01", "FIXABLE_CONFLICT", "ADDRESS_MISMATCH"),
                entry("A02", "STORED", null)));
        pass();
        nextOutcome = null;
        addMember("A03");

        pass();

        assertThat(requests).containsExactly(List.of("A01", "A02"), List.of("A03"));
    }

    @Test
    void anUnknownEntryStatusLeavesTheValueNotSentWithTheStatusAsReason() {
        var member = addMember("A01");
        nextOutcome = MemberDidRegistrationSendOutcome.answered(5, List.of(entry("A01", "SOMETHING_NEW", null)));

        pass();

        assertThat(member.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(member.getPublicationReason()).isEqualTo("SOMETHING_NEW");
    }

    @Test
    void anUnknownEntryStatusPausesItsValueSoTheOthersAreSentOnceTheKindPauseIsOver() {
        addMember("A01");
        nextOutcome = MemberDidRegistrationSendOutcome.answered(5, List.of(entry("A01", "SOMETHING_NEW", null)));
        var jitters = new ArrayDeque<>(List.of(1.0, 0.0));
        stage.setBackoff(new PublicationBackoff(clock, () -> jitters.isEmpty() ? 1.0 : jitters.poll()));
        pass();
        nextOutcome = null;
        addMember("A02");

        clock.advance(Duration.ofSeconds(25));
        pass();

        assertThat(requests).containsExactly(List.of("A01"), List.of("A02"));
    }

    @Test
    void aWholeRequestFailureLeavesAllEntriesNotSentAndPausesTheMemberKind() {
        var first = addMember("A01");
        var second = addMember("A02");
        nextOutcome = MemberDidRegistrationSendOutcome.sendFailure(new IllegalStateException("refused"));

        pass();

        assertThat(List.of(first, second)).allMatch(row -> row.getPublicationState() == PublicationState.NOT_SENT
                && "SEND_FAILURE: IllegalStateException".equals(row.getPublicationReason()));
        nextOutcome = null;
        pass();
        assertThat(requests).hasSize(1);

        clock.advance(Duration.ofMinutes(6));
        pass();

        assertThat(requests).hasSize(2);
        assertThat(first.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @Test
    void aSubmittedMemberDidIsNotSentAgain() {
        addMember("A01");

        pass();
        pass();

        assertThat(requests).hasSize(1);
    }

    @Test
    void theMemberDidsGoOutOnlyAfterTheSystemValuesInTheSamePass() {
        systemRow.setPublicationState(PublicationState.NOT_SENT);
        addMember("A01");
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.stored(1));

        pass();

        var order = inOrder(didRegistrationSender);
        order.verify(didRegistrationSender).sendSystemDidRegisterRequest(any(), any(), any());
        order.verify(didRegistrationSender).sendMemberDidRegisterRequest(any());
    }

    @Test
    void aRequestTooLargeForHundredEntriesIsResentWithFiftyAndTwentyFiveInTheSamePass() {
        maxEntries = 25;
        addMembers(100);

        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50, 25);
        assertThat(memberRows.subList(0, 25)).allMatch(row -> row.getPublicationState() == PublicationState.SUBMITTED);
        assertThat(memberRows.subList(25, 100)).allMatch(row -> row.getPublicationState() == null);
    }

    @Test
    void theEntriesOfAResentRequestThatWasRefusedKeepTheirStateAndReason() {
        maxEntries = 50;
        addMembers(100);

        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50);
        assertThat(memberRows.subList(50, 100))
                .allMatch(row -> row.getPublicationState() == null && row.getPublicationReason() == null);
    }

    @Test
    void halvedResendsAreNoEntryFailuresSoTheNextPassSendsTheRestAtOnce() {
        maxEntries = 50;
        addMembers(100);

        pass();
        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50, 50);
        assertThat(memberRows).allMatch(row -> row.getPublicationState() == PublicationState.SUBMITTED);
    }

    @Test
    void halvingSendsEachDepthOnceAndStopsAtOneEntry() {
        maxEntries = 0;
        addMembers(100);

        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50, 25, 12, 6, 3, 1);
    }

    @Test
    void aBatchSizeAboveTheCentralServerLimitShrinksOnceAndLaterPassesSendOneAcceptedRequestEach() {
        batchSize = 100;
        maxEntries = 40;
        addMembers(250);

        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50, 25);
        IntStream.range(0, 9).forEach(_ -> {
            var before = requests.size();
            pass();
            assertThat(requests).hasSize(before + 1);
            assertThat(requests.getLast()).hasSize(25);
        });
        assertThat(memberRows).allMatch(row -> row.getPublicationState() == PublicationState.SUBMITTED);
    }

    @Test
    void aSendFailureOfAResentRequestPausesTheMemberKindForOneStepAndLeavesTheRestUntouched() {
        maxEntries = 50;
        addMembers(100);
        nextOutcome = MemberDidRegistrationSendOutcome.sendFailure(new IllegalStateException("refused"));

        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50);
        assertThat(memberRows.subList(0, 50)).allMatch(row -> row.getPublicationState() == PublicationState.NOT_SENT
                && "SEND_FAILURE: IllegalStateException".equals(row.getPublicationReason()));
        assertThat(memberRows.subList(50, 100)).allMatch(row -> row.getPublicationState() == null);

        clock.advance(Duration.ofSeconds(30));
        pass();

        assertThat(requests).extracting(List::size).containsExactly(100, 50, 100, 50);
    }

    @Test
    void aSingleEntryStillTooLargeStaysNotSentForFiveMinutes() {
        maxEntries = 0;
        var member = addMember("A01");

        pass();

        assertThat(requests).hasSize(1);
        assertThat(member.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(member.getPublicationReason()).isEqualTo("REQUEST_TOO_LARGE");

        clock.advance(Duration.ofSeconds(299));
        pass();
        assertThat(requests).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        pass();
        assertThat(requests).hasSize(2);
    }

    @Test
    void aSingleEntryStillTooLargeHoldsBackTheOtherMemberDidsOnlyForTheKindPause() {
        maxEntries = 0;
        addMember("A01");
        pass();
        maxEntries = Integer.MAX_VALUE;
        var other = addMember("A02");

        clock.advance(Duration.ofSeconds(29));
        pass();
        assertThat(requests).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        pass();

        assertThat(requests).extracting(List::size).containsExactly(1, 1);
        assertThat(other.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @Test
    void aCentralServerThatRefusesEvenOneEntryGetsOneHalvingChainPerKindPause() {
        maxEntries = 0;
        addMembers(3);

        pass();
        assertThat(requests).extracting(List::size).containsExactly(3, 1);

        for (var seconds : List.of(10, 10, 9)) {
            clock.advance(Duration.ofSeconds(seconds));
            pass();
        }
        assertThat(requests).hasSize(2);

        clock.advance(Duration.ofSeconds(1));
        pass();
        assertThat(requests).extracting(List::size).containsExactly(3, 1, 2, 1);
    }

    @Test
    void afterABatchWithNothingResolvedTheNextBatchHasOneEntryAndFullSizeReturnsOnceItResolves() {
        batchSize = 3;
        addMembers(6);
        unresolvedCodes = Set.of("M000", "M001", "M002");

        pass();
        unresolvedCodes = Set.of();
        clock.advance(Duration.ofSeconds(31));
        pass();
        pass();

        assertThat(requests).extracting(List::size).containsExactly(3, 1, 3);
        assertThat(requests.get(1)).containsExactly("M000");
        assertThat(requests.get(2)).containsExactly("M001", "M002", "M003");
    }

    @Test
    void aProbeThatDoesNotResolveKeepsTheNextBatchAtOneEntryWhileTheRestGoOut() {
        batchSize = 3;
        addMembers(6);
        unresolvedCodes = Set.of("M000", "M001", "M002", "M003", "M004", "M005");

        pass();
        clock.advance(Duration.ofSeconds(31));
        unresolvedCodes = Set.of("M000");
        pass();
        clock.advance(Duration.ofSeconds(31));
        unresolvedCodes = Set.of();
        pass();

        assertThat(requests).extracting(List::size).containsExactly(3, 1, 1);
        assertThat(requests.get(2)).containsExactly("M001");
    }

    @Test
    void aProbeReachesAHealthyMemberWhenMoreMembersKeepFailingThanProbeSlotsFitInTheMaximumPause() {
        batchSize = 5;
        addMembers(12);
        unresolvedCodes = IntStream.range(0, 10).mapToObj(i -> String.format("M%03d", i)).collect(Collectors.toSet());

        pass();
        for (int i = 0; i < 12; i++) {
            clock.advance(Duration.ofSeconds(30));
            pass();
        }

        assertThat(requests.subList(1, 12)).extracting(request -> request.getFirst())
                .containsExactly("M000", "M001", "M002", "M003", "M004", "M005", "M006", "M007", "M008", "M009", "M010");
        assertThat(requests.subList(1, 12)).allMatch(request -> request.size() == 1);
    }

    @Test
    void aProbeWrapsBackToTheFirstMemberAfterTheLast() {
        batchSize = 3;
        addMembers(3);
        unresolvedCodes = Set.of("M000", "M001", "M002");

        pass();
        for (int i = 0; i < 4; i++) {
            clock.advance(Duration.ofSeconds(30));
            pass();
        }

        assertThat(requests).containsExactly(List.of("M000", "M001", "M002"), List.of("M000"), List.of("M001"),
                List.of("M002"), List.of("M000"));
    }

    @Test
    void aSecondProbeEpisodeContinuesAfterTheMemberProbedLastInTheFirst() {
        batchSize = 5;
        addMembers(12);
        unresolvedCodes = IntStream.range(0, 10).mapToObj(i -> String.format("M%03d", i)).collect(Collectors.toSet());

        pass();
        for (int i = 0; i < 13; i++) {
            clock.advance(Duration.ofSeconds(30));
            pass();
        }

        assertThat(requests.get(12)).containsExactly("M000", "M001", "M002", "M003", "M004");
        assertThat(requests.get(13)).containsExactly("M011");
    }

    @Test
    void aProbeThatFailsWhileItsAnswerIsAppliedStillMovesTheNextProbeOn() {
        batchSize = 3;
        addMembers(6);
        unresolvedCodes = Set.of("M000", "M001", "M002");
        pass();
        clock.advance(Duration.ofSeconds(30));
        nextOutcome = MemberDidRegistrationSendOutcome.answered(1, List.of(entry("M999", "STORED", null)));
        pass();
        nextOutcome = null;
        clock.advance(Duration.ofSeconds(30));

        pass();

        assertThat(requests).containsExactly(List.of("M000", "M001", "M002"), List.of("M000"), List.of("M001"));
    }

    @Test
    void aConfiguredBatchSizeOfOneSendsLowestIdFirstWithoutProbingOrRememberingAProbedMember() {
        batchSize = 1;
        addMembers(3);

        pass();
        pass();

        assertThat(requests).containsExactly(List.of("M000"), List.of("M001"));
        assertThat(backoff.lastProbedMember(RequestKind.MEMBER)).isEmpty();
    }

    @Test
    void aProbeEntryWithAnUnknownStatusKeepsTheNextBatchAtOneEntry() {
        batchSize = 3;
        addMembers(6);
        unresolvedCodes = Set.of("M000", "M001", "M002", "M003", "M004", "M005");
        pass();
        clock.advance(Duration.ofSeconds(31));
        unresolvedCodes = Set.of();
        nextOutcome = MemberDidRegistrationSendOutcome.answered(1, List.of(entry("M000", "SOMETHING_NEW", null)));
        pass();
        nextOutcome = null;
        clock.advance(Duration.ofSeconds(31));

        pass();

        assertThat(requests).extracting(List::size).containsExactly(3, 1, 1);
        assertThat(requests.get(2)).containsExactly("M001");
    }

    @Test
    void aBatchWithOneResolvedEntryDoesNotStartAProbe() {
        batchSize = 3;
        addMembers(6);
        unresolvedCodes = Set.of("M000", "M001");

        pass();
        clock.advance(Duration.ofSeconds(31));
        pass();

        assertThat(requests).extracting(List::size).containsExactly(3, 3);
    }

    private void pass() {
        stage.publish(Set.copyOf(provisioned), Set.copyOf(issued));
    }

    private void globalconfCheck() {
        check.check(SERVER_ID);
    }

    private static ClientEntity registeredClient(ClientId memberId) {
        var client = new ClientEntity();
        client.setClientStatus(Client.STATUS_REGISTERED);
        client.setIdentifier(MemberIdEntity.create(memberId.getXRoadInstance(), memberId.getMemberClass(),
                memberId.getMemberCode()));
        return client;
    }

    private void carryDid(DsParticipantEntity member) {
        when(globalConfProvider.getMemberDid(member.getMemberIdentifier(), SERVER_ID)).thenReturn(Optional.of(member.getDid()));
    }

    private void host(String memberCode) {
        var context = memberContext(memberCode);
        provisioned.add(context);
        issued.add(context);
    }

    private static ParticipantContext memberContext(String memberCode) {
        return new ParticipantContext("xrd-ss0-" + memberCode.toLowerCase(), ParticipantKind.MEMBER,
                ClientId.Conf.create("DEV", "COM", memberCode));
    }

    private void addMembers(int count) {
        IntStream.range(0, count).forEach(i -> addMember(String.format("M%03d", i)));
    }

    private DsParticipantEntity addMember(String memberCode) {
        var row = addUnhostedMember(memberCode);
        provisioned.add(memberContext(memberCode));
        return row;
    }

    private DsParticipantEntity addUnhostedMember(String memberCode) {
        var row = row(memberRows.size() + 1L, memberCode, null);
        memberRows.add(row);
        return row;
    }

    private static DsParticipantEntity row(long id, String memberCode, PublicationState state) {
        var row = new DsParticipantEntity();
        row.setId(id);
        row.setDid("did:web:ss.example.test%3A7183:xrd-" + (memberCode == null ? "ss0" : memberCode.toLowerCase()));
        row.setState(ParticipantState.ACTIVE);
        row.setPublicationState(state);
        if (memberCode != null) {
            row.setMemberIdentifier(MemberIdEntity.create("DEV", "COM", memberCode));
        }
        return row;
    }

    private static Entry entry(String memberCode, String status, String reason) {
        return new Entry(ClientId.Conf.create("DEV", "COM", memberCode), status, reason);
    }

    private MemberDidRegistrationSendOutcome answeredFor(Map<ClientId.Conf, String> request) {
        return MemberDidRegistrationSendOutcome.answered(1, request.keySet().stream()
                .map(member -> unresolvedCodes.contains(member.getMemberCode())
                        ? new Entry(member, "NOT_RESOLVED", "DID_UNRESOLVABLE")
                        : new Entry(member, "STORED", null))
                .toList());
    }
}
