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
package ee.ria.xroad.signer.protocol;

import io.grpc.Channel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.KeyServiceGrpc;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.TokenServiceGrpc;

import static ee.ria.xroad.common.SystemProperties.getGrpcInternalHost;
import static ee.ria.xroad.common.SystemProperties.getGrpcSignerPort;
import static ee.ria.xroad.common.SystemProperties.getSignerClientTimeout;

@Slf4j
public final class RpcSignerClient {
    private static RpcSignerClient instance;

    private final RpcClient<SignerRpcExecutionContext> client;

    /**
     * Construct client for accessing Signer services using the provided channel.
     */
    private RpcSignerClient(final RpcClient<SignerRpcExecutionContext> client) {
        this.client = client;
    }

    /**
     * Initialize with default settings
     *
     * @throws Exception
     */
    public static void init() throws Exception {
        init(getGrpcInternalHost(), getGrpcSignerPort(), getSignerClientTimeout());
    }

    public static void init(String host, int port, int clientTimeoutMillis) throws Exception {
        var client = RpcClient.newClient(host, port, clientTimeoutMillis, SignerRpcExecutionContext::new);
        instance = new RpcSignerClient(client);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.client.shutdown();
        }
    }

    @Getter
    public static class SignerRpcExecutionContext implements RpcClient.ExecutionContext {
        private final TokenServiceGrpc.TokenServiceBlockingStub blockingTokenService;
        private final CertificateServiceGrpc.CertificateServiceBlockingStub blockingCertificateService;
        private final KeyServiceGrpc.KeyServiceBlockingStub blockingKeyService;
        private final OcspServiceGrpc.OcspServiceBlockingStub blockingOcspService;

        public SignerRpcExecutionContext(Channel channel) {
            blockingTokenService = TokenServiceGrpc.newBlockingStub(channel).withWaitForReady();
            blockingCertificateService = CertificateServiceGrpc.newBlockingStub(channel).withWaitForReady();
            blockingKeyService = KeyServiceGrpc.newBlockingStub(channel).withWaitForReady();
            blockingOcspService = OcspServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }

    public static <V> V execute(RpcClient.RpcExecution<V, SignerRpcExecutionContext> grpcCall) throws Exception {
        return getInstance().client.execute(grpcCall);
    }


    public static RpcSignerClient getInstance() {
        if (instance == null) {
            throw new RuntimeException("RpcSignerClient is not initialized! Execute RpcSignerClient#init before using this client.");
        }
        return instance;
    }
}
