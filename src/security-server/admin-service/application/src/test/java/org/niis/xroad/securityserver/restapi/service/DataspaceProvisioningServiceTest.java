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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.STATUS_ERROR;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.STATUS_ISSUED;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.STATUS_PENDING;

@ExtendWith(MockitoExtension.class)
class DataspaceProvisioningServiceTest {

    private static final String PARTICIPANT_ID = "test-participant";
    private static final String HOLDER_PID_SLOT0 = PARTICIPANT_ID + "-xroad-membership-credential-request";
    private static final String HOLDER_PID_SLOT1 = PARTICIPANT_ID + "-xroad-membership-credential-request-1";
    private static final String HOLDER_PID_SLOT2 = PARTICIPANT_ID + "-xroad-membership-credential-request-2";

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private DataspaceProvisioningClient provisioningClient;

    private DataspaceProvisioningService service;
    private Dataspace dataspace;

    @BeforeEach
    void setUp() {
        dataspace = new Dataspace();
        dataspace.setEnabled(true);
        dataspace.setParticipantId(PARTICIPANT_ID);
        dataspace.setIdentityHubUrl("https://ih.example.test");
        dataspace.setIssuerDid("did:web:issuer.example.test");
        dataspace.setCredentialDefinitionId("xroad-membership-credential-definition");
        dataspace.setMaxHolderPidSlots(20);
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        service = new DataspaceProvisioningService(adminServiceProperties, provisioningClient);
    }

    // --- submitCredentialRequest ---

    @Test
    void submitCredentialRequestSubmitsIntoSlot0WhenNoExistingRequest() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(null);

        service.submitCredentialRequest(PARTICIPANT_ID);

        verify(provisioningClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT0),
                anyString(), anyString(), anyString());
    }

    @Test
    void submitCredentialRequestNoOpWhenSlot0IsPending() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_PENDING);

        service.submitCredentialRequest(PARTICIPANT_ID);

        verify(provisioningClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitCredentialRequestNoOpWhenSlot0IsIssued() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ISSUED);

        service.submitCredentialRequest(PARTICIPANT_ID);

        verify(provisioningClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitCredentialRequestAdvancesPastErrorSlot() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(null);

        service.submitCredentialRequest(PARTICIPANT_ID);

        verify(provisioningClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT1),
                anyString(), anyString(), anyString());
    }

    @Test
    void submitCredentialRequestAdvancesPastMultipleErrorSlots() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT2)).thenReturn(null);

        service.submitCredentialRequest(PARTICIPANT_ID);

        verify(provisioningClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT2),
                anyString(), anyString(), anyString());
    }

    @Test
    void submitCredentialRequestNoSubmitWhenAllSlotsExhausted() {
        dataspace.setMaxHolderPidSlots(2);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(STATUS_ERROR);

        service.submitCredentialRequest(PARTICIPANT_ID);

        verify(provisioningClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    // --- readCredentialStatus ---

    @Test
    void readCredentialStatusReturnsNullWhenNoRequests() {
        dataspace.setMaxHolderPidSlots(2);
        when(provisioningClient.getCredentialRequestState(eq(PARTICIPANT_ID), anyString())).thenReturn(null);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isNull();
    }

    @Test
    void readCredentialStatusReturnsIssuedWhenSlot0Issued() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ISSUED);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(STATUS_ISSUED);
    }

    @Test
    void readCredentialStatusReturnsPendingWhenSlot0Pending() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_PENDING);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(STATUS_PENDING);
    }

    @Test
    void readCredentialStatusSkipsErrorSlotAndReturnsNextActiveStatus() {
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(STATUS_PENDING);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(STATUS_PENDING);
    }

    @Test
    void readCredentialStatusReturnsNullWhenAllSlotsError() {
        dataspace.setMaxHolderPidSlots(2);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(STATUS_ERROR);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isNull();
    }

    @Test
    void readCredentialStatusStopsAtFirstActiveSlotAfterErrors() {
        dataspace.setMaxHolderPidSlots(20);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ERROR);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(STATUS_ISSUED);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(STATUS_ISSUED);
        // slot2..N are not queried
        verify(provisioningClient, times(2)).getCredentialRequestState(eq(PARTICIPANT_ID), anyString());
    }

    // --- participantContextIds ---

    @Test
    void participantContextIdsReturnsOnlyHostWhenManagementNotRegistered() {
        var ids = service.participantContextIds(false);

        assertThat(ids).containsExactly(PARTICIPANT_ID);
    }

    @Test
    void participantContextIdsIncludesMgmtWhenManagementRegistered() {
        var ids = service.participantContextIds(true);

        assertThat(ids).containsExactly(PARTICIPANT_ID, PARTICIPANT_ID + "-mgmt");
    }

    // --- ensureParticipantContexts ---

    @Test
    void ensureParticipantContextsCreatesOnlyHostContextWhenManagementNotRegistered() {
        service.ensureParticipantContexts(false, "TEST/ORG/CODE");

        verify(provisioningClient).createIdentityHubParticipantContext(eq(PARTICIPANT_ID), any(), any(), any(), any(), any());
        verify(provisioningClient).createControlPlaneParticipantContext(eq(PARTICIPANT_ID), any());
        verify(provisioningClient).putControlPlaneParticipantContextConfig(eq(PARTICIPANT_ID), any(), any());
    }

    @Test
    void ensureParticipantContextsCreatesBothContextsWhenManagementRegistered() {
        var mgmtId = PARTICIPANT_ID + "-mgmt";

        service.ensureParticipantContexts(true, "TEST/ORG/CODE");

        verify(provisioningClient).createIdentityHubParticipantContext(eq(PARTICIPANT_ID), any(), any(), any(), any(), any());
        verify(provisioningClient).createIdentityHubParticipantContext(eq(mgmtId), any(), any(), any(), any(), any());
        verify(provisioningClient).createControlPlaneParticipantContext(eq(PARTICIPANT_ID), any());
        verify(provisioningClient).createControlPlaneParticipantContext(eq(mgmtId), any());
    }
}
