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

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.config.ControlPlaneProvisioningRpcClient;
import org.niis.xroad.securityserver.restapi.config.IdentityHubProvisioningRpcClient;
import org.springframework.stereotype.Component;

/**
 * gRPC-backed {@link DataspaceProvisioningClient} that delegates to the per-runtime transport clients.
 */
@Component
@RequiredArgsConstructor
public class GrpcDataspaceProvisioningClient implements DataspaceProvisioningClient {

    private final IdentityHubProvisioningRpcClient identityHubClient;
    private final ControlPlaneProvisioningRpcClient controlPlaneClient;

    @Override
    public void createIdentityHubParticipantContext(String participantContextId, String did, String memberId,
                                                    String credentialServiceUrl, String keyId, String privateKeyAlias) {
        identityHubClient.createIdentityHubParticipantContext(participantContextId, did, memberId, credentialServiceUrl,
                keyId, privateKeyAlias);
    }

    @Override
    public String requestMembershipCredential(String participantContextId, String issuerDid, String holderPid,
                                              String credentialDefinitionId, String credentialType, String format) {
        return identityHubClient.requestMembershipCredential(participantContextId, issuerDid, holderPid,
                credentialDefinitionId, credentialType, format);
    }

    @Override
    @Nullable
    public String getCredentialRequestState(String participantContextId, String holderPid) {
        return identityHubClient.getCredentialRequestState(participantContextId, holderPid);
    }

    @Override
    public void createControlPlaneParticipantContext(String participantContextId, String did) {
        controlPlaneClient.createParticipantContext(participantContextId, did);
    }

    @Override
    public void putControlPlaneParticipantContextConfig(String participantContextId, String did, String stsTokenUrl) {
        controlPlaneClient.putParticipantContextConfig(participantContextId, did, stsTokenUrl);
    }
}
