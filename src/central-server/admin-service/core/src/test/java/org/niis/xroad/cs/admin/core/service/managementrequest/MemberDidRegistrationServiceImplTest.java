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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;

class MemberDidRegistrationServiceImplTest {
    private static final int SERVER_ROW_ID = 7;
    private static final String ADDRESS = "ss.example.org";
    private static final ClientId MEMBER_1 = ClientId.Conf.create("DEV", "COM", "111");
    private static final ClientId MEMBER_2 = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId MEMBER_3 = ClientId.Conf.create("DEV", "COM", "333");
    private static final String DID_1 = "did:web:ss.example.org:v1:member:DEV:COM:111";
    private static final String DID_2 = "did:web:ss.example.org:v1:member:DEV:COM:222";
    private static final String DID_3 = "did:web:ss.example.org:v1:member:DEV:COM:333";
    private static final String OTHER_DID_1 = "did:web:ss.example.org:v1:member:DEV:COM:119";

    private final SecurityServerId securityServerId = SecurityServerId.create("DEV", "COM", "222", "SS0");
    private final List<String> calls = new ArrayList<>();
    private final List<Map<ClientId, String>> checked = new ArrayList<>();
    private final List<List<String>> resolved = new ArrayList<>();

    private Optional<SecurityServerEntity> server = Optional.of(serverEntity("did:web:ss.example.org:v1:system"));
    private Function<Map<ClientId, String>, Map<ClientId, ParticipantValueResult>> storeAnswer = memberDids -> {
        var answer = new LinkedHashMap<ClientId, ParticipantValueResult>();
        memberDids.keySet().forEach(member -> answer.put(member, ParticipantValueResult.STORED));
        return answer;
    };
    private Function<List<String>, List<DidResolution>> resolverAnswer = dids -> dids.stream().map(DidResolution::resolved).toList();

    private final ManagementRequestService managementRequestService = mock(ManagementRequestService.class);
    private final SecurityServerRepository servers = mock(SecurityServerRepository.class);
    private final ParticipantValueStore store = mock(ParticipantValueStore.class);
    private final DidResolutionService resolver = dids -> {
        calls.add("resolve");
        resolved.add(dids);
        return resolverAnswer.apply(dids);
    };

    private static SecurityServerEntity serverEntity(String systemDid) {
        var entity = mock(SecurityServerEntity.class);
        when(entity.getId()).thenReturn(SERVER_ROW_ID);
        when(entity.getAddress()).thenReturn(ADDRESS);
        when(entity.getSystemDid()).thenReturn(systemDid);
        return entity;
    }

    private MemberDidRegistrationRequest add(Entry... entries) {
        var request = new MemberDidRegistrationRequest(SECURITY_SERVER, securityServerId, List.of(entries));
        when(servers.findBy(securityServerId)).thenReturn(server);
        when(store.checkMemberDids(anyInt(), anyMap())).thenAnswer(inv -> {
            calls.add("check");
            Map<ClientId, String> memberDids = inv.getArgument(1);
            checked.add(memberDids);
            return storeAnswer.apply(memberDids);
        });
        when(managementRequestService.add(request)).thenAnswer(inv -> {
            calls.add("add");
            return request;
        });
        return new MemberDidRegistrationServiceImpl(resolver, store, servers, managementRequestService).add(request);
    }

    private Entry[] threeEntries() {
        return new Entry[] {new Entry(MEMBER_1, DID_1), new Entry(MEMBER_2, DID_2), new Entry(MEMBER_3, DID_3)};
    }

    @Test
    void checksThenResolvesTheEligibleEntriesInOneCallThenAddsWithNothingSettled() {
        var result = add(threeEntries());

        assertThat(calls).containsExactly("check", "resolve", "add");
        assertThat(checked).containsExactly(Map.of(MEMBER_1, DID_1, MEMBER_2, DID_2, MEMBER_3, DID_3));
        assertThat(resolved).containsExactly(List.of(DID_1, DID_2, DID_3));
        assertThat(result.getSettledResults()).isEmpty();
    }

