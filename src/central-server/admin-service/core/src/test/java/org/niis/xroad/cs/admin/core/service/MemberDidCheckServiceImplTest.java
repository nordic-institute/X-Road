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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

class MemberDidCheckServiceImplTest {
    private static final String MEMBER_DID = "did:web:ss.example.org%3A7183:v1:DEV:COM:333";
    private static final int SERVER_ROW_ID = 5;

    private final SecurityServerId serverId = SecurityServerId.Conf.create("DEV", "COM", "222", "SS0");
    private final ClientId memberId = ClientId.Conf.create("DEV", "COM", "333");
    private final List<List<String>> resolverCalls = new ArrayList<>();
    private Supplier<List<DidResolution>> resolverAnswer = List::of;

    private final XRoadMemberRepository xRoadMemberRepository = mock(XRoadMemberRepository.class);
    private final SecurityServerRepository securityServerRepository = mock(SecurityServerRepository.class);
    private final SecurityServerEntity server = mock(SecurityServerEntity.class);
    private final ParticipantValueStore store = mock(ParticipantValueStore.class);
    private final DidResolutionService resolver = dids -> {
        resolverCalls.add(dids);
        return resolverAnswer.get();
    };
    private final MemberDidCheckServiceImpl service = new MemberDidCheckServiceImpl(
            xRoadMemberRepository, securityServerRepository, store, new DidBatchResolution(resolver));

    private DidCheck check(ParticipantValueResult verdict) {
        when(xRoadMemberRepository.count(memberId)).thenReturn(1L);
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        when(server.getId()).thenReturn(SERVER_ROW_ID);
        when(store.checkMemberDidReplacements(SERVER_ROW_ID, Map.of(memberId, MEMBER_DID)))
                .thenReturn(Map.of(memberId, verdict));
        return service.check(serverId, memberId, MEMBER_DID);
    }

    @Test
    void shouldAnswerResolvedWhenTheResolverResolvesTheDid() {
        resolverAnswer = () -> List.of(DidResolution.resolved(MEMBER_DID));

        assertThat(check(ParticipantValueResult.STORED)).isEqualTo(DidCheck.RESOLVED);
        assertThat(resolverCalls).containsExactly(List.of(MEMBER_DID));
    }

    @ParameterizedTest
    @EnumSource(DidResolutionReason.class)
    void shouldAnswerNotResolvedWithTheResolversReason(DidResolutionReason reason) {
        resolverAnswer = () -> List.of(DidResolution.notResolved(MEMBER_DID, reason));

        assertThat(check(ParticipantValueResult.STORED)).isEqualTo(DidCheck.notResolved(reason));
    }

    @Test
    void shouldAnswerResolverUnavailableOnATransportFailure() {
        resolverAnswer = () -> {
            throw XrdRuntimeException.systemException(NETWORK_ERROR).details("DID resolution call failed: UNAVAILABLE").build();
        };

        assertThat(check(ParticipantValueResult.STORED)).isEqualTo(DidCheck.RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldAnswerResolverUnavailableWhenTheResolverAnswersForNoDidOrAnotherDid() {
        resolverAnswer = List::of;
        assertThat(check(ParticipantValueResult.STORED)).isEqualTo(DidCheck.RESOLVER_UNAVAILABLE);

        resolverAnswer = () -> List.of(DidResolution.resolved("did:web:other.example.org%3A7183:v1:DEV:COM:333"));
        assertThat(check(ParticipantValueResult.STORED)).isEqualTo(DidCheck.RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldResolveADidEqualToTheStoredOne() {
        resolverAnswer = () -> List.of(DidResolution.resolved(MEMBER_DID));

        assertThat(check(ParticipantValueResult.UNCHANGED)).isEqualTo(DidCheck.RESOLVED);
        assertThat(resolverCalls).containsExactly(List.of(MEMBER_DID));
    }

    @ParameterizedTest
    @EnumSource(ParticipantValueRejection.class)
    void shouldReturnEachRefusalWithItsReasonAndNeverResolve(ParticipantValueRejection rejection) {
        resolverAnswer = () -> List.of(DidResolution.resolved(MEMBER_DID));

        assertThat(check(ParticipantValueResult.rejected(rejection))).isEqualTo(DidCheck.refused(rejection));
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldFailForAnUnknownMemberWithoutAskingTheStoreOrResolving() {
        when(xRoadMemberRepository.count(memberId)).thenReturn(0L);

        assertThatThrownBy(() -> service.check(serverId, memberId, MEMBER_DID)).isInstanceOf(NotFoundException.class);
        assertThat(resolverCalls).isEmpty();
        verifyNoMoreInteractions(store);
    }

    @Test
    void shouldFailForAnUnknownServerWithoutAskingTheStoreOrResolving() {
        when(xRoadMemberRepository.count(memberId)).thenReturn(1L);
        when(securityServerRepository.findBy(any(SecurityServerId.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.check(serverId, memberId, MEMBER_DID))
                .isInstanceOf(SecurityServerNotFoundException.class);
        assertThat(resolverCalls).isEmpty();
        verifyNoMoreInteractions(store);
    }

    @Test
    void shouldOnlyDryRunTheReplacementAndNeverWrite() {
        resolverAnswer = () -> List.of(DidResolution.resolved(MEMBER_DID));

        check(ParticipantValueResult.STORED);

        verify(store).checkMemberDidReplacements(SERVER_ROW_ID, Map.of(memberId, MEMBER_DID));
        verifyNoMoreInteractions(store);
    }
}
