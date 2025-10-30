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
package org.niis.xroad.confclient.rpc;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.confclient.proto.AdminServiceGrpc;
import org.niis.xroad.confclient.proto.AnchorServiceGrpc;
import org.niis.xroad.confclient.proto.CheckAndGetConnectionStatusRequest;
import org.niis.xroad.confclient.proto.ConfigurationAnchorMessage;
import org.niis.xroad.confclient.proto.DownloadUrlConnectionStatus;
import org.niis.xroad.confclient.proto.GetGlobalConfReq;
import org.niis.xroad.confclient.proto.GetGlobalConfRespWrapped;
import org.niis.xroad.confclient.proto.GlobalConfServiceGrpc;
import org.niis.xroad.rpc.common.DiagnosticsStatus;
import org.niis.xroad.rpc.common.Empty;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class ConfClientRpcClient extends AbstractRpcClient {
    private final RpcChannelFactory rpcChannelFactory;

    private final ConfClientRpcChannelProperties rpcChannelProperties;

    private ManagedChannel channel;
    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;
    private AnchorServiceGrpc.AnchorServiceBlockingStub anchorServiceBlockingStub;
    private GlobalConfServiceGrpc.GlobalConfServiceBlockingStub globalConfServiceBlockingStub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.CONF_CLIENT;
    }

    @PostConstruct
    public void afterPropertiesSet() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        channel = rpcChannelFactory.createChannel(rpcChannelProperties);

        this.adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();
        this.anchorServiceBlockingStub = AnchorServiceGrpc.newBlockingStub(channel).withWaitForReady();
        this.globalConfServiceBlockingStub = GlobalConfServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public DiagnosticsStatus getStatus() {
        return exec(() -> adminServiceBlockingStub
                .getStatus(Empty.getDefaultInstance()));
    }

    public DownloadUrlConnectionStatus checkAndGetConnectionStatus(CheckAndGetConnectionStatusRequest request) {
        return exec(() -> adminServiceBlockingStub
                .checkAndGetConnectionStatus(request));
    }

    public GetGlobalConfRespWrapped getGlobalConf() {
        return exec(() -> globalConfServiceBlockingStub
                .getGlobalConf(GetGlobalConfReq.newBuilder().build()));
    }

    public byte[] getConfigurationAnchor() {
        return exec(() -> anchorServiceBlockingStub
                .getConfigurationAnchor(Empty.getDefaultInstance()))
                .getConfigurationAnchor().toByteArray();
    }

    public byte[] getVerificationConfZip() {
        return exec(() -> globalConfServiceBlockingStub
                .getVerificationConf(Empty.getDefaultInstance()))
                .getContent().toByteArray();
    }

    public int verifyAndSaveConfigurationAnchor(byte[] anchorBytes) {
        try {
            var response = exec(() -> anchorServiceBlockingStub
                    .verifyAndSaveConfigurationAnchor(ConfigurationAnchorMessage.newBuilder()
                            .setConfigurationAnchor(ByteString.copyFrom(anchorBytes))
                            .build()));
            return response.getReturnCode();
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Configuration anchor validation failed", e);
        }
    }

}