    @Test
    void aServerWithoutStoredSystemValuesAnswersEveryEntryFixableWithoutResolvingOrChecking() {
        server = Optional.of(serverEntity(null));
        resolverAnswer = dids -> {
            throw new AssertionError("the resolver must not be called");
        };

        var result = add(threeEntries());

        assertThat(result.getSettledResults()).containsOnlyKeys(0, 1, 2);
        assertThat(result.getSettledResults().values()).containsExactly(
                new Result(MEMBER_1, Status.FIXABLE_CONFLICT, MemberDidRegistrationOutcome.SYSTEM_VALUES_NOT_STORED),
                new Result(MEMBER_2, Status.FIXABLE_CONFLICT, MemberDidRegistrationOutcome.SYSTEM_VALUES_NOT_STORED),
                new Result(MEMBER_3, Status.FIXABLE_CONFLICT, MemberDidRegistrationOutcome.SYSTEM_VALUES_NOT_STORED));
        assertThat(calls).containsExactly("add");
        verify(store, never()).writeMemberDidsOnce(anyInt(), anyMap());
    }

    @Test
    void anUnknownServerAnswersEveryEntryOwnerChangedWithoutResolving() {
        server = Optional.empty();

        var result = add(threeEntries());

        assertThat(result.getSettledResults().values()).extracting(Result::reason)
                .containsExactly("OWNER_CHANGED", "OWNER_CHANGED", "OWNER_CHANGED");
        assertThat(calls).containsExactly("add");
    }

    @Test
    void identicalDuplicatesShareOneCheckAndOneResolutionAndStayUnsettledForOneWrite() {
        var result = add(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_1, DID_1), new Entry(MEMBER_2, DID_2));

