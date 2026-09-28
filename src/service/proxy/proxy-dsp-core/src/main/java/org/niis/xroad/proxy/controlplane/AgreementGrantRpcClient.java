/*
 * The MIT License
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

package org.niis.xroad.proxy.controlplane;

import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.edc.agreementgrant.proto.AgreementGrantServiceGrpc;
import org.niis.xroad.edc.agreementgrant.proto.ResolveAgreementGrantRequest;

import java.util.Optional;

/**
 * Resolves the grant behind a negotiated agreement from the control plane, over the same gRPC server
 * {@link AssetAccessRpcClient} already talks to. Never throws: any gRPC error, deadline, or an explicit
 * {@code NoGrant} answer — including the {@code SUBJECT_NOT_A_SUBSYSTEM} case, which is how the control plane
 * already enforces the member/group-subject minting ban — comes back as an empty result, logged without the
 * agreement id's associated grant details (only the id itself, never token or grant material).
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class AgreementGrantRpcClient extends AbstractRpcClient {

    private final RpcChannelFactory rpcChannelFactory;
    private final AssetAccessRpcChannelProperties channelProperties;

    private ManagedChannel channel;
    private AgreementGrantServiceGrpc.AgreementGrantServiceBlockingStub grantServiceBlockingStub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.PROXY;
    }

    @Override
    public ManagedChannel getChannel() {
        return channel;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(),
                channelProperties.host(), channelProperties.port());
        channel = rpcChannelFactory.createChannel(channelProperties);
        grantServiceBlockingStub = AgreementGrantServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    /**
     * @param agreementId the negotiated agreement's id
     * @return the resolved grant, or empty when the control plane states no grant or the call otherwise fails
     */
    public Optional<AgreementGrant> resolveAgreementGrant(String agreementId) {
        try {
            var request = ResolveAgreementGrantRequest.newBuilder().setAgreementId(agreementId).build();
            var response = exec(() -> grantServiceBlockingStub.resolveAgreementGrant(request));
            if (!response.hasGrant()) {
                log.info("No agreement grant for agreement id '{}': {}", agreementId,
                        response.hasNoGrant() ? response.getNoGrant().getReason() : "no grant stated");
                return Optional.empty();
            }
            return Optional.of(toAgreementGrant(response.getGrant()));
        } catch (Exception e) {
            log.warn("Failed to resolve agreement grant for agreement id '{}'", agreementId, e);
            return Optional.empty();
        }
    }

    private static AgreementGrant toAgreementGrant(org.niis.xroad.edc.agreementgrant.proto.Grant grant) {
        var scope = grant.getScopesList().stream()
                .map(s -> new AgreementTokenScope(s.getMethod(), s.getPath()))
                .toList();
        return new AgreementGrant(ClientIdMapper.fromDto(grant.getClientId()), ServiceIdMapper.fromDto(grant.getServiceId()), scope);
    }
}
