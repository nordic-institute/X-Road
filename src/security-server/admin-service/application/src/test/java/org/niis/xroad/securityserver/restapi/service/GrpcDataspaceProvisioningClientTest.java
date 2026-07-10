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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.config.ControlPlaneProvisioningRpcClient;
import org.niis.xroad.securityserver.restapi.config.IdentityHubProvisioningRpcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcDataspaceProvisioningClientTest {

    private static final String CTX_ID = "test-ctx";
    private static final String DID = "did:web:example";
    private static final String MEMBER_ID = "TEST/GOV/1234";
    private static final String CRED_SERVICE_URL = "https://cred.example/v1";
    private static final String KEY_ID = DID + "#key-1";
    private static final String KEY_ALIAS = CTX_ID + "-key";
    private static final String ISSUER_DID = "did:web:issuer.example";
    private static final String HOLDER_PID = "holder-pid-0";
    private static final String CRED_DEF_ID = "xroad-membership-credential-definition";
    private static final String CRED_TYPE = "XRoadMembershipCredential";
    private static final String FORMAT = "VC1_0_JWT";
    private static final String STS_TOKEN_URL = "https://sts.example/token";

    @Mock
    private IdentityHubProvisioningRpcClient identityHubClient;
    @Mock
    private ControlPlaneProvisioningRpcClient controlPlaneClient;

    @InjectMocks
    private GrpcDataspaceProvisioningClient client;

    @Test
    void createIdentityHubParticipantContextRoutsToIhClient() {
        client.createIdentityHubParticipantContext(CTX_ID, DID, MEMBER_ID, CRED_SERVICE_URL, KEY_ID, KEY_ALIAS);

        verify(identityHubClient).createIdentityHubParticipantContext(CTX_ID, DID, MEMBER_ID, CRED_SERVICE_URL, KEY_ID, KEY_ALIAS);
        verify(controlPlaneClient, never()).createParticipantContext(CTX_ID, DID);
    }

    @Test
    void requestMembershipCredentialRoutsToIhClient() {
        when(identityHubClient.requestMembershipCredential(CTX_ID, ISSUER_DID, HOLDER_PID, CRED_DEF_ID, CRED_TYPE, FORMAT))
                .thenReturn("req-id-1");

        var result = client.requestMembershipCredential(CTX_ID, ISSUER_DID, HOLDER_PID, CRED_DEF_ID, CRED_TYPE, FORMAT);

        assertThat(result).isEqualTo("req-id-1");
        verify(identityHubClient).requestMembershipCredential(CTX_ID, ISSUER_DID, HOLDER_PID, CRED_DEF_ID, CRED_TYPE, FORMAT);
        verify(controlPlaneClient, never()).createParticipantContext(CTX_ID, DID);
    }

    @Test
    void getCredentialRequestStateRoutsToIhClientAndReturnsStatus() {
        when(identityHubClient.getCredentialRequestState(CTX_ID, HOLDER_PID)).thenReturn("PENDING");

        var result = client.getCredentialRequestState(CTX_ID, HOLDER_PID);

        assertThat(result).isEqualTo("PENDING");
        verify(identityHubClient).getCredentialRequestState(CTX_ID, HOLDER_PID);
        verify(controlPlaneClient, never()).createParticipantContext(CTX_ID, DID);
    }

    @Test
    void getCredentialRequestStateReturnsNullWhenIhClientReturnsNull() {
        when(identityHubClient.getCredentialRequestState(CTX_ID, HOLDER_PID)).thenReturn(null);

        var result = client.getCredentialRequestState(CTX_ID, HOLDER_PID);

        assertThat(result).isNull();
    }

    @Test
    void contextExistsRoutsToIhClient() {
        when(identityHubClient.participantContextExists(CTX_ID)).thenReturn(true);

        var result = client.contextExists(CTX_ID);

        assertThat(result).isTrue();
        verify(identityHubClient).participantContextExists(CTX_ID);
        verify(controlPlaneClient, never()).createParticipantContext(CTX_ID, DID);
    }

    @Test
    void contextExistsReturnsFalseWhenIhClientReturnsFalse() {
        when(identityHubClient.participantContextExists(CTX_ID)).thenReturn(false);

        assertThat(client.contextExists(CTX_ID)).isFalse();
    }

    @Test
    void createControlPlaneParticipantContextRoutsToCpClient() {
        client.createControlPlaneParticipantContext(CTX_ID, DID);

        verify(controlPlaneClient).createParticipantContext(CTX_ID, DID);
        verify(identityHubClient, never()).participantContextExists(CTX_ID);
    }

    @Test
    void putControlPlaneParticipantContextConfigRoutsToCpClient() {
        client.putControlPlaneParticipantContextConfig(CTX_ID, DID, STS_TOKEN_URL);

        verify(controlPlaneClient).putParticipantContextConfig(CTX_ID, DID, STS_TOKEN_URL);
        verify(identityHubClient, never()).participantContextExists(CTX_ID);
    }
}
