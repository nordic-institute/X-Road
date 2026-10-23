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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Result;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;

@ExtendWith(MockitoExtension.class)
class MemberDidRegistrationRequestHandlerTest {
    private static final int SERVER_ROW_ID = 7;
    private static final ClientId MEMBER_1 = ClientId.Conf.create("DEV", "COM", "111");
    private static final ClientId MEMBER_2 = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId MEMBER_3 = ClientId.Conf.create("DEV", "COM", "333");
    private static final String DID_1 = "did:web:ss.example.org:v1:member:DEV:COM:111";
    private static final String DID_2 = "did:web:ss.example.org:v1:member:DEV:COM:222";
    private static final String DID_3 = "did:web:ss.example.org:v1:member:DEV:COM:333";

    private final SecurityServerId securityServerId = SecurityServerId.create("DEV", "COM", "222", "SS0");
    private final List<Entry> entries = List.of(new Entry(MEMBER_1, DID_1), new Entry(MEMBER_2, DID_2), new Entry(MEMBER_3, DID_3));
    private final MemberDidRegistrationRequest request = new MemberDidRegistrationRequest(SECURITY_SERVER, securityServerId, entries);

    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Mock
    private RequestRepository<MemberDidRegistrationRequestEntity> memberDidRegistrationRequests;
    @Mock
    private SecurityServerRepository servers;
    @Mock
    private ParticipantValueStore participantValueStore;
    @Mock
    private RequestMapper requestMapper;

    @InjectMocks
    private MemberDidRegistrationRequestHandler handler;

    @Captor
    private ArgumentCaptor<MemberDidRegistrationRequestEntity> entityCaptor;
    @Captor
    private ArgumentCaptor<Map<ClientId, String>> writtenCaptor;

    @Test
    void canAutoApproveIsFalse() {
        assertThat(handler.canAutoApprove(request)).isFalse();
    }

    @Test
    void addLocksTheServerRowThenWritesOnceAndSavesTheRequestRowWithoutEntries() {
        stubRowSaved();
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.of(SERVER_ROW_ID));
        when(participantValueStore.writeMemberDidsOnce(anyInt(), anyMap())).thenReturn(storedFor(MEMBER_1, MEMBER_2, MEMBER_3));

        var result = handler.add(request);

