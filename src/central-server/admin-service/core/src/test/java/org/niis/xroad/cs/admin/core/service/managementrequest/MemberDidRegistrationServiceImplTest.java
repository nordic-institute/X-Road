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
import org.niis.xroad.common.core.exception.DeviationAware;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_DUPLICATE_MEMBER;

class MemberDidRegistrationServiceImplTest {
    private static final ClientId MEMBER_1 = ClientId.Conf.create("DEV", "COM", "111");
    private static final ClientId MEMBER_2 = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId MEMBER_3 = ClientId.Conf.create("DEV", "COM", "333");
    private static final String DID_1 = "did:web:ss.example.org:v1:member:DEV:COM:111";
    private static final String DID_2 = "did:web:ss.example.org:v1:member:DEV:COM:222";
    private static final String DID_3 = "did:web:ss.example.org:v1:member:DEV:COM:333";

    private final SecurityServerId securityServerId = SecurityServerId.create("DEV", "COM", "222", "SS0");
    private final List<String> calls = new ArrayList<>();
    private Function<List<String>, List<DidResolution>> resolverAnswer = dids -> dids.stream().map(DidResolution::resolved).toList();

    private final ManagementRequestService managementRequestService = mock(ManagementRequestService.class);
    private final DidResolutionService resolver = dids -> {
        calls.add("resolve" + dids);
        return resolverAnswer.apply(dids);
    };

    private MemberDidRegistrationServiceImpl service(MemberDidRegistrationRequest request) {
        when(managementRequestService.add(request)).thenAnswer(inv -> {
            calls.add("add");
            return request;
        });
        return new MemberDidRegistrationServiceImpl(resolver, managementRequestService);
    }

    private MemberDidRegistrationRequest threeEntries() {
        return new MemberDidRegistrationRequest(SECURITY_SERVER, securityServerId,
                List.of(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_2, DID_2), new Entry(MEMBER_3, DID_3)));
    }

    @Test
    void resolvesAllDidsInOneCallThenAddsWithNothingSettled() {
        var request = threeEntries();

        var result = service(request).add(request);

        assertThat(calls).containsExactly("resolve[" + DID_1 + ", " + DID_2 + ", " + DID_3 + "]", "add");
        assertThat(result).isSameAs(request);
        assertThat(result.getSettledResults()).isEmpty();
    }

    @Test
    void settlesTheEntryTheResolverCouldNotResolveAndLeavesTheOthersForTheStore() {
        var request = threeEntries();
        resolverAnswer = dids -> List.of(
                DidResolution.resolved(DID_1),
                DidResolution.notResolved(DID_2, DidResolutionReason.FETCH_FAILURE),
                DidResolution.resolved(DID_3));

        var result = service(request).add(request);

        assertThat(calls).hasSize(2);
        assertThat(result.getSettledResults())
                .containsOnlyKeys(1)
                .containsEntry(1, new Result(MEMBER_2, Status.NOT_RESOLVED, DidResolutionReason.FETCH_FAILURE.name()));
    }

    @Test
    void resolverTransportFailureSettlesEveryEntryAsNotResolved() {
        var request = threeEntries();
        resolverAnswer = dids -> {
            throw XrdRuntimeException.systemException(NETWORK_ERROR).details("DID resolution call failed: UNAVAILABLE").build();
        };

        var result = service(request).add(request);

        assertThat(result.getSettledResults()).containsOnlyKeys(0, 1, 2);
        assertThat(result.getSettledResults().values())
                .allSatisfy(settled -> {
                    assertThat(settled.status()).isEqualTo(Status.NOT_RESOLVED);
                    assertThat(settled.reason()).isEqualTo(SystemDidRegistrationOutcome.RESOLVER_UNAVAILABLE);
                });
        assertThat(calls).endsWith("add");
    }

    @Test
    void anAnswerForAnotherDidIsNotResolved() {
        var request = threeEntries();
        resolverAnswer = dids -> List.of(
                DidResolution.resolved(DID_1),
                DidResolution.resolved(DID_3),
                DidResolution.resolved(DID_2));

        var result = service(request).add(request);

        assertThat(result.getSettledResults()).containsOnlyKeys(1, 2);
    }

    @Test
    void aMissingAnswerIsNotResolved() {
        var request = threeEntries();
        resolverAnswer = dids -> List.of(DidResolution.resolved(DID_1));

        var result = service(request).add(request);

        assertThat(result.getSettledResults()).containsOnlyKeys(1, 2);
    }

    @Test
    void aBatchNamingTheSameMemberTwiceIsRefusedBeforeResolvingAndAdding() {
        var request = new MemberDidRegistrationRequest(SECURITY_SERVER, securityServerId,
                List.of(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_1, DID_2)));
        var service = service(request);

        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((DeviationAware) e).getErrorDeviation().code())
                        .isEqualTo(MR_DID_DUPLICATE_MEMBER.build().code()));

        assertThat(calls).isEmpty();
        verify(managementRequestService, never()).add(any(MemberDidRegistrationRequest.class));
    }
}
