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
import org.niis.xroad.securityserver.restapi.config.IdentityHubProvisioningRpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * gRPC-backed {@link IdentityHubProvisioningClient} that delegates to {@link IdentityHubProvisioningRpcClient}.
 */
@Component
@RequiredArgsConstructor
public class GrpcIdentityHubProvisioningClient implements IdentityHubProvisioningClient {

    private final IdentityHubProvisioningRpcClient rpcClient;

    @Override
    public void createParticipantContext(String participantContextId, String did, String memberId,
                                         String credentialServiceUrl, String keyId, String privateKeyAlias) {
        rpcClient.createIdentityHubParticipantContext(participantContextId, did, memberId, credentialServiceUrl,
                keyId, privateKeyAlias);
    }

    @Override
    public String requestMembershipCredential(String participantContextId, String issuerDid, String holderPid,
                                              String credentialDefinitionId, String credentialType, String format) {
        return rpcClient.requestMembershipCredential(participantContextId, issuerDid, holderPid,
                credentialDefinitionId, credentialType, format);
    }

    @Override
    @Nullable
    public String getCredentialRequestState(String participantContextId, String holderPid) {
        return rpcClient.getCredentialRequestState(participantContextId, holderPid);
    }

    @Override
    public Optional<String> contextDid(String participantContextId) {
        return rpcClient.getParticipantContextDid(participantContextId);
    }
}
