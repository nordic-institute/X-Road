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
package org.niis.xroad.cs.management.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.request.AuthCertDeletionRequestType;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.ClientRequestType;
import ee.ria.xroad.common.request.MemberDidRegEntryType;
import ee.ria.xroad.common.request.MemberDidRegRequestType;
import ee.ria.xroad.common.request.MemberDidRegResultType;
import ee.ria.xroad.common.request.SystemDidRegRequestType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.client.FeignManagementRequestsApi;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationEntryDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationResultDto;
import org.niis.xroad.cs.openapi.model.MemberDidRegistrationStatusDto;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_DELETION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.CLIENT_ENABLE_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.MEMBER_DID_REGISTRATION_REQUEST;
import static org.niis.xroad.common.managementrequest.model.ManagementRequestType.SYSTEM_DID_REGISTRATION_REQUEST;

@ExtendWith(MockitoExtension.class)
class ManagementRequestServiceImplTest {
    private static final Integer REQUEST_ID = 123;

    @Mock
    private FeignManagementRequestsApi managementRequestsApi;
    @Mock
    private SecurityServerIdConverter securityServerIdConverter;
    @Mock
    private ClientIdConverter clientIdConverter;

    @InjectMocks
    private ManagementRequestServiceImpl managementRequestService;

    @Test
    void shouldAddManagementRequestSucceed() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().body(new ManagementRequestDto().id(REQUEST_ID)));

        ClientRequestType request = new ClientRequestType();

        Integer result = managementRequestService.addManagementRequestWithPayload(request, CLIENT_ENABLE_REQUEST).requestId();

        assertThat(result).isEqualTo(REQUEST_ID);
    }

    @Test
    void shouldAddManagementCertDeletionRequestSucceed() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().body(new ManagementRequestDto().id(REQUEST_ID)));

        AuthCertDeletionRequestType request = new AuthCertDeletionRequestType();

        Integer result = managementRequestService.addManagementRequestWithPayload(request, AUTH_CERT_DELETION_REQUEST).requestId();

        assertThat(result).isEqualTo(REQUEST_ID);
    }

    @Test
    void shouldAddManagementRequestThrownXrdRuntimeException() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().build());

        ClientRequestType request = new ClientRequestType();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> managementRequestService.addManagementRequestWithPayload(request, CLIENT_ENABLE_REQUEST));
    }

    @Test
    void shouldAddManagementCertDeletionRequestThrownXrdRuntimeException() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().build());

        AuthCertDeletionRequestType request = new AuthCertDeletionRequestType();

        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> managementRequestService.addManagementRequestWithPayload(request, AUTH_CERT_DELETION_REQUEST));
    }

    @Test
    void shouldMapMemberDidRegRequestAndAnswerOneResultElementPerEntry() {
        var server = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
        var memberA = ClientId.Conf.create("DEV", "COM", "222");
        var memberB = ClientId.Conf.create("DEV", "COM", "222", "SUB1");
        when(securityServerIdConverter.convertId(server)).thenReturn("DEV:COM:222:SS1");
        when(clientIdConverter.convertId(memberA)).thenReturn("DEV:COM:222");
        when(clientIdConverter.convertId(memberB)).thenReturn("DEV:COM:222:SUB1");
        when(clientIdConverter.convertId("DEV:COM:222")).thenReturn(memberA);
        when(clientIdConverter.convertId("DEV:COM:222:SUB1")).thenReturn(memberB);
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.ok(new MemberDidRegistrationRequestDto()
                        .results(List.of(
                                new MemberDidRegistrationResultDto().memberId("DEV:COM:222")
                                        .status(MemberDidRegistrationStatusDto.STORED),
                                new MemberDidRegistrationResultDto().memberId("DEV:COM:222:SUB1")
                                        .status(MemberDidRegistrationStatusDto.FIXABLE_CONFLICT).reason("SOME_REASON")))
                        .id(REQUEST_ID)));
        var request = new MemberDidRegRequestType();
        request.setServer(server);
        request.getEntry().add(entry(memberA, "did:web:a"));
        request.getEntry().add(entry(memberB, "did:web:b"));

        var payload = managementRequestService.addManagementRequestWithPayload(request, MEMBER_DID_REGISTRATION_REQUEST);

        var captor = ArgumentCaptor.forClass(ManagementRequestDto.class);
        verify(managementRequestsApi).addManagementRequest(captor.capture());
        var sent = (MemberDidRegistrationRequestDto) captor.getValue();
        assertThat(sent.getType()).isEqualTo(ManagementRequestTypeDto.MEMBER_DID_REGISTRATION_REQUEST);
        assertThat(sent.getSecurityServerId()).isEqualTo("DEV:COM:222:SS1");
        assertThat(sent.getEntries()).containsExactly(
                new MemberDidRegistrationEntryDto().memberId("DEV:COM:222").did("did:web:a"),
                new MemberDidRegistrationEntryDto().memberId("DEV:COM:222:SUB1").did("did:web:b"));
        assertThat(payload.requestId()).isEqualTo(REQUEST_ID);
        assertThat(payload.elements()).hasSize(2);
        var first = (MemberDidRegResultType) payload.elements().getFirst().getValue();
        var second = (MemberDidRegResultType) payload.elements().get(1).getValue();
        assertThat(first.getMember()).isEqualTo(memberA);
        assertThat(first.getStatus()).isEqualTo("STORED");
        assertThat(first.getReason()).isNull();
        assertThat(second.getMember()).isEqualTo(memberB);
        assertThat(second.getStatus()).isEqualTo("FIXABLE_CONFLICT");
        assertThat(second.getReason()).isEqualTo("SOME_REASON");
    }

    @Test
    void shouldAnswerRequestIdOnlyForOtherTypes() {
        when(managementRequestsApi.addManagementRequest(any()))
                .thenReturn(ResponseEntity.accepted().body(new ManagementRequestDto().id(REQUEST_ID)));

        var payload = managementRequestService.addManagementRequestWithPayload(new ClientRequestType(), CLIENT_ENABLE_REQUEST);

        assertThat(payload.requestId()).isEqualTo(REQUEST_ID);
        assertThat(payload.elements()).isEmpty();
    }

    private static MemberDidRegEntryType entry(ClientId.Conf member, String did) {
        var entry = new MemberDidRegEntryType();
        entry.setMember(member);
        entry.setDid(did);
        return entry;
    }

    @ParameterizedTest
    @MethodSource("directRequestTypes")
    void shouldRejectDirectRequestTypesWithoutAdminApiCall(Object request, ManagementRequestType requestType) {
        assertThatExceptionOfType(XrdRuntimeException.class)
                .isThrownBy(() -> managementRequestService.addManagementRequestWithPayload(request, requestType))
                .satisfies(e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.code()));

        verifyNoInteractions(managementRequestsApi);
    }

    private static Stream<Arguments> directRequestTypes() {
        return Stream.of(
                Arguments.of(new AuthCertRegRequestType(), AUTH_CERT_REGISTRATION_REQUEST),
                Arguments.of(new SystemDidRegRequestType(), SYSTEM_DID_REGISTRATION_REQUEST));
    }
}
