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
package org.niis.xroad.common.rpc.server;

import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;

import java.util.List;

/**
 * Managed RPC server that supports
 */
@Slf4j
public abstract class ManagedRpcServer {
    private final List<BindableService> services;
    private final RpcServerProperties rpcServerProperties;
    private final RpcCredentialsConfigurer rpcCredentialsConfigurer;

    private RpcServer rpcServer;

    protected ManagedRpcServer(List<BindableService> services,
                               RpcServerProperties rpcServerProperties,
                               RpcCredentialsConfigurer rpcCredentialsConfigurer) {
        this.services = services;
        this.rpcServerProperties = rpcServerProperties;
        this.rpcCredentialsConfigurer = rpcCredentialsConfigurer;
    }

    public void init() throws Exception {
        if (rpcServerProperties.enabled()) {
            rpcServer = createServer();
            rpcServer.init();
        } else {
            log.info("RPC server is disabled by configuration.");
        }
    }

    public void destroy() throws Exception {
        if (rpcServer != null) {
            rpcServer.destroy();
        }
    }

    private RpcServer createServer() {
        return new RpcServer(
                rpcServerProperties.listenAddress(),
                rpcServerProperties.port(),
                rpcCredentialsConfigurer.createServerCredentials(),
                builder -> services.forEach(service -> {
                    log.info("Registering {} RPC service.", service.getClass().getSimpleName());
                    builder.addService(service);
                }));
    }
}
