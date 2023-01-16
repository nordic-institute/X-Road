package org.niis.xroad.signer.grpc;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;

import static org.niis.xroad.signer.grpc.ServerCredentialsConfigurer.createServerCredentials;

@Slf4j
public class RpcServer {
    private Server server;

    private final int port;
    private final ServerCredentials creds;

    public RpcServer(int port, ServerCredentials creds) {
        this.port = port;
        this.creds = creds;
    }

    private void start(Consumer<ServerBuilder<?>> configFunc) throws IOException {
        ServerBuilder<?> builder = Grpc.newServerBuilderForPort(port, creds);
        configFunc.accept(builder);

        server = builder.build()
                .start();
        log.info("Server started, listening on " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            log.info("*** shutting down gRPC server since JVM is shutting down");
            RpcServer.this.stop();
            log.info("*** server shut down");
        }));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public static void init(int port, Consumer<ServerBuilder<?>> configFunc) throws IOException {
        final RpcServer server = new RpcServer(port, createServerCredentials());
        server.start(configFunc);
    }


}
