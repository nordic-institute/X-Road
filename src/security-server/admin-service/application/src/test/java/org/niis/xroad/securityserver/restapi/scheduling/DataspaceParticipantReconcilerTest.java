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
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspaceParticipantReconcilerTest {

    private static final String OWNER_SLASH_FORM = "TEST/GOV/1234";
    private static final String HOST_ID = "xrd-ss0";
    private static final String MGMT_ID = "xrd-ss0-mgmt";

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private DataspaceProvisioningService dataspaceProvisioningService;
    @Mock
    private ScheduledJobHelper scheduledJobHelper;
    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private SignerRpcClient signerRpcClient;

    @InjectMocks
    private DataspaceParticipantReconciler reconciler;

    @Test
    void scheduledReconcileSkipsWhenDataspaceDisabled() {
        withDataspaceEnabled(false);

        reconciler.scheduledReconcile();

        verifyNoInteractions(scheduledJobHelper, dataspaceProvisioningService, signerRpcClient);
    }

    @Test
    void scheduledReconcileSwallowsFailures() {
        withDataspaceEnabled(true);
        when(scheduledJobHelper.getServerConf()).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> reconciler.scheduledReconcile()).doesNotThrowAnyException();
    }

    @Test
    void reconcileSkipsWhenServerNotInitialized() {
        when(scheduledJobHelper.getServerConf()).thenThrow(mock(XrdRuntimeException.class));

        reconciler.reconcile();

        verify(dataspaceProvisioningService, never()).ensureParticipantContexts(anyBoolean(), anyString());
        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    @Test
    void reconcileEnsuresContextsButDefersCredentialUntilAuthCertRegistered() {
        givenInitializedServer();
        withoutRegisteredAuthCert();

        reconciler.reconcile();

        verify(dataspaceProvisioningService).ensureParticipantContexts(true, OWNER_SLASH_FORM);
        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    @Test
    void reconcileSubmitsCredentialWhenAbsentAndAuthCertRegistered() {
        givenInitializedServer();
        withRegisteredAuthCert();
        when(dataspaceProvisioningService.participantContextIds(true)).thenReturn(List.of(HOST_ID, MGMT_ID));
        when(dataspaceProvisioningService.readCredentialStatus(HOST_ID)).thenReturn("ERROR");
        when(dataspaceProvisioningService.readCredentialStatus(MGMT_ID)).thenReturn(null);

        reconciler.reconcile();

        verify(dataspaceProvisioningService).submitCredentialRequest(HOST_ID);
        verify(dataspaceProvisioningService).submitCredentialRequest(MGMT_ID);
    }

    @Test
    void reconcileDoesNotResubmitWhenCredentialIssuedOrPending() {
        givenInitializedServer();
        withRegisteredAuthCert();
        when(dataspaceProvisioningService.participantContextIds(true)).thenReturn(List.of(HOST_ID, MGMT_ID));
        when(dataspaceProvisioningService.readCredentialStatus(HOST_ID))
                .thenReturn(DataspaceProvisioningService.STATUS_ISSUED);
        when(dataspaceProvisioningService.readCredentialStatus(MGMT_ID))
                .thenReturn(DataspaceProvisioningService.STATUS_PENDING);

        reconciler.reconcile();

        verify(dataspaceProvisioningService, never()).submitCredentialRequest(anyString());
    }

    private void withDataspaceEnabled(boolean enabled) {
        var dataspace = new AdminServiceProperties.Dataspace();
        dataspace.setEnabled(enabled);
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
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
        when(serverConf.getClients()).thenReturn(Set.of());
        when(scheduledJobHelper.getServerConf()).thenReturn(serverConf);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
    }

    private void withoutRegisteredAuthCert() {
        when(signerRpcClient.getTokens()).thenReturn(List.of());
    }

    private void withRegisteredAuthCert() {
        var cert = mock(CertificateInfo.class);
        when(cert.getStatus()).thenReturn(CertificateInfo.STATUS_REGISTERED);
        var key = mock(KeyInfo.class);
        when(key.getUsage()).thenReturn(KeyUsageInfo.AUTHENTICATION);
        when(key.getCerts()).thenReturn(List.of(cert));
        var token = mock(TokenInfo.class);
        when(token.getKeyInfo()).thenReturn(List.of(key));
        when(signerRpcClient.getTokens()).thenReturn(List.of(token));
    }
}
