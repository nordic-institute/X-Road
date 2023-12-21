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
package org.niis.xroad.common.rpc.server;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.StartStop;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import io.grpc.netty.shaded.io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.RpcCredentialsConfigurer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Server that manages startup/shutdown of RPC server.
 */
@Slf4j
public class RpcServer implements StartStop {
    private final Server server;

    public RpcServer(final String host, final int port, final ServerCredentials creds, final Consumer<ServerBuilder<?>> configFunc) {
        final var bossGroupThreadFactory = new DefaultThreadFactory("rpc-server-nio-boss", true);
        final var workerGroupThreadFactory = new DefaultThreadFactory("rpc-server-" + port + "-nio-worker", true);

        ServerBuilder<?> builder = NettyServerBuilder.forAddress(new InetSocketAddress(host, port), creds)
                .channelType(NioServerSocketChannel.class)
                .channelFactory(NioServerSocketChannel::new)
                .bossEventLoopGroup(new NioEventLoopGroup(1, bossGroupThreadFactory))
                .workerEventLoopGroup(new NioEventLoopGroup(0, workerGroupThreadFactory))
                .executor(ForkJoinPool.commonPool());

        configFunc.accept(builder);
        server = builder.build();
    }

    @Override
    public void start() throws IOException {
        server.start();

        log.info("RPC server has started, listening on {}", server.getListenSockets());
    }

    @Override
    public void stop() throws Exception {
        if (server != null) {
            log.info("Shutting down RPC server..");
            server.shutdown();
            log.info("Shutting down RPC server.. Success!");
        }
    }

    @Override
    public void join() throws InterruptedException {
        //NO-OP
    }


    public static RpcServer newServer(String host, int port, Consumer<ServerBuilder<?>> configFunc)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        var serverCredentials = SystemProperties.isGrpcInternalTlsEnabled()
                ? RpcCredentialsConfigurer.createServerCredentials() : InsecureRpcCredentialsConfigurer.createServerCredentials();
        log.info("Initializing RPC server with {} credentials..", serverCredentials.getClass().getSimpleName());

        return new RpcServer(host, port, serverCredentials, configFunc);
    }


}
