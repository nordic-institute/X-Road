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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.DeviationAware;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.SystemDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck.CheckedServer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FINAL_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FIXABLE_CONFLICT;

@ExtendWith(MockitoExtension.class)
class SystemDidRegistrationRequestHandlerTest {
    private static final int SERVER_ROW_ID = 7;
    private static final String SYSTEM_DID = "did:web:ss.example.org:v1:system";
    private static final String DSP_BASE_URL = "https://ss.example.org:7183/dsp";
    private static final byte[] CERTIFICATE = {1, 2, 3};

    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");
    private final SystemDidRegistrationRequest request =
            new SystemDidRegistrationRequest(SECURITY_SERVER, securityServerId, SYSTEM_DID, DSP_BASE_URL, CERTIFICATE);

    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Mock
    private RequestRepository<SystemDidRegistrationRequestEntity> systemDidRegistrationRequests;
    @Mock
    private SecurityServerRepository servers;
    @Mock
    private AuthCertServerCheck authCertServerCheck;
    @Mock
    private ParticipantValueStore participantValueStore;
    @Mock
    private RequestMapper requestMapper;

    @InjectMocks
    private SystemDidRegistrationRequestHandler handler;

    @Captor
    private ArgumentCaptor<SystemDidRegistrationRequestEntity> entityCaptor;

    @Test
    void canAutoApproveIsFalse() {
        assertThat(handler.canAutoApprove(request)).isFalse();
    }

    @Test
    void addStoresThePairWriteOnceWithTheRequestValuesAndSavesTheRequestRow() {
        var serverIdEntity = mock(SecurityServerIdEntity.class);
        var mapped = mock(SystemDidRegistrationRequest.class);
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE)).thenReturn(checkedServer());
        when(participantValueStore.writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL)).thenReturn(ParticipantValueResult.STORED);
        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(serverIdEntity);
        when(systemDidRegistrationRequests.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(requestMapper.toDto(any(SystemDidRegistrationRequestEntity.class))).thenReturn(mapped);

        var result = handler.add(request);

        verify(participantValueStore).writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL);
        assertThat(entityCaptor.getValue().getManagementRequestType()).isEqualTo(ManagementRequestType.SYSTEM_DID_REGISTRATION_REQUEST);
        assertThat(entityCaptor.getValue().getOrigin()).isEqualTo(SECURITY_SERVER);
        assertThat(result).isSameAs(mapped);
    }

    @Test
    void addLocksTheServerRowBeforeTheCertificateCheckAndWritesAfterBoth() {
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE)).thenReturn(checkedServer());
        when(participantValueStore.writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL)).thenReturn(ParticipantValueResult.STORED);
        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mock(SecurityServerIdEntity.class));
        when(systemDidRegistrationRequests.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.add(request);

        InOrder order = inOrder(servers, authCertServerCheck, participantValueStore);
        order.verify(servers).lockForUpdateBy(securityServerId);
        order.verify(authCertServerCheck).requireServer(securityServerId, CERTIFICATE);
        order.verify(participantValueStore).writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL);
    }

    @Test
    void addLeavesTheOwnerChangedClassificationToTheCertificateCheckWhenThereIsNoRowToLock() {
        when(servers.lockForUpdateBy(securityServerId)).thenReturn(Optional.empty());
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE))
                .thenThrow(SystemDidRegistrationOutcome.ownerChanged().toException());

        assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((DeviationAware) e).getErrorDeviation())
                        .isEqualTo(MR_DID_FIXABLE_CONFLICT.build(SystemDidRegistrationOutcome.OWNER_CHANGED)));

        verify(participantValueStore, never()).writeSystemPairOnce(any(Integer.class), any(), any());
        verify(systemDidRegistrationRequests, never()).save(any());
    }

    @Test
    void addAcceptsAnUnchangedPair() {
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE)).thenReturn(checkedServer());
        when(participantValueStore.writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL))
                .thenReturn(ParticipantValueResult.UNCHANGED);
        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(mock(SecurityServerIdEntity.class));
        when(systemDidRegistrationRequests.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.add(request);

        verify(systemDidRegistrationRequests).save(any());
    }

    @Test
    void addRejectsWhenTheCertificateCheckFailsAndWritesNothing() {
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE)).thenThrow(mock(ConflictException.class));

        assertThatThrownBy(() -> handler.add(request)).isInstanceOf(ConflictException.class);

        verify(participantValueStore, never()).writeSystemPairOnce(any(Integer.class), any(), any());
        verify(systemDidRegistrationRequests, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(ParticipantValueRejection.class)
    void addMapsEveryStoreRejectionThroughTheOutcomeTableAndSavesNoRow(ParticipantValueRejection rejection) {
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE)).thenReturn(checkedServer());
        when(participantValueStore.writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL))
                .thenReturn(ParticipantValueResult.rejected(rejection));

        assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((DeviationAware) e).getErrorDeviation()).isEqualTo(expectedDeviation(rejection)));

        verify(systemDidRegistrationRequests, never()).save(any());
    }

    @Test
    void addTreatsANonAsciiHostAsFixableWhenTheRegisteredAddressIsNonAscii() {
        when(authCertServerCheck.requireServer(securityServerId, CERTIFICATE))
                .thenReturn(new CheckedServer(SERVER_ROW_ID, "ss.exämple.org"));
        when(participantValueStore.writeSystemPairOnce(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL))
                .thenReturn(ParticipantValueResult.rejected(ParticipantValueRejection.NON_ASCII_HOST));

        assertThatThrownBy(() -> handler.add(request))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((DeviationAware) e).getErrorDeviation())
                        .isEqualTo(MR_DID_FIXABLE_CONFLICT.build("NON_ASCII_HOST")));

        verify(systemDidRegistrationRequests, never()).save(any());
    }

    @Test
    void handlesOnlySystemDidRegistrationRequests() {
        assertThat(handler.requestType()).isEqualTo(SystemDidRegistrationRequest.class);
    }

    private static CheckedServer checkedServer() {
        return new CheckedServer(SERVER_ROW_ID, "ss.example.org");
    }

    private static ErrorDeviation expectedDeviation(ParticipantValueRejection rejection) {
        return switch (rejection) {
            case NOT_CANONICAL, DID_TOO_LONG, WRONG_SCHEME, DSP_BASE_URL_NOT_HTTPS, MALFORMED_DSP_BASE_URL,
                 FOREIGN_SYSTEM_DID, UNRELATED_MEMBER, NON_ASCII_HOST -> MR_DID_FINAL_CONFLICT.build(rejection.name());
            case HOST_DIFFERS, UNPARSEABLE_REGISTERED_ADDRESS, VALUE_DIFFERS, DID_HELD_BY_ANOTHER_SERVER ->
                    MR_DID_FIXABLE_CONFLICT.build(rejection.name());
        };
    }
}
