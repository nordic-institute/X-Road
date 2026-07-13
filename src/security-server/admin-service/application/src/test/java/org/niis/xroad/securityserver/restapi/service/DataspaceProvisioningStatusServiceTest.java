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
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService.DataspaceStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.STATUS_ABSENT;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.STATUS_ISSUED;
import static org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.STATUS_UNKNOWN;

@ExtendWith(MockitoExtension.class)
class DataspaceProvisioningStatusServiceTest {

    private static final String PARTICIPANT_ID = "test-participant";
    private static final String MGMT_PARTICIPANT_ID = PARTICIPANT_ID + "-mgmt";
    private static final String HOLDER_PID_SLOT0 = PARTICIPANT_ID + "-xroad-membership-credential-request";

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceProvisioningClient provisioningClient;

    private DataspaceProvisioningService provisioningService;
    private DataspaceProvisioningStatusService statusService;

    @BeforeEach
    void setUp() {
        var dataspace = new Dataspace();
        dataspace.setParticipantId(PARTICIPANT_ID);
        dataspace.setIdentityHubUrl("https://ih.example.test");
        dataspace.setIssuerDid("did:web:issuer.example.test");
        dataspace.setCredentialDefinitionId("xroad-membership-credential-definition");
        dataspace.setMaxHolderPidSlots(20);
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);

        provisioningService = new DataspaceProvisioningService(adminServiceProperties, provisioningClient);

        statusService = new DataspaceProvisioningStatusService(
                provisioningService, readinessPredicates);
    }

    @Test
    void readStatusHostOnlySsContextCreatedAndIssued() {
        when(readinessPredicates.isManagementSubsystemRegistered()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(provisioningClient.contextExists(PARTICIPANT_ID)).thenReturn(true);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ISSUED);

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.participantContexts()).hasSize(1);
        var ctx = status.participantContexts().get(0);
        assertThat(ctx.participantId()).isEqualTo(PARTICIPANT_ID);
        assertThat(ctx.kind()).isEqualTo(ParticipantKind.HOST);
        assertThat(ctx.contextCreated()).isTrue();
        assertThat(ctx.credentialStatus()).isEqualTo(STATUS_ISSUED);
    }

    @Test
    void readStatusHostPlusManagementSsTwoContextsReturned() {
        when(readinessPredicates.isManagementSubsystemRegistered()).thenReturn(true);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(provisioningClient.contextExists(PARTICIPANT_ID)).thenReturn(true);
        when(provisioningClient.contextExists(MGMT_PARTICIPANT_ID)).thenReturn(false);
        when(provisioningClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(STATUS_ISSUED);

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(2);
        var host = status.participantContexts().get(0);
        var mgmt = status.participantContexts().get(1);
        assertThat(host.kind()).isEqualTo(ParticipantKind.HOST);
        assertThat(host.contextCreated()).isTrue();
        assertThat(host.credentialStatus()).isEqualTo(STATUS_ISSUED);
        assertThat(mgmt.kind()).isEqualTo(ParticipantKind.MANAGEMENT);
        assertThat(mgmt.contextCreated()).isFalse();
        assertThat(mgmt.credentialStatus()).isEqualTo(STATUS_ABSENT);
    }

    @Test
    void readStatusBackendUnreachableReportsUnknownInsteadOf5xx() {
        when(readinessPredicates.isManagementSubsystemRegistered()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(provisioningClient.contextExists(anyString()))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR).build());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.participantContexts()).hasSize(1);
        var ctx = status.participantContexts().get(0);
        assertThat(ctx.contextCreated()).isFalse();
        assertThat(ctx.credentialStatus()).isEqualTo(STATUS_UNKNOWN);
    }

    @Test
    void readStatusSsNotInitializedManagementTreatedAsNotRegistered() {
        when(readinessPredicates.isManagementSubsystemRegistered()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(provisioningClient.contextExists(PARTICIPANT_ID)).thenReturn(false);

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(1);
        assertThat(status.participantContexts().get(0).kind()).isEqualTo(ParticipantKind.HOST);
        assertThat(status.participantContexts().get(0).contextCreated()).isFalse();
        assertThat(status.participantContexts().get(0).credentialStatus()).isEqualTo(STATUS_ABSENT);
    }
}
