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
package org.niis.xroad.confclient.proto;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import lombok.Getter;
import org.niis.xroad.common.rpc.RpcClientProperties;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.rpc.common.Empty;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class ConfClientRpcClient implements DisposableBean, InitializingBean {

    private RpcClient<ConfClientRpcExecutionContext> rpcClient;
    private final RpcClientProperties rpcClientProperties;

    public ConfClientRpcClient(RpcClientProperties rpcClientProperties) {
        this.rpcClientProperties = rpcClientProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.rpcClient = RpcClient.newClient(rpcClientProperties, ConfClientRpcExecutionContext::new);
    }

    public DiagnosticsStatus getStatus() throws Exception {
        return rpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getStatus(Empty.getDefaultInstance()));
    }

    public GetGlobalConfResp getGlobalConf() throws Exception {
        return rpcClient.execute(ctx -> ctx.getGlobalConfServiceBlockingStub()
                .getGlobalConf(GetGlobalConfReq.newBuilder().build()));
    }

    public byte[] getConfigurationAnchor() throws Exception {
        return rpcClient.execute(ctx -> ctx.getAnchorServiceBlockingStub()
                        .getConfigurationAnchor(Empty.getDefaultInstance()))
                .getConfigurationAnchor().toByteArray();
    }

    public int verifyAndSaveConfigurationAnchor(byte[] anchorBytes) {
        try {
            var response = rpcClient.execute(ctx -> ctx.getAnchorServiceBlockingStub()
                    .verifyAndSaveConfigurationAnchor(ConfigurationAnchorMessage.newBuilder()
                            .setConfigurationAnchor(ByteString.copyFrom(anchorBytes))
                            .build()));
            return response.getReturnCode();
        } catch (Exception e) {
            throw new RuntimeException("Configuration anchor validation failed", e);
        }
    }

    @Override
    public void destroy() {
        rpcClient.shutdown();
    }

    @Getter
    private static class ConfClientRpcExecutionContext implements RpcClient.ExecutionContext {
        private final AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;
        private final AnchorServiceGrpc.AnchorServiceBlockingStub anchorServiceBlockingStub;
        private final GlobalConfServiceGrpc.GlobalConfServiceBlockingStub globalConfServiceBlockingStub;

        ConfClientRpcExecutionContext(Channel channel) {
            this.adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();
            this.anchorServiceBlockingStub = AnchorServiceGrpc.newBlockingStub(channel).withWaitForReady();
            this.globalConfServiceBlockingStub = GlobalConfServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }

}