        InOrder order = inOrder(servers, participantValueStore, memberDidRegistrationRequests);
        order.verify(servers).lockForUpdateBy(securityServerId);
        order.verify(participantValueStore).writeMemberDidsOnce(anyInt(), anyMap());
        order.verify(memberDidRegistrationRequests).save(any());
        verify(participantValueStore).writeMemberDidsOnce(eq(SERVER_ROW_ID), writtenCaptor.capture());
        assertThat(writtenCaptor.getValue()).containsExactly(
                Map.entry(MEMBER_1, DID_1), Map.entry(MEMBER_2, DID_2), Map.entry(MEMBER_3, DID_3));
        assertThat(entityCaptor.getValue().getManagementRequestType()).isEqualTo(ManagementRequestType.MEMBER_DID_REGISTRATION_REQUEST);
        assertThat(entityCaptor.getValue().getOrigin()).isEqualTo(SECURITY_SERVER);
        assertThat(result.getResults()).containsExactly(
                new Result(MEMBER_1, Status.STORED, null),
                new Result(MEMBER_2, Status.STORED, null),
                new Result(MEMBER_3, Status.STORED, null));
    }

    @Test
    void addWritesOnlyTheEntriesTheResolverLetThroughAndKeepsRequestOrder() {
        stubRowSaved();
        request.setSettledResults(Map.of(1, new Result(MEMBER_2, Status.NOT_RESOLVED, "FETCH_FAILURE")));
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.of(SERVER_ROW_ID));
        when(participantValueStore.writeMemberDidsOnce(anyInt(), anyMap())).thenReturn(storedFor(MEMBER_1, MEMBER_3));

        var result = handler.add(request);

        verify(participantValueStore).writeMemberDidsOnce(eq(SERVER_ROW_ID), writtenCaptor.capture());
        assertThat(writtenCaptor.getValue()).containsOnlyKeys(MEMBER_1, MEMBER_3);
        assertThat(result.getResults()).containsExactly(
                new Result(MEMBER_1, Status.STORED, null),
                new Result(MEMBER_2, Status.NOT_RESOLVED, "FETCH_FAILURE"),
                new Result(MEMBER_3, Status.STORED, null));
    }

    @Test
    void addAnswersARejectedEntryAsFixableConflictWithTheStoreReasonAndStoresTheOthers() {
        stubRowSaved();
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.of(SERVER_ROW_ID));
        var written = new LinkedHashMap<ClientId, ParticipantValueResult>();
        written.put(MEMBER_1, ParticipantValueResult.STORED);
        written.put(MEMBER_2, ParticipantValueResult.rejected(ParticipantValueRejection.VALUE_DIFFERS));
        written.put(MEMBER_3, ParticipantValueResult.STORED);
        when(participantValueStore.writeMemberDidsOnce(anyInt(), anyMap())).thenReturn(written);

        var result = handler.add(request);

        assertThat(result.getResults()).containsExactly(
                new Result(MEMBER_1, Status.STORED, null),
                new Result(MEMBER_2, Status.FIXABLE_CONFLICT, ParticipantValueRejection.VALUE_DIFFERS.name()),
                new Result(MEMBER_3, Status.STORED, null));
        verify(memberDidRegistrationRequests).save(any());
    }

    @Test
    void addAnswersAnEqualResendAsStoredAndLeavesOneRequestRow() {
        stubRowSaved();
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.of(SERVER_ROW_ID));
        var written = new LinkedHashMap<ClientId, ParticipantValueResult>();
        written.put(MEMBER_1, ParticipantValueResult.UNCHANGED);
        written.put(MEMBER_2, ParticipantValueResult.UNCHANGED);
        written.put(MEMBER_3, ParticipantValueResult.UNCHANGED);
        when(participantValueStore.writeMemberDidsOnce(anyInt(), anyMap())).thenReturn(written);

        var result = handler.add(request);

        assertThat(result.getResults()).extracting(Result::status).containsOnly(Status.STORED);
        assertThat(result.getResults()).extracting(Result::reason).containsOnlyNulls();
        verify(memberDidRegistrationRequests).save(any(MemberDidRegistrationRequestEntity.class));
    }

    @Test
    void addAnswersEveryEntryOwnerChangedAndWritesNothingWhenTheServerRowIsGone() {
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.empty());

        var result = handler.add(request);

        assertThat(result.getResults()).containsExactly(
                new Result(MEMBER_1, Status.FIXABLE_CONFLICT, MemberDidRegistrationRequestHandler.OWNER_CHANGED),
                new Result(MEMBER_2, Status.FIXABLE_CONFLICT, MemberDidRegistrationRequestHandler.OWNER_CHANGED),
                new Result(MEMBER_3, Status.FIXABLE_CONFLICT, MemberDidRegistrationRequestHandler.OWNER_CHANGED));
        verify(participantValueStore, never()).writeMemberDidsOnce(anyInt(), anyMap());
        verify(memberDidRegistrationRequests, never()).save(any());
        verify(serverIds, never()).findOne(any());
    }

    @Test
    void addDoesNotCallTheStoreWhenEveryEntryIsAlreadySettled() {
        stubRowSaved();
        request.setSettledResults(Map.of(
                0, new Result(MEMBER_1, Status.NOT_RESOLVED, "RESOLVER_UNAVAILABLE"),
                1, new Result(MEMBER_2, Status.NOT_RESOLVED, "RESOLVER_UNAVAILABLE"),
                2, new Result(MEMBER_3, Status.NOT_RESOLVED, "RESOLVER_UNAVAILABLE")));
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.of(SERVER_ROW_ID));

        var result = handler.add(request);

        assertThat(result.getResults()).extracting(Result::status).containsOnly(Status.NOT_RESOLVED);
        verify(participantValueStore, never()).writeMemberDidsOnce(anyInt(), anyMap());
    }

    @Test
    void approveReturnsTheRequestUnchanged() {
        assertThat(handler.approve(request)).isSameAs(request);
    }

    @Test
    void requestTypeIsTheMemberDidRegistrationRequest() {
        assertThat(handler.requestType()).isEqualTo(MemberDidRegistrationRequest.class);
    }

    private void stubRowSaved() {
        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mock(SecurityServerIdEntity.class));
        when(memberDidRegistrationRequests.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(requestMapper.toDto(any(MemberDidRegistrationRequestEntity.class))).thenReturn(new MemberDidRegistrationRequest());
    }

    private static Map<ClientId, ParticipantValueResult> storedFor(ClientId... members) {
        var written = new LinkedHashMap<ClientId, ParticipantValueResult>();
        for (var member : members) {
            written.put(member, ParticipantValueResult.STORED);
        }
        return written;
    }
}
