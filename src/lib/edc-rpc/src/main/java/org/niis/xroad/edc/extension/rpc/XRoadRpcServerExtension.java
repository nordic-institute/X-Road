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
package org.niis.xroad.edc.extension.rpc;

import io.grpc.ServerCredentials;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;

import java.io.IOException;

/**
 * Hosts a single X-Road gRPC server shared by all gRPC service extensions in this EDC runtime.
 *
 * <p>The {@link GrpcServiceRegistry} is provided to other extensions, which register their services
 * during EDC {@code initialize()}. The server is built and started in {@code start()} with every
 * registered service. If no service registered, no server is started.</p>
 */
@Provides(GrpcServiceRegistry.class)
@Extension(value = XRoadRpcServerExtension.EXTENSION_NAME)
public class XRoadRpcServerExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road shared gRPC server";

    @Setting(key = "xroad.ds.rpc.port", description = "Shared gRPC server port for this EDC runtime", defaultValue = "5460")
    private int rpcPort;

    @Setting(key = "xroad.ds.rpc.host", description = "Shared gRPC server listen address", defaultValue = "127.0.0.1")
    private String rpcHost;

    @Inject
    private RpcCredentialsConfigurer credentialsConfigurer;

    @Inject
    private Monitor monitor;

    private final DefaultGrpcServiceRegistry registry = new DefaultGrpcServiceRegistry();

    private RpcServer rpcServer;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    /**
     * Provides the registry that other extensions use to register their gRPC services.
     *
     * @return the shared gRPC service registry for this runtime
     */
    @Provider
    public GrpcServiceRegistry grpcServiceRegistry() {
        return registry;
    }

    @Override
    public void start() {
        var services = registry.services();
        if (services.isEmpty()) {
            monitor.info(EXTENSION_NAME + ": no gRPC services registered, server not started");
            return;
        }

        var credentials = resolveServerCredentials();
        rpcServer = new RpcServer(rpcHost, rpcPort, credentials,
                builder -> services.forEach(builder::addService));
        try {
            rpcServer.init();
            monitor.info("Started %s on %s:%d with %d service(s)".formatted(EXTENSION_NAME, rpcHost, rpcPort, services.size()));
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(
                    ErrorCode.INTERNAL_ERROR, e, "Failed to start %s", EXTENSION_NAME);
        }
    }

    @Override
    public void shutdown() {
        if (rpcServer != null) {
            try {
                rpcServer.destroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitor.warning("Interrupted while shutting down " + EXTENSION_NAME, e);
            } catch (RuntimeException e) {
                monitor.severe("Failed to shut down " + EXTENSION_NAME, e);
            }
        }
    }

    ServerCredentials resolveServerCredentials() {
        try {
            return credentialsConfigurer.createServerCredentials();
        } catch (Exception e) {
            monitor.severe("%s failed to build server credentials — gRPC server cannot start"
                    .formatted(EXTENSION_NAME), e);
            throw XrdRuntimeException.systemException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "%s failed to build server credentials",
                    EXTENSION_NAME);
        }
    }
}
