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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.DeviationAware;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionOutcome;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck.CheckedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_OF_OTHER_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_TOO_LONG;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DSP_BASE_URL_NOT_HTTPS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.FOREIGN_SYSTEM_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MALFORMED_DSP_BASE_URL;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MEMBER_NOT_SERVED;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NON_ASCII_HOST;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NOT_CANONICAL;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNDECODABLE_DID;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNPARSEABLE_REGISTERED_ADDRESS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.WRONG_SCHEME;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FINAL_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FIXABLE_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_NOT_RESOLVED;

class SystemDidRegistrationServiceImplTest {
    private static final int SERVER_ROW_ID = 7;
    private static final String ADDRESS = "ss.example.org";
    private static final String NON_ASCII_ADDRESS = "ss.exämple.org";
    private static final String SYSTEM_DID = "did:web:ss.example.org:v1:system";
    private static final String DSP_BASE_URL = "https://ss.example.org:7183/dsp";
    private static final byte[] CERTIFICATE = {1, 2, 3};

    private final SecurityServerId securityServerId = SecurityServerId.create("DEV", "COM", "222", "SS0");
    private final SystemDidRegistrationRequest request =
            new SystemDidRegistrationRequest(SECURITY_SERVER, securityServerId, SYSTEM_DID, DSP_BASE_URL, CERTIFICATE);

    private final List<String> calls = new ArrayList<>();
    private String registeredAddress = ADDRESS;
    private SystemPairCheck storeVerdict = SystemPairCheck.ABSENT;
    private Supplier<DidResolution> resolverAnswer = () -> fail("the resolver must not be called");
    private List<DidResolution> resolverList;
    private RuntimeException certificateFailure;

    private final AuthCertServerCheck authCertServerCheck = mock(AuthCertServerCheck.class);
    private final ParticipantValueStore store = mock(ParticipantValueStore.class);
    private final ManagementRequestService managementRequestService = mock(ManagementRequestService.class);
    private final DidResolutionService resolver = dids -> {
        calls.add("resolve" + dids);
        return resolverList != null ? resolverList : List.of(resolverAnswer.get());
    };

    private SystemDidRegistrationServiceImpl service() {
        when(authCertServerCheck.requireServer(any(), any())).thenAnswer(inv -> {
            calls.add("certificate");
            if (certificateFailure != null) {
                throw certificateFailure;
            }
            return new CheckedServer(SERVER_ROW_ID, registeredAddress);
        });
        when(store.checkSystemPair(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL)).thenAnswer(inv -> {
            calls.add("store");
            return storeVerdict;
        });
        when(managementRequestService.add(request)).thenAnswer(inv -> {
            calls.add("add");
            return request;
        });
        return new SystemDidRegistrationServiceImpl(authCertServerCheck, store, resolver, managementRequestService);
    }

