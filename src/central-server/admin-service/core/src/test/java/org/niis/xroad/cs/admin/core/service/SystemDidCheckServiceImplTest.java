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

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.SystemDidCheck;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.FOREIGN_SYSTEM_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NO_STORED_VALUE;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNPARSEABLE_REGISTERED_ADDRESS;

class SystemDidCheckServiceImplTest {
    private static final String ADDRESS = "ss.example.org";
    private static final String SYSTEM_DID = "did:web:ss.example.org%3A7183:v1:system";
    private static final int SERVER_ROW_ID = 5;

    private final SecurityServerId serverId = SecurityServerId.Conf.create("DEV", "COM", "222", "SS0");
    private final List<List<String>> resolverCalls = new ArrayList<>();
    private Supplier<List<DidResolution>> resolverAnswer = List::of;

    private final SecurityServerRepository securityServerRepository = mock(SecurityServerRepository.class);
    private final SecurityServerEntity server = mock(SecurityServerEntity.class);
    private final DidResolutionService resolver = dids -> {
        resolverCalls.add(dids);
        return resolverAnswer.get();
    };
    private final SystemDidCheckServiceImpl service = new SystemDidCheckServiceImpl(securityServerRepository, resolver);

    private SystemDidCheck check(String address, String did) {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        when(server.getId()).thenReturn(SERVER_ROW_ID);
        when(server.getSystemDid()).thenReturn(SYSTEM_DID);
        return service.check(serverId, address, did);
    }

    @Test
    void shouldAnswerResolvedWhenTheResolverResolvesTheDid() {
        resolverAnswer = () -> List.of(DidResolution.resolved(SYSTEM_DID));

        assertThat(check(ADDRESS, SYSTEM_DID)).isEqualTo(SystemDidCheck.RESOLVED);
        assertThat(resolverCalls).containsExactly(List.of(SYSTEM_DID));
    }

    @ParameterizedTest
    @EnumSource(DidResolutionReason.class)
    void shouldAnswerNotResolvedWithTheResolversReason(DidResolutionReason reason) {
        resolverAnswer = () -> List.of(DidResolution.notResolved(SYSTEM_DID, reason));

        assertThat(check(ADDRESS, SYSTEM_DID)).isEqualTo(SystemDidCheck.notResolved(reason));
    }

    @Test
    void shouldAnswerResolverUnavailableOnATransportFailure() {
        resolverAnswer = () -> {
            throw XrdRuntimeException.systemException(NETWORK_ERROR).details("DID resolution call failed: UNAVAILABLE").build();
        };

        assertThat(check(ADDRESS, SYSTEM_DID)).isEqualTo(SystemDidCheck.RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldRethrowAResolverFailureThatIsNotATransportFailure() {
        var failure = XrdRuntimeException.systemException(INTERNAL_ERROR).details("unknown outcome").build();
        resolverAnswer = () -> {
            throw failure;
        };

        assertThatThrownBy(() -> check(ADDRESS, SYSTEM_DID)).isSameAs(failure);
    }

    @Test
    void shouldAnswerResolverUnavailableWhenTheResolverAnswersForNoDidOrAnotherDid() {
        resolverAnswer = List::of;
        assertThat(check(ADDRESS, SYSTEM_DID)).isEqualTo(SystemDidCheck.RESOLVER_UNAVAILABLE);

        resolverAnswer = () -> List.of(DidResolution.resolved("did:web:other.example.org%3A7183:v1:system"));
        assertThat(check(ADDRESS, SYSTEM_DID)).isEqualTo(SystemDidCheck.RESOLVER_UNAVAILABLE);
    }

    @Test
    void shouldRefuseADidWhoseHostDiffersFromTheProposedAddressWithoutResolving() {
        assertThat(check("other.example.org", SYSTEM_DID)).isEqualTo(SystemDidCheck.refused(HOST_DIFFERS));
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldRefuseADidThatIsNotTheServersSystemDidWithoutResolving() {
        var memberDid = "did:web:ss.example.org%3A7183:v1:DEV:COM:222";

        assertThat(check(ADDRESS, memberDid)).isEqualTo(SystemDidCheck.refused(FOREIGN_SYSTEM_DID));
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldRefuseWithoutResolvingWhenTheProposedAddressIsUnusable() {
        assertThat(check("", SYSTEM_DID)).isEqualTo(SystemDidCheck.refused(UNPARSEABLE_REGISTERED_ADDRESS));
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldRefuseADidHeldByAnotherServerWithoutResolving() {
        when(securityServerRepository.existsBySystemDidAndIdNot(SYSTEM_DID, SERVER_ROW_ID)).thenReturn(true);

        assertThat(check(ADDRESS, SYSTEM_DID)).isEqualTo(SystemDidCheck.refused(DID_HELD_BY_ANOTHER_SERVER));
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldRefuseAServerWithNoStoredPairBeforeCheckingTheDid() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        when(server.getSystemDid()).thenReturn(null);

        assertThat(service.check(serverId, "other.example.org", SYSTEM_DID)).isEqualTo(SystemDidCheck.refused(NO_STORED_VALUE));
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldFailForAnUnknownServerWithoutResolving() {
        when(securityServerRepository.findBy(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.check(serverId, ADDRESS, SYSTEM_DID))
                .isInstanceOf(SecurityServerNotFoundException.class);
        assertThat(resolverCalls).isEmpty();
    }

    @Test
    void shouldStoreNothing() {
        resolverAnswer = () -> List.of(DidResolution.resolved(SYSTEM_DID));

        check(ADDRESS, SYSTEM_DID);

        verify(securityServerRepository).findBy(serverId);
        verify(securityServerRepository).existsBySystemDidAndIdNot(SYSTEM_DID, SERVER_ROW_ID);
        verifyNoMoreInteractions(securityServerRepository);
    }
}