        assertThat(checked).containsExactly(Map.of(MEMBER_1, DID_1, MEMBER_2, DID_2));
        assertThat(resolved).containsExactly(List.of(DID_1, DID_2));
        assertThat(result.getSettledResults()).isEmpty();
    }

    @Test
    void identicalDuplicatesAlreadyStoredSkipResolutionAndStayUnsettledForTheWrite() {
        storeAnswer = memberDids -> Map.of(MEMBER_1, ParticipantValueResult.UNCHANGED);

        var result = add(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_1, DID_1));

        assertThat(result.getSettledResults()).isEmpty();
        assertThat(resolved).isEmpty();
    }

    @Test
    void theSameMemberWithDifferentDidsIsFinalOnBothEntriesAndNeverCheckedOrResolved() {
        var result = add(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_2, DID_2), new Entry(MEMBER_1, OTHER_DID_1));

        assertThat(result.getSettledResults()).containsOnlyKeys(0, 2)
                .containsEntry(0, new Result(MEMBER_1, Status.FINAL_CONFLICT, MemberDidRegistrationOutcome.CONFLICTING_DUPLICATE))
                .containsEntry(2, new Result(MEMBER_1, Status.FINAL_CONFLICT, MemberDidRegistrationOutcome.CONFLICTING_DUPLICATE));
        assertThat(checked).containsExactly(Map.of(MEMBER_2, DID_2));
        assertThat(resolved).containsExactly(List.of(DID_2));
    }

    @Test
    void equalEntriesSkipResolutionAndStayUnsettledWhileOnlyTheOthersGoToTheSingleResolverCall() {
        storeAnswer = memberDids -> {
            var answer = new LinkedHashMap<ClientId, ParticipantValueResult>();
            memberDids.keySet().forEach(member -> answer.put(member,
                    member.equals(MEMBER_2) ? ParticipantValueResult.STORED : ParticipantValueResult.UNCHANGED));
            return answer;
        };

        var result = add(threeEntries());

        assertThat(resolved).containsExactly(List.of(DID_2));
        assertThat(result.getSettledResults()).isEmpty();
    }

    @Test
    void aRerunOfStoredValuesSkipsResolutionAndLeavesEveryEntryToTheWrite() {
        storeAnswer = memberDids -> {
            var answer = new LinkedHashMap<ClientId, ParticipantValueResult>();
            memberDids.keySet().forEach(member -> answer.put(member, ParticipantValueResult.UNCHANGED));
            return answer;
        };

        var result = add(threeEntries());

        assertThat(resolved).isEmpty();
        assertThat(result.getSettledResults()).isEmpty();
        verify(store, never()).writeMemberDidsOnce(anyInt(), anyMap());
    }

    @Test
    void entriesTheStoreRefusesGetTheirClassBeforeAnyResolverCallAndTheOthersStillResolve() {
        var member4 = ClientId.Conf.create("DEV", "COM", "444");
        var did4 = "did:web:ss.example.org:v1:member:DEV:COM:444";
        storeAnswer = memberDids -> Map.of(
                MEMBER_1, ParticipantValueResult.rejected(ParticipantValueRejection.NOT_CANONICAL),
                MEMBER_2, ParticipantValueResult.rejected(ParticipantValueRejection.VALUE_DIFFERS),
                MEMBER_3, ParticipantValueResult.STORED,
                member4, ParticipantValueResult.rejected(ParticipantValueRejection.MEMBER_NOT_SERVED));

        var result = add(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_2, DID_2), new Entry(MEMBER_3, DID_3), new Entry(member4, did4));

        assertThat(resolved).containsExactly(List.of(DID_3));
        assertThat(result.getSettledResults()).containsOnlyKeys(0, 1, 3)
                .containsEntry(0, new Result(MEMBER_1, Status.FINAL_CONFLICT, "NOT_CANONICAL"))
                .containsEntry(1, new Result(MEMBER_2, Status.FIXABLE_CONFLICT, "VALUE_DIFFERS"))
                .containsEntry(3, new Result(member4, Status.FIXABLE_CONFLICT, "MEMBER_NOT_SERVED"));
    }

    @Test
    void settlesTheEntryTheResolverCouldNotResolveAndLeavesTheOthersForTheStore() {
        resolverAnswer = dids -> List.of(
                DidResolution.resolved(DID_1),
                DidResolution.notResolved(DID_2, DidResolutionReason.FETCH_FAILURE),
                DidResolution.resolved(DID_3));

        var result = add(threeEntries());

        assertThat(result.getSettledResults())
                .containsOnlyKeys(1)
                .containsEntry(1, new Result(MEMBER_2, Status.NOT_RESOLVED, DidResolutionReason.FETCH_FAILURE.name()));
    }

    @Test
    void aFetchFailureForEveryEntryIsNotResolvedForEveryEntryFromOneResolverCall() {
        resolverAnswer = dids -> dids.stream().map(did -> DidResolution.notResolved(did, DidResolutionReason.FETCH_FAILURE)).toList();

        var result = add(threeEntries());

        assertThat(resolved).hasSize(1);
        assertThat(result.getSettledResults()).containsOnlyKeys(0, 1, 2);
        assertThat(result.getSettledResults().values()).extracting(Result::status).containsOnly(Status.NOT_RESOLVED);
        assertThat(result.getSettledResults().values()).extracting(Result::reason).containsOnly("FETCH_FAILURE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAVAILABLE", "DEADLINE_EXCEEDED"})
    void aResolverFailureAsAWholeIsNotResolvedForEveryEligibleEntryFromOneResolverCall(String status) {
        resolverAnswer = dids -> {
            throw XrdRuntimeException.systemException(NETWORK_ERROR).details("DID resolution call failed: " + status).build();
        };

        var result = add(threeEntries());

        assertThat(resolved).hasSize(1);
        assertThat(result.getSettledResults()).containsOnlyKeys(0, 1, 2);
        assertThat(result.getSettledResults().values()).allSatisfy(settled -> {
            assertThat(settled.status()).isEqualTo(Status.NOT_RESOLVED);
            assertThat(settled.reason()).isEqualTo(SystemDidRegistrationOutcome.RESOLVER_UNAVAILABLE);
        });
        assertThat(calls).endsWith("add");
    }

    @Test
    void anAnswerForAnotherDidIsNotResolved() {
        resolverAnswer = dids -> List.of(
                DidResolution.resolved(DID_1),
                DidResolution.resolved(DID_3),
                DidResolution.resolved(DID_2));

        var result = add(threeEntries());

        assertThat(result.getSettledResults()).containsOnlyKeys(1, 2);
    }

    @Test
    void aMissingAnswerIsNotResolved() {
        resolverAnswer = dids -> List.of(DidResolution.resolved(DID_1));

        var result = add(threeEntries());

        assertThat(result.getSettledResults()).containsOnlyKeys(1, 2);
    }

    @Test
    void aRefusedEntryCarriesTheRegisteredAddressForTheAddressDependentClass() {
        when(server.get().getAddress()).thenReturn("ss.exämple.org");
        storeAnswer = memberDids -> Map.of(MEMBER_1, ParticipantValueResult.rejected(ParticipantValueRejection.NON_ASCII_HOST));

        var result = add(new Entry(MEMBER_1, DID_1));

        assertThat(result.getSettledResults()).containsEntry(0, new Result(MEMBER_1, Status.FIXABLE_CONFLICT, "NON_ASCII_HOST"));
    }

    @Test
    void nothingIsWrittenByTheService() {
        add(threeEntries());

        verify(store, never()).writeMemberDidsOnce(anyInt(), anyMap());
        verify(store, never()).replaceMemberDids(anyInt(), anyMap());
        verify(managementRequestService).add(any(MemberDidRegistrationRequest.class));
    }
}
