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
package org.niis.xroad.securityserver.restapi.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspaceParticipantProvisioningWorkerTest {

    private static final String OWNER_SLASH_FORM = "TEST/GOV/1234";
    private static final String MEMBER_SLASH_FORM = "TEST/COM/5678";
    private static final String HOST_ID = "xrd-ss0";
    private static final String MGMT_ID = "xrd-ss0-mgmt";
    private static final String MEMBER_ID = "TEST:COM:5678";

    private static final ParticipantContext HOST_CONTEXT = new ParticipantContext(HOST_ID, ParticipantKind.HOST, OWNER_SLASH_FORM);
    private static final ParticipantContext MGMT_CONTEXT = new ParticipantContext(MGMT_ID, ParticipantKind.MANAGEMENT, OWNER_SLASH_FORM);
    private static final ParticipantContext MEMBER_CONTEXT = new ParticipantContext(MEMBER_ID, ParticipantKind.MEMBER, MEMBER_SLASH_FORM);

    @Mock
    private DataspaceProvisioningService dataspaceProvisioningService;
    @Mock
    private ScheduledJobHelper scheduledJobHelper;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;

    @InjectMocks
    private DataspaceParticipantProvisioningWorker worker;

    @Test
    void scheduledProvisionSwallowsFailures() {
        when(scheduledJobHelper.getServerConf()).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> worker.scheduledProvision()).doesNotThrowAnyException();
    }

    @Test
    void provisionParticipantSkipsWhenServerNotInitialized() {
        when(scheduledJobHelper.getServerConf()).thenThrow(mock(XrdRuntimeException.class));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(anyString(), any(), anyString());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(anyString());
    }

    @Test
    void provisionParticipantEnsuresAllContextsAndDefersCredentialUntilAuthCertRegistered() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(HOST_ID, ParticipantKind.HOST, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService).ensureParticipantContext(MGMT_ID, ParticipantKind.MANAGEMENT, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_ID, ParticipantKind.MEMBER, MEMBER_SLASH_FORM);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(anyString());
    }

    @Test
    void provisionParticipantEnsuresCredentialForEachContextWhenAuthCertRegistered() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureMembershipCredential(HOST_ID);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MGMT_ID);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_ID);
    }

    @Test
    void provisionParticipantSkipsRemoteCallsForAlreadyEnsuredContextsOnNextTick() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(HOST_ID, ParticipantKind.HOST, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(MEMBER_ID, ParticipantKind.MEMBER, MEMBER_SLASH_FORM);
    }

    @Test
    void provisionParticipantStopsCheckingCredentialOnceIssuedButKeepsCheckingPending() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.ensureMembershipCredential(HOST_ID))
                .thenReturn(DataspaceProvisioningService.STATUS_ISSUED);
        when(dataspaceProvisioningService.ensureMembershipCredential(MEMBER_ID))
                .thenReturn(DataspaceProvisioningService.STATUS_PENDING);

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(dataspaceProvisioningService, times(1)).ensureMembershipCredential(HOST_ID);
        verify(dataspaceProvisioningService, times(2)).ensureMembershipCredential(MEMBER_ID);
    }

    @Test
    void provisionParticipantContinuesWithRemainingContextsWhenOneFails() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT, MGMT_CONTEXT));
        doThrow(new IllegalStateException("pinned row mismatch"))
                .when(dataspaceProvisioningService).ensureParticipantContext(MEMBER_ID, ParticipantKind.MEMBER, MEMBER_SLASH_FORM);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureParticipantContext(HOST_ID, ParticipantKind.HOST, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService).ensureParticipantContext(MGMT_ID, ParticipantKind.MANAGEMENT, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService).ensureMembershipCredential(HOST_ID);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MGMT_ID);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_ID);
    }

    @Test
    void provisionParticipantContinuesWithRemainingCredentialsWhenOneCredentialStepFails() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.ensureMembershipCredential(HOST_ID)).thenThrow(new IllegalStateException("ih down"));

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_ID);
    }

    @Test
    void provisionParticipantSkipsWhenOwnerIsNull() {
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(null);
        when(scheduledJobHelper.getServerConf()).thenReturn(serverConf);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(anyString(), any(), anyString());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(anyString());
    }

    private void givenInitializedServer() {
        var ownerId = mock(ClientIdEntity.class);
        when(ownerId.getXRoadInstance()).thenReturn("TEST");
        when(ownerId.getMemberClass()).thenReturn("GOV");
        when(ownerId.getMemberCode()).thenReturn("1234");
        var owner = mock(ClientEntity.class);
        when(owner.getIdentifier()).thenReturn(ownerId);
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(owner);
        when(scheduledJobHelper.getServerConf()).thenReturn(serverConf);
    }
}