    @Test
    void checksCertificateThenStoreThenResolvesOneDidThenAdds() {
        resolverAnswer = () -> DidResolution.resolved(SYSTEM_DID);

        var result = service().add(request);

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]", "add");
        assertThat(result).isSameAs(request);
    }

    @ParameterizedTest
    @MethodSource("verdictsThatResolve")
    void resolvesBeforeAddingWhenThePairIsNotYetStored(SystemPairCheck verdict) {
        storeVerdict = verdict;
        resolverAnswer = () -> DidResolution.resolved(SYSTEM_DID);

        service().add(request);

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]", "add");
    }

    @Test
    void equalPairIsAddedWithoutResolving() {
        storeVerdict = SystemPairCheck.EQUAL;

        service().add(request);

        assertThat(calls).containsExactly("certificate", "store", "add");
    }

    @ParameterizedTest
    @MethodSource("certificateFailures")
    void certificateFailureIsRaisedBeforeTheStoreAndTheResolver(RuntimeException failure) {
        certificateFailure = failure;

        var service = service();
        assertThatThrownBy(() -> service.add(request)).isSameAs(failure);

        assertThat(calls).containsExactly("certificate");
    }

    @ParameterizedTest
    @MethodSource("rejectionRows")
    void storeRejectionIsClassifiedAndNeverResolvedOrAdded(ParticipantValueRejection rejection, String address, String code) {
        registeredAddress = address;
        storeVerdict = SystemPairCheck.rejected(rejection);

        var service = service();
        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(deviationOf(e)).isEqualTo(new ErrorDeviation(code, List.of(rejection.name()))));

        assertThat(calls).doesNotContain("add").noneMatch(call -> call.startsWith("resolve"));
    }

    @Test
    void everyStoreRejectionHasARow() {
        var covered = rejectionRows().map(row -> row.get()[0]).toList();

        assertThat(covered).containsAll(List.of(ParticipantValueRejection.values()));
    }

    @Test
    void valueDifferingFromTheStoredPairIsFixableAndNeverResolvedOrAdded() {
        storeVerdict = SystemPairCheck.VALUE_DIFFERS;

        var service = service();
        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(deviationOf(e)).isEqualTo(MR_DID_FIXABLE_CONFLICT.build(VALUE_DIFFERS.name())));

        assertThat(calls).containsExactly("certificate", "store");
    }

    @ParameterizedTest
    @MethodSource("unresolvedRows")
    void unresolvedDidIsNotResolvedAndNotAdded(SystemPairCheck verdict, DidResolutionReason reason) {
        storeVerdict = verdict;
        resolverAnswer = () -> DidResolution.notResolved(SYSTEM_DID, reason);

        var service = service();
        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(deviationOf(e)).isEqualTo(MR_DID_NOT_RESOLVED.build(reason.name())));

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]");
    }

    @Test
    void resolverTransportFailureIsNotResolved() {
        resolverAnswer = () -> {
            throw XrdRuntimeException.systemException(NETWORK_ERROR).details("DID resolution call failed: UNAVAILABLE").build();
        };

        var service = service();
        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(deviationOf(e))
                        .isEqualTo(MR_DID_NOT_RESOLVED.build(SystemDidRegistrationOutcome.RESOLVER_UNAVAILABLE)));

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]");
    }

    @Test
    void emptyResolverAnswerIsNotResolvedAndNotAdded() {
        resolverList = List.of();

        assertNotResolvedUnavailable();
    }

    @Test
    void resolverAnswerForAnotherDidIsNotResolvedAndNotAdded() {
        resolverList = List.of(DidResolution.resolved("did:web:other.example.org:v1:system"));

        assertNotResolvedUnavailable();
    }

    @Test
    void notResolvedAnswerWithoutReasonIsResolverUnavailable() {
        resolverAnswer = () -> new DidResolution(SYSTEM_DID, DidResolutionOutcome.NOT_RESOLVED, null);

        assertNotResolvedUnavailable();
    }

    private void assertNotResolvedUnavailable() {
        var service = service();
        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(deviationOf(e))
                        .isEqualTo(MR_DID_NOT_RESOLVED.build(SystemDidRegistrationOutcome.RESOLVER_UNAVAILABLE)));

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]");
    }

    private static ErrorDeviation deviationOf(Throwable e) {
        return ((DeviationAware) e).getErrorDeviation();
    }

    private static Stream<SystemPairCheck> verdictsThatResolve() {
        return Stream.of(SystemPairCheck.ABSENT, SystemPairCheck.STALE_REPLACEABLE);
    }

    private static Stream<RuntimeException> certificateFailures() {
        return Stream.of(SystemDidRegistrationOutcome.certificateOfAnotherServer().toException(),
                SystemDidRegistrationOutcome.ownerChanged().toException());
    }

    private static Stream<Arguments> unresolvedRows() {
        return verdictsThatResolve().flatMap(verdict -> Stream.of(DidResolutionReason.values())
                .map(reason -> Arguments.of(verdict, reason)));
    }

    private static Stream<Arguments> rejectionRows() {
        var finalCode = MR_DID_FINAL_CONFLICT.code();
        var fixableCode = MR_DID_FIXABLE_CONFLICT.code();
        return Stream.of(
                row(NOT_CANONICAL, ADDRESS, finalCode),
                row(DID_TOO_LONG, ADDRESS, finalCode),
                row(WRONG_SCHEME, ADDRESS, finalCode),
                row(DSP_BASE_URL_NOT_HTTPS, ADDRESS, finalCode),
                row(MALFORMED_DSP_BASE_URL, ADDRESS, finalCode),
                row(FOREIGN_SYSTEM_DID, ADDRESS, finalCode),
                row(UNDECODABLE_DID, ADDRESS, finalCode),
                row(DID_OF_OTHER_MEMBER, ADDRESS, finalCode),
                row(MEMBER_NOT_SERVED, ADDRESS, fixableCode),
                row(NON_ASCII_HOST, ADDRESS, finalCode),
                row(HOST_DIFFERS, ADDRESS, fixableCode),
                row(UNPARSEABLE_REGISTERED_ADDRESS, ADDRESS, fixableCode),
                row(VALUE_DIFFERS, ADDRESS, fixableCode),
                row(DID_HELD_BY_ANOTHER_SERVER, ADDRESS, fixableCode),
                row(NON_ASCII_HOST, NON_ASCII_ADDRESS, fixableCode));
    }

    private static Arguments row(ParticipantValueRejection rejection, String address, String code) {
        return Arguments.of(rejection, address, code);
    }
}
