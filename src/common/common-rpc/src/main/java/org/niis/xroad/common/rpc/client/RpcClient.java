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
package org.niis.xroad.common.rpc.client;

import ee.ria.xroad.common.CodedException;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.netty.shaded.io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.RpcClientProperties;
import org.niis.xroad.common.rpc.RpcCredentialsConfigurer;
import org.niis.xroad.rpc.error.CodedExceptionProto;

import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public final class RpcClient<C extends RpcClient.ExecutionContext> {
    private static final int DEFAULT_DEADLINE_MILLIS = 60 * 1000;

    private final long rpcDeadlineMillis;
    private final ManagedChannel channel;

    private final C executionContext;

    /**
     * Construct client for accessing Signer services using the provided channel.
     */
    private RpcClient(final ManagedChannel channel, final long rpcDeadlineMillis, final C executionContext) {
        this.channel = channel;
        this.rpcDeadlineMillis = rpcDeadlineMillis;
        this.executionContext = executionContext;
    }

    public static <C extends RpcClient.ExecutionContext> RpcClient<C> newClient(
            RpcClientProperties rpcClientProperties, ExecutionContextFactory<C> contextFactory) throws Exception {
        return newClient(rpcClientProperties, DEFAULT_DEADLINE_MILLIS, contextFactory);
    }

    public static <C extends RpcClient.ExecutionContext> RpcClient<C> newClient(
            RpcClientProperties rpcClientProperties, int clientTimeoutMillis,
            ExecutionContextFactory<C> contextFactory) throws Exception {
        var credentials = rpcClientProperties.isGrpcTlsEnabled()
                ? RpcCredentialsConfigurer.createClientCredentials(rpcClientProperties)
                : InsecureRpcCredentialsConfigurer.createClientCredentials();
        var host = rpcClientProperties.getGrpcHost();
        var port = rpcClientProperties.getGrpcPort();

        log.info("Starting grpc client to {}:{} with {} credentials..", host, port, credentials.getClass().getSimpleName());

        final ClientInterceptor timeoutInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return next.newCall(method, callOptions.withDeadlineAfter(clientTimeoutMillis, MILLISECONDS));
            }
        };

        final var workerGroupThreadFactory = new DefaultThreadFactory("rpc-client-" + port + "-nio-worker", true);
        final ManagedChannel channel = NettyChannelBuilder.forAddress(host, port, credentials)
                .executor(ForkJoinPool.commonPool())
                .channelType(NioSocketChannel.class)
                .channelFactory(NioSocketChannel::new)
                .eventLoopGroup(new NioEventLoopGroup(0, workerGroupThreadFactory))
                .intercept(timeoutInterceptor)
                .build();

        var executionContext = contextFactory.createContext(channel);
        return new RpcClient<>(channel, clientTimeoutMillis, executionContext);
    }

    public void shutdown() {
        if (channel.isShutdown()) {
            log.warn("gRPC client is already shutdown!");
        } else {
            channel.shutdown();
        }
    }

    public void executeAsync(AsyncRpcExecution<C> grpcCall) {
        grpcCall.exec(executionContext);
    }

    public <V> V execute(RpcExecution<V, C> grpcCall) throws Exception {
        try {
            return grpcCall.exec(executionContext);
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                executionContext.handleTimeout(rpcDeadlineMillis);
            }
            com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(error);
            if (status != null) {
                handleGenericStatusRuntimeException(status, executionContext);
            }
            throw error;
        }
    }

    private void handleGenericStatusRuntimeException(com.google.rpc.Status status, ExecutionContext context) {
        for (Any any : status.getDetailsList()) {
            if (any.is(CodedExceptionProto.class)) {
                try {
                    final CodedExceptionProto ce = any.unpack(CodedExceptionProto.class);
                    var codedException = CodedException.tr(ce.getFaultCode(), ce.getTranslationCode(), ce.getFaultString());
                    context.getErrorPrefix()
                            .ifPresent(codedException::withPrefix);

                    throw codedException;
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException("Failed to parse grpc message", e);
                }
            }
        }
    }

    @FunctionalInterface
    public interface RpcExecution<V, C extends ExecutionContext> {
        /**
         * Computes a result, or throws an exception if unable to do so.
         *
         * @return computed result
         */
        V exec(C ctx) throws Exception;
    }

    @FunctionalInterface
    public interface AsyncRpcExecution<C extends ExecutionContext> {
        /**
         * Computes a result, or throws an exception if unable to do so.
         */
        void exec(C ctx);
    }

    public interface ExecutionContextFactory<C extends ExecutionContext> {
        C createContext(Channel channel);
    }

    public interface ExecutionContext {

        default Optional<String> getErrorPrefix() {
            return Optional.empty();
        }

        default void handleTimeout(long rpcDeadlineMillis) {
            var codedException = new CodedException(X_INTERNAL_ERROR, "rpc_client_timeout",
                    "RPC client timed out. Deadline: " + rpcDeadlineMillis + " ms");
            getErrorPrefix().ifPresent(codedException::withPrefix);
            throw codedException;
        }
    }
}
