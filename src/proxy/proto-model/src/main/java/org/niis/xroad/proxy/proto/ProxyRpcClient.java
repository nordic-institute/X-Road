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

package org.niis.xroad.proxy.proto;

import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.SystemProperties;

import io.grpc.Channel;
import lombok.Getter;
import org.niis.xroad.common.rpc.client.RpcClient;

public class ProxyRpcClient {
    private final RpcClient<ProxyRpcExecutionContext> proxyRpcClient;

    public ProxyRpcClient() throws Exception {
        this.proxyRpcClient = RpcClient.newClient(SystemProperties.getGrpcInternalHost(),
                SystemProperties.getProxyGrpcPort(), ProxyRpcExecutionContext::new);
    }

    public void shutdown() {
        proxyRpcClient.shutdown();
    }

    public BackupEncryptionStatusDiagnostics getBackupEncryptionStatus() throws Exception {
        var response = proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getBackupEncryptionStatus(Empty.newBuilder().build()));
        return new BackupEncryptionStatusDiagnostics(
                response.getBackupEncryptionStatus(),
                response.getBackupEncryptionKeysList());
    }

    @Getter
    private static class ProxyRpcExecutionContext implements RpcClient.ExecutionContext {
        private final AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

        ProxyRpcExecutionContext(Channel channel) {
            adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }
}
