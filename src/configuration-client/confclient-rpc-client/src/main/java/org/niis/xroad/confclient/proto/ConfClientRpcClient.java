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

import ee.ria.xroad.common.SystemProperties;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import lombok.Getter;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class ConfClientRpcClient implements DisposableBean, InitializingBean {

    private RpcClient<ConfClientRpcExecutionContext> rpcClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.rpcClient = RpcClient.newClient(SystemProperties.getConfigurationClientGrpcHost(),
                SystemProperties.getConfigurationClientGrpcPort(), ConfClientRpcExecutionContext::new);
    }

    public void execute() throws Exception {
        rpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .execute(Empty.getDefaultInstance()));
    }

    public DiagnosticsStatus getStatus() throws Exception {
        return rpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getStatus(Empty.getDefaultInstance()));
    }

    public GetGlobalConfResp getGlobalConf() throws Exception {
        return rpcClient.execute(ctx -> ctx.getGlobalConfServiceBlockingStub()
                .getGlobalConf(GetGlobalConfReq.newBuilder().build()));
    }

    @Override
    public void destroy() {
        rpcClient.shutdown();
    }

    public int verifyInternalConfiguration(byte[] configurationAnchor) {
        try {
            var response = rpcClient.execute(ctx -> ctx.getVerifierServiceBlockingStub()
                    .verifyInternalConf(VerifyInternalConfRequest.newBuilder()
                            .setConfigurationAnchor(ByteString.copyFrom(configurationAnchor))
                            .build()));
            return response.getReturnCode();
        } catch (Exception e) {
            throw new RuntimeException("Configuration anchor validation failed", e);
        }
    }

    @Getter
    private static class ConfClientRpcExecutionContext implements RpcClient.ExecutionContext {
        private final AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;
        private final VerifierServiceGrpc.VerifierServiceBlockingStub verifierServiceBlockingStub;
        private final GlobalConfServiceGrpc.GlobalConfServiceBlockingStub globalConfServiceBlockingStub;

        ConfClientRpcExecutionContext(Channel channel) {
            this.adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();
            this.verifierServiceBlockingStub = VerifierServiceGrpc.newBlockingStub(channel).withWaitForReady();
            this.globalConfServiceBlockingStub = GlobalConfServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }

}

