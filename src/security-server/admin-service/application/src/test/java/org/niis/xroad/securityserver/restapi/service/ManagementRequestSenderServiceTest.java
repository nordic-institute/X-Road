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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.common.managementrequest.model.MemberDidRegResponse;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementRequestSenderServiceTest {

    private static final SecurityServerId.Conf SERVER_ID = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
    private static final String SYSTEM_DID = "did:web:ss1.example.org";
    private static final String DSP_BASE_URL = "https://ss1.example.org/dsp";
    private static final byte[] AUTH_CERT = {1, 2, 3};
    private static final ClientId.Conf MEMBER_A = ClientId.Conf.create("DEV", "COM", "111");
    private static final ClientId.Conf MEMBER_B = ClientId.Conf.create("DEV", "COM", "222");
    private static final Map<ClientId.Conf, String> MEMBER_DIDS = memberDids();

    @Mock
    private ManagementRequestSender sender;

    private ManagementRequestSenderService service;

    @BeforeEach
    void setUp() {
        service = spy(new ManagementRequestSenderService(null, null, null, null,
                new CurrentSecurityServerId(SERVER_ID), null, null));
        doReturn(sender).when(service).createManagementRequestSender();
    }

    @Test
    void systemDidRegisterIsStoredWhenResponseCarriesRequestId() throws Exception {
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenReturn(413);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(SystemDidRegistrationSendOutcome.Status.STORED);
        assertThat(outcome.requestId()).isEqualTo(413);
        assertThat(outcome.cause()).isNull();
    }

    @Test
    void systemDidRegisterIsSendFailureWithCauseWhenSendingFails() throws Exception {
        var failure = new IOException("connection refused");
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenThrow(failure);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(SystemDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.requestId()).isNull();
        assertThat(outcome.cause()).isSameAs(failure);
    }

    @Test
    void systemDidRegisterIsSendFailureWithCauseWhenCentralServerAnswersWithFault() throws Exception {
        var failure = XrdRuntimeException.systemInternalError("central server fault");
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenThrow(failure);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(SystemDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.cause()).isSameAs(failure);
    }

    @ParameterizedTest
    @CsvSource({
            "system_did_final_conflict, FINAL_CONFLICT, CERTIFICATE_OF_ANOTHER_SERVER",
            "system_did_fixable_conflict, FIXABLE_CONFLICT, OWNER_CHANGED",
            "system_did_not_resolved, NOT_RESOLVED, RESOLVER_UNAVAILABLE"
    })
    void systemDidRegisterReturnsClassifiedOutcomeWithReasonForClassFault(String faultCode,
                                                                         SystemDidRegistrationSendOutcome.Status expected,
                                                                         String reason) throws Exception {
        var fault = XrdRuntimeException.systemException(ErrorCode.withCode(faultCode)).details(reason).build();
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenThrow(fault);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(expected);
        assertThat(outcome.reason()).isEqualTo(reason);
        assertThat(outcome.requestId()).isNull();
        assertThat(outcome.cause()).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "system_did_final_conflict, FINAL_CONFLICT",
            "system_did_fixable_conflict, FIXABLE_CONFLICT",
            "system_did_not_resolved, NOT_RESOLVED"
    })
    void systemDidRegisterFallsBackToFaultCodeWhenClassFaultHasNoDetails(String faultCode,
                                                                       SystemDidRegistrationSendOutcome.Status expected)
            throws Exception {
        var fault = XrdRuntimeException.systemException(ErrorCode.withCode(faultCode)).build();
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenThrow(fault);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(expected);
        assertThat(outcome.reason()).isEqualTo(faultCode);
    }

    @Test
    void systemDidRegisterIsSendFailureWhenRateLimiterAnswersWithHttpError() throws Exception {
        var failure = XrdRuntimeException.systemException(ErrorCode.HTTP_ERROR,
                "Server responded with error %s: %s", 429, "Too Many Requests");
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenThrow(failure);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(SystemDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.reason()).isNull();
        assertThat(outcome.cause()).isSameAs(failure);
    }

    @Test
    void systemDidRegisterPropagatesProgrammingErrors() throws Exception {
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT))
                .thenThrow(new IllegalArgumentException("bug"));

        assertThatThrownBy(() -> service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemDidRegisterIsSendFailureWhenResponseHasNoRequestId() throws Exception {
        when(sender.sendSystemDidRegRequest(SERVER_ID, SYSTEM_DID, DSP_BASE_URL, AUTH_CERT)).thenReturn(null);

        var outcome = service.sendSystemDidRegisterRequest(SYSTEM_DID, DSP_BASE_URL, AUTH_CERT);

        assertThat(outcome.status()).isEqualTo(SystemDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.cause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void memberDidRegisterReturnsOneRecordPerEntryInResponseOrder() throws Exception {
        when(sender.sendMemberDidRegRequest(SERVER_ID, MEMBER_DIDS)).thenReturn(new MemberDidRegResponse(77, List.of(
                new MemberDidRegResponse.Result(MEMBER_A, "STORED", null),
                new MemberDidRegResponse.Result(MEMBER_B, "NOT_RESOLVED", "RESOLVER_UNAVAILABLE"))));

        var outcome = service.sendMemberDidRegisterRequest(MEMBER_DIDS);

        assertThat(outcome.status()).isEqualTo(MemberDidRegistrationSendOutcome.Status.ANSWERED);
        assertThat(outcome.requestId()).isEqualTo(77);
        assertThat(outcome.cause()).isNull();
        assertThat(outcome.entries()).containsExactly(
                new MemberDidRegistrationSendOutcome.Entry(MEMBER_A, "STORED", null),
                new MemberDidRegistrationSendOutcome.Entry(MEMBER_B, "NOT_RESOLVED", "RESOLVER_UNAVAILABLE"));
    }

    @Test
    void memberDidRegisterIsSendFailureWhenResultsDoNotCoverTheSentMembers() throws Exception {
        when(sender.sendMemberDidRegRequest(SERVER_ID, MEMBER_DIDS)).thenReturn(new MemberDidRegResponse(77, List.of(
                new MemberDidRegResponse.Result(MEMBER_A, "STORED", null))));

        var outcome = service.sendMemberDidRegisterRequest(MEMBER_DIDS);

        assertThat(outcome.status()).isEqualTo(MemberDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.requestId()).isNull();
        assertThat(outcome.entries()).isEmpty();
        assertThat(outcome.cause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void memberDidRegisterIsSendFailureWithCauseWhenSendingFails() throws Exception {
        var failure = new IOException("connection refused");
        when(sender.sendMemberDidRegRequest(SERVER_ID, MEMBER_DIDS)).thenThrow(failure);

        var outcome = service.sendMemberDidRegisterRequest(MEMBER_DIDS);

        assertThat(outcome.status()).isEqualTo(MemberDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.requestId()).isNull();
        assertThat(outcome.entries()).isEmpty();
        assertThat(outcome.cause()).isSameAs(failure);
    }

    @Test
    void memberDidRegisterIsSendFailureWithCauseWhenCentralServerAnswersWithFault() throws Exception {
        var failure = XrdRuntimeException.systemInternalError("central server fault");
        when(sender.sendMemberDidRegRequest(SERVER_ID, MEMBER_DIDS)).thenThrow(failure);

        var outcome = service.sendMemberDidRegisterRequest(MEMBER_DIDS);

        assertThat(outcome.status()).isEqualTo(MemberDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.cause()).isSameAs(failure);
    }

    @Test
    void memberDidRegisterIsSendFailureWhenResponseHasNoRequestId() throws Exception {
        when(sender.sendMemberDidRegRequest(SERVER_ID, MEMBER_DIDS))
                .thenReturn(new MemberDidRegResponse(null, List.of()));

        var outcome = service.sendMemberDidRegisterRequest(MEMBER_DIDS);

        assertThat(outcome.status()).isEqualTo(MemberDidRegistrationSendOutcome.Status.SEND_FAILURE);
        assertThat(outcome.cause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void memberDidRegisterPropagatesProgrammingErrors() throws Exception {
        when(sender.sendMemberDidRegRequest(SERVER_ID, MEMBER_DIDS)).thenThrow(new IllegalArgumentException("bug"));

        assertThatThrownBy(() -> service.sendMemberDidRegisterRequest(MEMBER_DIDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<ClientId.Conf, String> memberDids() {
        var dids = new LinkedHashMap<ClientId.Conf, String>();
        dids.put(MEMBER_A, "did:web:a.example.org");
        dids.put(MEMBER_B, "did:web:b.example.org");
        return dids;
    }
}
