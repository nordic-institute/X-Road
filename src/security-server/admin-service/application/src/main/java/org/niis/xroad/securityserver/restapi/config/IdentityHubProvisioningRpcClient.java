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
package org.niis.xroad.securityserver.restapi.config;

import io.grpc.ManagedChannel;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.IdentityHubProvisioningServiceGrpc;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * gRPC transport client for the IdentityHub provisioning service.
 */
@Slf4j
@RequiredArgsConstructor
public class IdentityHubProvisioningRpcClient extends AbstractRpcClient implements InitializingBean, DisposableBean {

    private final RpcChannelFactory rpcChannelFactory;
    private final IdentityHubProvisioningRpcChannelProperties channelProperties;

    private ManagedChannel channel;
    private IdentityHubProvisioningServiceGrpc.IdentityHubProvisioningServiceBlockingStub stub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.DS_IDENTITY_HUB;
    }

    @Override
    public ManagedChannel getChannel() {
        return channel;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(),
                channelProperties.host(), channelProperties.port());
        channel = rpcChannelFactory.createChannel(channelProperties);
        stub = IdentityHubProvisioningServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    public void destroy() {
        close();
    }

    public void createIdentityHubParticipantContext(String participantContextId, String did, String memberId,
                                                    String credentialServiceUrl, String keyId, String privateKeyAlias) {
        exec(() -> stub.createParticipantContext(CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .setDid(did)
                .setMemberId(memberId)
                .setCredentialServiceUrl(credentialServiceUrl)
                .setKeyId(keyId)
                .setPrivateKeyAlias(privateKeyAlias)
                .build()));
    }

    public String requestMembershipCredential(String participantContextId, String issuerDid, String holderPid,
                                              String credentialDefinitionId, String credentialType, String format) {
        var response = exec(() -> stub.requestCredential(RequestCredentialReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .setIssuerDid(issuerDid)
                .setHolderPid(holderPid)
                .setCredentialDefinitionId(credentialDefinitionId)
                .setCredentialType(credentialType)
                .setFormat(format)
                .build()));
        return response.getRequestId();
    }

    @Nullable
    public String getCredentialRequestState(String participantContextId, String holderPid) {
        var response = exec(() -> stub.getCredentialRequestState(GetCredentialRequestStateReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .setHolderPid(holderPid)
                .build()));
        return response.getFound() ? response.getStatus() : null;
    }

    public boolean participantContextExists(String participantContextId) {
        var response = exec(() -> stub.getParticipantContextExists(GetParticipantContextExistsReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .build()));
        return response.getExists();
    }
}
