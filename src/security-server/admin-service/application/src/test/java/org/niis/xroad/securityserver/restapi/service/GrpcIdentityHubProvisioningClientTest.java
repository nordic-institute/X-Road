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
import org.niis.xroad.securityserver.restapi.config.IdentityHubProvisioningRpcClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcIdentityHubProvisioningClientTest {

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

    @Mock
    private IdentityHubProvisioningRpcClient rpcClient;

    @InjectMocks
    private GrpcIdentityHubProvisioningClient client;

    @Test
    void createParticipantContextDelegatesToRpcClient() {
        client.createParticipantContext(CTX_ID, DID, MEMBER_ID, CRED_SERVICE_URL, KEY_ID, KEY_ALIAS);

        verify(rpcClient).createIdentityHubParticipantContext(CTX_ID, DID, MEMBER_ID, CRED_SERVICE_URL, KEY_ID, KEY_ALIAS);
    }

    @Test
    void requestMembershipCredentialDelegatesToRpcClientAndReturnsRequestId() {
        when(rpcClient.requestMembershipCredential(CTX_ID, ISSUER_DID, HOLDER_PID, CRED_DEF_ID, CRED_TYPE, FORMAT))
                .thenReturn("req-id-1");

        var result = client.requestMembershipCredential(CTX_ID, ISSUER_DID, HOLDER_PID, CRED_DEF_ID, CRED_TYPE, FORMAT);

        assertThat(result).isEqualTo("req-id-1");
        verify(rpcClient).requestMembershipCredential(CTX_ID, ISSUER_DID, HOLDER_PID, CRED_DEF_ID, CRED_TYPE, FORMAT);
    }

    @Test
    void getCredentialRequestStateDelegatesToRpcClientAndReturnsStatus() {
        when(rpcClient.getCredentialRequestState(CTX_ID, HOLDER_PID)).thenReturn("PENDING");

        var result = client.getCredentialRequestState(CTX_ID, HOLDER_PID);

        assertThat(result).isEqualTo("PENDING");
        verify(rpcClient).getCredentialRequestState(CTX_ID, HOLDER_PID);
    }

    @Test
    void getCredentialRequestStateReturnsNullWhenRpcClientReturnsNull() {
        when(rpcClient.getCredentialRequestState(CTX_ID, HOLDER_PID)).thenReturn(null);

        var result = client.getCredentialRequestState(CTX_ID, HOLDER_PID);

        assertThat(result).isNull();
    }

    @Test
    void contextDidDelegatesToRpcClientAndReturnsDid() {
        when(rpcClient.getParticipantContextDid(CTX_ID)).thenReturn(Optional.of("did:web:example"));

        var result = client.contextDid(CTX_ID);

        assertThat(result).contains("did:web:example");
        verify(rpcClient).getParticipantContextDid(CTX_ID);
    }

    @Test
    void contextDidReturnsEmptyWhenRpcClientReturnsEmpty() {
        when(rpcClient.getParticipantContextDid(CTX_ID)).thenReturn(Optional.empty());

        assertThat(client.contextDid(CTX_ID)).isEmpty();
    }
}
