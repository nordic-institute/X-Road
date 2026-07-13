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
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspaceParticipantProvisioningWorkerTest {

    private static final String OWNER_SLASH_FORM = "TEST/GOV/1234";
    private static final String HOST_ID = "xrd-ss0";
    private static final String MGMT_ID = "xrd-ss0-mgmt";

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

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(anyString(), anyString());
        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    @Test
    void provisionParticipantEnsuresContextsOnlyForAbsentParticipantsAndDefersCredentialUntilAuthCertRegistered() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContextIds(true)).thenReturn(List.of(HOST_ID, MGMT_ID));
        when(dataspaceProvisioningService.contextExists(HOST_ID)).thenReturn(false);
        when(dataspaceProvisioningService.contextExists(MGMT_ID)).thenReturn(true);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(HOST_ID, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(MGMT_ID, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    @Test
    void provisionParticipantSkipsEnsureForAlreadyExistingContexts() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContextIds(true)).thenReturn(List.of(HOST_ID, MGMT_ID));
        when(dataspaceProvisioningService.contextExists(HOST_ID)).thenReturn(true);
        when(dataspaceProvisioningService.contextExists(MGMT_ID)).thenReturn(true);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(anyString(), anyString());
        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    @Test
    void provisionParticipantSubmitsCredentialWhenAbsentAndAuthCertRegistered() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContextIds(true)).thenReturn(List.of(HOST_ID, MGMT_ID));
        when(dataspaceProvisioningService.contextExists(HOST_ID)).thenReturn(true);
        when(dataspaceProvisioningService.contextExists(MGMT_ID)).thenReturn(true);
        when(dataspaceProvisioningService.readCredentialStatus(HOST_ID)).thenReturn("ERROR");
        when(dataspaceProvisioningService.readCredentialStatus(MGMT_ID)).thenReturn(null);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).submitCredentialRequest(HOST_ID);
        verify(dataspaceProvisioningService).submitCredentialRequest(MGMT_ID);
    }

    @Test
    void provisionParticipantDoesNotResubmitWhenCredentialIssuedOrPending() {
        givenInitializedServer();
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContextIds(true)).thenReturn(List.of(HOST_ID, MGMT_ID));
        when(dataspaceProvisioningService.contextExists(HOST_ID)).thenReturn(true);
        when(dataspaceProvisioningService.contextExists(MGMT_ID)).thenReturn(true);
        when(dataspaceProvisioningService.readCredentialStatus(HOST_ID))
                .thenReturn(DataspaceProvisioningService.STATUS_ISSUED);
        when(dataspaceProvisioningService.readCredentialStatus(MGMT_ID))
                .thenReturn(DataspaceProvisioningService.STATUS_PENDING);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    @Test
    void provisionParticipantSkipsWhenOwnerIsNull() {
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(null);
        when(scheduledJobHelper.getServerConf()).thenReturn(serverConf);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(anyString(), anyString());
        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
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
