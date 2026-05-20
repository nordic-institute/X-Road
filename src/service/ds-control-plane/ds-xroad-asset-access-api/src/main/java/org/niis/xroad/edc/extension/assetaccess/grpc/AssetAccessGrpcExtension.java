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
package org.niis.xroad.edc.extension.assetaccess.grpc;

import io.grpc.ServerCredentials;
import jakarta.enterprise.inject.spi.CDI;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * EDC ServiceExtension that hosts a gRPC server for the AssetAccessService.
 * Manages RpcServer lifecycle: creates in start(), destroys in shutdown().
 */
@Slf4j
@Extension(value = AssetAccessGrpcExtension.EXTENSION_NAME)
public class AssetAccessGrpcExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Asset Access gRPC Extension";

    @Setting(key = "xroad.asset-access.rpc.port", description = "gRPC server port for asset access service", defaultValue = "5460")
    private int rpcPort;

    @Setting(key = "xroad.asset-access.rpc.host", description = "gRPC server listen address", defaultValue = "0.0.0.0")
    private String rpcHost;

    @Inject
    private AssetAccessOrchestrator assetAccessOrchestrator;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private Monitor monitor;

    private RpcServer rpcServer;
    private AssetAccessGrpcService grpcService;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext extensionContext) {
        var responseHandler = new RpcResponseHandler();
        grpcService = new AssetAccessGrpcService(
                assetAccessOrchestrator, participantContextService, responseHandler);
        monitor.info("Initialized extension: " + EXTENSION_NAME);
    }

    @Override
    public void start() {
        var credentials = resolveServerCredentials();

        rpcServer = new RpcServer(rpcHost, rpcPort, credentials,
                builder -> builder.addService(grpcService));
        try {
            rpcServer.init();
            monitor.info("Started " + EXTENSION_NAME + " on " + rpcHost + ":" + rpcPort);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server", e);
        }
    }

    @Override
    public void shutdown() {
        if (rpcServer != null) {
            try {
                rpcServer.destroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitor.warning("Interrupted while shutting down gRPC server", e);
            }
        }
    }

    private ServerCredentials resolveServerCredentials() {
        return resolveServerCredentials(() -> CDI.current().select(RpcCredentialsConfigurer.class).get());
    }

    // Package-private seam for unit testing without CDI.current() mocking (see AssetAccessGrpcExtensionTest).
    ServerCredentials resolveServerCredentials(Supplier<RpcCredentialsConfigurer> configurerSupplier) {
        try {
            return configurerSupplier.get().createServerCredentials();
        } catch (Exception e) {
            monitor.severe("%s failed to resolve %s from CDI — gRPC server cannot start"
                    .formatted(EXTENSION_NAME, RpcCredentialsConfigurer.class.getSimpleName()), e);
            throw XrdRuntimeException.systemException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "%s failed to resolve %s from CDI",
                    EXTENSION_NAME,
                    RpcCredentialsConfigurer.class.getSimpleName());
        }
    }
}
