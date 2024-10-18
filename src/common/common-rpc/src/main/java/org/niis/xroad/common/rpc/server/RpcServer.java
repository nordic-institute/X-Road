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
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Consumer;

/**
 * Server that manages startup/shutdown of RPC server.
 */
@Slf4j
public class RpcServer implements InitializingBean, DisposableBean {
    private final Server server;

    public RpcServer(final String host, final int port, final ServerCredentials creds, final Consumer<ServerBuilder<?>> configFunc) {
        final var bossGroupThreadFactory = new DefaultThreadFactory("rpc-server-nio-boss", true);
        final var workerGroupThreadFactory = new DefaultThreadFactory("rpc-server-" + port + "-nio-worker", true);

        ServerBuilder<?> builder = NettyServerBuilder.forAddress(new InetSocketAddress(host, port), creds)
                .channelType(NioServerSocketChannel.class)
                .channelFactory(NioServerSocketChannel::new)
                .bossEventLoopGroup(new NioEventLoopGroup(1, bossGroupThreadFactory))
                .workerEventLoopGroup(new NioEventLoopGroup(0, workerGroupThreadFactory))
                .executor(new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
                        new SpringAwareThreadFactory(), null, true));

        configFunc.accept(builder);

        server = builder.build();
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        server.start();

        log.info("RPC server has started, listening on {}", server.getListenSockets());
    }

    @Override
    public void destroy() throws Exception {
        if (server != null) {
            log.info("Shutting down RPC server..");
            server.shutdown();
            log.info("Shutting down RPC server.. Success!");
        }
    }

    public static RpcServer newServer(RpcServerProperties serverProperties, Consumer<ServerBuilder<?>> configFunc)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

        var serverCredentials = serverProperties.isGrpcTlsEnabled()
                ? RpcCredentialsConfigurer.createServerCredentials(serverProperties)
                : InsecureRpcCredentialsConfigurer.createServerCredentials();
        return new RpcServer(serverProperties.getGrpcListenAddress(), serverProperties.getGrpcPort(), serverCredentials, configFunc);
    }

    /**
     * TODO Workaround for spring boot classloader issue.
     * Remove this when fixed in spring boot.
     * https://github.com/spring-projects/spring-boot/issues/39843
     */
    private static class SpringAwareThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        @Override
        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new MyForkJoinWorkerThread(pool);
        }

        private static class MyForkJoinWorkerThread extends ForkJoinWorkerThread {
            private MyForkJoinWorkerThread(final ForkJoinPool pool) {
                super(pool);
                setContextClassLoader(Thread.currentThread().getContextClassLoader());
            }
        }
    }
}
