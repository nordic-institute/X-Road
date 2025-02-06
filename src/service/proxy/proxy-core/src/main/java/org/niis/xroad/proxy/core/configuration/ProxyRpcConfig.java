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
package org.niis.xroad.proxy.core.configuration;

import io.grpc.BindableService;
import io.quarkus.runtime.Startup;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.proxy.core.addon.AddOn;
import org.niis.xroad.proxy.core.admin.AdminService;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ProxyRpcConfig {

    @ApplicationScoped
    @Startup
    RpcServer proxyRpcServer(AddOn.BindableServiceRegistry bindableServiceRegistry,
                             AdminService adminService,
                             ProxyRpcServerProperties rpcServerProperties,
                             RpcCredentialsConfigurer rpcCredentialsConfigurer) throws Exception {
        List<BindableService> rpcServices = new ArrayList<>(bindableServiceRegistry.getRegisteredServices());
        rpcServices.add(adminService);
        var rpcServer =  new RpcServer(
                rpcServerProperties.listenAddress(),
                rpcServerProperties.port(),
                rpcCredentialsConfigurer.createServerCredentials(),
                builder -> rpcServices.forEach(bindableService -> {
                    log.info("Registering {} RPC service.", bindableService.getClass().getSimpleName());
                    builder.addService(bindableService);
                }));
        if (rpcServerProperties.enabled()) {
            rpcServer.afterPropertiesSet();
        }
        return rpcServer;
    }

    @ConfigMapping(prefix = "xroad.proxy.rpc")
    public interface ProxyRpcServerProperties extends RpcServerProperties {
        @WithName("enabled")
        @WithDefault("true")
        @Override
        boolean enabled();

        @WithName("listen-address")
        @WithDefault("127.0.0.1")
        @Override
        String listenAddress();

        @WithName("port")
        @WithDefault("5567")
        @Override
        int port();
    }

    // todo will be removed after signer migration
    @ApplicationScoped
    SignerRpcClient signerRpcClient() throws Exception {
        var signerRpcClient = new SignerRpcClient();
        signerRpcClient.init();
        return signerRpcClient;
    }

}
