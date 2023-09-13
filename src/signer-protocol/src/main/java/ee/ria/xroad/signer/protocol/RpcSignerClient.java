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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CodedExceptionProto;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.CertificateServiceGrpc;
import org.niis.xroad.signer.proto.KeyServiceGrpc;
import org.niis.xroad.signer.proto.OcspServiceGrpc;
import org.niis.xroad.signer.proto.TokenServiceGrpc;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.SystemProperties.getGrpcSignerHost;
import static ee.ria.xroad.common.SystemProperties.getGrpcSignerPort;
import static ee.ria.xroad.common.SystemProperties.getSignerClientTimeout;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.niis.xroad.signer.grpc.ServerCredentialsConfigurer.createClientCredentials;

@Slf4j
public final class RpcSignerClient {
    private static RpcSignerClient instance;

    private final ManagedChannel channel;
    private final ExecutionContext executionContext;

    /**
     * Construct client for accessing Signer services using the provided channel.
     */
    private RpcSignerClient(final ManagedChannel channel, int clientTimeoutMillis) {
        this.channel = channel;
        this.executionContext = new ExecutionContext(channel, clientTimeoutMillis);
    }

    /**
     * Initialize with default settings
     *
     * @throws Exception
     */
    public static void init() throws Exception {
        init(getGrpcSignerHost(), getGrpcSignerPort(), getSignerClientTimeout());
    }

    public static void init(String host, int port, int clientTimeoutMillis) throws Exception {
        var credentials = createClientCredentials();
        log.info("Starting grpc client with {} credentials..", credentials.getClass().getSimpleName());
        ManagedChannel channel = Grpc.newChannelBuilderForAddress(host, port, credentials)
                .build();

        instance = new RpcSignerClient(channel, clientTimeoutMillis);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.channel.shutdown();
        }
    }

    public static class ExecutionContext {
        public final TokenServiceGrpc.TokenServiceBlockingStub blockingTokenService;
        public final CertificateServiceGrpc.CertificateServiceBlockingStub blockingCertificateService;
        public final KeyServiceGrpc.KeyServiceBlockingStub blockingKeyService;
        public final OcspServiceGrpc.OcspServiceBlockingStub blockingOcspService;

        public ExecutionContext(final Channel channel, int clientTimeoutMillis) {
            blockingTokenService = TokenServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(clientTimeoutMillis, MILLISECONDS);
            blockingCertificateService = CertificateServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(clientTimeoutMillis, MILLISECONDS);
            blockingKeyService = KeyServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(clientTimeoutMillis, MILLISECONDS);
            blockingOcspService = OcspServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(clientTimeoutMillis, MILLISECONDS);
        }
    }

    public static <V> V execute(RpcExecution<V> grpcCall) throws Exception {
        try {
            return grpcCall.exec(getInstance().executionContext);
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw CodedException.tr(SIGNER_X, "signer_client_timeout", "Signer client timed out")
                        .withPrefix(SIGNER_X);
            }
            com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(error);
            if (status != null) {
                for (Any any : status.getDetailsList()) {
                    if (any.is(CodedExceptionProto.class)) {
                        try {
                            final CodedExceptionProto ce = any.unpack(CodedExceptionProto.class);
                            throw CodedException.tr(ce.getFaultCode(), ce.getTranslationCode(), ce.getFaultString())
                                    .withPrefix(SIGNER_X);
                        } catch (InvalidProtocolBufferException e) {
                            throw new RuntimeException("Failed to parse grpc message", e);
                        }
                    }
                }
            }
            throw error;
        }
    }

    @FunctionalInterface
    public interface RpcExecution<V> {
        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         * @throws Exception if unable to compute a result
         */
        V exec(ExecutionContext ctx) throws Exception;
    }

    public static RpcSignerClient getInstance() {
        if (instance == null) {
            throw new RuntimeException("RpcSignerClient is not initialized! Execute RpcSignerClient#init before using this client.");
        }
        return instance;
    }
}
